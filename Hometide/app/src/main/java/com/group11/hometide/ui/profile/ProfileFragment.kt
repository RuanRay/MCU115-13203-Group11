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
import com.group11.hometide.util.labelRes
import kotlinx.coroutines.launch

/** P6 個人（規格 4.8）：目前只有會員證與登出 */
class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private val vm: ProfileViewModel by viewModels {
        viewModelFactory { initializer { ProfileViewModel((requireActivity().application as HometideApp).container.authRepository) } }
    }

    // 登入成功後 profile Flow 會自動更新，這裡不需額外處理
    private val showLoginPrompt = registerLoginPrompt()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentProfileBinding.bind(view)
        binding.btnMemberLogin.setOnClickListener { showLoginPrompt() }
        binding.btnLogout.setOnClickListener { confirmLogout() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { state ->
                    val member = (state as? ProfileViewModel.UiState.Member)?.profile
                    binding.layoutMember.isVisible = member != null
                    binding.btnLogout.isVisible = member != null
                    binding.btnMemberLogin.isVisible = state == ProfileViewModel.UiState.Guest
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
