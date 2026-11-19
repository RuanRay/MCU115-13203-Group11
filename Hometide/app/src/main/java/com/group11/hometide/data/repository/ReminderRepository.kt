package com.group11.hometide.data.repository

import android.util.Log
import com.group11.hometide.data.local.dao.ReminderDao
import com.group11.hometide.data.local.entity.ReminderStatus
import com.group11.hometide.data.local.entity.TimetableCache
import com.group11.hometide.data.local.entity.TripReminder
import com.group11.hometide.reminder.ReminderScheduler
import com.group11.hometide.util.AppError
import com.group11.hometide.util.ReminderRules
import com.group11.hometide.util.Result
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.cancellation.CancellationException

/** 出發提醒（FR-RMD-01～03、FR-FAV-05、FR-BUS-05）：Room trip_reminder＋AlarmManager */
class ReminderRepository(
    private val dao: ReminderDao,
    private val scheduler: ReminderScheduler,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** 已預約與已提醒（不含已取消），依發車時間排序 */
    fun observe(uid: String): Flow<List<TripReminder>> = dao.active(uid)

    /**
     * FR-RMD-01：寫入 SCHEDULED 並排程；同一車次同一天已有提醒時更新原提醒。
     * [typeName] 鐵路為車種（例：自強），國道客運為「業者 路線號」（FR-BUS-05）。
     */
    suspend fun set(
        uid: String,
        train: TimetableCache,
        typeName: String,
        originName: String,
        destName: String,
        beforeMin: Int,
    ): Result<TripReminder> = guarded("Reminder set failed") {
        val departAt = ReminderRules.departAt(train.travelDate, train.departTime)
        if (!ReminderRules.isAvailable(departAt, beforeMin, now())) return@guarded Result.Failure(AppError.Unknown)
        val triggerAt = ReminderRules.triggerAt(departAt, beforeMin)
        val (dayStart, dayEnd) = ReminderRules.dayRange(departAt)
        val existing = dao.findSameTrip(uid, train.operator, train.trainNo, dayStart, dayEnd)
        // 同一車次同一天：沿用原 id（PendingIntent requestCode）整列更新，起訖站不同時也會換成這次的站名
        val r = TripReminder(existing?.id ?: 0, uid, train.operator, train.trainNo, typeName, originName, destName, departAt, beforeMin, triggerAt, ReminderStatus.SCHEDULED)
        val reminder = if (existing != null) r.also { dao.update(it) } else r.copy(id = dao.insert(r))
        scheduler.schedule(reminder) // 同一 requestCode 會取代舊排程
        Result.Success(reminder)
    }

    /** FR-FAV-05：左滑取消（同時取消 AlarmManager） */
    suspend fun cancel(reminder: TripReminder): Result<Unit> = guarded("Reminder cancel failed") {
        scheduler.cancel(reminder.id)
        dao.updateStatus(reminder.id, ReminderStatus.CANCELLED)
        Result.Success(Unit)
    }

    /**
     * FR-RMD-03：ReminderReceiver 收到廣播時讀取提醒並改為 FIRED；已取消、不存在、或不屬於目前登入者 [uid] 時回 null（不發通知）。
     * uid 檢查是 FR-AUTH-05 的第二道防線：登出時取消排程失敗，也不會收到原帳號的提醒。
     */
    suspend fun fire(id: Long, uid: String?): TripReminder? = try {
        dao.get(id)?.takeIf { it.status == ReminderStatus.SCHEDULED && it.uid == uid }?.also { dao.updateStatus(id, ReminderStatus.FIRED) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Reminder fire failed: ${e.javaClass.simpleName}")
        null
    }

    /** FR-AUTH-05：登出時取消該使用者所有已預約的提醒（AlarmManager 排程與狀態） */
    suspend fun cancelAll(uid: String): Result<Unit> = guarded("Reminder cancelAll failed") {
        dao.scheduled(uid).forEach { scheduler.cancel(it.id) }
        dao.cancelScheduled(uid)
        Result.Success(Unit)
    }

    /**
     * FR-RMD-04：開機後觸發時間未過的重新排程；觸發時間已過但發車時間未過的改為 FIRED 並回傳，由 BootReceiver 立即通知。
     * 只處理目前登入者 [uid] 的提醒（未登入時不處理）。
     */
    suspend fun rescheduleAfterBoot(uid: String?): List<TripReminder> = try {
        val t = now()
        dao.pendingForBoot(t).filter { it.uid == uid }.mapNotNull { r ->
            if (r.triggerAt > t) {
                scheduler.schedule(r)
                null
            } else {
                dao.updateStatus(r.id, ReminderStatus.FIRED)
                r
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Reminder reschedule failed: ${e.javaClass.simpleName}")
        emptyList()
    }

    /** FR-FAV-05：發車超過 7 天的提醒自動移除 */
    suspend fun purgeOld(): Result<Int> = guarded("Reminder purge failed") {
        Result.Success(dao.deleteDepartedBefore(ReminderRules.purgeBefore(now())))
    }

    private inline fun <T> guarded(log: String, block: () -> Result<T>): Result<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "$log: ${e.javaClass.simpleName}")
        Result.Failure(AppError.Unknown)
    }

    private companion object {
        const val TAG = "ReminderRepository"
    }
}
