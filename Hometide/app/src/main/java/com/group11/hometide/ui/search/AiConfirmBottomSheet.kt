package com.group11.hometide.ui.search

import android.animation.ValueAnimator
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.group11.hometide.R
import com.group11.hometide.data.repository.TripDraft
import com.group11.hometide.databinding.SheetAiConfirmBinding
import com.group11.hometide.util.DateUtils
import com.group11.hometide.util.labelRes
import com.group11.hometide.util.passengerText
import com.group11.hometide.util.periodTimeText
import kotlinx.coroutines.launch

/**
 * AI 確認卡（FR-AI-04；4.4、ai_confirm.png）。父 Fragment 的 [AiQueryViewModel] 持有內容，按鈕交給它處理。
 * AN-04（約 1 秒）：確認卡滑上 → 頂端出票口分段印出預覽車票（與 AN-01 共用 TicketPrinterView）→ 驗證通過才蓋章。
 */
class AiConfirmBottomSheet : BottomSheetDialogFragment() {

    private val vm: AiQueryViewModel by viewModels(ownerProducer = { requireParentFragment() })
    private var binding: SheetAiConfirmBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        SheetAiConfirmBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding!!
        val ticket = vm.card.value ?: return dismiss()
        render(b, ticket.draft)
        if (savedInstanceState == null) {
            // 印票期間先藏章，印完才蓋（驗證失敗不蓋）
            b.vVerifyStamp.alpha = 0f
            b.printerAi.hideTicket()
            b.printerAi.print(PRINT_MS, PRINT_DELAY_MS) { if (ticket.draft.canSearch) stamp(b) else b.vVerifyStamp.alpha = 1f }
        }
        b.btnConfirm.setOnClickListener { vm.confirm(); dismiss() }
        b.btnEdit.setOnClickListener { vm.edit(); dismiss() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.card.collect { if (it == null) dismissAllowingStateLoss() }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    /** 下滑、點外面或返回鍵關閉（轉向重建時不會呼叫） */
    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        vm.dismiss()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun render(b: SheetAiConfirmBinding, d: TripDraft) {
        val ctx = requireContext()
        b.tvOriginal.text = getString(R.string.ai_original_quote, d.input)
        // FR-AI-04：票種顯示乘客組合；只有全票 1 人時同畫面圖只寫「全票」
        val pax = if (d.passengers.isSingle) getString(d.passengers.counts.keys.first().labelRes) else ctx.passengerText(d.passengers)
        b.tvAiMeta.text = getString(R.string.ai_ticket_meta, getString(d.operator.labelRes), pax)
        val unknown = getString(R.string.ai_ticket_unknown_station)
        b.tvAiStations.text = getString(
            R.string.ai_ticket_stations,
            d.origin?.nameZh ?: d.originText ?: unknown,
            d.dest?.nameZh ?: d.destText ?: unknown,
        )
        val date = DateUtils.rocDate(d.date)
        val weekday = DateUtils.weekdayNarrow(d.date)
        b.tvAiDateTime.text = ctx.periodTimeText(d.timeFrom, d.timeTo)
            ?.let { getString(R.string.ai_ticket_date_time, date, weekday, it) }
            ?: getString(R.string.ai_ticket_date, date, weekday)
        b.vVerifyStamp.isVisible = d.canSearch
        b.btnConfirm.isEnabled = d.canSearch
        val errors = errors(d)
        b.tvError.text = errors.joinToString("\n")
        b.tvError.isVisible = errors.isNotEmpty()
    }

    /** FR-AI-03、9.5、11.2 */
    private fun errors(d: TripDraft): List<String> = buildList {
        val missing = listOfNotNull(d.originText.takeIf { d.origin == null }, d.destText.takeIf { d.dest == null })
        when {
            missing.isNotEmpty() -> add(getString(R.string.ai_error_station_not_found, missing.joinToString(getString(R.string.ai_station_separator))))
            d.origin == null || d.dest == null -> add(getString(R.string.ai_error_no_station))
            d.sameStation -> add(getString(R.string.error_same_station))
        }
        if (d.dateReset) add(getString(R.string.ai_error_date_reset))
        if (d.passengersReset) add(getString(R.string.ai_error_passengers_reset))
    }

    /** 「站名已驗證」章（AN-07 的蓋章，含觸覺回饋） */
    private fun stamp(b: SheetAiConfirmBinding) = b.vVerifyStamp.playStampAnimation()

    companion object {
        const val TAG = "AiConfirmBottomSheet"
        private const val PRINT_MS = 700L
        private const val PRINT_DELAY_MS = 280L // 等確認卡滑上
    }
}
