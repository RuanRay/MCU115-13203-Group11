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
                MyRoutesViewModel(c.favoriteCards, c.favoriteRepository)
            }
        }
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
        val mediator = TabLayoutMediator(b.tabLayout, b.viewPager) { tab, pos ->
            tab.text = if (pos == 0) getString(R.string.tab_favorites, favoriteCount) else getString(R.string.tab_reminders, 0) // 提醒數於 5-3
        }
        mediator.attach()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) vm.refresh() // 分頁以 show／hide 切換：回到 P5 時重算下一班（FR-FAV-04）
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }
}
