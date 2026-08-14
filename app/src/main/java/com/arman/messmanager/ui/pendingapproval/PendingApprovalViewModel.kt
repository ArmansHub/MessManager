package com.arman.messmanager.ui.pendingapproval

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arman.messmanager.data.model.UserRole
import com.arman.messmanager.data.repository.AuthRepository
import com.arman.messmanager.data.repository.MessRepository
import com.arman.messmanager.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Everything the Pending Approval screen needs to draw itself. Same plain-data-class
// approach as the dashboards: this screen just displays a status and reacts to a manual
// "Check Approval Status" tap, there's no multi-step flow to model with a sealed class.
data class PendingApprovalUiState(
    val isLoading: Boolean = true,
    val messName: String = "",
    val isApproved: Boolean = false,
    val role: UserRole = UserRole.MEMBER
)

// Landing screen for a user whose join request (SRS section 4, "Mess Creation &
// Joining") hasn't been approved by the mess's Super Admin yet. Reached either right
// after submitting a join request from MessSetupFragment, or on a later Login while
// still unapproved - either way this screen re-reads the live Firestore state itself,
// so it doesn't matter which path got the user here.
class PendingApprovalViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val messRepository: MessRepository = MessRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(PendingApprovalUiState())
    val uiState: StateFlow<PendingApprovalUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    // Re-reads the signed-in user's own profile (and their mess's name) from Firestore.
    // Called on first load, and again whenever the user taps "Check Approval Status" -
    // there's no realtime listener here, so approval only becomes visible on a manual
    // refresh rather than the instant a Super Admin approves it.
    fun refresh() {
        viewModelScope.launch {
            val uid = authRepository.currentUser?.uid ?: return@launch
            val user = userRepository.getUser(uid) ?: return@launch
            val messId = user.messId ?: return@launch

            val mess = messRepository.getMess(messId)

            _uiState.value = PendingApprovalUiState(
                isLoading = false,
                messName = mess?.name.orEmpty(),
                isApproved = user.joinApproved,
                role = user.role
            )
        }
    }

    fun signOut() = authRepository.signOut()
}
