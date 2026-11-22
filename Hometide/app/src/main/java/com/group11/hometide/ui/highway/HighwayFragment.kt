package com.group11.hometide.ui.highway

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.group11.hometide.HometideApp
import com.group11.hometide.R
import com.group11.hometide.databinding.FragmentHighwayBinding
import com.group11.hometide.ui.base.BaseActivity
import com.group11.hometide.ui.base.asButtonForAccessibility
import com.group11.hometide.ui.highway.HighwayViewModel.Title
import com.group11.hometide.util.AppError
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 國道分頁（規格 4.11；highway.png、highway_dark.png、highway_guest.png；FR-HWY-02～06）。
 * 6-2a：空白車票表單＋查看路況；前景時每 60 秒更新（FR-HWY-05）。開車路線晶片與收藏於 6-2b。
 */
class HighwayFragment : Fragment(R.layout.fragment_highway) {

    private val vm: HighwayViewModel by viewModels {
        viewModelFactory {
            initializer {
                val c = (requireActivity().application as HometideApp).container
                HighwayViewModel(c.authRepository, c.highwayRepository)
            }
        }
    }

    private var binding: FragmentHighwayBinding? = null
    private var tickJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentHighwayBinding.bind(view).also { binding = it }
        val compare = CompareAdapter()
        val road = RoadStripAdapter()
        b.rvCompare.adapter = compare
        b.rvRoadStrip.adapter = road

        b.spFreeway.onSelect { i -> vm.state.value.freeways.getOrNull(i)?.let { vm.selectFreeway(it.id) } }
        b.spDirection.onSelect { i -> vm.state.value.directions.getOrNull(i)?.let { vm.selectDirection(it.dir) } }
        b.spEntryIc.onSelect { i -> vm.state.value.entries.getOrNull(i)?.let(vm::selectEntry) }
        b.spExitIc.onSelect { i -> vm.state.value.exits.getOrNull(i)?.let(vm::selectExit) }
        b.btnShowTraffic.setOnClickListener { vm.showTraffic() }
        b.btnShowTraffic.asButtonForAccessibility()
        b.btnHwyRetry.setOnClickListener {
            if (vm.state.value.metaErrorRes != null) vm.loadMeta() else vm.showTraffic()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.messages.collect { res ->
                        val activity = requireActivity() as BaseActivity
                        activity.showSnackbar(res, anchor = activity.findViewById(R.id.bottomNav))
                    }
                }
                vm.state.collect { s ->
                    render(b, s)
                    val r = s.result
                    compare.submitList(r?.compare?.options?.map { CompareAdapter.Item(it, it.mode.name == r.compare.fastest) }.orEmpty())
                    road.submitSegments(r?.traffic?.segments.orEmpty())
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

    // 分頁以 show／hide 切換：離開分頁即停止更新（FR-HWY-05）
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
                delay(TICK_MS)
                vm.refresh()
            }
        }
    }

    private fun stopTicking() {
        tickJob?.cancel()
        tickJob = null
    }

    private fun render(b: FragmentHighwayBinding, s: HighwayViewModel.UiState) {
        val ctx = requireContext()
        val r = s.result
        b.vDateStamp.setDate(LocalDate.now())
        b.tvHwyTitle.text = when (val t = HighwayViewModel.titleOf(r?.traffic)) {
            Title.Form -> getString(R.string.hwy_title_form)
            is Title.Delay -> getString(R.string.hwy_title_delay, t.minutes)
            Title.Smooth -> getString(R.string.hwy_title_smooth)
            Title.NoLive -> getString(R.string.hwy_title_no_live)
        }

        val offline = r?.error == AppError.NetworkError
        b.tvOfflineBanner.isVisible = offline
        if (offline) b.tvOfflineBanner.text = getString(R.string.offline_banner, hhmm(r!!.updatedAt))

        // 表單
        b.tkHwyForm.isVisible = s.freeways.isNotEmpty()
        b.spFreeway.bind(s.freeways.map { it.name }, s.freeways.indexOfFirst { it.id == s.selection?.freewayId })
        b.spDirection.bind(s.directions.map { it.name }, s.directions.indexOfFirst { it.dir == s.selection?.direction })
        b.spEntryIc.bind(s.entries, s.entries.indexOf(s.selection?.entryIc))
        b.spExitIc.bind(s.exits, s.exits.indexOf(s.selection?.exitIc))

        val errorRes = s.metaErrorRes ?: s.errorRes?.takeIf { r == null && !s.loading }
        b.layoutHwyError.isVisible = errorRes != null
        errorRes?.let { b.tvHwyError.setText(it) }
        b.layoutHwySkeleton.isVisible = s.loading || (s.metaLoading && s.freeways.isEmpty())
        b.layoutHwyResult.isVisible = r != null && !s.loading
        b.cardLoginHint.isVisible = s.isGuest == true && !s.metaLoading // FR-HWY-06：會員不顯示
        r ?: return

        // 4. 大車票
        val t = r.traffic
        b.tvMainFreeway.text = getString(R.string.hwy_main_meta, t.freewayName, t.directionName)
        b.tvMainFastest.isVisible = r.compare?.fastest == DRIVE // 開車最快（實作者確認）
        b.tvMainRoute.text = getString(R.string.result_route, t.from, t.to)
        b.tvMainNormal.text = getString(R.string.hwy_normal, hoursMinutes(ctx, t.normalMin), HighwayViewModel.jamCount(t))
        // 11.2：部分路段無資料（實作者確認：接在更新時間後面）
        b.tvMainUpdated.text = getString(
            if (HighwayViewModel.partialNoLive(t)) R.string.hwy_updated_partial else R.string.hwy_updated,
            hhmm(r.updatedAt),
        )
        b.tvMainEstimate.text = durationHm(ctx, t.estimatedMin)
        val extra = HighwayViewModel.extraMin(t)
        b.tvMainExtra.isVisible = extra != null
        extra?.let { b.tvMainExtra.text = getString(R.string.hwy_extra, it) }

        // 5–6. 回家方式比較：縣市用全名（實作者確認）
        b.layoutCompare.isVisible = r.compare != null
        r.compare?.let { b.tvCompareTitle.text = getString(R.string.hwy_compare_title, it.fromCity, it.toCity) }
    }

    /** 選項或選取改變時才重設，避免觸發選取監聽造成迴圈 */
    private fun Spinner.bind(items: List<String>, selected: Int) {
        @Suppress("UNCHECKED_CAST")
        val current = (adapter as? ArrayAdapter<String>)?.let { a -> List(a.count) { a.getItem(it) } }
        if (current != items) {
            adapter = ArrayAdapter(context, R.layout.item_hwy_spinner, items).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        if (selected >= 0 && selectedItemPosition != selected) setSelection(selected, false)
    }

    private inline fun Spinner.onSelect(crossinline block: (Int) -> Unit) {
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = block(position)
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun hhmm(epochMs: Long): String = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(HHMM)

    private companion object {
        const val TICK_MS = 60_000L
        const val DRIVE = "DRIVE"
        val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

/** 存根的時間「3:05」 */
internal fun durationHm(ctx: Context, minutes: Int): String = ctx.getString(R.string.hwy_duration_hm, minutes / 60, minutes % 60)

/** 「2 時 40 分」；不到 1 小時為「40 分」 */
internal fun hoursMinutes(ctx: Context, minutes: Int): String =
    if (minutes >= 60) ctx.getString(R.string.hwy_hours_minutes, minutes / 60, minutes % 60) else ctx.getString(R.string.hwy_minutes, minutes)
