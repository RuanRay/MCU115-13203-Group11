package com.group11.hometide.ui.routes

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.group11.hometide.R
import com.group11.hometide.data.local.entity.FavoriteRoute
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.databinding.ItemTicketRouteBinding
import com.group11.hometide.ui.base.asButtonForAccessibility
import com.group11.hometide.ui.widget.TicketView
import com.group11.hometide.util.NextTrain
import com.group11.hometide.util.labelRes
import com.group11.hometide.util.priceText

/**
 * 車票夾（FR-FAV-01～04、FR-SRCH-04；routes.png、home.png）。第一張為深色主票，其餘為紙票。
 * [horizontal]＝P3 橫向：固定寬度、不顯示 ≡、存根「一鍵查詢」、不能改名。
 */
class FavoriteTicketAdapter(
    private val horizontal: Boolean,
    /** 第二個參數為被點的車票 tkRoute（AN-05 撕票、AN-03 展開） */
    private val onQuery: (FavoriteCard, View) -> Unit,
    private val onRename: (FavoriteCard) -> Unit = {},
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit = {},
) : ListAdapter<FavoriteCard, FavoriteTicketAdapter.Holder>(Diff) {

    class Holder(val binding: ItemTicketRouteBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val b = ItemTicketRouteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        if (horizontal) {
            // 橫向：一張約占 82% 寬，露出下一張提示可滑動（home.png）
            b.root.layoutParams = b.root.layoutParams.apply { width = (parent.width * HORIZONTAL_WIDTH).toInt() }
            b.root.setPaddingRelative(0, b.root.paddingTop, parent.resources.getDimensionPixelSize(R.dimen.ticket_gap), b.root.paddingBottom)
        }
        return Holder(b)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val card = getItem(position)
        val b = holder.binding
        val ctx = b.root.context
        val style = if (position == 0) TicketView.Style.MAIN else TicketView.Style.PAPER
        b.tkRoute.ticketStyle = style
        val main = color(ctx, style.onBody)
        val muted = if (style == TicketView.Style.MAIN) main.withAlpha(MUTED_ALPHA) else color(ctx, R.color.color_on_surface_muted)

        b.tvRouteMeta.text = metaText(ctx, card)
        b.tvRouteMeta.setTextColor(muted)
        b.tvRouteStations.text = ctx.getString(R.string.result_route, card.originName, card.destName)
        b.tvRouteStations.setTextColor(main)
        b.tvNextTrain.text = nextText(ctx, card.next)
        b.tvNextTrain.setTextColor(main)
        b.btnRouteQuery.setText(if (horizontal) R.string.btn_fav_quick_query else R.string.btn_route_query)
        b.btnRouteQuery.setTextColor(main)
        ImageViewCompat.setImageTintList(b.ivDragHandle, ColorStateList.valueOf(muted))

        b.ivDragHandle.isVisible = !horizontal
        b.btnRouteQuery.isVisible = true // AN-05 撕下後隱藏，重新綁定時恢復
        b.btnRouteQuery.setOnClickListener { onQuery(card, b.tkRoute) }
        b.btnRouteQuery.asButtonForAccessibility()
        if (horizontal) {
            b.tvRouteMeta.isClickable = false
            b.tkRoute.setOnClickListener(null)
            b.tkRoute.isClickable = false
        } else {
            // FR-FAV-03：點卡片查詢；點左上角名稱改名；長按 ≡ 拖曳
            b.tkRoute.setOnClickListener { onQuery(card, b.tkRoute) }
            b.tvRouteMeta.setOnClickListener { onRename(card) }
            b.ivDragHandle.setOnLongClickListener {
                onStartDrag(holder)
                true
            }
        }
    }

    // 拖曳中的順序（同步更新；currentList 要等背景 diff 完成才會更新）
    private var dragOrder: MutableList<FavoriteCard>? = null

    /** 拖曳中即時交換位置（放開後由畫面以 [takeOrder] 寫回 sort_order） */
    fun move(from: Int, to: Int) {
        if (from < 0 || to < 0) return
        val list = dragOrder ?: currentList.toMutableList().also { dragOrder = it }
        list.add(to, list.removeAt(from))
        submitList(list.toList())
    }

    fun takeOrder(): List<FavoriteCard> = (dragOrder ?: currentList).also { dragOrder = null }

    /** 未改名時只顯示運具（label 等於預設「起站 → 迄站」，實作者確認） */
    private fun metaText(ctx: Context, card: FavoriteCard): String {
        val op = ctx.getString(card.route.operator.labelRes)
        return if (card.isDefaultLabel) op else ctx.getString(R.string.route_meta, op, card.route.label)
    }

    private fun nextText(ctx: Context, next: NextTrain): String = when (next) {
        NextTrain.Loading -> ctx.getString(R.string.next_train_loading)
        NextTrain.Offline -> ctx.getString(R.string.next_train_offline)
        NextTrain.NoneToday -> ctx.getString(R.string.next_train_none)
        is NextTrain.Tomorrow -> ctx.getString(R.string.next_train_tomorrow, next.departTime)
        is NextTrain.Next -> {
            val t = next.train
            // FR-BUS-05：國道客運以「業者 路線號」取代「車種 車次」
            val name = if (t.operator == Operator.BUS) {
                ctx.getString(R.string.bus_meta, t.trainTypeName.orEmpty(), t.trainTypeCode ?: t.trainNo)
            } else {
                ctx.getString(R.string.next_train_name, t.trainTypeName.orEmpty(), t.trainNo)
            }
            // P3 橫向車票較窄，照 home.png 省略車種車次
            val base = if (horizontal) {
                ctx.getString(R.string.next_train_short, t.departTime, next.minutes)
            } else {
                ctx.getString(R.string.next_train, t.departTime, name, next.minutes)
            }
            // 票價接在後面，查無時不加（實作者確認）
            next.fare?.total?.let { ctx.getString(R.string.next_train_with_fare, base, ctx.priceText(it, next.fare.approx)) } ?: base
        }
    }

    private fun color(ctx: Context, id: Int) = ContextCompat.getColor(ctx, id)

    private fun Int.withAlpha(alpha: Int) = (this and 0x00FFFFFF) or (alpha shl 24)

    private object Diff : DiffUtil.ItemCallback<FavoriteCard>() {
        override fun areItemsTheSame(a: FavoriteCard, b: FavoriteCard) = a.route.id == b.route.id
        override fun areContentsTheSame(a: FavoriteCard, b: FavoriteCard) = a == b
    }

    private companion object {
        const val HORIZONTAL_WIDTH = 0.82f
        const val MUTED_ALPHA = 0xB3 // 70%
    }
}

/** 收藏的 id 依目前順序（拖曳後寫回 sort_order 用） */
val List<FavoriteCard>.routeIds: List<Long> get() = map { it.route.id }

/** 復原用：從清單找回被刪除的收藏 */
fun List<FavoriteCard>.routeAt(position: Int): FavoriteRoute? = getOrNull(position)?.route
