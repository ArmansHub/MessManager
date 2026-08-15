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

// Everything the Manager Election voting screen needs to draw itself.
data class ElectionUiState(
    val isLoading: Boolean = true,
    val hasActivePoll: Boolean = false,
    val title: String = "",
    val candidates: List<CandidateOption> = emptyList(),
    // The signed-in member's own current picks, or null if they haven't voted for that
    // role yet. Used to highlight the selected row on each ballot.
    val myFinanceVote: String? = null,
    val myMealVote: String? = null,
    val financeVoteCounts: Map<String, Int> = emptyMap(),
    val mealVoteCounts: Map<String, Int> = emptyMap(),
    val isExpired: Boolean = false,
    val endTime: Long = 0L,
    val rolesToElect: List<String> = emptyList()
)

class ElectionViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val electionPollRepository: ElectionPollRepository = ElectionPollRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ElectionUiState())
    val uiState: StateFlow<ElectionUiState> = _uiState.asStateFlow()

    private var pollId: String? = null

    init {
        refresh()
    }

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

            val messMembers = userRepository.getUsersForMess(messId)
            val membersMap = messMembers.associateBy { it.uid }
            val candidates = poll.options.mapNotNull { candidateUid ->
                membersMap[candidateUid]?.let { profile ->
                    CandidateOption(candidateUid, profile.name.ifBlank { profile.email.ifBlank { profile.uid } })
                }
            }

            val financeCounts = poll.financeVotes.values.groupingBy { it }.eachCount()
            val mealCounts = poll.mealVotes.values.groupingBy { it }.eachCount()
            val isExpired = poll.endTime != 0L && System.currentTimeMillis() > poll.endTime

            _uiState.value = ElectionUiState(
                isLoading = false,
                hasActivePoll = true,
                title = poll.title,
                candidates = candidates,
                myFinanceVote = poll.financeVotes[uid],
                myMealVote = poll.mealVotes[uid],
                financeVoteCounts = financeCounts,
                mealVoteCounts = mealCounts,
                isExpired = isExpired,
                endTime = poll.endTime,
                rolesToElect = poll.roles
            )
        }
    }

    fun voteFinanceManager(candidateUid: String) {
        val currentPollId = pollId ?: return
        val uid = authRepository.currentUser?.uid ?: return
        if (_uiState.value.isExpired) return

        // Optimistically update
        val currentState = _uiState.value
        if (currentState.myFinanceVote == candidateUid) return

        viewModelScope.launch {
            electionPollRepository.castVote(currentPollId, uid, "finance", candidateUid)
            refresh()
        }
    }

    fun voteMealManager(candidateUid: String) {
        val currentPollId = pollId ?: return
        val uid = authRepository.currentUser?.uid ?: return
        if (_uiState.value.isExpired) return

        // Optimistically update
        val currentState = _uiState.value
        if (currentState.myMealVote == candidateUid) return

        viewModelScope.launch {
            electionPollRepository.castVote(currentPollId, uid, "meal", candidateUid)
            refresh()
        }
    }
}
