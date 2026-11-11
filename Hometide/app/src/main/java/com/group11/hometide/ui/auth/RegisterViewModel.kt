package com.group11.hometide.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RegisterViewModel(private val repo: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    /** FR-AUTH-01：依序驗證，任一失敗時不呼叫 Firebase（TC-01） */
    fun register(nickname: String, email: String, password: String, confirm: String, operator: Operator) {
        if (_state.value == AuthUiState.Loading) return
        val n = nickname.trim()
        val e = email.trim()
        AuthValidator.validateRegister(n, e, password, confirm)?.let {
            _state.value = AuthUiState.FieldError(it)
            return
        }
        _state.value = AuthUiState.Loading
        viewModelScope.launch { _state.value = repo.register(n, e, password, operator).toUiState() }
    }

    fun messageShown() {
        if (_state.value is AuthUiState.Message) _state.value = AuthUiState.Idle
    }
}
