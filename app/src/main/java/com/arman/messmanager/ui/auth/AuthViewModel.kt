package com.arman.messmanager.ui.auth

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

// Holds the login/register screen logic so it survives configuration changes (e.g. rotation)
// and keeps the Fragments themselves free of business logic (standard MVVM).
class AuthViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private companion object {
        // Firebase Auth/Firestore calls that can't reach the server (disabled API, no
        // network, ...) retry silently instead of failing, so await() never returns on
        // its own - this bounds how long the screen will spin before showing an error.
        const val NETWORK_TIMEOUT_MS = 15_000L
    }

    // All the possible states the Login/Register screens can be in.
    // Using a sealed interface means the "when" blocks in the Fragments must handle
    // every case, so we can't forget to handle Loading or Error.
    sealed interface AuthUiState {
        data object Idle : AuthUiState
        data object Loading : AuthUiState
        data class LoginSuccess(val role: UserRole) : AuthUiState
        data object RegisterSuccess : AuthUiState
        // The signed-in account has no mess yet (brand new profile, or an earlier
        // registration that never finished "Mess Creation & Joining" - SRS section 4).
        data object NeedsMessSetup : AuthUiState
        // The account joined a mess via an invite code, but the Super Admin hasn't
        // approved the join request yet, so the dashboard must stay locked out.
        data object PendingApproval : AuthUiState
        data class Error(val message: String) : AuthUiState
    }

    // MutableStateFlow is the "writable" version, kept private so only this ViewModel can
    // change it. We expose the read-only StateFlow below for the UI to observe.
    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun checkCurrentUserSession() {
        // If a user is already signed in with Firebase Auth, check their profile status
        // and navigate them to the correct screen without requiring a manual login.
        val firebaseUser = authRepository.currentUser ?: return
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                withTimeout(NETWORK_TIMEOUT_MS) {
                    val user = userRepository.getUser(firebaseUser.uid)
                        ?: throw IllegalStateException("No profile found for this account")

                    when {
                        user.messId == null -> AuthUiState.NeedsMessSetup
                        !user.joinApproved -> AuthUiState.PendingApproval
                        else -> AuthUiState.LoginSuccess(user.role)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                AuthUiState.Error("Request timed out. Check your connection and try again.")
            } catch (e: Exception) {
                // Don't show a login error if the auto-login check fails, just stay on the login screen.
                AuthUiState.Idle
            }
        }
    }

    fun login(email: String, password: String) {
        _uiState.value = AuthUiState.Loading

        // viewModelScope.launch starts a coroutine tied to this ViewModel's lifecycle,
        // so it is cancelled automatically if the ViewModel is cleared.
        viewModelScope.launch {
            _uiState.value = try {
                // withTimeout bounds the whole network round-trip: if Firebase Auth or
                // Firestore never calls back (e.g. Firestore API disabled, no network),
                // the SDKs retry silently forever instead of failing the await() - without
                // this, the screen would spin indefinitely instead of showing an error.
                withTimeout(NETWORK_TIMEOUT_MS) {
                    // Step 1: verify the email/password with Firebase Authentication.
                    val firebaseUser = authRepository.signIn(email, password)
                        ?: throw IllegalStateException("Login failed")

                    // Step 2: look up this user's profile in Firestore to find their role
                    // (Super Admin, Finance Manager, Meal Manager, or General Member) and
                    // whether they actually belong to an approved mess yet.
                    val user = userRepository.getUser(firebaseUser.uid)
                        ?: throw IllegalStateException("No profile found for this account")

                    when {
                        // Registered but never finished "Mess Creation & Joining".
                        user.messId == null -> AuthUiState.NeedsMessSetup
                        // Joined a mess via invite code, but the Super Admin hasn't
                        // approved the request yet (SRS section 4).
                        !user.joinApproved -> AuthUiState.PendingApproval
                        else -> AuthUiState.LoginSuccess(user.role)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                AuthUiState.Error("Request timed out. Check your connection and try again.")
            } catch (e: Exception) {
                AuthUiState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun register(email: String, password: String) {
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                withTimeout(NETWORK_TIMEOUT_MS) {
                    val firebaseUser = authRepository.register(email, password)
                        ?: throw IllegalStateException("Registration failed")

                    // Every new account starts as a bare profile with no mess yet
                    // (messId/role/joinApproved all keep their User() defaults - see
                    // data/model/User.kt). Full "Profile Setup" (name/phone/emergency
                    // contact - SRS section 4) isn't built yet, so the display name is
                    // seeded from the email for now. From here the Fragment sends the
                    // user straight into "Mess Creation & Joining" to either create a
                    // mess (becoming its Super Admin) or submit a join request.
                    userRepository.createUser(
                        User(
                            uid = firebaseUser.uid,
                            name = email.substringBefore("@"),
                            email = email
                        )
                    )

                    AuthUiState.RegisterSuccess
                }
            } catch (e: TimeoutCancellationException) {
                AuthUiState.Error("Request timed out. Check your connection and try again.")
            } catch (e: Exception) {
                AuthUiState.Error(e.message ?: "Registration failed")
            }
        }
    }

    // Called after the Fragment reacts to a Success/Error state, so the same state
    // doesn't get handled twice (e.g. on a configuration change).
    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}
