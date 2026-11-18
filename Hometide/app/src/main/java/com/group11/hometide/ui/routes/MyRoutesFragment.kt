package com.group11.hometide.ui.routes

import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.group11.hometide.HometideApp
import com.group11.hometide.R
import com.group11.hometide.databinding.FragmentMyRoutesBinding
import com.group11.hometide.ui.auth.LoginActivity
import com.group11.hometide.ui.main.MainActivity
import kotlinx.coroutines.launch

/** P5 我的路線（規格 4.7、routes.png）：車票夾（FR-FAV-01～04）、出發提醒（FR-FAV-05，任務 5-3） */
class MyRoutesFragment : Fragment(R.layout.fragment_my_routes) {

    // 分頁內的 FavoritesFragment 以 requireParentFragment() 共用
    val vm: MyRoutesViewModel by viewModels {
        viewModelFactory {
            initializer {
                val c = (requireActivity().application as HometideApp).container
                MyRoutesViewModel(c.favoriteCards, c.favoriteRepository, c.authRepository, c.reminderRepository)
            }
        }
    }

    // 點提醒通知時，分頁可能還沒建立：先記下，畫面建立後再切換
    private var pendingReminders = false
    private var pager: androidx.viewpager2.widget.ViewPager2? = null

    /** FR-RMD-03：切到「出發提醒」分頁 */
    fun showReminders() {
        pager?.setCurrentItem(1, false) ?: run { pendingReminders = true }
    }

    // 登入成功後 profile Flow 會自動更新
    private val login = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentMyRoutesBinding.bind(view)
        b.btnAdd.setOnClickListener { (requireActivity() as MainActivity).openManualSearch() }
        b.btnRoutesLogin.setOnClickListener { login.launch(LoginActivity.intentForResult(requireContext())) }

        b.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 2
            override fun createFragment(position: Int): Fragment = if (position == 0) FavoritesFragment() else RemindersFragment()
        }
        var favoriteCount = 0
        var reminderCount = 0
        val mediator = TabLayoutMediator(b.tabLayout, b.viewPager) { tab, pos ->
            tab.text = if (pos == 0) getString(R.string.tab_favorites, favoriteCount) else getString(R.string.tab_reminders, reminderCount)
        }
        mediator.attach()
        pager = b.viewPager
        if (pendingReminders) {
            pendingReminders = false
            b.viewPager.setCurrentItem(1, false)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    // 分頁數字：尚未發車的已預約提醒（reminders.png「出發提醒 1」）
                    vm.reminders.collect { list ->
                        val now = System.currentTimeMillis()
                        val n = list.count { it.status == com.group11.hometide.data.local.entity.ReminderStatus.SCHEDULED && it.departAt > now }
                        if (n != reminderCount) {
                            reminderCount = n
                            b.tabLayout.getTabAt(1)?.text = getString(R.string.tab_reminders, n)
                        }
                    }
                }
                vm.state.collect { s ->
                    b.layoutRoutesGuest.isVisible = s.isGuest && !s.loading
                    b.btnAdd.isVisible = !s.isGuest
                    if (favoriteCount != s.cards.size) {
                        favoriteCount = s.cards.size
                        b.tabLayout.getTabAt(0)?.text = getString(R.string.tab_favorites, favoriteCount)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        pager = null
        super.onDestroyView()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) vm.refresh() // 分頁以 show／hide 切換：回到 P5 時重算下一班（FR-FAV-04）
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }
}
