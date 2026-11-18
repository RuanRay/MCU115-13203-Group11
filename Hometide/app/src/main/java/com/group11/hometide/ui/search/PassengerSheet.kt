package com.group11.hometide.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.group11.hometide.R
import com.group11.hometide.data.local.entity.FareClass
import com.group11.hometide.databinding.ItemPaxRowBinding
import com.group11.hometide.ui.base.asButtonForAccessibility
import com.group11.hometide.databinding.SheetPassengersBinding
import com.group11.hometide.util.Passengers
import com.group11.hometide.util.labelRes

/**
 * 選擇乘客面板 sheetPassengers（4.1；FR-SRCH-08、FR-RES-02、FR-SET-02）。
 * 結果以 Fragment Result 回傳：按「確定」送 [KEY_PASSENGERS]；按「設為我的預設」另送 [KEY_SET_DEFAULT]＝true（面板不關閉）。
 */
class PassengerSheet : BottomSheetDialogFragment() {

    private var binding: SheetPassengersBinding? = null
    private lateinit var passengers: Passengers
    private val requestKey get() = requireArguments().getString(ARG_REQUEST)!!
    private val forDefault get() = requireArguments().getBoolean(ARG_FOR_DEFAULT)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        SheetPassengersBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding!!
        passengers = Passengers.fromJson(savedInstanceState?.getString(KEY_PASSENGERS) ?: requireArguments().getString(KEY_PASSENGERS))
        // FR-SET-02：從個人頁開啟時說明改為「設為預設」，不顯示「設為我的預設」
        b.tvPaxHint.setText(if (forDefault) R.string.pax_hint_default else R.string.pax_hint_query)
        b.btnSetDefault.isVisible = !forDefault

        rows(b).forEach { (cls, row) ->
            val name = getString(cls.labelRes)
            row.tvPaxName.text = name
            descRes(cls)?.let { row.tvPaxDesc.setText(it); row.tvPaxDesc.isVisible = true }
            row.btnMinus.contentDescription = getString(R.string.pax_minus_desc, name)
            row.btnPlus.contentDescription = getString(R.string.pax_plus_desc, name)
            row.btnMinus.asButtonForAccessibility()
            row.btnPlus.asButtonForAccessibility()
            row.btnMinus.setOnClickListener { update(passengers.minus(cls)) }
            row.btnPlus.setOnClickListener { update(passengers.plus(cls)) }
        }
        b.btnSetDefault.setOnClickListener {
            setFragmentResult(requestKey, bundleOf(KEY_PASSENGERS to passengers.toJson(), KEY_SET_DEFAULT to true))
            Snackbar.make(b.root, R.string.pax_default_saved, Snackbar.LENGTH_SHORT).show()
        }
        b.btnPaxConfirm.setOnClickListener {
            setFragmentResult(requestKey, bundleOf(KEY_PASSENGERS to passengers.toJson()))
            dismiss()
        }
        render(b)
    }

    /** 五列＋確定在小螢幕超過預設的半展開高度：一律完整展開 */
    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::passengers.isInitialized) outState.putString(KEY_PASSENGERS, passengers.toJson())
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun update(p: Passengers) {
        passengers = p
        binding?.let(::render)
    }

    /** FR-SRCH-08：合計 1 時有人數的列「−」停用；合計 6 時所有「＋」停用 */
    private fun render(b: SheetPassengersBinding) {
        rows(b).forEach { (cls, row) ->
            row.tvCount.text = passengers[cls].toString()
            row.btnMinus.isEnabled = passengers.canRemove(cls)
            row.btnPlus.isEnabled = passengers.canAdd()
        }
        b.tvPaxTotal.text = getString(R.string.pax_total, passengers.total)
    }

    private fun rows(b: SheetPassengersBinding): List<Pair<FareClass, ItemPaxRowBinding>> = listOf(
        FareClass.ADULT to b.rowPaxAdult,
        FareClass.STUDENT to b.rowPaxStudent,
        FareClass.CHILD to b.rowPaxChild,
        FareClass.SENIOR to b.rowPaxSenior,
        FareClass.DISABLED to b.rowPaxDisabled,
    )

    private fun descRes(cls: FareClass): Int? = when (cls) {
        FareClass.ADULT -> R.string.pax_desc_adult
        FareClass.STUDENT -> R.string.pax_desc_student
        else -> null
    }

    companion object {
        const val KEY_PASSENGERS = "passengers"
        const val KEY_SET_DEFAULT = "set_default"
        private const val ARG_REQUEST = "request"
        private const val ARG_FOR_DEFAULT = "for_default"
        private const val TAG = "PassengerSheet"

        /** [forDefault]：個人頁設定預設乘客（FR-SET-02） */
        fun show(host: Fragment, requestKey: String, current: Passengers, forDefault: Boolean = false) =
            create(requestKey, current, forDefault).show(host.childFragmentManager, TAG)

        fun show(host: FragmentActivity, requestKey: String, current: Passengers) =
            create(requestKey, current, false).show(host.supportFragmentManager, TAG)

        private fun create(requestKey: String, current: Passengers, forDefault: Boolean) = PassengerSheet().apply {
            arguments = bundleOf(ARG_REQUEST to requestKey, KEY_PASSENGERS to current.toJson(), ARG_FOR_DEFAULT to forDefault)
        }
    }
}
