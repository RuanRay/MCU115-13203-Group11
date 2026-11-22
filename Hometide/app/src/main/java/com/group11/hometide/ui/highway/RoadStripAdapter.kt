package com.group11.hometide.ui.highway

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.group11.hometide.R
import com.group11.hometide.data.repository.RoadLevel
import com.group11.hometide.data.repository.RoadSegment
import com.group11.hometide.databinding.ItemRoadNodeBinding
import com.group11.hometide.databinding.ItemRoadSegmentBinding

/** 沿路路況路線圖（FR-HWY-03）：交流道節點與路段色條交錯排列 */
class RoadStripAdapter : ListAdapter<RoadStripAdapter.Row, RecyclerView.ViewHolder>(DIFF) {

    sealed interface Row {
        data class Node(val name: String) : Row
        data class Segment(val segment: RoadSegment) : Row
    }

    fun submitSegments(segments: List<RoadSegment>) = submitList(rowsOf(segments))

    override fun getItemViewType(position: Int) = if (getItem(position) is Row.Node) NODE else SEGMENT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == NODE) {
            NodeHolder(ItemRoadNodeBinding.inflate(inflater, parent, false))
        } else {
            SegmentHolder(ItemRoadSegmentBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is Row.Node -> (holder as NodeHolder).binding.tvRoadIc.text = row.name
            is Row.Segment -> {
                val b = (holder as SegmentHolder).binding
                val ctx = b.root.context
                val s = row.segment
                b.vRoadBar.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, levelColor(s.level)))
                // 封閉：level 為 JAM（紅色），時速位置顯示「封閉」；無資料：灰色「—」
                b.tvRoadSpeed.text = when {
                    s.closed -> ctx.getString(R.string.hwy_closed)
                    s.speedKmh == null -> ctx.getString(R.string.fare_unknown)
                    else -> ctx.getString(R.string.hwy_speed, s.speedKmh)
                }
                val textColor = if (!s.closed && s.speedKmh == null) R.color.color_on_surface_muted else levelColor(s.level)
                b.tvRoadSpeed.setTextColor(ContextCompat.getColor(ctx, textColor))
            }
        }
    }

    private class NodeHolder(val binding: ItemRoadNodeBinding) : RecyclerView.ViewHolder(binding.root)

    private class SegmentHolder(val binding: ItemRoadSegmentBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private const val NODE = 0
        private const val SEGMENT = 1

        /** 起點節點，之後每段接「路段、終點節點」 */
        fun rowsOf(segments: List<RoadSegment>): List<Row> =
            if (segments.isEmpty()) emptyList()
            else listOf(Row.Node(segments.first().fromIc)) + segments.flatMap { listOf(Row.Segment(it), Row.Node(it.toIc)) }

        /** FR-HWY-03：順暢＝color_success、車多＝color_accent、壅塞＝color_stamp、無資料＝color_outline */
        @ColorRes
        fun levelColor(level: RoadLevel): Int = when (level) {
            RoadLevel.SMOOTH -> R.color.color_success
            RoadLevel.BUSY -> R.color.color_accent
            RoadLevel.JAM -> R.color.color_stamp
            RoadLevel.UNKNOWN -> R.color.color_outline
        }

        private val DIFF = object : DiffUtil.ItemCallback<Row>() {
            override fun areItemsTheSame(a: Row, b: Row) = a == b || (a is Row.Segment && b is Row.Segment && a.segment.fromIc == b.segment.fromIc)
            override fun areContentsTheSame(a: Row, b: Row) = a == b
        }
    }
}
