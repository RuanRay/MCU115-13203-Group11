package com.group11.hometide.ui.result

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.LinearLayoutManager
import com.group11.hometide.HometideApp
import com.group11.hometide.R
import com.group11.hometide.data.local.dao.TimetableQuery
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.databinding.ActivityResultBinding
import com.group11.hometide.ui.auth.registerLoginPrompt
import com.group11.hometide.ui.base.BaseActivity
import com.group11.hometide.ui.base.asButtonForAccessibility
import com.group11.hometide.ui.base.NotificationPermission
import com.group11.hometide.ui.search.PassengerSheet
import com.group11.hometide.util.Passengers
import com.group11.hometide.util.labelRes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * P4 查詢結果（規格 4.6；FR-RES-01～04、07、FR-BUS-02～04）。收藏與提醒於第 5 週（訪客先顯示登入提示）。
 */
class ResultActivity : BaseActivity() {

    private lateinit var binding: ActivityResultBinding
    private val showLoginPrompt = registerLoginPrompt()
    private val askNotificationPermission = NotificationPermission.register(this, this) { this }

    private val vm: ResultViewModel by viewModels {
        viewModelFactory {
            initializer {
                val c = (application as HometideApp).container
                ResultViewModel(c.transitRepository, c.authRepository, c.favoriteRepository, c.reminderRepository, queryOf(intent), Passengers.fromJson(intent.getStringExtra(EXTRA_PASSENGERS)))
            }
        }
    }

    private var scrolledToNow = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)
        scrolledToNow = savedInstanceState != null

        binding.tvBack.setOnClickListener { finish() }
        // FR-RES-05：會員切換收藏；訪客顯示登入提示
        binding.btnFavorite.setOnClickListener {
            val s = vm.state.value
            if (s.isGuest) showLoginPrompt() else vm.toggleFavorite()
        }
        // FR-RES-04：選項依運具（國道客運沒有「僅對號列車」）
        val sortOptions = SortOption.optionsFor(vm.state.value.operator)
        binding.spSort.adapter = ArrayAdapter(this, R.layout.item_operator_spinner, sortOptions.map { getString(it.label) })
            .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = vm.setSort(sortOptions[position])
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        // FR-RES-02：改乘客只重算票價，不重新查時刻表
        binding.chipPassengers.asButtonForAccessibility()
        binding.chipPassengers.setOnClickListener { PassengerSheet.show(this, REQUEST_PASSENGERS, vm.state.value.passengers) }
        supportFragmentManager.setFragmentResultListener(REQUEST_PASSENGERS, this) { _, r ->
            val p = Passengers.fromJson(r.getString(PassengerSheet.KEY_PASSENGERS))
            if (r.getBoolean(PassengerSheet.KEY_SET_DEFAULT)) vm.setDefaultPassengers(p)
            vm.setPassengers(p)
        }

        val adapter = TimelineAdapter(
            // FR-RMD-01：會員開提醒對話框；訪客顯示登入提示
            onRemind = { item -> if (vm.state.value.isGuest) showLoginPrompt() else showReminderDialog(item) },
            onToggleDetail = vm::toggleDetail,
        )
        val nowLine = NowLineDecoration(this)
        binding.rvTimeline.adapter = adapter
        binding.rvTimeline.addItemDecoration(nowLine)
        binding.swipeRefresh.setColorSchemeResources(R.color.color_primary)
        binding.swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.color_surface)
        binding.swipeRefresh.setOnRefreshListener { vm.refresh() }
        binding.layoutEmpty.btnRetry.setOnClickListener { vm.retry() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.state.collect { s ->
                        binding.tvBack.text = getString(R.string.result_back, getString(s.operator.labelRes))
                        binding.tvRoute.text = getString(R.string.result_route, s.originName, s.destName)
                        binding.vDateStamp.setDate(s.date, showWeekday = true)
                        binding.btnFavorite.setImageResource(if (s.isFavorite) R.drawable.ic_heart else R.drawable.ic_heart_outline)
                        binding.btnFavorite.contentDescription = getString(if (s.isFavorite) R.string.cd_unfavorite else R.string.cd_favorite)
                        binding.chipPassengers.text = getString(R.string.passengers_chip, s.passengers.total)
                        binding.tvCount.text = if (s.hasList || s.phase == ResultViewModel.Phase.EMPTY) {
                            getString(R.string.result_count, s.items.size)
                        } else {
                            ""
                        }
                        renderPhase(s)
                        nowLine.index = s.nowLineIndex
                        nowLine.label = getString(R.string.now_line, s.now.format(HHMM))
                        adapter.submitList(s.items) {
                            binding.rvTimeline.invalidateItemDecorations()
                            scrollOnce(s)
                        }
                    }
                }
                launch { vm.messages.collect { showSnackbar(it) } }
                launch {
                    vm.reminderMessages.collect { e ->
                        when (e) {
                            is ResultViewModel.ReminderEvent.Set -> {
                                showSnackbar(getString(R.string.reminder_set, e.beforeMin))
                                askNotificationPermission() // FR-RMD-05：提醒建立後才說明並請求
                            }
                            ResultViewModel.ReminderEvent.Cancelled -> showSnackbar(R.string.reminder_cancelled)
                            ResultViewModel.ReminderEvent.Failed -> showSnackbar(R.string.error_unknown)
                        }
                    }
                }
                launch {
                    vm.favoriteAdded.collect { route ->
                        playHeartPop()
                        showSnackbar(getText(R.string.favorite_added), actionText = R.string.btn_undo) { vm.undoFavorite(route) }
                    }
                }
                // FR-RES-03：前景時每 60 秒重算狀態並移動「現在」線
                launch {
                    vm.tick() // 從背景回來時先更新一次
                    while (true) {
                        delay(TICK_MS)
                        vm.tick()
                    }
                }
            }
        }
    }

    /** FR-RES-06、FR-OFF-01：載入中骨架、無班次、錯誤＋重試、離線橫幅 */
    private fun renderPhase(s: ResultViewModel.UiState) {
        val phase = s.phase
        binding.layoutSkeleton.isVisible = phase == ResultViewModel.Phase.LOADING
        binding.swipeRefresh.isVisible = s.hasList
        binding.swipeRefresh.isRefreshing = s.refreshing
        binding.tvOfflineBanner.isVisible = phase == ResultViewModel.Phase.OFFLINE
        s.cachedAt?.let {
            val time = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(BANNER_TIME)
            binding.tvOfflineBanner.text = getString(R.string.offline_banner, time)
        }
        val empty = binding.layoutEmpty
        empty.root.isVisible = phase == ResultViewModel.Phase.EMPTY || phase == ResultViewModel.Phase.ERROR
        val error = phase == ResultViewModel.Phase.ERROR
        empty.ivEmpty.setImageResource(if (error) R.drawable.ill_machine_offline else R.drawable.ill_ticket_blank)
        empty.tvEmptyTitle.setText(if (error) R.string.trains_unavailable else R.string.error_no_trains)
        empty.tvEmptyMessage.isVisible = error && s.errorRes != null
        s.errorRes?.let { empty.tvEmptyMessage.setText(it) }
        empty.btnRetry.isVisible = error
    }

    private fun showReminderDialog(item: ResultViewModel.Item) {
        val t = item.train
        ReminderDialog.show(
            this,
            t,
            item.reminder,
            onSet = { min -> vm.setReminder(t, ReminderDialog.typeName(this, t), min) },
            onCancelReminder = { item.reminder?.let(vm::cancelReminder) },
        )
    }

    /** AN-06：愛心放大到 1.35 倍再回彈（250 ms，OvershootInterpolator） */
    private fun playHeartPop() {
        if (!animationsEnabled) return
        val heart = binding.btnFavorite
        heart.animate().cancel()
        heart.animate().scaleX(HEART_SCALE).scaleY(HEART_SCALE).setDuration(HEART_UP_MS).setInterpolator(null)
            .withEndAction {
                heart.animate().scaleX(1f).scaleY(1f).setDuration(HEART_MS - HEART_UP_MS).setInterpolator(OvershootInterpolator()).start()
            }.start()
    }

    /** 進入頁面時讓「現在」線位於畫面上方約 1/4 處 */
    private fun scrollOnce(s: ResultViewModel.UiState) {
        // FR-NEXT-04：從下一班點小車票時捲到該班，否則捲到「現在」線
        val target = intent.getStringExtra(EXTRA_SCROLL_TRAIN)
        val index = if (target != null && s.hasList) s.items.indexOfFirst { it.train.trainNo == target }.takeIf { it >= 0 } else null
        scrollToNowOnce(index ?: s.nowLineIndex)
    }

    private fun scrollToNowOnce(index: Int?) {
        if (scrolledToNow || index == null) return
        scrolledToNow = true
        val rv = binding.rvTimeline
        rv.post {
            val lm = rv.layoutManager as LinearLayoutManager
            lm.scrollToPositionWithOffset(index.coerceAtMost((rv.adapter?.itemCount ?: 1) - 1), rv.height / 4)
        }
    }

    companion object {
        const val EXTRA_OPERATOR = "operator"
        const val EXTRA_ORIGIN_ID = "originId"
        const val EXTRA_DEST_ID = "destId"
        const val EXTRA_DATE = "date" // yyyy-MM-dd
        const val EXTRA_PASSENGERS = "passengers" // JSON（CR-01），沒有時為全票 1
        const val EXTRA_SCROLL_TRAIN = "scrollTrain" // FR-NEXT-04：開啟後捲到這個車次
        private const val REQUEST_PASSENGERS = "result_passengers"

        private const val TICK_MS = 60_000L
        private const val HEART_SCALE = 1.35f
        private const val HEART_MS = 250L
        private const val HEART_UP_MS = 100L
        private val HHMM = DateTimeFormatter.ofPattern("HH:mm")
        private val BANNER_TIME = DateTimeFormatter.ofPattern("MM/dd HH:mm")

        fun intent(
            context: Context,
            operator: Operator,
            originId: String,
            destId: String,
            date: LocalDate,
            passengers: Passengers = Passengers.DEFAULT,
            scrollToTrain: String? = null,
        ): Intent =
            Intent(context, ResultActivity::class.java)
                .putExtra(EXTRA_SCROLL_TRAIN, scrollToTrain)
                .putExtra(EXTRA_PASSENGERS, passengers.toJson())
                .putExtra(EXTRA_OPERATOR, operator.name)
                .putExtra(EXTRA_ORIGIN_ID, originId)
                .putExtra(EXTRA_DEST_ID, destId)
                .putExtra(EXTRA_DATE, date.toString())

        private fun queryOf(intent: Intent) = TimetableQuery(
            operator = runCatching { Operator.valueOf(intent.getStringExtra(EXTRA_OPERATOR).orEmpty()) }.getOrDefault(Operator.TRA),
            originId = intent.getStringExtra(EXTRA_ORIGIN_ID).orEmpty(),
            destId = intent.getStringExtra(EXTRA_DEST_ID).orEmpty(),
            travelDate = intent.getStringExtra(EXTRA_DATE) ?: LocalDate.now().toString(),
        )
    }
}
