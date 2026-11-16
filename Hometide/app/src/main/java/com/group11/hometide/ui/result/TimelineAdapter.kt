package com.group11.hometide.ui.result

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.group11.hometide.R
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.TimetableCache
import com.group11.hometide.databinding.ItemTimelineTrainBinding

/** P4 時間軸（規格 4.6；FR-RES-01、03、FR-BUS-04）。票價於 4-3 接上，先顯示「—」。 */
class TimelineAdapter(
    private val onRemind: (TimetableCache) -> Unit,
) : ListAdapter<ResultViewModel.Item, TimelineAdapter.Holder>(Diff) {

    class Holder(val binding: ItemTimelineTrainBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemTimelineTrainBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val (train, status) = getItem(position)
        val b = holder.binding
        val ctx = b.root.context
        val departed = status == TrainStatus.Departed

        b.tvHour.text = train.departTime
        b.tvTrainMeta.text = metaText(ctx, train)
        b.tvTime.text = ctx.getString(R.string.train_time, train.departTime, train.arriveTime)
        b.tvFare.setText(R.string.fare_unknown)

        val color = statusColor(status)
        b.vNode.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, color))
        b.chipStatus.isVisible = status.labelRes != null
        status.labelRes?.let { res ->
            b.chipStatus.text = if (status is TrainStatus.Soon) ctx.getString(res, status.minutes) else ctx.getString(res)
            tint(b.chipStatus, color)
        }

        b.chipFacilities.removeAllViews()
        facilityTags(train).forEach { (text, tagColor) ->
            val tag = LayoutInflater.from(ctx).inflate(R.layout.item_tag, b.chipFacilities, false) as TextView
            tag.setText(text)
            tint(tag, tagColor)
            b.chipFacilities.addView(tag)
        }

        // FR-RES-03：已開出 → 車票與節點透明度 45%、隱藏提醒、蓋「已開出」章
        val alpha = if (departed) DEPARTED_ALPHA else 1f
        b.tkTrain.alpha = alpha
        b.vNode.alpha = alpha
        b.tvHour.alpha = alpha
        b.btnRemind.isVisible = !departed
        b.vDepartedStamp.isVisible = departed
        b.btnRemind.setOnClickListener { onRemind(train) }
    }

    private fun metaText(ctx: android.content.Context, t: TimetableCache): String {
        val h = t.durationMin / 60
        val m = t.durationMin % 60
        return if (t.operator == Operator.BUS) {
            // FR-BUS-04：「業者 路線號」＋「約 H:mm」
            ctx.getString(
                R.string.meta_with_extra,
                ctx.getString(R.string.bus_meta, t.trainTypeName.orEmpty(), t.trainTypeCode ?: t.trainNo),
                ctx.getString(R.string.duration_about, h, m),
            )
        } else {
            val head = ctx.getString(R.string.train_meta, t.trainTypeName.orEmpty(), t.trainNo)
            val line = when (t.tripLine) {
                1 -> ctx.getString(R.string.line_mountain)
                2 -> ctx.getString(R.string.line_sea)
                else -> null
            }
            val tail = listOfNotNull(line, ctx.getString(R.string.duration, h, m)).joinToString("・")
            ctx.getString(R.string.meta_with_extra, head, tail)
        }
    }

    private fun facilityTags(t: TimetableCache): List<Pair<Int, Int>> = buildList {
        if (t.operator == Operator.BUS) {
            if (t.arriveEstimated) add(R.string.tag_arrive_estimated to R.color.color_facility)
        } else {
            add((if (t.reservedSeat) R.string.tag_reserved else R.string.tag_unreserved) to R.color.color_facility)
            if (t.bikeAllowed) add(R.string.tag_bike to R.color.color_facility)
        }
    }

    /** 標籤：外框與字同色、底透明（backgroundTint 會連內部一起填滿，所以直接改框線色） */
    private fun tint(view: TextView, @ColorRes color: Int) {
        val c = ContextCompat.getColor(view.context, color)
        view.setTextColor(c)
        (view.background.mutate() as GradientDrawable).setStroke(view.resources.getDimensionPixelSize(R.dimen.tag_stroke), c)
    }

    /** 節點與狀態色：準點、即將發車綠；已開出灰；依班表藏青（bus_result.png）；誤點紅於 4-3 */
    @ColorRes
    private fun statusColor(s: TrainStatus): Int = when (s) {
        TrainStatus.Departed -> R.color.color_on_surface_muted
        is TrainStatus.Soon, TrainStatus.OnTime -> R.color.color_success
        TrainStatus.BySchedule, TrainStatus.None -> R.color.color_primary
    }

    private object Diff : DiffUtil.ItemCallback<ResultViewModel.Item>() {
        override fun areItemsTheSame(a: ResultViewModel.Item, b: ResultViewModel.Item) =
            a.train.trainNo == b.train.trainNo && a.train.departTime == b.train.departTime
        override fun areContentsTheSame(a: ResultViewModel.Item, b: ResultViewModel.Item) = a == b
    }

    private companion object {
        const val DEPARTED_ALPHA = 0.45f
    }
}
