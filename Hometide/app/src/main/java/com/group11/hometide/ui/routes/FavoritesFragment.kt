package com.group11.hometide.ui.routes

import android.graphics.Canvas
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.group11.hometide.R
import com.group11.hometide.databinding.DialogRenameRouteBinding
import com.group11.hometide.databinding.FragmentFavoritesBinding
import com.group11.hometide.ui.base.BaseActivity
import com.group11.hometide.ui.result.ResultActivity
import com.group11.hometide.ui.base.ExpandTransition
import kotlinx.coroutines.launch
import java.time.LocalDate

/** P5「車票夾」分頁（FR-FAV-01～04；routes.png）：左滑刪除＋復原、長按 ≡ 拖曳排序、點卡片以今天查詢、點名稱改名 */
class FavoritesFragment : Fragment(R.layout.fragment_favorites) {

    private val vm get() = (requireParentFragment() as MyRoutesFragment).vm

    /** 拖曳中不套用新狀態（下一班重算會重設清單），放開後寫回排序，資料庫的新順序會再送回來 */
    private var dragging = false

    private var openedTicket: View? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentFavoritesBinding.bind(view)
        lateinit var touch: ItemTouchHelper
        val adapter = FavoriteTicketAdapter(
            horizontal = false,
            onQuery = { card, ticket ->
                openedTicket = ticket
                vm.openToday(card.route)
            },
            onRename = ::showRename,
            onStartDrag = { touch.startDrag(it) },
        )
        b.rvFavoriteRoutes.adapter = adapter
        touch = ItemTouchHelper(TouchCallback(adapter))
        touch.attachToRecyclerView(b.rvFavoriteRoutes)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.state.collect { s ->
                        if (!dragging) adapter.submitList(s.cards)
                        val empty = !s.isGuest && !s.loading && s.cards.isEmpty()
                        b.tkRoutesEmpty.isVisible = empty
                        b.tvRoutesHint.isVisible = s.cards.isNotEmpty()
                    }
                }
                launch {
                    // FR-FAV-02：「已刪除」＋「復原」
                    vm.deleted.collect { route ->
                        val activity = requireActivity() as BaseActivity
                        activity.showSnackbar(
                            getText(R.string.favorite_deleted),
                            anchor = activity.findViewById(R.id.bottomNav),
                            actionText = R.string.btn_undo,
                        ) { vm.restore(route) }
                    }
                }
                launch {
                    // FR-FAV-03：以今天、預設乘客開啟 P4，車票展開成結果頁（AN-03）
                    vm.open.collect { (r, pax) ->
                        val intent = ResultActivity.intent(requireContext(), r.operator, r.originId, r.destId, LocalDate.now(), pax)
                        ExpandTransition.startExpanding(requireActivity(), intent, openedTicket)
                        openedTicket = null
                    }
                }
                launch {
                    // 刪除失敗：把滑出的卡片放回原位
                    vm.errors.collect { res ->
                        adapter.notifyItemRangeChanged(0, adapter.itemCount)
                        val activity = requireActivity() as BaseActivity
                        activity.showSnackbar(res, anchor = activity.findViewById(R.id.bottomNav))
                    }
                }
            }
        }
    }

    /** 改名（實作者確認）：最多 10 字，空白不儲存 */
    private fun showRename(card: FavoriteCard) {
        val dialog = DialogRenameRouteBinding.inflate(LayoutInflater.from(requireContext()))
        // 預設名稱「起站 → 迄站」可能超過 10 字：未改名時不預填
        if (!card.isDefaultLabel) dialog.etRouteLabel.setText(card.route.label)
        dialog.etRouteLabel.setSelection(dialog.etRouteLabel.length())
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.rename_route_title)
            .setView(dialog.root)
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.btn_confirm) { _, _ -> vm.rename(card.route, dialog.etRouteLabel.text.toString()) }
            .show()
    }

    /** 左滑露出紅色「刪除」（只移動車票本身，AN-08 於 7-3）；拖曳排序放開後寫回 sort_order */
    private inner class TouchCallback(private val adapter: FavoriteTicketAdapter) :
        ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.LEFT) {

        private var dragged = false

        override fun isLongPressDragEnabled() = false

        override fun onSelectedChanged(holder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(holder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) dragging = true
        }

        override fun onMove(rv: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
            adapter.move(from.bindingAdapterPosition, to.bindingAdapterPosition)
            dragged = true
            return true
        }

        override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) {
            adapter.currentList.routeAt(holder.bindingAdapterPosition)?.let(vm::delete)
        }

        override fun onChildDraw(
            c: Canvas,
            rv: RecyclerView,
            holder: RecyclerView.ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean,
        ) {
            if (actionState != ItemTouchHelper.ACTION_STATE_SWIPE) {
                return super.onChildDraw(c, rv, holder, dX, dY, actionState, isCurrentlyActive)
            }
            val b = (holder as FavoriteTicketAdapter.Holder).binding
            b.vDeleteBg.visibility = if (dX < 0) View.VISIBLE else View.INVISIBLE
            getDefaultUIUtil().onDraw(c, rv, b.tkRoute, dX, dY, actionState, isCurrentlyActive)
        }

        override fun clearView(rv: RecyclerView, holder: RecyclerView.ViewHolder) {
            val b = (holder as FavoriteTicketAdapter.Holder).binding
            b.vDeleteBg.visibility = View.INVISIBLE
            getDefaultUIUtil().clearView(b.tkRoute)
            super.clearView(rv, holder)
            dragging = false
            if (dragged) {
                dragged = false
                vm.reorder(adapter.takeOrder().routeIds)
                // 第一張的主票樣式依位置決定：放開後重繪
                rv.post { adapter.notifyItemRangeChanged(0, adapter.itemCount) }
            }
        }
    }
}
