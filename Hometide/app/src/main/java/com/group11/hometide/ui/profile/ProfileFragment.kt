package com.group11.hometide.ui.profile

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.group11.hometide.HometideApp
import com.group11.hometide.R
import com.group11.hometide.databinding.FragmentProfileBinding
import com.group11.hometide.ui.auth.LoginActivity
import com.group11.hometide.ui.auth.registerLoginPrompt
import com.group11.hometide.ui.base.asButtonForAccessibility
import com.group11.hometide.ui.search.PassengerSheet
import com.group11.hometide.util.Passengers
import com.group11.hometide.util.passengerText
import com.group11.hometide.util.labelRes
import kotlinx.coroutines.launch
import android.text.Spannable
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import com.group11.hometide.data.repository.UsageStats
import com.group11.hometide.databinding.ItemPageBarBinding

/** P6 個人（規格 4.8）：會員證（含預設乘客 FR-SET-02）、我的乘車紀錄（FR-USG-04）、登出 */
class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private val vm: ProfileViewModel by viewModels {
        viewModelFactory {
            initializer {
                val c = (requireActivity().application as HometideApp).container
                ProfileViewModel(c.authRepository, c.usageRepository)
            }
        }
    }

    // 登入成功後 profile Flow 會自動更新，這裡不需額外處理
    private val showLoginPrompt = registerLoginPrompt()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentProfileBinding.bind(view)
        binding.btnMemberLogin.setOnClickListener { showLoginPrompt() }
        binding.btnLogout.setOnClickListener { confirmLogout() }
        bindStats(binding)
        // FR-SET-02：會員在會員證上、訪客在登入按鈕下方；開啟同一個選擇乘客面板（設為預設）
        listOf(binding.tvDefaultPassengers, binding.tvGuestPassengers).forEach { tv ->
            tv.asButtonForAccessibility()
            tv.setOnClickListener { PassengerSheet.show(this, REQUEST_PASSENGERS, vm.defaultPassengers.value, forDefault = true) }
        }
        childFragmentManager.setFragmentResultListener(REQUEST_PASSENGERS, viewLifecycleOwner) { _, r ->
            vm.setDefaultPassengers(Passengers.fromJson(r.getString(PassengerSheet.KEY_PASSENGERS)))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.defaultPassengers.collect { p ->
                        val text = getString(R.string.member_default_passengers, requireContext().passengerText(p))
                        binding.tvDefaultPassengers.text = text
                        binding.tvGuestPassengers.text = text
                    }
                }
                vm.state.collect { state ->
                    val member = (state as? ProfileViewModel.UiState.Member)?.profile
                    binding.layoutMember.isVisible = member != null
                    binding.btnLogout.isVisible = member != null
                    binding.btnMemberLogin.isVisible = state == ProfileViewModel.UiState.Guest
                    binding.tvGuestPassengers.isVisible = state == ProfileViewModel.UiState.Guest
                    if (member != null) {
                        binding.tvNickname.text = member.nickname
                        binding.tvEmail.text = member.email
                        binding.tvDefaultOperator.text =
                            getString(R.string.member_default_operator, getString(member.defaultOperator.labelRes))
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshStats()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) vm.refreshStats() // 分頁以 show／hide 切換
    }

    /** FR-USG-04 */
    private fun bindStats(binding: FragmentProfileBinding) {
        binding.spRange.adapter = ArrayAdapter(requireContext(), R.layout.item_operator_spinner, StatRange.entries.map { getString(it.label) })
            .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spRange.setSelection(vm.stats.value.range.ordinal, false)
        binding.spRange.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = vm.setRange(StatRange.entries[position])
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.stats.collect { renderStats(binding, it.stats) }
            }
        }
    }

    private fun renderStats(binding: FragmentProfileBinding, stats: UsageStats?) {
        binding.tvOpenCount.text = (stats?.openCount ?: 0).toString()
        // 「46分」：單位字縮小（profile.png）
        val minutes = UsageRules.minutes(stats?.totalMs ?: 0).toString()
        binding.tvTotalTime.text = SpannableString(minutes + getString(R.string.stat_minutes_unit)).apply {
            setSpan(RelativeSizeSpan(UNIT_SCALE), minutes.length, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val box = binding.layoutPageBars
        box.removeAllViews()
        val pages = stats?.pages.orEmpty()
        if (pages.isEmpty()) {
            // 實作者確認：沒有紀錄時的提示
            TextView(requireContext()).apply {
                setText(R.string.stat_empty)
                setTextAppearance(R.style.TextAppearance_Hometide_Body)
                setTextColor(requireContext().getColor(R.color.color_on_surface_muted))
                resources.getDimensionPixelSize(R.dimen.stat_empty_padding).let { setPadding(0, it, 0, it) }
                box.addView(this)
            }
            return
        }
        val max = pages.first().durationMs
        pages.forEach { p ->
            val row = ItemPageBarBinding.inflate(layoutInflater, box, true)
            val name = UsageRules.pageLabel(p.pageName)?.let(::getString) ?: p.pageName
            // 未滿 60 秒顯示「不到 1 分」，其餘四捨五入
            val time = if (p.durationMs < MS_PER_MIN) getString(R.string.stat_page_under_minute)
            else getString(R.string.stat_page_minutes, UsageRules.minutes(p.durationMs).toInt())
            row.tvPageName.text = name
            row.tvPageTime.text = time
            row.pbPage.progress = UsageRules.barPercent(p.durationMs, max)
            row.root.contentDescription = getString(R.string.stat_page_desc, name, time)
        }
    }

    private companion object {
        const val REQUEST_PASSENGERS = "profile_passengers"
        const val UNIT_SCALE = 0.55f
        const val MS_PER_MIN = 60_000L
    }

    /** FR-AUTH-05：確認後登出並清除堆疊回 P1 */
    private fun confirmLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.logout_confirm)
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.btn_confirm) { _, _ ->
                // 登出期間 Fragment 可能已分離，先取 applicationContext（Intent 已含 NEW_TASK）
                val app = requireContext().applicationContext
                vm.logout { app.startActivity(LoginActivity.intentAfterLogout(app)) }
            }
            .show()
    }
}
