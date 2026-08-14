package com.arman.messmanager.ui.specialmealpoll

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arman.messmanager.data.repository.AuthRepository
import com.arman.messmanager.data.repository.SpecialMealPollRepository
import com.arman.messmanager.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// One open poll as shown on this screen - just enough to render a card and react to a
// Yes/No tap. optedInCount is the mess-wide total, not just this member's own status.
data class SpecialMealPollOption(
    val pollId: String,
    val title: String,
    val eventDate: String,
    val optedInCount: Int,
    val isCurrentUserOptedIn: Boolean
)

data class SpecialMealPollUiState(
    val isLoading: Boolean = true,
    val polls: List<SpecialMealPollOption> = emptyList()
)

// Opt-in/opt-out side of "Special Meal Polls" (SRS section 6). Any mess member can reach
// this screen (it isn't role-specific, same as ElectionFragment) to vote Yes/No on every
// currently open special-event poll for their mess - unlike the Manager Election, several
// of these can be open at once, so this screen renders a list of cards instead of one
// ballot. Same MVVM shape as every other screen: the Fragment only calls optIn()/optOut()
// and renders whatever state comes back.
class SpecialMealPollViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val specialMealPollRepository: SpecialMealPollRepository = SpecialMealPollRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpecialMealPollUiState())
    val uiState: StateFlow<SpecialMealPollUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    // Re-reads every open special meal poll for this member's mess and this member's own
    // opt-in status on each one. Called on first load, and again after every opt-in/out
    // tap so the screen reflects the just-saved choice immediately.
    fun refresh() {
        viewModelScope.launch {
            val uid = authRepository.currentUser?.uid ?: return@launch
            val user = userRepository.getUser(uid) ?: return@launch
            val messId = user.messId ?: return@launch

            val polls = specialMealPollRepository.getOpenPolls(messId)

            _uiState.value = SpecialMealPollUiState(
                isLoading = false,
                polls = polls.map { poll ->
                    SpecialMealPollOption(
                        pollId = poll.pollId,
                        title = poll.title,
                        eventDate = poll.eventDate,
                        optedInCount = poll.optedInUserIds.size,
                        isCurrentUserOptedIn = uid in poll.optedInUserIds
                    )
                }
            )
        }
    }

    fun optIn(pollId: String) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            specialMealPollRepository.optIn(pollId, uid)
            refresh()
        }
    }

    fun optOut(pollId: String) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            specialMealPollRepository.optOut(pollId, uid)
            refresh()
        }
    }
}
