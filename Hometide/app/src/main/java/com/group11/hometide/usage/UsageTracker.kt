package com.group11.hometide.usage

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.group11.hometide.data.local.dao.UsageDao
import com.group11.hometide.data.local.entity.AppSession
import com.group11.hometide.data.local.entity.PageVisit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * 使用紀錄（FR-USG-01～03）：註冊在 ProcessLifecycleOwner 上記錄 app_session；頁面進出由 BaseActivity 與 MainActivity（分頁）呼叫。
 * 寫入一律排進單一執行緒佇列，依呼叫順序執行（開 session 一定早於該 session 的頁面紀錄）。
 */
class UsageTracker(
    private val dao: UsageDao,
    private val deviceId: suspend () -> String,
    private val uid: () -> String?,
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
    private val now: () -> Long = System::currentTimeMillis,
) : DefaultLifecycleObserver {

    private val queue = CoroutineScope(scope.coroutineContext + dispatcher)

    // 只在佇列內讀寫
    private var sessionId: Long? = null

    // 使用中的這次 session（統計時另外加上，P6 才不會顯示 0 分）
    @Volatile private var live: Pair<Long, String?>? = null

    // 只在主執行緒讀寫
    private var page: Pair<String, Long>? = null

    /** FR-USG-01：App 回到前景；先修補上次異常關閉的 session（FR-USG-03） */
    override fun onStart(owner: LifecycleOwner) {
        val start = now()
        val u = uid()
        live = start to u
        enqueue {
            dao.fixUnclosed()
            sessionId = dao.openSession(AppSession(uid = u, deviceId = deviceId(), startAt = start))
        }
    }

    /** FR-USG-01：App 進到背景 */
    override fun onStop(owner: LifecycleOwner) {
        val end = now()
        live = null
        enqueue {
            sessionId?.let { dao.closeSession(it, end) }
            sessionId = null
        }
    }

    /** FR-USG-02：進入頁面（Activity 的 onResume，或切換到某個分頁） */
    fun enterPage(name: String) {
        page = name to now()
    }

    /** FR-USG-02：離開頁面；停留少於 [MIN_VISIT_MS] 不記錄 */
    fun leavePage() {
        val (name, enter) = page ?: return
        page = null
        val leave = now()
        if (leave - enter < MIN_VISIT_MS) return
        val u = uid()
        enqueue {
            val sid = sessionId ?: return@enqueue
            dao.insertVisit(PageVisit(sessionId = sid, uid = u, pageName = name, enterAt = enter, leaveAt = leave, durationMs = leave - enter))
        }
    }

    /** 使用中的這次 session 若屬於 [forUid] 且在 [from] 之後開始，回傳到目前為止的毫秒數 */
    fun liveSessionMs(forUid: String?, from: Long): Long =
        live?.takeIf { (start, u) -> start >= from && u == forUid }?.let { now() - it.first } ?: 0L

    private fun enqueue(block: suspend () -> Unit) {
        queue.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 使用紀錄失敗不影響使用
                Log.w(TAG, "Usage write failed ${e.javaClass.simpleName}")
            }
        }
    }

    companion object {
        private const val TAG = "UsageTracker"
        const val MIN_VISIT_MS = 300L
    }
}
