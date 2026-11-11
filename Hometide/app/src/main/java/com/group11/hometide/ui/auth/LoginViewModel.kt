package com.group11.hometide.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group11.hometide.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(private val repo: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    val isLoggedIn: Boolean get() = repo.currentUid != null

    /** FR-AUTH-02；驗證失敗時不呼叫 Firebase */
    fun login(email: String, password: String) {
        if (_state.value == AuthUiState.Loading) return
        val trimmedEmail = email.trim()
        AuthValidator.validateLogin(trimmedEmail, password)?.let {
            _state.value = AuthUiState.FieldError(it)
            return
        }
        _state.value = AuthUiState.Loading
        viewModelScope.launch { _state.value = repo.login(trimmedEmail, password).toUiState() }
    }

    /** FR-AUTH-04 */
    fun enterGuest(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.enterGuest()
            onDone()
        }
    }

    fun messageShown() {
        if (_state.value is AuthUiState.Message) _state.value = AuthUiState.Idle
    }
}
