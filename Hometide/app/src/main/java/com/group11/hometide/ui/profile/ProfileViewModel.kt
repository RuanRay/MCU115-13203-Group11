package com.group11.hometide.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group11.hometide.data.local.entity.UserProfile
import com.group11.hometide.data.repository.AuthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(private val repo: AuthRepository) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data object Guest : UiState
        data class Member(val profile: UserProfile) : UiState
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
