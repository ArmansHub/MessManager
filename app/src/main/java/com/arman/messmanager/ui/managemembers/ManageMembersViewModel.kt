package com.arman.messmanager.ui.managemembers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arman.messmanager.data.model.User
import com.arman.messmanager.data.model.UserRole
import com.arman.messmanager.data.repository.AuthRepository
import com.arman.messmanager.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Everything the "Manage Members" screen needs to draw itself. Same plain-data-class
// approach as the dashboards: this screen just displays two live lists and reacts to
// per-row button taps, there's no multi-step Loading/Success flow to model.
data class ManageMembersUiState(
    val isLoading: Boolean = true,
    val pendingMembers: List<User> = emptyList(),
    val approvedMembers: List<User> = emptyList()
)

// Closes the loop opened by MessSetupViewModel.joinMess(): that flow attaches a user to
// a mess with joinApproved = false, and this screen is where the Super Admin actually
// approves (or rejects) that request - SRS section 4, "Super Admin must approve the join
// request before the user is admitted." It also covers "Delete Mess / Remove Members"
// (SRS section 1), which the RBAC matrix restricts to the Super Admin only - this screen
// is only reachable from the Super Admin Dashboard, so no extra role check is needed here.
class ManageMembersViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManageMembersUiState())
    val uiState: StateFlow<ManageMembersUiState> = _uiState.asStateFlow()

    // Remembered after the first load so approve()/removeFromMess() don't have to
    // re-fetch the Super Admin's own profile every time.
    private var messId: String? = null

    init {
        refresh()
    }

    // Re-reads every member of this mess from Firestore and splits them into "pending"
    // (joinApproved == false) and "approved" (joinApproved == true) for the two sections
    // on screen. Called on first load, and again after every approve/reject/remove
    // action so the lists update immediately instead of waiting for a manual refresh.
    fun refresh() {
        viewModelScope.launch {
            val uid = authRepository.currentUser?.uid ?: return@launch
            val currentMessId = messId ?: userRepository.getUser(uid)?.messId ?: return@launch
            messId = currentMessId

            // The Super Admin manages other members, not themselves - excluding their
            // own uid means there's no "Remove" button on their own row to worry about.
            val members = userRepository.getUsersForMess(currentMessId)
                .filterNot { it.uid == uid }

            _uiState.value = ManageMembersUiState(
                isLoading = false,
                pendingMembers = members.filter { !it.joinApproved },
                approvedMembers = members.filter { it.joinApproved }
            )
        }
    }

    // Admits a pending join request. We read the existing profile first (rather than
    // building a fresh User) so nothing else on it - name, balance, role - gets lost.
    fun approve(uid: String) {
        viewModelScope.launch {
            val user = userRepository.getUser(uid) ?: return@launch
            userRepository.createUser(user.copy(joinApproved = true))
            refresh()
        }
    }

    // Used both to reject a still-pending request and to remove an already-approved
    // member - either way the account loses its mess membership. Clearing messId sends
    // it back through "Mess Creation & Joining" the next time it logs in
    // (AuthViewModel.login() routes to NeedsMessSetup whenever messId is null).
    fun removeFromMess(uid: String) {
        viewModelScope.launch {
            val user = userRepository.getUser(uid) ?: return@launch
            userRepository.createUser(
                user.copy(messId = null, role = UserRole.MEMBER, joinApproved = false)
            )
            refresh()
        }
    }
}
