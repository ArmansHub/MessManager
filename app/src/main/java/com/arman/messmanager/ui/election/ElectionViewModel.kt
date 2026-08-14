package com.arman.messmanager.ui.election

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arman.messmanager.data.repository.AuthRepository
import com.arman.messmanager.data.repository.ElectionPollRepository
import com.arman.messmanager.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// One nominee as shown on the ballot - just enough to render a row and cast a vote.
data class CandidateOption(val uid: String, val name: String)

// Everything the Manager Election voting screen needs to draw itself. Same plain-data-
// class approach as the dashboards: this screen just shows the open poll's candidates
// and reacts to vote taps, there's no multi-step Loading/Success flow to model.
data class ElectionUiState(
    val isLoading: Boolean = true,
    val hasActivePoll: Boolean = false,
    val title: String = "",
    val candidates: List<CandidateOption> = emptyList(),
    // The signed-in member's own current picks, or null if they haven't voted for that
    // role yet. Used to highlight the selected row on each ballot.
    val myVote: String? = null,
    val voteCounts: Map<String, Int> = emptyMap()
)

// Voting side of the "Manager Election & Rotation System" (SRS section 3). Any mess
// member can reach this screen (it isn't role-specific) to vote for next month's Finance
// and Meal Manager once the Super Admin has triggered an election from their dashboard.
// Same MVVM shape as every other screen: ElectionFragment only calls
// voteFinanceManager()/voteMealManager() and renders whatever state comes back.
class ElectionViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val electionPollRepository: ElectionPollRepository = ElectionPollRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ElectionUiState())
    val uiState: StateFlow<ElectionUiState> = _uiState.asStateFlow()

    // Remembered after the first load so voteFinanceManager()/voteMealManager() don't
    // need to re-look-up which poll is active every time a candidate is tapped.
    private var pollId: String? = null

    init {
        refresh()
    }

    // Re-reads the mess's active election poll (if any) and the signed-in member's own
    // votes on it. Called on first load, and again after every vote so the ballot
    // reflects the just-saved choice immediately.
    fun refresh() {
        viewModelScope.launch {
            val uid = authRepository.currentUser?.uid ?: return@launch
            val user = userRepository.getUser(uid) ?: return@launch
            val messId = user.messId ?: return@launch

            val poll = electionPollRepository.getActivePoll(messId)
            if (poll == null) {
                pollId = null
                _uiState.value = ElectionUiState(isLoading = false, hasActivePoll = false)
                return@launch
            }
            pollId = poll.pollId

            // Resolve each eligible candidate's uid to a display name. Reading all mess
            // members at once is more efficient than reading each candidate's profile
            // individually (an N+1 query problem).
            val messMembers = userRepository.getUsersForMess(messId)
            val membersMap = messMembers.associateBy { it.uid }
            val candidates = poll.options.mapNotNull { candidateUid ->
                membersMap[candidateUid]?.let { profile ->
                    CandidateOption(candidateUid, profile.name.ifBlank { profile.email })
                }
            }

            val voteCounts = poll.votesMap.values.groupingBy { it }.eachCount()

            _uiState.value = ElectionUiState(
                isLoading = false,
                hasActivePoll = true,
                title = poll.title,
                candidates = candidates,
                myVote = poll.votesMap[uid],
                voteCounts = voteCounts
            )
        }
    }

    fun vote(candidateUid: String) {
        val currentPollId = pollId ?: return
        val uid = authRepository.currentUser?.uid ?: return

        // Optimistically update the UI state immediately.
        val currentState = _uiState.value
        if (currentState.myVote == candidateUid) return // No change

        val newVoteCounts = currentState.voteCounts.toMutableMap()
        // Decrement old vote if exists
        currentState.myVote?.let { oldVote ->
            newVoteCounts[oldVote] = (newVoteCounts[oldVote] ?: 1) - 1
        }
        // Increment new vote
        newVoteCounts[candidateUid] = (newVoteCounts[candidateUid] ?: 0) + 1

        _uiState.value = currentState.copy(
            myVote = candidateUid,
            voteCounts = newVoteCounts
        )

        viewModelScope.launch {
            electionPollRepository.castVote(currentPollId, uid, candidateUid)
            // refresh() is removed to prevent lag; UI is already updated.
        }
    }

}
