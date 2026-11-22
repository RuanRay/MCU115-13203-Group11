package com.group11.hometide.ui.highway

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group11.hometide.R
import com.group11.hometide.data.repository.AuthRepository
import com.group11.hometide.data.repository.Freeway
import com.group11.hometide.data.repository.HighwayQuery
import com.group11.hometide.data.repository.HighwayRepository
import com.group11.hometide.data.repository.HighwayResult
import com.group11.hometide.data.repository.RoadLevel
import com.group11.hometide.data.repository.Traffic
import com.group11.hometide.util.AppError
import com.group11.hometide.util.Result
import com.group11.hometide.util.messageRes
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 國道分頁（規格 4.11；FR-HWY-02～06）。6-2a：空白車票表單＋查看路況；會員的開車路線晶片與收藏於 6-2b。
 * 實作者確認：表單留在上方、結果接在下面；表單預設照 highway_guest.png（國道 1 號、南下、桃園 → 永康）。
 */
class HighwayViewModel(
    auth: AuthRepository,
    private val repo: HighwayRepository,
) : ViewModel() {

    data class UiState(
        /** null：登入狀態還沒讀到（不顯示訪客提示卡，避免會員閃一下） */
        val isGuest: Boolean? = null,
        val freeways: List<Freeway> = emptyList(),
        val metaLoading: Boolean = true,
        @StringRes val metaErrorRes: Int? = null,
        val selection: HighwayQuery? = null,
        val loading: Boolean = false,
        val result: HighwayResult? = null,
        @StringRes val errorRes: Int? = null,
    ) {
        val freeway: Freeway? get() = freeways.firstOrNull { it.id == selection?.freewayId }
        val directions get() = freeway?.directions.orEmpty()
        private val interchanges get() = directions.firstOrNull { it.dir == selection?.direction }?.interchanges.orEmpty()
        val entries: List<String> get() = entriesOf(interchanges)
        val exits: List<String> get() = exitsOf(interchanges, selection?.entryIc)
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val messageEvents = Channel<Int>(Channel.BUFFERED)

    /** 重新整理失敗但仍顯示上一份資料時的 Snackbar（例如「查詢太頻繁」） */
    val messages = messageEvents.receiveAsFlow()

    private var loadJob: Job? = null

    /** 同一種錯誤只提示一次（每 60 秒更新時不重複跳 Snackbar），成功後重置 */
    private var notified: AppError? = null

    private suspend fun notifyOnce(error: AppError) {
        if (error == notified) return
        notified = error
        messageEvents.send(error.highwayMessageRes)
    }

    init {
        viewModelScope.launch { auth.profile.collect { p -> _state.update { it.copy(isGuest = p == null) } } }
        loadMeta()
    }

    fun loadMeta() {
        _state.update { it.copy(metaLoading = true, metaErrorRes = null) }
        viewModelScope.launch {
            when (val r = repo.freeways()) {
                is Result.Success -> {
                    val last = repo.lastQuery
                    _state.update { it.copy(metaLoading = false, freeways = r.data, selection = defaultSelection(r.data, last ?: DEFAULT)) }
                    // 同一次執行中回到國道分頁：直接顯示上次查看的路線
                    if (last != null && _state.value.result == null) showTraffic()
                }
                is Result.Failure -> _state.update { it.copy(metaLoading = false, metaErrorRes = r.error.highwayMessageRes) }
            }
        }
    }

    /** 換國道：方向取第一個、上下路取首尾。選到同一條（例如 Spinner 第一次排版時自動回報）不動作 */
    fun selectFreeway(id: String) = reselect { s ->
        if (id == s.selection?.freewayId) return@reselect null
        s.freeways.firstOrNull { it.id == id }?.let { f -> firstLast(f, f.directions.first().dir) }
    }

    fun selectDirection(dir: String) = reselect { s ->
        if (dir == s.selection?.direction) return@reselect null
        s.freeway?.let { firstLast(it, dir) }
    }

    /** 下路必須在上路之後；換上路後原本的下路不合法時改為最後一個交流道 */
    fun selectEntry(ic: String) = reselect { s ->
        val sel = s.selection ?: return@reselect null
        val exits = exitsOf(s.directions.first { it.dir == sel.direction }.interchanges, ic)
        sel.copy(entryIc = ic, exitIc = sel.exitIc.takeIf { it in exits } ?: exits.last())
    }

    fun selectExit(ic: String) = reselect { s -> s.selection?.copy(exitIc = ic) }

    private inline fun reselect(next: (UiState) -> HighwayQuery?) {
        val s = _state.value
        next(s)?.takeIf { it != s.selection }?.let { q -> _state.update { it.copy(selection = q) } }
    }

    /** 「查看路況」：查詢表單目前的路線（不儲存，FR-HWY-06） */
    fun showTraffic() {
        val q = _state.value.selection ?: return
        load(q, showLoading = true)
    }

    /** FR-HWY-05：前景時每 60 秒重新取得；尚未查看路況時不動作 */
    fun refresh() {
        val q = repo.lastQuery ?: return
        if (_state.value.result == null || loadJob?.isActive == true) return
        load(q, showLoading = false)
    }

    private fun load(q: HighwayQuery, showLoading: Boolean) {
        loadJob?.cancel()
        if (showLoading) _state.update { it.copy(loading = true, result = null, errorRes = null) }
        loadJob = viewModelScope.launch {
            when (val r = repo.load(q)) {
                is Result.Success -> {
                    // 顯示快取但錯誤不是離線（例如限流）：另以 Snackbar 告知
                    val error = r.data.error
                    if (error == null) notified = null else if (error != AppError.NetworkError) notifyOnce(error)
                    _state.update { it.copy(loading = false, result = r.data, errorRes = null) }
                }
                is Result.Failure ->
                    if (_state.value.result != null) {
                        notifyOnce(r.error)
                        _state.update { it.copy(loading = false) }
                    } else {
                        _state.update { it.copy(loading = false, errorRes = r.error.highwayMessageRes) }
                    }
            }
        }
    }

    sealed interface Title {
        /** 尚未查看路況：「看看國道塞不塞」 */
        data object Form : Title

        /** 「比平常多 N 分鐘」 */
        data class Delay(val minutes: Int) : Title

        /** 與平常相差 5 分鐘以內：「路況順暢」（FR-HWY-02） */
        data object Smooth : Title

        /** 所有路段都沒有即時資料（實作者確認）：「暫無即時路況」 */
        data object NoLive : Title
    }

    companion object {
        /** highway_guest.png 的表單預設 */
        val DEFAULT = HighwayQuery("000010", "S", "桃園", "永康")
        private const val SMOOTH_WITHIN_MIN = 5

        fun entriesOf(ics: List<String>): List<String> = ics.dropLast(1)

        fun exitsOf(ics: List<String>, entry: String?): List<String> = ics.drop(ics.indexOf(entry) + 1)

        private fun firstLast(f: Freeway, dir: String): HighwayQuery? {
            val ics = f.directions.firstOrNull { it.dir == dir }?.interchanges ?: return null
            return HighwayQuery(f.id, dir, ics.first(), ics.last())
        }

        /** [preferred] 的各欄位在清單中存在就沿用，否則國道取第一條、方向取第一個、上下路取首尾 */
        fun defaultSelection(freeways: List<Freeway>, preferred: HighwayQuery): HighwayQuery? {
            val f = freeways.firstOrNull { it.id == preferred.freewayId } ?: freeways.firstOrNull() ?: return null
            val d = f.directions.firstOrNull { it.dir == preferred.direction } ?: f.directions.first()
            val base = firstLast(f, d.dir) ?: return null
            val entry = preferred.entryIc.takeIf { it in entriesOf(d.interchanges) } ?: base.entryIc
            val exit = preferred.exitIc.takeIf { it in exitsOf(d.interchanges, entry) } ?: d.interchanges.last()
            return base.copy(entryIc = entry, exitIc = exit)
        }

        fun titleOf(t: Traffic?): Title {
            t ?: return Title.Form
            if (t.segments.all { it.level == RoadLevel.UNKNOWN }) return Title.NoLive
            val diff = t.estimatedMin - t.normalMin
            return if (diff <= SMOOTH_WITHIN_MIN) Title.Smooth else Title.Delay(diff)
        }

        /** 「壅塞 N 段」＝路線圖紅色段數（FR-HWY-02 AC、TC-35；含道路封閉）：由路段自行計算，與畫面一致 */
        fun jamCount(t: Traffic): Int = t.segments.count { it.level == RoadLevel.JAM }

        /** 11.2「國道無資料」：部分（不是全部）路段沒有即時資料時，大車票另外標示 */
        fun partialNoLive(t: Traffic): Boolean =
            t.segments.any { it.level == RoadLevel.UNKNOWN } && titleOf(t) != Title.NoLive

        /** 存根「+N 分」：比平常多才顯示；沒有即時資料時不顯示 */
        fun extraMin(t: Traffic): Int? =
            (t.estimatedMin - t.normalMin).takeIf { it > 0 && titleOf(t) != Title.NoLive }

        /** 路線不存在（NotFound）不是「查無班次」，改用一般錯誤訊息 */
        private val AppError.highwayMessageRes: Int
            get() = if (this == AppError.NotFound) R.string.error_unknown else messageRes
    }
}
