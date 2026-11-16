package com.group11.hometide.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.group11.hometide.R
import com.group11.hometide.data.local.dao.RecentSearch
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.databinding.ItemRecentSearchBinding
import com.group11.hometide.util.labelRes
import com.group11.hometide.util.monthDayWeekday
import java.time.LocalDate

/** P3 最近查詢（FR-SRCH-07）：點一筆以今天查詢、長按刪除；顯示乘車日期，非預設運具加括號（實作者確認） */
class RecentAdapter(
    private val onClick: (RecentSearch) -> Unit,
    private val onLongClick: (RecentSearch) -> Unit,
) : ListAdapter<RecentSearch, RecentAdapter.Holder>(Diff) {

    var defaultOperator: Operator = Operator.TRA
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    class Holder(val binding: ItemRecentSearchBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemRecentSearchBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        val ctx = holder.itemView.context
        holder.binding.tvRecentRoute.text = if (item.operator == defaultOperator) {
            ctx.getString(R.string.recent_route, item.originName, item.destName)
        } else {
            ctx.getString(R.string.recent_route_operator, item.originName, item.destName, ctx.getString(item.operator.labelRes))
        }
        holder.binding.tvRecentDate.text = runCatching { ctx.monthDayWeekday(LocalDate.parse(item.travelDate)) }.getOrDefault("")
        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener {
            onLongClick(item)
            true
        }
    }

    private object Diff : DiffUtil.ItemCallback<RecentSearch>() {
        override fun areItemsTheSame(a: RecentSearch, b: RecentSearch) =
            a.operator == b.operator && a.originId == b.originId && a.destId == b.destId
        override fun areContentsTheSame(a: RecentSearch, b: RecentSearch) = a == b
    }
}
