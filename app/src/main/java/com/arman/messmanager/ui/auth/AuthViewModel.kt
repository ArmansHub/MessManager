package com.arman.messmanager.ui.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arman.messmanager.data.model.User
import com.arman.messmanager.data.model.UserRole
import com.arman.messmanager.data.repository.AuthRepository
import com.arman.messmanager.data.repository.UserRepository
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class AuthViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    companion object {
        private const val NETWORK_TIMEOUT_MS = 10000L
    }

    sealed interface AuthUiState {
        data object Idle : AuthUiState
        data object Loading : AuthUiState
        data class LoginSuccess(val role: UserRole) : AuthUiState
        data object RegisterSuccess : AuthUiState
        data object NeedsMessSetup : AuthUiState
        data object PendingApproval : AuthUiState
        data class Error(val message: String) : AuthUiState
    }

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun checkCurrentUserSession() {
        val currentUser = authRepository.currentUser
        if (currentUser == null) {
            _uiState.value = AuthUiState.Idle
            return
        }

        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                val user = userRepository.getUser(currentUser.uid)
                if (user == null) {
                    AuthUiState.Idle
                } else if (user.messId == null) {
                    AuthUiState.NeedsMessSetup
                } else if (!user.joinApproved) {
                    AuthUiState.PendingApproval
                } else {
                    AuthUiState.LoginSuccess(user.role)
                }
            } catch (e: Exception) {
                AuthUiState.Error(e.message ?: "Session check failed")
            }
        }
    }

    fun login(email: String, password: String) {
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.value = AuthUiState.Error("Invalid email format")
            return
        }

        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                withTimeout(NETWORK_TIMEOUT_MS) {
                    val firebaseUser = authRepository.signIn(email, password)
                        ?: throw IllegalStateException("Invalid email or password")

                    val user = userRepository.getUser(firebaseUser.uid)
                        ?: throw IllegalStateException("User profile not found")

                    if (user.messId == null) {
                        AuthUiState.NeedsMessSetup
                    } else if (!user.joinApproved) {
                        AuthUiState.PendingApproval
                    } else {
                        AuthUiState.LoginSuccess(user.role)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                AuthUiState.Error("Request timed out. Check your connection.")
            } catch (e: Exception) {
                AuthUiState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun register(name: String, email: String, password: String) {
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                withTimeout(NETWORK_TIMEOUT_MS) {
                    val firebaseUser = authRepository.register(email, password)
                        ?: throw IllegalStateException("Registration failed")

                    userRepository.createUser(
                        User(
                            uid = firebaseUser.uid,
                            name = name,
                            email = email
                        )
                    )

                    AuthUiState.RegisterSuccess
                }
            } catch (e: TimeoutCancellationException) {
                AuthUiState.Error("Request timed out. Check your connection.")
            } catch (e: Exception) {
                AuthUiState.Error(e.message ?: "Registration failed")
            }
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}
