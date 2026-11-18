package com.group11.hometide.ui.result

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.group11.hometide.R
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.TimetableCache
import com.group11.hometide.databinding.ItemTimelineTrainBinding
import com.group11.hometide.databinding.ItemFareRowBinding
import com.group11.hometide.ui.base.asButtonForAccessibility
import com.group11.hometide.util.FareLine
import com.group11.hometide.util.FareTotal
import com.group11.hometide.util.labelRes
import com.group11.hometide.util.priceText

/** P4 時間軸（規格 4.6；FR-RES-01～03、07、FR-BUS-02～04）。 */
class TimelineAdapter(
    private val onRemind: (TimetableCache) -> Unit,
    private val onToggleDetail: (trainNo: String) -> Unit,
) : ListAdapter<ResultViewModel.Item, TimelineAdapter.Holder>(Diff) {

    class Holder(val binding: ItemTimelineTrainBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemTimelineTrainBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        val (train, status) = item
        val b = holder.binding
        val ctx = b.root.context
        val departed = status == TrainStatus.Departed

        b.tvHour.text = train.departTime
        b.tvTrainMeta.text = metaText(ctx, train)
        b.tvTime.text = ctx.getString(R.string.train_time, train.departTime, train.arriveTime)
        bindFare(b, item)

        val color = status.colorRes
        b.vNode.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, color))
        b.chipStatus.isVisible = status.labelRes != null
        status.labelRes?.let { res ->
            b.chipStatus.text = status.minutes?.let { ctx.getString(res, it) } ?: ctx.getString(res)
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
        // FR-RES-07：點車票本體展開／收合（「提醒」有自己的點擊，不會觸發）
        b.tkTrain.setOnClickListener { onToggleDetail(train.trainNo) }
        b.tkTrain.asButtonForAccessibility()
        ViewCompat.setStateDescription(b.tkTrain, ctx.getString(if (item.expanded) R.string.fare_detail_expanded else R.string.fare_detail_collapsed))
    }

    /**
     * FR-RES-02 存根：一種票種一人顯示單價＋票種名稱，否則總價＋組合（「全票2・孩童1」）；
     * 任一票種查無時總價「—」並加一行「<票種>查無」；任一單價為約略值時加「約」。
     */
    private fun bindFare(b: ItemTimelineTrainBinding, item: ResultViewModel.Item) {
        val ctx = b.root.context
        val fare = item.fare
        val mix = fare.lines.singleOrNull()?.takeIf { it.count == 1 }?.let { ctx.getString(it.fareClass.labelRes) }
            ?: fare.lines.joinToString(ctx.getString(R.string.pax_separator)) { ctx.getString(R.string.pax_mix_item, ctx.getString(it.fareClass.labelRes), it.count) }
        val missing = if (item.faresLoaded) missingText(ctx, fare) else null
        b.tvFare.text = ctx.priceText(if (item.faresLoaded) fare.total else null, fare.approx)
        b.tvFareMix.text = listOfNotNull(mix, missing?.let { ctx.getString(R.string.fare_missing, it) }).joinToString(LINE_BREAK)

        // FR-RES-07 明細：查無的票種單價「查無」、小計「—」；合計「—」並說明
        b.layoutFareDetail.isVisible = item.expanded
        if (!item.expanded) return
        (b.rvFareRows.adapter as? FareRowAdapter ?: FareRowAdapter().also { b.rvFareRows.adapter = it }).submit(fare.lines, item.faresLoaded)
        b.tvDetailTotal.text = ctx.priceText(if (item.faresLoaded) fare.total else null, fare.approx)
        b.tvDetailNote.isVisible = missing != null
        missing?.let { b.tvDetailNote.text = ctx.getString(R.string.fare_detail_note, typeName(item.train), it) }
    }

    /** 查無的票種，以「、」連接；沒有查無時為 null */
    private fun missingText(ctx: Context, fare: FareTotal): String? =
        fare.missing.takeIf { it.isNotEmpty() }?.joinToString(ctx.getString(R.string.fare_class_joiner)) { ctx.getString(it.labelRes) }

    /** 明細說明的「車種」：鐵路為車種名稱，國道客運為路線號 */
    private fun typeName(t: TimetableCache): String =
        if (t.operator == Operator.BUS) t.trainTypeCode ?: t.trainNo else t.trainTypeName ?: t.trainTypeCode.orEmpty()

    private fun metaText(ctx: Context, t: TimetableCache): String {
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

    private object Diff : DiffUtil.ItemCallback<ResultViewModel.Item>() {
        override fun areItemsTheSame(a: ResultViewModel.Item, b: ResultViewModel.Item) =
            a.train.trainNo == b.train.trainNo && a.train.departTime == b.train.departTime
        override fun areContentsTheSame(a: ResultViewModel.Item, b: ResultViewModel.Item) = a == b
    }

    private companion object {
        const val DEPARTED_ALPHA = 0.45f
        const val LINE_BREAK = "\n"
    }
}

/** 票價明細各列（FR-RES-07，item_fare_row）。資料少（最多 5 列），直接整份重繪 */
private class FareRowAdapter : RecyclerView.Adapter<FareRowAdapter.Holder>() {

    class Holder(val binding: ItemFareRowBinding) : RecyclerView.ViewHolder(binding.root)

    private var lines: List<FareLine> = emptyList()
    private var loaded = true

    @SuppressLint("NotifyDataSetChanged")
    fun submit(lines: List<FareLine>, loaded: Boolean) {
        this.lines = lines
        this.loaded = loaded
        notifyDataSetChanged()
    }

    override fun getItemCount() = lines.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemFareRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val line = lines[position]
        val b = holder.binding
        val ctx = b.root.context
        b.tvClass.text = ctx.getString(R.string.fare_row_class, ctx.getString(line.fareClass.labelRes), line.count)
        b.tvUnit.text = when {
            !loaded -> ctx.getString(R.string.fare_unknown)
            line.unit == null -> ctx.getString(R.string.fare_unit_missing)
            else -> ctx.priceText(line.unit, line.approx)
        }
        b.tvSubtotal.text = ctx.priceText(line.subtotal, line.approx)
    }
}
