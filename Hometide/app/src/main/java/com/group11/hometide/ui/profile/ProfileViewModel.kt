package com.group11.hometide.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group11.hometide.data.local.entity.UserProfile
import com.group11.hometide.data.repository.AuthRepository
import com.group11.hometide.util.Passengers
import com.group11.hometide.data.repository.UsageRepository
import com.group11.hometide.data.repository.UsageStats
import com.group11.hometide.util.Result
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val repo: AuthRepository,
    private val usage: UsageRepository,
    private val clock: () -> LocalDateTime = LocalDateTime::now,
) : ViewModel() {

    /** FR-USG-04：區間預設「本週」 */
    private val range = MutableStateFlow(StatRange.WEEK)
    private val statsRefresh = MutableStateFlow(0)

    data class StatsState(val range: StatRange, val stats: UsageStats? = null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val stats: StateFlow<StatsState> = combine(range, repo.profile.map { it?.uid }.distinctUntilChanged(), statsRefresh) { r, uid, _ -> r to uid }
        .mapLatest { (r, uid) ->
            val from = UsageRules.rangeStart(r, clock(), ZoneId.systemDefault())
            StatsState(r, (usage.stats(uid, from) as? Result.Success)?.data)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StatsState(StatRange.WEEK))

    fun setRange(r: StatRange) {
        range.value = r
    }

    /** 回到個人頁時重算（離開期間有新的紀錄） */
    fun refreshStats() {
        statsRefresh.value++
    }


    sealed interface UiState {
        data object Loading : UiState
        data object Guest : UiState
        data class Member(val profile: UserProfile) : UiState
    }

    /** FR-SET-02 */
    val defaultPassengers: StateFlow<Passengers> =
        repo.defaultPassengers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), Passengers.DEFAULT)

    fun setDefaultPassengers(p: Passengers) {
        viewModelScope.launch { runCatching { repo.setDefaultPassengers(p) } }
    }

    val state: StateFlow<UiState> = repo.profile
        .map { if (it == null) UiState.Guest else UiState.Member(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    /** FR-AUTH-05 */
    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.logout()
            onDone()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
