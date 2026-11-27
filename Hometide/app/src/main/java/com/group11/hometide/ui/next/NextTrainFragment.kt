package com.group11.hometide.ui.next

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.android.material.chip.Chip
import com.group11.hometide.HometideApp
import com.group11.hometide.R
import com.group11.hometide.data.local.entity.FavoriteRoute
import com.group11.hometide.data.local.entity.TimetableCache
import com.group11.hometide.databinding.FragmentNextTrainBinding
import com.group11.hometide.ui.auth.LoginActivity
import com.group11.hometide.ui.base.BaseActivity
import com.group11.hometide.ui.base.NotificationPermission
import com.group11.hometide.ui.base.asButtonForAccessibility
import com.group11.hometide.ui.main.MainActivity
import com.group11.hometide.ui.result.ReminderDialog
import com.group11.hometide.ui.result.ResultActivity
import com.group11.hometide.util.labelRes
import com.group11.hometide.util.passengerText
import com.group11.hometide.util.priceText
import com.group11.hometide.ui.search.AiQueryViewModel
import com.group11.hometide.ui.search.bindAiInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 下一班分頁（規格 4.5、next.png、next_guest.png；FR-NEXT-01～04、06）。
 * 倒數只在分頁可見時每 60 秒更新（FR-NEXT-03）；翻牌 AN-13 與脈動於 7-3；浮動一句話列見 FR-NEXT-05。
 */
class NextTrainFragment : Fragment(R.layout.fragment_next_train) {

    private val vm: NextTrainViewModel by viewModels {
        viewModelFactory {
            initializer {
                val c = (requireActivity().application as HometideApp).container
                NextTrainViewModel(c.authRepository, c.favoriteRepository, c.reminderRepository)
            }
        }
    }

    private val aiVm: AiQueryViewModel by viewModels {
        viewModelFactory {
            initializer {
                val c = (requireActivity().application as HometideApp).container
                AiQueryViewModel(c.aiRepository, c.authRepository, c.transitRepository)
            }
        }
    }

    // 登入成功後 profile Flow 會自動更新
    private val login = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    private val askNotificationPermission = NotificationPermission.register(this, this) { requireContext() }
    private var binding: FragmentNextTrainBinding? = null
    private var tickJob: Job? = null
    private var chipIds: List<Long> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentNextTrainBinding.bind(view).also { binding = it }
        val adapter = UpcomingAdapter(onClick = ::openResult)
        b.rvUpcoming.adapter = adapter

        // FR-NEXT-01：未設定提醒 → 提醒對話框；已設定 → 開 P4 並捲到該班
        b.tkNextMain.setOnClickListener {
            val s = vm.state.value
            val main = s.main ?: return@setOnClickListener
            if (s.mainReminder != null) {
                openResult(main.train)
            } else {
                ReminderDialog.show(
                    requireContext(),
                    main.train,
                    existing = null, // 這裡只處理「未設定」：已設定時點主車票開 P4，取消在 P4 存根
                    onSet = { min -> vm.setReminder(main.train, ReminderDialog.typeName(requireContext(), main.train), min) },
                    onCancelReminder = {},
                )
            }
        }
        b.tkNextMain.asButtonForAccessibility()
        b.tvSeeAll.setOnClickListener { openResult(null) }
        b.tvSeeAll.asButtonForAccessibility()
        b.btnLogin.setOnClickListener { login.launch(LoginActivity.intentForResult(requireContext())) }
        b.btnGoSearch.setOnClickListener { (requireActivity() as MainActivity).openManualSearch() }
        // FR-NEXT-05：「修改」切到查詢分頁並填入空白車票
        bindAiInput(aiVm, b.etFloatingAi, b.btnFloatingParse, ::snackbar) { (requireActivity() as MainActivity).openSearchWithDraft(it) }
        b.chipGroupRoutes.setOnCheckedStateChangeListener { _, ids ->
            val index = ids.firstOrNull()?.let { id -> b.chipGroupRoutes.indexOfChild(b.chipGroupRoutes.findViewById(id)) } ?: return@setOnCheckedStateChangeListener
            vm.state.value.routes.getOrNull(index)?.takeIf { it.id != vm.state.value.selected?.id }?.let(vm::select)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.reminderSet.collect { min ->
                        snackbar(if (min > 0) getString(R.string.reminder_set, min) else getString(R.string.error_unknown))
                        if (min > 0) askNotificationPermission() // FR-RMD-05：提醒建立後才說明並請求
                    }
                }
                vm.state.collect { s ->
                    render(b, s)
                    adapter.submitList(s.upcoming)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden) startTicking()
    }

    override fun onPause() {
        stopTicking()
        super.onPause()
    }

    // 分頁以 show／hide 切換：離開分頁就停止計時（FR-NEXT-03）
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            stopTicking()
        } else {
            vm.refresh()
            startTicking()
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun startTicking() {
        if (tickJob?.isActive == true) return
        tickJob = viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                vm.tick()
                delay(TICK_MS)
            }
        }
    }

    private fun stopTicking() {
        tickJob?.cancel()
        tickJob = null
    }

    private fun render(b: FragmentNextTrainBinding, s: NextTrainViewModel.UiState) {
        val ctx = requireContext()
        b.vDateStamp.setDate(LocalDate.now()) // 跨午夜後回到分頁也會更新
        b.tvNextTitle.text = when {
            s.loading -> ""
            s.isGuest -> getString(R.string.next_title_guest)
            s.routes.isEmpty() -> getString(R.string.next_title_no_routes)
            s.boardLoading -> ""
            s.main != null -> titleFor(s.main.minutes)
            s.offline -> getString(R.string.next_title_offline)
            else -> getString(R.string.next_title_none)
        }
        b.layoutNextEmpty.isVisible = s.isEmpty
        b.layoutNextContent.isVisible = !s.loading && !s.isEmpty
        b.vDateStamp.isVisible = !s.isEmpty
        b.layoutFloatingAi.isVisible = !s.isEmpty && !s.loading // next_guest.png 的空狀態沒有浮動列
        // FR-NEXT-06：訪客「登入／註冊」「先去查詢」；會員沒有收藏「去查詢並收藏」
        b.tvNextEmptyHint.isVisible = s.isGuest
        b.btnLogin.isVisible = s.isGuest
        b.btnGoSearch.setText(if (s.isGuest) R.string.btn_go_search else R.string.btn_go_search_favorite)

        b.tvOfflineBanner.isVisible = s.cachedAt != null
        s.cachedAt?.let {
            b.tvOfflineBanner.text = getString(R.string.offline_banner, Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(BANNER_TIME))
        }
        if (s.isEmpty) return
        renderChips(b, s.routes, s.selected)

        // 主車票
        val main = s.main
        // 站名在查詢完成前還沒有：先用路線名稱（預設即「起站 → 迄站」）
        b.tvMainStations.text = if (s.boardLoading) s.selected?.label.orEmpty() else getString(R.string.result_route, s.originName, s.destName)
        b.tvMainMeta.text = main?.let { mainMeta(it.train, s) } ?: s.selected?.let { getString(it.operator.labelRes) }.orEmpty()
        b.tvMainTimes.text = when {
            s.boardLoading -> getString(R.string.next_train_loading)
            main != null -> getString(R.string.train_time, main.train.departTime, main.train.arriveTime)
            s.offline -> getString(R.string.next_train_offline)
            s.tomorrowFirst != null -> getString(R.string.next_train_tomorrow, s.tomorrowFirst)
            else -> getString(R.string.next_train_none)
        }
        b.tvMainCountdown.text = main?.minutes?.toString() ?: getString(R.string.fare_unknown)
        b.tvMainCountdownUnit.isVisible = main != null
        // FR-NEXT-03：剩 5 分鐘以內數字變紅（脈動於 7-3）
        val urgent = main != null && NextTrainViewModel.isUrgent(main.minutes)
        b.tvMainCountdown.setTextColor(ContextCompat.getColor(ctx, if (urgent) R.color.countdown_urgent else R.color.color_on_primary))
        // FR-NEXT-01：「🔔 已設定提前 N 分鐘提醒」
        b.tvMainReminder.isVisible = main != null && s.mainReminder != null
        s.mainReminder?.let { b.tvMainReminder.text = getString(R.string.next_reminder_status, it.remindBeforeMin) }
        b.tvMainFare.isVisible = main != null
        b.tvMainFare.text = ctx.priceText(main?.fare?.total, main?.fare?.approx ?: false)
        b.tkNextMain.isClickable = main != null

        b.tvUpcomingTitle.isVisible = s.upcoming.isNotEmpty()
        b.tvSeeAll.isVisible = !s.boardLoading && !s.offline && s.selected != null
    }

    /** 「還有 N 分鐘」，60 分鐘以上「還有 X 小時 Y 分」（FR-NEXT-01） */
    private fun titleFor(minutes: Int): String =
        NextTrainViewModel.hoursMinutes(minutes)?.let { (h, m) -> getString(R.string.next_title_hours, h, m) }
            ?: getString(R.string.next_title_minutes, minutes)

    /** 「自強 139 次・山線・全票」：鐵路車次、山海線、票種（預設乘客）；國道客運為「業者 路線號」 */
    private fun mainMeta(t: TimetableCache, s: NextTrainViewModel.UiState): String {
        val head = if (t.operator == com.group11.hometide.data.local.entity.Operator.BUS) {
            getString(R.string.bus_meta, t.trainTypeName.orEmpty(), t.trainTypeCode ?: t.trainNo)
        } else {
            getString(R.string.train_meta, t.trainTypeName.orEmpty(), t.trainNo)
        }
        val line = when (t.tripLine) {
            1 -> getString(R.string.line_mountain)
            2 -> getString(R.string.line_sea)
            else -> null
        }
        // 票種：一人一種時只寫票種（next.png「全票」），否則寫組合（同 FR-RES-02 存根）
        val pax = if (s.passengers.isSingle) getString(s.passengers.counts.keys.first().labelRes) else requireContext().passengerText(s.passengers)
        return listOfNotNull(head, line, pax).joinToString(getString(R.string.pax_separator))
    }

    /** FR-NEXT-02：路線晶片「名稱・運具」；清單有變才重建 */
    private fun renderChips(b: FragmentNextTrainBinding, routes: List<FavoriteRoute>, selected: FavoriteRoute?) {
        val group = b.chipGroupRoutes
        if (chipIds != routes.map { it.id } || group.childCount != routes.size) {
            chipIds = routes.map { it.id }
            group.removeAllViews()
            val inflater = LayoutInflater.from(group.context)
            routes.forEach { r ->
                val chip = inflater.inflate(R.layout.item_route_chip, group, false) as Chip
                chip.id = View.generateViewId()
                chip.text = getString(R.string.next_main_meta, r.label, getString(r.operator.labelRes))
                group.addView(chip)
            }
        }
        val index = routes.indexOfFirst { it.id == selected?.id }
        if (index >= 0) {
            val chip = group.getChildAt(index) as Chip
            if (!chip.isChecked) chip.isChecked = true
        }
    }

    /** FR-NEXT-04：開 P4（今天、預設乘客），[train] 非 null 時捲到該班 */
    private fun openResult(train: TimetableCache?) {
        val s = vm.state.value
        val r = s.selected ?: return
        startActivity(
            ResultActivity.intent(requireContext(), r.operator, r.originId, r.destId, LocalDate.now(), s.passengers, scrollToTrain = train?.trainNo),
        )
    }

    private fun snackbar(text: String) {
        val activity = requireActivity() as BaseActivity
        activity.showSnackbar(text, anchor = binding?.layoutFloatingAi)
    }

    private companion object {
        const val TICK_MS = 60_000L
        val BANNER_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd HH:mm")
    }
}
