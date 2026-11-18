package com.group11.hometide.ui.next

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.group11.hometide.R
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.TimetableCache
import com.group11.hometide.databinding.ItemTicketSmallBinding
import com.group11.hometide.ui.base.asButtonForAccessibility
import com.group11.hometide.ui.result.colorRes
import com.group11.hometide.ui.result.labelRes
import com.group11.hometide.ui.result.minutes
import com.group11.hometide.util.priceText

/** 下一班「接下來的班次」小車票（FR-NEXT-04；next.png）。點擊開 P4 並捲到該班 */
class UpcomingAdapter(
    private val onClick: (TimetableCache) -> Unit,
) : ListAdapter<NextTrainViewModel.Upcoming, UpcomingAdapter.Holder>(Diff) {

    class Holder(val binding: ItemTicketSmallBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemTicketSmallBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        val t = item.train
        val b = holder.binding
        val ctx = b.root.context
        b.tvSmallTimes.text = ctx.getString(R.string.train_time, t.departTime, t.arriveTime)
        b.tvSmallName.text = ctx.getString(R.string.next_small_name, trainName(ctx, t))
        // 「準點・12 分後」「誤點 5 分・12 分後」；30 分鐘以上只顯示狀態（實作者確認）
        val status = item.status.labelRes?.let { res -> item.status.minutes?.let { ctx.getString(res, it) } ?: ctx.getString(res) }
        val soon = if (item.showMinutes) ctx.getString(R.string.next_small_minutes, item.minutes) else null
        val text = listOfNotNull(status, soon).joinToString(ctx.getString(R.string.pax_separator))
        b.tvSmallStatus.isVisible = text.isNotEmpty()
        b.tvSmallStatus.text = text
        b.tvSmallStatus.setTextColor(ContextCompat.getColor(ctx, item.status.colorRes))
        // FR-NEXT-04：票價以預設乘客計算；取不到顯示「—」
        b.tvSmallFare.text = ctx.priceText(item.fare?.total, item.fare?.approx ?: false)
        b.tkSmall.setOnClickListener { onClick(t) }
        b.tkSmall.asButtonForAccessibility()
    }

    private object Diff : DiffUtil.ItemCallback<NextTrainViewModel.Upcoming>() {
        override fun areItemsTheSame(a: NextTrainViewModel.Upcoming, b: NextTrainViewModel.Upcoming) = a.train.trainNo == b.train.trainNo
        override fun areContentsTheSame(a: NextTrainViewModel.Upcoming, b: NextTrainViewModel.Upcoming) = a == b
    }
}

/** 「自強 143」；國道客運為「業者 路線號」（FR-BUS-05） */
fun trainName(ctx: android.content.Context, t: TimetableCache): String =
    if (t.operator == Operator.BUS) {
        ctx.getString(R.string.bus_meta, t.trainTypeName.orEmpty(), t.trainTypeCode ?: t.trainNo)
    } else {
        ctx.getString(R.string.next_train_name, t.trainTypeName.orEmpty(), t.trainNo)
    }
