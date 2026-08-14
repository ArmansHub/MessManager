package com.arman.messmanager.ui.messsetup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arman.messmanager.data.model.UserRole
import com.arman.messmanager.data.repository.AuthRepository
import com.arman.messmanager.data.repository.MessRepository
import com.arman.messmanager.data.repository.UserRepository
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

// All the possible states the Mess Setup screen can be in. A sealed interface (same
// pattern as AuthViewModel.AuthUiState) forces the Fragment's "when" block to handle
// every case, so a new state can't silently fall through unhandled.
sealed interface MessSetupUiState {
    data object Idle : MessSetupUiState
    data object Loading : MessSetupUiState
    // Carries the generated invite code so the Fragment can show it to the new
    // Super Admin before continuing to their dashboard.
    data class MessCreated(val inviteCode: String) : MessSetupUiState
    data object JoinRequestSubmitted : MessSetupUiState
    data class Error(val message: String) : MessSetupUiState
}

// Backs the "Mess Creation & Joining" screen (SRS section 4). Standard MVVM, same shape
// as AuthViewModel: the Fragment only calls createMess()/joinMess() and renders whatever
// state comes back through the StateFlow - it never talks to Firestore directly.
class MessSetupViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val messRepository: MessRepository = MessRepository()
) : ViewModel() {

    private companion object {
        // Same reasoning as AuthViewModel: bounds how long a stalled Firestore call
        // (disabled API, no network, ...) can leave the screen stuck loading.
        const val NETWORK_TIMEOUT_MS = 15_000L
    }

    private val _uiState = MutableStateFlow<MessSetupUiState>(MessSetupUiState.Idle)
    val uiState: StateFlow<MessSetupUiState> = _uiState.asStateFlow()

    // "Create a New Mess" (SRS section 4): the signed-in user creates a brand new Mess
    // document and is immediately promoted to its Super Admin - unlike joining, the
    // creator needs no approval.
    fun createMess(messName: String) {
        if (messName.isBlank()) {
            _uiState.value = MessSetupUiState.Error("Enter a mess name")
            return
        }

        _uiState.value = MessSetupUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                withTimeout(NETWORK_TIMEOUT_MS) {
                    val uid = authRepository.currentUser?.uid
                        ?: throw IllegalStateException("Not signed in")

                    // Step 1: create the Mess document. MessRepository generates the
                    // unique invite code other members will use to join.
                    val mess = messRepository.createMess(
                        name = messName.trim(),
                        superAdminUid = uid
                    )

                    // Step 2: promote the signed-in user's own profile to Super Admin
                    // of that new mess. We read the existing profile first (rather than
                    // building a fresh User from scratch) so fields we don't touch here -
                    // like the placeholder name saved at registration - aren't lost.
                    val existingUser = userRepository.getUser(uid)
                        ?: throw IllegalStateException("Profile not found")
                    userRepository.createUser(
                        existingUser.copy(
                            messId = mess.messId,
                            role = UserRole.SUPER_ADMIN,
                            joinApproved = true
                        )
                    )

                    MessSetupUiState.MessCreated(mess.inviteCode)
                }
            } catch (e: TimeoutCancellationException) {
                MessSetupUiState.Error("Request timed out. Check your connection and try again.")
            } catch (e: Exception) {
                MessSetupUiState.Error(e.message ?: "Could not create the mess")
            }
        }
    }

    // "Join an Existing Mess" (SRS section 4): the signed-in user attaches themselves to
    // an existing mess as an unapproved General Member. The Super Admin must approve the
    // request (a separate future "Manage Members" screen) before the user is admitted -
    // joinApproved is deliberately left false here, and AuthViewModel.login() checks that
    // flag on every future sign-in to keep the dashboard locked out until then.
    fun joinMess(inviteCode: String) {
        if (inviteCode.isBlank()) {
            _uiState.value = MessSetupUiState.Error("Enter an invite code")
            return
        }

        _uiState.value = MessSetupUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                withTimeout(NETWORK_TIMEOUT_MS) {
                    val uid = authRepository.currentUser?.uid
                        ?: throw IllegalStateException("Not signed in")

                    // Step 1: look up which mess this invite code belongs to. Invite
                    // codes are generated in uppercase (MessRepository.generateInviteCode),
                    // so normalize the typed input the same way before matching it.
                    val normalizedCode = inviteCode.trim().uppercase()
                    val mess = messRepository.findByInviteCode(normalizedCode)
                        ?: throw IllegalStateException("Invalid invite code")

                    // Step 2: record the join request on the user's own profile.
                    val existingUser = userRepository.getUser(uid)
                        ?: throw IllegalStateException("Profile not found")
                    userRepository.createUser(
                        existingUser.copy(
                            messId = mess.messId,
                            role = UserRole.MEMBER,
                            joinApproved = false
                        )
                    )

                    MessSetupUiState.JoinRequestSubmitted
                }
            } catch (e: TimeoutCancellationException) {
                MessSetupUiState.Error("Request timed out. Check your connection and try again.")
            } catch (e: Exception) {
                MessSetupUiState.Error(e.message ?: "Could not join that mess")
            }
        }
    }

    // Called after the Fragment reacts to a terminal state (success/error), so the same
    // state doesn't get handled twice (e.g. on a configuration change).
    fun resetState() {
        _uiState.value = MessSetupUiState.Idle
    }
}
