package com.group11.hometide.ui.search

import android.animation.ValueAnimator
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.SimpleCursorAdapter
import android.widget.TextView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.group11.hometide.HometideApp
import com.group11.hometide.R
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.databinding.FragmentSearchBinding
import com.group11.hometide.provider.StationProvider
import com.group11.hometide.ui.auth.registerLoginPrompt
import com.group11.hometide.ui.base.BaseActivity
import com.group11.hometide.ui.result.ResultActivity
import com.group11.hometide.util.DateUtils
import com.group11.hometide.util.labelRes
import com.group11.hometide.util.monthDayWeekday
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * P3 查詢（規格 4.4、home.png）。任務 4-1：AI 解析（第 6 週）先顯示「即將推出」、
 * 車票夾（5-1）先顯示空白提示票、乘客列（CR-01，任務 4-5）尚未加入。
 */
class SearchFragment : Fragment(R.layout.fragment_search) {

    private val vm: SearchViewModel by viewModels {
        viewModelFactory {
            initializer {
                val c = (requireActivity().application as HometideApp).container
                SearchViewModel(c.transitRepository, c.authRepository)
            }
        }
    }

    private val showLoginPrompt = registerLoginPrompt()
    private var binding: FragmentSearchBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentSearchBinding.bind(view).also { binding = it }
        bindAiCard(b)
        b.tkFavEmpty.setOnClickListener { if (vm.state.value.isGuest) showLoginPrompt() } // 會員：5-1 再說明收藏方式
        bindManualTicket(b)

        val recentAdapter = RecentAdapter(onClick = vm::searchRecent, onLongClick = vm::deleteRecent)
        b.rvRecent.adapter = recentAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.state.collect { s ->
                        if (b.spOperator.selectedItemPosition != s.operator.ordinal) b.spOperator.setSelection(s.operator.ordinal)
                        b.btnDate.text = getString(R.string.travel_date, requireContext().monthDayWeekday(s.travelDate))
                        b.tkFavEmpty.isClickable = s.isGuest // 會員點擊於 5-1 再處理，先不顯示按壓效果
                        recentAdapter.defaultOperator = s.defaultOperator
                        recentAdapter.submitList(s.recent)
                        b.tvRecentTitle.isVisible = s.recent.isNotEmpty()
                    }
                }
                launch { vm.eventFlow.collect { onEvent(b, it) } }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding?.let(::bindHeader) // 時段與日期可能在離開期間改變
        vm.ensureValidDate()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    /** FR-SRCH-05：問候語、標題、日期章 */
    private fun bindHeader(b: FragmentSearchBinding) {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        b.tvGreeting.text = getString(R.string.search_greeting, DateUtils.weekdayNarrow(today), getString(SearchRules.periodRes(now.hour)))
        b.tvTitle.setText(SearchRules.titleRes(today))
        b.vDateStamp.setDate(today)
    }

    /** FR-AI-01、FR-SRCH-06：AI 第 6 週才接，範例句只帶入輸入框，解析顯示「即將推出」（實作者確認） */
    private fun bindAiCard(b: FragmentSearchBinding) {
        b.etAiQuery.doAfterTextChanged { b.btnParse.isEnabled = !it.isNullOrBlank() }
        b.btnParse.setOnClickListener { snackbar(getString(R.string.ai_coming_soon)) }
        b.etAiQuery.setOnEditorActionListener { _, action, _ ->
            (action == EditorInfo.IME_ACTION_SEND && b.btnParse.isEnabled).also { if (it) b.btnParse.performClick() }
        }
        b.chipGroupExamples.children.forEach { chip ->
            chip.setOnClickListener {
                b.etAiQuery.setText((chip as TextView).text)
                b.etAiQuery.setSelection(b.etAiQuery.length())
            }
        }
    }

    /** FR-SRCH-01、02、03、FR-BUS-01 */
    private fun bindManualTicket(b: FragmentSearchBinding) {
        val labels = Operator.entries.map { getString(it.labelRes) }
        b.spOperator.adapter = ArrayAdapter(requireContext(), R.layout.item_operator_spinner, labels)
            .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        b.spOperator.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) =
                vm.selectOperator(Operator.entries[position], b.actvOrigin.text.toString(), b.actvDest.text.toString())
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        setUpStationInput(b.actvOrigin)
        setUpStationInput(b.actvDest)

        b.btnSwap.setOnClickListener {
            val origin = b.actvOrigin.text.toString()
            b.actvOrigin.setText(b.actvDest.text, false)
            b.actvDest.setText(origin, false)
            // AN-09：交換按鈕旋轉 180°
            if (ValueAnimator.areAnimatorsEnabled()) b.btnSwap.animate().rotationBy(SWAP_ROTATION).setDuration(SWAP_MS).start()
        }

        // NFR-07：文字樣式的按鈕，讀屏時仍讀成「按鈕」
        listOf(b.btnDate, b.btnSearch).forEach { ViewCompat.setAccessibilityDelegate(it, ButtonRole) }
        b.btnDate.setOnClickListener { pickDate() }
        b.btnSearch.setOnClickListener { vm.submit(b.actvOrigin.text.toString(), b.actvDest.text.toString()) }
    }

    /** FR-SRCH-02：以 StationProvider 取得 Cursor，SimpleCursorAdapter 顯示中文站名 */
    private fun setUpStationInput(input: AutoCompleteTextView) {
        val adapter = SimpleCursorAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            null,
            arrayOf(COL_NAME),
            intArrayOf(android.R.id.text1),
            0,
        )
        adapter.setCursorToStringConverter { it.getString(it.getColumnIndexOrThrow(COL_NAME)) }
        adapter.setFilterQueryProvider { constraint ->
            val uri = StationProvider.queryUri(vm.state.value.operator, constraint?.toString().orEmpty())
            requireContext().contentResolver.query(uri, null, null, null, null)
        }
        input.setAdapter(adapter)
    }

    /** 4.4：DatePickerDialog，可選今天起 60 天內 */
    private fun pickDate() {
        val selected = vm.state.value.travelDate
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        DatePickerDialog(requireContext(), { _, y, m, d -> vm.setDate(LocalDate.of(y, m + 1, d)) },
            selected.year, selected.monthValue - 1, selected.dayOfMonth).apply {
            datePicker.minDate = today.atStartOfDay(zone).toInstant().toEpochMilli()
            datePicker.maxDate = today.plusDays(SearchRules.MAX_DAYS_AHEAD).atStartOfDay(zone).toInstant().toEpochMilli()
        }.show()
    }

    private fun onEvent(b: FragmentSearchBinding, e: SearchViewModel.Event) {
        when (e) {
            is SearchViewModel.Event.Message -> snackbar(if (e.arg == null) getString(e.res) else getString(e.res, e.arg))
            is SearchViewModel.Event.OpenResult ->
                startActivity(ResultActivity.intent(requireContext(), e.operator, e.originId, e.destId, e.date))
            is SearchViewModel.Event.ClearStations -> {
                if (e.origin) b.actvOrigin.setText("", false)
                if (e.dest) b.actvDest.setText("", false)
            }
        }
    }

    private fun snackbar(text: String) {
        val activity = requireActivity() as BaseActivity
        activity.showSnackbar(text, anchor = activity.findViewById(R.id.bottomNav))
    }

    private object ButtonRole : AccessibilityDelegateCompat() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.className = android.widget.Button::class.java.name
        }
    }

    private companion object {
        const val COL_NAME = "name_zh"
        const val SWAP_ROTATION = 180f
        const val SWAP_MS = 250L
    }
}
