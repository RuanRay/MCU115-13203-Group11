package com.group11.hometide.ui.routes

import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.group11.hometide.R
import com.group11.hometide.databinding.FragmentRemindersBinding
import com.group11.hometide.ui.base.BaseActivity
import kotlinx.coroutines.launch

/** P5「出發提醒」分頁（FR-FAV-05；reminders.png）：已預約的提醒可左滑取消（同時取消 AlarmManager） */
class RemindersFragment : Fragment(R.layout.fragment_reminders) {

    private val vm get() = (requireParentFragment() as MyRoutesFragment).vm

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentRemindersBinding.bind(view)
        val adapter = ReminderAdapter()
        b.rvReminders.adapter = adapter
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(rv: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder) = false

            override fun getSwipeDirs(rv: RecyclerView, holder: RecyclerView.ViewHolder): Int {
                val pos = holder.bindingAdapterPosition
                return if (pos >= 0 && adapter.canCancel(pos)) ItemTouchHelper.LEFT else 0
            }

            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) {
                adapter.currentList.getOrNull(holder.bindingAdapterPosition)?.let(vm::cancelReminder)
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
                val hb = (holder as ReminderAdapter.Holder).binding
                hb.vCancelBg.visibility = if (dX < 0) View.VISIBLE else View.INVISIBLE
                getDefaultUIUtil().onDraw(c, rv, hb.tkReminder, dX, dY, actionState, isCurrentlyActive)
            }

            override fun clearView(rv: RecyclerView, holder: RecyclerView.ViewHolder) {
                val hb = (holder as ReminderAdapter.Holder).binding
                hb.vCancelBg.visibility = View.INVISIBLE
                getDefaultUIUtil().clearView(hb.tkReminder)
            }
        }).attachToRecyclerView(b.rvReminders)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    // 停在畫面時，發車時間過了也要變成「已使用」：每 60 秒重繪
                    while (true) {
                        kotlinx.coroutines.delay(REFRESH_MS)
                        adapter.notifyItemRangeChanged(0, adapter.itemCount)
                    }
                }
                launch {
                    vm.reminders.collect { list ->
                        adapter.submitList(list)
                        b.tkRemindersEmpty.isVisible = list.isEmpty()
                    }
                }
                launch {
                    vm.reminderMessages.collect { res ->
                        // 取消失敗時把卡片放回原位
                        adapter.notifyItemRangeChanged(0, adapter.itemCount)
                        val activity = requireActivity() as BaseActivity
                        activity.showSnackbar(res, anchor = activity.findViewById(R.id.bottomNav))
                    }
                }
            }
        }
    }

    private companion object {
        const val REFRESH_MS = 60_000L
    }
}
