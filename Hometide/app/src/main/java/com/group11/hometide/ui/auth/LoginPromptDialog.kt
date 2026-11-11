package com.group11.hometide.ui.auth

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.view.Window
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import com.group11.hometide.databinding.DialogLoginPromptBinding

/**
 * 登入提示對話框（FR-AUTH-06）。按「去登入」只通知宿主，由宿主以 registerForActivityResult 開 P1，
 * 對話框關閉後仍能收到登入結果。用法見 [Fragment.registerLoginPrompt]。
 */
class LoginPromptDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogLoginPromptBinding.inflate(layoutInflater)
        binding.btnLater.setOnClickListener { dismiss() }
        binding.btnGoLogin.setOnClickListener {
            setFragmentResult(REQUEST_KEY, Bundle.EMPTY)
            dismiss()
        }
        // TODO(第 7 週)：「會員限定」章播放 AN-07 蓋章動畫
        return Dialog(requireContext()).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(binding.root)
            window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * WIDTH_RATIO).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    companion object {
        const val TAG = "LoginPromptDialog"
        const val REQUEST_KEY = "login_prompt_go_login"
        private const val WIDTH_RATIO = 0.85f
    }
}

/**
 * 在 Fragment 註冊登入提示：回傳顯示對話框的函式。必須在 Fragment 建立時（屬性初始化）呼叫。
 * 登入成功後呼叫 [onLoggedIn]（FR-AUTH-06：回到原頁面）。
 */
fun Fragment.registerLoginPrompt(onLoggedIn: () -> Unit = {}): () -> Unit {
    val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) onLoggedIn()
    }
    // FragmentManager 在 onCreate 後才可用；重建後也會重新註冊
    lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onCreate(owner: LifecycleOwner) {
            setFragmentResultListener(LoginPromptDialog.REQUEST_KEY) { _, _ ->
                launcher.launch(LoginActivity.intentForResult(requireContext()))
            }
        }
    })
    return { LoginPromptDialog().show(parentFragmentManager, LoginPromptDialog.TAG) }
}
