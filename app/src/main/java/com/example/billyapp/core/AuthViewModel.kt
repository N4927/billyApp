package com.example.billyapp.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.billyapp.shared.BillySDK
import com.billyapp.shared.core.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.util.Log

class AuthViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState

    fun login(email: String, password: String) {
        _uiState.value = AuthUiState.Loading

        viewModelScope.launch {

            Log.d("AuthViewModel", "Login attempt: $email")

            when (val result = BillySDK.auth.login(email, password)) {

                is Result.Success -> {
                    Log.d("AuthViewModel", "Login success")
                    _uiState.value = AuthUiState.Success
                }

                is Result.Failure -> {
                    Log.e("AuthViewModel", "Login error: ${result.error}")
                    _uiState.value = AuthUiState.Error(
                        result.error?.toString() ?: "Login failed"
                    )
                }
            }
        }
    }

    fun register(username: String, email: String, password: String) {
        _uiState.value = AuthUiState.Loading

        viewModelScope.launch {

            Log.d("AuthViewModel", "Register attempt: $email")

            when (val result = BillySDK.auth.register(username, email, password)) {

                is Result.Success -> {
                    // register() nel KMM fa già login automaticamente
                    Log.d("AuthViewModel", "Register success")
                    _uiState.value = AuthUiState.Success
                }

                is Result.Failure -> {
                    Log.e("AuthViewModel", "Register error: ${result.error}")
                    _uiState.value = AuthUiState.Error(
                        result.error?.toString() ?: "Register failed"
                    )
                }
            }
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }

    sealed class AuthUiState {
        object Idle : AuthUiState()
        object Loading : AuthUiState()
        object Success : AuthUiState()
        data class Error(val message: String) : AuthUiState()
    }
}