package com.group11.hometide.ui.routes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.group11.hometide.R
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.ReminderStatus
import com.group11.hometide.data.local.entity.TripReminder
import com.group11.hometide.databinding.ItemTicketReminderBinding
import com.group11.hometide.ui.widget.StampView
import com.group11.hometide.util.monthDayWeekday
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 出發提醒分頁（FR-FAV-05；reminders.png）。已預約：紅色「已預約」章，可左滑取消；
 * 已提醒但還沒發車：不變淡、寫「已提醒」；已發車：變淡、灰色「已使用」章（實作者確認）
 */
class ReminderAdapter(private val now: () -> Long = System::currentTimeMillis) :
    ListAdapter<TripReminder, ReminderAdapter.Holder>(Diff) {

    class Holder(val binding: ItemTicketReminderBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemTicketReminderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val r = getItem(position)
        val b = holder.binding
        val ctx = b.root.context
        val zone = ZoneId.systemDefault()
        val depart = Instant.ofEpochMilli(r.departAt).atZone(zone)
        val date = ctx.monthDayWeekday(depart.toLocalDate())
        b.tvReminderMeta.text = if (r.operator == Operator.BUS) {
            ctx.getString(R.string.reminder_meta_bus, r.trainTypeName, date)
        } else {
            ctx.getString(R.string.reminder_meta_rail, r.trainTypeName, r.trainNo, date)
        }
        b.tvReminderBefore.text = ctx.getString(R.string.reminder_before, r.remindBeforeMin)
        b.tvReminderRoute.text = ctx.getString(R.string.reminder_route, depart.format(HHMM), r.originName, r.destName)

        val used = isUsed(r, now())
        val scheduled = r.status == ReminderStatus.SCHEDULED && !used
        b.tvReminderNote.text = if (scheduled) {
            ctx.getString(R.string.reminder_note_scheduled, Instant.ofEpochMilli(r.triggerAt).atZone(zone).format(HHMM))
        } else {
            ctx.getString(R.string.reminder_note_fired)
        }
        b.tkReminder.alpha = if (used) USED_ALPHA else 1f
        b.vReminderStamp.isVisible = scheduled || used
        if (scheduled) {
            b.vReminderStamp.text = ctx.getString(R.string.stamp_reserved)
            b.vReminderStamp.tone = StampView.Tone.RED
        } else if (used) {
            b.vReminderStamp.text = ctx.getString(R.string.stamp_used)
            b.vReminderStamp.tone = StampView.Tone.GRAY
        }
    }

    /** 只有尚未發車的已預約提醒可以左滑取消 */
    fun canCancel(position: Int): Boolean =
        getItem(position).let { it.status == ReminderStatus.SCHEDULED && !isUsed(it, now()) }

    private object Diff : DiffUtil.ItemCallback<TripReminder>() {
        override fun areItemsTheSame(a: TripReminder, b: TripReminder) = a.id == b.id
        override fun areContentsTheSame(a: TripReminder, b: TripReminder) = a == b
    }

    companion object {
        private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private const val USED_ALPHA = 0.55f

        /** 已過發車時間即「已使用」（FR-FAV-05） */
        fun isUsed(r: TripReminder, now: Long): Boolean = r.departAt <= now
    }
}
