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
    val targetMonthId: String = "",
    val candidates: List<CandidateOption> = emptyList(),
    // The signed-in member's own current picks, or null if they haven't voted for that
    // role yet. Used to highlight the selected row on each ballot.
    val myFinanceManagerVote: String? = null,
    val myMealManagerVote: String? = null
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

            // Resolve each eligible candidate's uid to a display name. One read per
            // candidate is fine at this scale (one mess's worth of members) - the same
            // simplicity trade-off ManageMembersViewModel makes.
            val candidates = poll.eligibleCandidateUids.mapNotNull { candidateUid ->
                userRepository.getUser(candidateUid)?.let { profile ->
                    CandidateOption(candidateUid, profile.name.ifBlank { profile.email })
                }
            }

            _uiState.value = ElectionUiState(
                isLoading = false,
                hasActivePoll = true,
                targetMonthId = poll.targetMonthId,
                candidates = candidates,
                myFinanceManagerVote = poll.financeManagerVotes[uid],
                myMealManagerVote = poll.mealManagerVotes[uid]
            )
        }
    }

    fun voteFinanceManager(candidateUid: String) {
        val currentPollId = pollId ?: return
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            electionPollRepository.castFinanceManagerVote(currentPollId, uid, candidateUid)
            refresh()
        }
    }

    fun voteMealManager(candidateUid: String) {
        val currentPollId = pollId ?: return
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            electionPollRepository.castMealManagerVote(currentPollId, uid, candidateUid)
            refresh()
        }
    }
}
