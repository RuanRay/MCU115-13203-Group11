package com.group11.hometide.ui.result

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.viewModels
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * P4 查詢結果（規格 4.6；FR-RES-01、03、FR-BUS-04）。任務 4-2：票價先顯示「—」（4-3）、
 * 排序只有「依出發時間」（4-3）、收藏與提醒於第 5 週（訪客先顯示登入提示）、空狀態與離線於 4-4。
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
        binding.spSort.adapter = ArrayAdapter(this, R.layout.item_operator_spinner, listOf(getString(R.string.sort_by_depart)))
            .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        val adapter = TimelineAdapter(onRemind = { if (vm.state.value.isGuest) showLoginPrompt() }) // 會員提醒於 5-3
        val nowLine = NowLineDecoration(this)
        binding.rvTimeline.adapter = adapter
        binding.rvTimeline.addItemDecoration(nowLine)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.state.collect { s ->
                        binding.tvBack.text = getString(R.string.result_back, getString(s.operator.labelRes))
                        binding.tvRoute.text = getString(R.string.result_route, s.originName, s.destName)
                        binding.vDateStamp.setDate(s.date, showWeekday = true)
                        binding.tvCount.text = if (s.loading) "" else getString(R.string.result_count, s.items.size)
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
