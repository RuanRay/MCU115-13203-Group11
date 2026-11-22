package com.group11.hometide.ui.highway

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group11.hometide.R
import com.group11.hometide.data.local.entity.DriveRoute
import com.group11.hometide.data.repository.AuthRepository
import com.group11.hometide.data.repository.DriveRouteRepository
import com.group11.hometide.data.repository.Freeway
import com.group11.hometide.data.repository.HighwayQuery
import com.group11.hometide.data.repository.HighwayRepository
import com.group11.hometide.data.repository.HighwayResult
import com.group11.hometide.data.repository.RoadLevel
import com.group11.hometide.data.repository.Traffic
import com.group11.hometide.util.AppError
import com.group11.hometide.util.Result
import com.group11.hometide.util.messageRes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 國道分頁（規格 4.11；FR-HWY-01～06）。
 * - 訪客或沒有收藏的會員：空白車票表單＋查看路況（表單在上、結果接在下面，實作者確認）
 * - 有收藏的會員：路線晶片，選中即顯示該路線；「＋ 新增」切回表單（FR-HWY-01、06）
 * 表單預設照 highway_guest.png（國道 1 號、南下、桃園 → 永康）。
 */
class HighwayViewModel(
    auth: AuthRepository,
    private val repo: HighwayRepository,
    private val drive: DriveRouteRepository,
) : ViewModel() {

    data class UiState(
        /** null：登入狀態還沒讀到（不顯示訪客提示卡，避免會員閃一下） */
        val isGuest: Boolean? = null,
        val freeways: List<Freeway> = emptyList(),
        val metaLoading: Boolean = true,
        @StringRes val metaErrorRes: Int? = null,
        val selection: HighwayQuery? = null,
        val loading: Boolean = false,
        val result: HighwayResult? = null,
        @StringRes val errorRes: Int? = null,
        val uid: String? = null,
        /** 會員的開車路線；[routesLoaded] 之前尚未讀到 */
        val routes: List<DriveRoute> = emptyList(),
        val routesLoaded: Boolean = false,
        /** 目前選中的開車路線；null 為表單模式 */
        val selectedRouteId: Long? = null,
        /** 會員按「＋ 新增」 */
        val addingNew: Boolean = false,
        /** 畫面上結果所屬的路線 */
        val shownQuery: HighwayQuery? = null,
    ) {
        /** FR-HWY-01：會員有收藏時顯示晶片 */
        val showChips: Boolean get() = isGuest == false && routes.isNotEmpty()

        /** FR-HWY-06：訪客、沒有收藏的會員、或按了「＋ 新增」 */
        val showForm: Boolean get() = isGuest == true || (routesLoaded && (routes.isEmpty() || addingNew))

        /** 實作者確認：查完路況、這條路線還沒收藏時，大車票下方顯示「♡ 收藏這條路線」 */
        val canFavorite: Boolean get() = showForm && result != null && routes.none { it.query == shownQuery }

        val freeway: Freeway? get() = freeways.firstOrNull { it.id == selection?.freewayId }
        val directions get() = freeway?.directions.orEmpty()
        private val interchanges get() = directions.firstOrNull { it.dir == selection?.direction }?.interchanges.orEmpty()
        val entries: List<String> get() = entriesOf(interchanges)
        val exits: List<String> get() = exitsOf(interchanges, selection?.entryIc)
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val messageEvents = Channel<Int>(Channel.BUFFERED)

    /** 重新整理失敗但仍顯示上一份資料時的 Snackbar（例如「查詢太頻繁」） */
    val messages = messageEvents.receiveAsFlow()

    private var loadJob: Job? = null

    private val deleteEvents = Channel<DriveRoute>(Channel.BUFFERED)

    /** 刪除開車路線後的「已刪除」＋「復原」 */
    val deleted = deleteEvents.receiveAsFlow()

    /** 同一種錯誤只提示一次（每 60 秒更新時不重複跳 Snackbar），成功後重置 */
    private var notified: AppError? = null

    private suspend fun notifyOnce(error: AppError) {
        if (error == notified) return
        notified = error
        messageEvents.send(error.highwayMessageRes)
    }

    init {
        viewModelScope.launch {
            auth.profile.collect { p ->
                val s = _state.value
                // 登出或換帳號：清掉上一個帳號的選取與「新增中」
                if (s.isGuest != null && s.uid != p?.uid) {
                    loadJob?.cancel()
                    _state.update { it.copy(selectedRouteId = null, addingNew = false, result = null, shownQuery = null, loading = false) }
                }
                _state.update { it.copy(isGuest = p == null, uid = p?.uid) }
            }
        }
        observeRoutes(auth)
        loadMeta()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeRoutes(auth: AuthRepository) {
        viewModelScope.launch {
            auth.profile.map { it?.uid }.distinctUntilChanged()
                .flatMapLatest { uid -> if (uid == null) flowOf(emptyList()) else drive.observe(uid) }
                .collect(::onRoutes)
        }
    }

    /** FR-HWY-01：預設選上次選的路線（last_drive_route），沒有就第一條；被刪光時回到表單 */
    private suspend fun onRoutes(list: List<DriveRoute>) {
        val before = _state.value
        _state.update { it.copy(routes = list, routesLoaded = true) }
        if (list.isEmpty()) {
            if (before.selectedRouteId != null) {
                loadJob?.cancel()
                _state.update { it.copy(selectedRouteId = null, result = null, shownQuery = null, loading = false) }
            } else {
                showLastIfIdle()
            }
            return
        }
        if (before.addingNew) return
        val target = list.firstOrNull { it.id == before.selectedRouteId }
            ?: list.firstOrNull { it.id == drive.lastDriveRoute.first() }
            ?: list.first()
        if (target.id != before.selectedRouteId) selectRoute(target.id)
    }

    /** 點路線晶片 */
    fun selectRoute(id: Long) {
        val route = _state.value.routes.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(selectedRouteId = id, addingNew = false) }
        viewModelScope.launch { drive.setLastDriveRoute(id) }
        load(route.query, showLoading = true)
    }

    /** 晶片「＋ 新增」：顯示表單（FR-HWY-06） */
    fun startAddNew() {
        loadJob?.cancel()
        _state.update { it.copy(addingNew = true, selectedRouteId = null, result = null, shownQuery = null, loading = false, errorRes = null) }
    }

    /** 實作者確認：命名後收藏；成功後出現在晶片並選中、表單收起 */
    fun favorite(label: String) {
        val s = _state.value
        val uid = s.uid ?: return
        val q = s.shownQuery ?: return
        viewModelScope.launch {
            when (val r = drive.add(uid, q, label)) {
                is Result.Success -> {
                    _state.update { it.copy(addingNew = false, selectedRouteId = r.data.id) }
                    drive.setLastDriveRoute(r.data.id)
                    messageEvents.send(R.string.hwy_route_saved)
                }
                is Result.Failure -> messageEvents.send(r.error.highwayMessageRes)
            }
        }
    }

    fun rename(route: DriveRoute, label: String) {
        viewModelScope.launch {
            (drive.rename(route, label) as? Result.Failure)?.let { messageEvents.send(it.error.highwayMessageRes) }
        }
    }

    fun remove(route: DriveRoute) {
        viewModelScope.launch {
            when (val r = drive.remove(route)) {
                is Result.Success -> deleteEvents.send(route)
                is Result.Failure -> messageEvents.send(r.error.highwayMessageRes)
            }
        }
    }

    /** 「復原」 */
    fun restore(route: DriveRoute) {
        viewModelScope.launch {
            (drive.restore(route) as? Result.Failure)?.let { messageEvents.send(it.error.highwayMessageRes) }
        }
    }

    /**
     * 同一次執行中回到國道分頁：直接顯示上次查看的路線。
     * 要等交流道清單與收藏都讀到才決定（有收藏的會員改由晶片決定），避免先閃一下別條路線。
     */
    private fun showLastIfIdle() {
        val s = _state.value
        if (repo.lastQuery != null && s.freeways.isNotEmpty() && s.routesLoaded && s.routes.isEmpty() &&
            s.result == null && s.selectedRouteId == null && !s.loading
        ) {
            showTraffic()
        }
    }

    fun loadMeta() {
        _state.update { it.copy(metaLoading = true, metaErrorRes = null) }
        viewModelScope.launch {
            when (val r = repo.freeways()) {
                is Result.Success -> {
                    val last = repo.lastQuery
                    _state.update { it.copy(metaLoading = false, freeways = r.data, selection = defaultSelection(r.data, last ?: DEFAULT)) }
                    showLastIfIdle()
                }
                is Result.Failure -> _state.update { it.copy(metaLoading = false, metaErrorRes = r.error.highwayMessageRes) }
            }
        }
    }

    /** 換國道：方向取第一個、上下路取首尾。選到同一條（例如 Spinner 第一次排版時自動回報）不動作 */
    fun selectFreeway(id: String) = reselect { s ->
        if (id == s.selection?.freewayId) return@reselect null
        s.freeways.firstOrNull { it.id == id }?.let { f -> firstLast(f, f.directions.first().dir) }
    }

    fun selectDirection(dir: String) = reselect { s ->
        if (dir == s.selection?.direction) return@reselect null
        s.freeway?.let { firstLast(it, dir) }
    }

    /** 下路必須在上路之後；換上路後原本的下路不合法時改為最後一個交流道 */
    fun selectEntry(ic: String) = reselect { s ->
        val sel = s.selection ?: return@reselect null
        val exits = exitsOf(s.directions.first { it.dir == sel.direction }.interchanges, ic)
        sel.copy(entryIc = ic, exitIc = sel.exitIc.takeIf { it in exits } ?: exits.last())
    }

    fun selectExit(ic: String) = reselect { s -> s.selection?.copy(exitIc = ic) }

    private inline fun reselect(next: (UiState) -> HighwayQuery?) {
        val s = _state.value
        next(s)?.takeIf { it != s.selection }?.let { q -> _state.update { it.copy(selection = q) } }
    }

    /** 「查看路況」：查詢表單目前的路線（不儲存，FR-HWY-06） */
    fun showTraffic() {
        val q = _state.value.selection ?: return
        load(q, showLoading = true)
    }

    /** FR-HWY-05：前景時每 60 秒重新取得；尚未查看路況時不動作 */
    fun refresh() {
        val q = repo.lastQuery ?: return
        if (_state.value.result == null || loadJob?.isActive == true) return
        load(q, showLoading = false)
    }

    private fun load(q: HighwayQuery, showLoading: Boolean) {
        loadJob?.cancel()
        _state.update { it.copy(shownQuery = q) }
        if (showLoading) _state.update { it.copy(loading = true, result = null, errorRes = null) }
        loadJob = viewModelScope.launch {
            when (val r = repo.load(q)) {
                is Result.Success -> {
                    // 顯示快取但錯誤不是離線（例如限流）：另以 Snackbar 告知
                    val error = r.data.error
                    if (error == null) notified = null else if (error != AppError.NetworkError) notifyOnce(error)
                    _state.update { it.copy(loading = false, result = r.data, errorRes = null) }
                }
                is Result.Failure ->
                    if (_state.value.result != null) {
                        notifyOnce(r.error)
                        _state.update { it.copy(loading = false) }
                    } else {
                        _state.update { it.copy(loading = false, errorRes = r.error.highwayMessageRes) }
                    }
            }
        }
    }

    sealed interface Title {
        /** 尚未查看路況：「看看國道塞不塞」 */
        data object Form : Title

        /** 「比平常多 N 分鐘」 */
        data class Delay(val minutes: Int) : Title

        /** 與平常相差 5 分鐘以內：「路況順暢」（FR-HWY-02） */
        data object Smooth : Title

        /** 所有路段都沒有即時資料（實作者確認）：「暫無即時路況」 */
        data object NoLive : Title
    }

    companion object {
        /** highway_guest.png 的表單預設 */
        val DEFAULT = HighwayQuery("000010", "S", "桃園", "永康")
        private const val SMOOTH_WITHIN_MIN = 5

        fun entriesOf(ics: List<String>): List<String> = ics.dropLast(1)

        fun exitsOf(ics: List<String>, entry: String?): List<String> = ics.drop(ics.indexOf(entry) + 1)

        private fun firstLast(f: Freeway, dir: String): HighwayQuery? {
            val ics = f.directions.firstOrNull { it.dir == dir }?.interchanges ?: return null
            return HighwayQuery(f.id, dir, ics.first(), ics.last())
        }

        /** [preferred] 的各欄位在清單中存在就沿用，否則國道取第一條、方向取第一個、上下路取首尾 */
        fun defaultSelection(freeways: List<Freeway>, preferred: HighwayQuery): HighwayQuery? {
            val f = freeways.firstOrNull { it.id == preferred.freewayId } ?: freeways.firstOrNull() ?: return null
            val d = f.directions.firstOrNull { it.dir == preferred.direction } ?: f.directions.first()
            val base = firstLast(f, d.dir) ?: return null
            val entry = preferred.entryIc.takeIf { it in entriesOf(d.interchanges) } ?: base.entryIc
            val exit = preferred.exitIc.takeIf { it in exitsOf(d.interchanges, entry) } ?: d.interchanges.last()
            return base.copy(entryIc = entry, exitIc = exit)
        }

        fun titleOf(t: Traffic?): Title {
            t ?: return Title.Form
            if (t.segments.all { it.level == RoadLevel.UNKNOWN }) return Title.NoLive
            val diff = t.estimatedMin - t.normalMin
            return if (diff <= SMOOTH_WITHIN_MIN) Title.Smooth else Title.Delay(diff)
        }

        /** 「國道 1 號」→「1」、「國道 2 甲」→「2甲」、「國道 1 號汐止五股高架道路」→「1高架」（實作者確認；晶片顯示「國1」） */
        fun shortCode(freewayName: String): String {
            val m = Regex("""^國道 (\d+) (號|甲)(.*)$""").find(freewayName) ?: return freewayName
            val (num, kind, rest) = m.destructured
            return num + (if (kind == "甲") kind else "") + (if ("高架" in rest) "高架" else "")
        }

        /** 「壅塞 N 段」＝路線圖紅色段數（FR-HWY-02 AC、TC-35；含道路封閉）：由路段自行計算，與畫面一致 */
        fun jamCount(t: Traffic): Int = t.segments.count { it.level == RoadLevel.JAM }

        /** 11.2「國道無資料」：部分（不是全部）路段沒有即時資料時，大車票另外標示 */
        fun partialNoLive(t: Traffic): Boolean =
            t.segments.any { it.level == RoadLevel.UNKNOWN } && titleOf(t) != Title.NoLive

        /** 存根「+N 分」：比平常多才顯示；沒有即時資料時不顯示 */
        fun extraMin(t: Traffic): Int? =
            (t.estimatedMin - t.normalMin).takeIf { it > 0 && titleOf(t) != Title.NoLive }

        /** 路線不存在（NotFound）不是「查無班次」，改用一般錯誤訊息 */
        private val AppError.highwayMessageRes: Int
            get() = if (this == AppError.NotFound) R.string.error_unknown else messageRes
    }
}

/** 開車路線對應的查詢（7.1.12 欄位） */
val DriveRoute.query: HighwayQuery get() = HighwayQuery(freewayId, direction, entryIc, exitIc)
