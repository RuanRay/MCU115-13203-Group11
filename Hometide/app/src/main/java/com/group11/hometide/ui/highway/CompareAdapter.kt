package com.group11.hometide.ui.highway

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.group11.hometide.R
import com.group11.hometide.data.repository.CompareMode
import com.group11.hometide.data.repository.CompareOption
import com.group11.hometide.databinding.ItemTicketCompareBinding
import com.group11.hometide.ui.widget.TicketView

/**
 * 回家方式比較車票（FR-HWY-04、highway.png）：最快者主票樣式＋「最快」；
 * 沒有班次或縣市沒有此運具時，本體寫「今日已無班次」或「—」、存根「—」並淡化（實作者確認）。
 */
class CompareAdapter : ListAdapter<CompareAdapter.Item, CompareAdapter.Holder>(DIFF) {

    data class Item(val option: CompareOption, val fastest: Boolean)

    class Holder(val binding: ItemTicketCompareBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemTicketCompareBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val (o, fastest) = getItem(position)
        val b = holder.binding
        val ctx = b.root.context
        val style = if (fastest) TicketView.Style.MAIN else TicketView.Style.PAPER
        b.tkCompare.ticketStyle = style
        val text = ContextCompat.getColor(ctx, style.onBody)
        listOf(b.tvCompareLabel, b.tvCompareFastest, b.tvCompareTimes, b.tvCompareTotal, b.tvCompareNote).forEach { it.setTextColor(text) }

        b.tvCompareLabel.text = ctx.getString(
            when (o.mode) {
                CompareMode.THSR -> R.string.hwy_compare_thsr
                CompareMode.TRA -> R.string.hwy_compare_tra
                CompareMode.BUS -> R.string.hwy_compare_bus
            },
            o.label,
        )
        b.tvCompareFastest.isVisible = fastest
        val hasTrip = o.departTime != null && o.arriveTime != null && o.totalMin != null
        b.tvCompareTimes.text = if (hasTrip) ctx.getString(R.string.hwy_compare_times, o.departTime, o.arriveTime) else o.note
        b.tvCompareTotal.text = if (hasTrip) durationHm(ctx, o.totalMin!!) else ctx.getString(R.string.fare_unknown)
        b.tvCompareNote.isVisible = hasTrip
        b.tvCompareNote.text = o.note
        b.root.alpha = if (hasTrip) 1f else DIMMED_ALPHA
    }

    private companion object {
        /** 同「已使用」的提醒（實作者確認） */
        const val DIMMED_ALPHA = 0.55f

        val DIFF = object : DiffUtil.ItemCallback<Item>() {
            override fun areItemsTheSame(a: Item, b: Item) = a.option.mode == b.option.mode
            override fun areContentsTheSame(a: Item, b: Item) = a == b
        }
    }
}
