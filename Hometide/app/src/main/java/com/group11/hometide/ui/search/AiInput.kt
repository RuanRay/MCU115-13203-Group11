package com.group11.hometide.ui.search

import android.content.res.ColorStateList
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.group11.hometide.R
import com.group11.hometide.data.repository.TripDraft
import com.group11.hometide.ui.result.ResultActivity
import com.group11.hometide.ui.widget.BouncingDotsDrawable
import kotlinx.coroutines.launch

/**
 * 一句話查詢輸入（FR-AI-01、FR-NEXT-05）：P3 輸入框與「下一班」浮動列共用。
 * 空字串時「解析」停用；解析中按鈕改為三點跳動（AN-15）、輸入框不可編輯；結果交給 [AiQueryViewModel]。
 */
fun Fragment.bindAiInput(
    vm: AiQueryViewModel,
    input: EditText,
    button: MaterialButton,
    snackbar: (String) -> Unit,
    onEdit: (TripDraft) -> Unit,
) {
    val label = button.text
    val dotColor = ContextCompat.getColor(requireContext(), R.color.color_on_primary)
    val dots = BouncingDotsDrawable(dotColor, resources.getDimension(R.dimen.parse_dot_size))
    fun canParse() = !input.text.isNullOrBlank() && !vm.parsing.value
    button.isEnabled = canParse()
    input.doAfterTextChanged { button.isEnabled = canParse() }
    button.setOnClickListener { vm.parse(input.text.toString()) }
    input.setOnEditorActionListener { _, action, _ ->
        (action == EditorInfo.IME_ACTION_SEND && canParse()).also { if (it) vm.parse(input.text.toString()) }
    }

    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                vm.parsing.collect { busy ->
                    input.isEnabled = !busy
                    if (busy) {
                        button.minWidth = button.width // 文字換成三點時寬度不跳動
                        button.text = null
                        button.icon = dots
                        button.iconTint = ColorStateList.valueOf(dotColor)
                        button.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                        button.iconPadding = 0
                        button.contentDescription = getString(R.string.ai_parsing_desc)
                        dots.start()
                    } else {
                        dots.stop()
                        button.icon = null
                        button.text = label
                        button.contentDescription = null
                    }
                    // 解析中維持主色外觀（重複點擊由 ViewModel 忽略）
                    button.isEnabled = busy || canParse()
                }
            }
            launch {
                vm.eventFlow.collect { e ->
                    when (e) {
                        is AiQueryViewModel.Event.Message -> snackbar(getString(e.res))
                        AiQueryViewModel.Event.ShowCard ->
                            if (childFragmentManager.findFragmentByTag(AiConfirmBottomSheet.TAG) == null) {
                                AiConfirmBottomSheet().show(childFragmentManager, AiConfirmBottomSheet.TAG)
                            }
                        is AiQueryViewModel.Event.Open -> e.draft.let { d ->
                            // FR-AI-04：P4 頂部時段晶片顯示篩選時段
                            startActivity(
                                ResultActivity.intent(
                                    requireContext(), d.operator, d.origin!!.stationId, d.dest!!.stationId, d.date, d.passengers,
                                    timeFrom = d.timeFrom, timeTo = d.timeTo,
                                ),
                            )
                        }
                        is AiQueryViewModel.Event.Edit -> onEdit(e.draft)
                    }
                }
            }
        }
    }
}
