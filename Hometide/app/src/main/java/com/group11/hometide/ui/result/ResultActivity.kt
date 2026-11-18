package com.group11.hometide.ui.result

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
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
import com.group11.hometide.util.labelRes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * P4 查詢結果（規格 4.6；FR-RES-01～04、FR-BUS-02～04）。收藏與提醒於第 5 週（訪客先顯示登入提示）、
 * 乘客晶片於 4-5。
 */
class ResultActivity : BaseActivity() {

    private lateinit var binding: ActivityResultBinding
    private val showLoginPrompt = registerLoginPrompt()

    private val vm: ResultViewModel by viewModels {
        viewModelFactory {
            initializer {
                val c = (application as HometideApp).container
                ResultViewModel(c.transitRepository, c.authRepository, queryOf(intent))
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
        binding.btnFavorite.setOnClickListener { if (vm.state.value.isGuest) showLoginPrompt() } // 會員收藏於 5-1
        // FR-RES-04：選項依運具（國道客運沒有「僅對號列車」）
        val sortOptions = SortOption.optionsFor(vm.state.value.operator)
        binding.spSort.adapter = ArrayAdapter(this, R.layout.item_operator_spinner, sortOptions.map { getString(it.label) })
            .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = vm.setSort(sortOptions[position])
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        val adapter = TimelineAdapter(onRemind = { if (vm.state.value.isGuest) showLoginPrompt() }) // 會員提醒於 5-3
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
                            scrollToNowOnce(s.nowLineIndex)
                        }
                    }
                }
                launch { vm.messages.collect { showSnackbar(it) } }
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

    /** 進入頁面時讓「現在」線位於畫面上方約 1/4 處 */
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

        private const val TICK_MS = 60_000L
        private val HHMM = DateTimeFormatter.ofPattern("HH:mm")
        private val BANNER_TIME = DateTimeFormatter.ofPattern("MM/dd HH:mm")

        fun intent(context: Context, operator: Operator, originId: String, destId: String, date: LocalDate): Intent =
            Intent(context, ResultActivity::class.java)
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
