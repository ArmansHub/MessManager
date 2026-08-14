package com.arman.messmanager.ui.dashboard.superadmin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arman.messmanager.data.model.MealEntry
import com.arman.messmanager.data.model.MealType
import com.arman.messmanager.data.model.UserRole
import com.arman.messmanager.data.repository.AuthRepository
import com.arman.messmanager.data.repository.ElectionPollRepository
import com.arman.messmanager.data.repository.MealEntryRepository
import com.arman.messmanager.data.repository.NoticeRepository
import com.arman.messmanager.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

// Everything the Super Admin Dashboard screen needs to draw itself. Same plain-data-class
// approach as the other dashboards (Member/FinanceManager/MealManager): this screen just
// shows live totals, there is no multi-step Loading/Success flow to model.
data class SuperAdminDashboardUiState(
    val isLoading: Boolean = true,
    // "Mess Overview" card - counts across every member of the mess.
    val totalMembers: Int = 0,
    val activeManagers: Int = 0,
    // "Your Snapshot" card - the Super Admin's own balance and meal status, since every
    // Admin/Manager is fundamentally a mess member too (SRS section 1, "Important Note").
    val personalBalance: Double = 0.0,
    val personalMealsOnCount: Int = 0,
    // "Trigger Manager Elections" quick action (SRS section 3). Null when no election is
    // currently open for this mess; otherwise the "yyyy-MM" month it's electing next
    // month's managers for - used to show status and stop a second poll being triggered
    // while one is already in progress.
    val activeElectionTargetMonthId: String? = null
)

// Standard MVVM data flow for this screen:
// 1. Repositories (UserRepository, MealEntryRepository) are the only classes that talk
//    to Firestore directly.
// 2. This ViewModel calls those repositories, combines the results into one
//    SuperAdminDashboardUiState, and publishes it through a StateFlow.
// 3. SuperAdminDashboardFragment only ever *observes* that StateFlow and updates its
//    TextViews - it never queries Firestore itself. Keeping Firestore calls out of the
//    Fragment means the data survives configuration changes (e.g. screen rotation)
//    and the UI layer stays simple to test.
class SuperAdminDashboardViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val mealEntryRepository: MealEntryRepository = MealEntryRepository(),
    private val electionPollRepository: ElectionPollRepository = ElectionPollRepository(),
    private val noticeRepository: NoticeRepository = NoticeRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SuperAdminDashboardUiState())
    val uiState: StateFlow<SuperAdminDashboardUiState> = _uiState.asStateFlow()

    // Today's date as "yyyy-MM-dd" - the same format every MealEntry document uses.
    private val today: String = LocalDate.now().toString()

    // Remembered after the first load so triggerElection() doesn't have to re-fetch the
    // Super Admin's own profile every time.
    private var messId: String? = null

    init {
        loadDashboard()
    }

    private fun loadDashboard() {
        // viewModelScope.launch starts a coroutine tied to this ViewModel's lifecycle,
        // so it is cancelled automatically if the ViewModel is cleared (e.g. the user
        // navigates away before Firestore responds).
        viewModelScope.launch {
            val uid = authRepository.currentUser?.uid ?: return@launch

            // Step 1: read the Super Admin's own profile document for their cached
            // balance and which mess they belong to.
            val user = userRepository.getUser(uid) ?: return@launch
            val currentMessId = user.messId ?: return@launch
            messId = currentMessId

            // Step 2: read every member document in this mess so we can count real
            // totals for the "Mess Overview" card. This is a live Firestore read (not a
            // cached counter on the Mess document), so it is always accurate as of the
            // moment the dashboard loads.
            val members = userRepository.getUsersForMess(currentMessId)
            val activeManagers = members.count {
                it.role == UserRole.FINANCE_MANAGER || it.role == UserRole.MEAL_MANAGER
            }

            // Step 3: read the Super Admin's own meal entries for today, the same way
            // MemberDashboardViewModel does, to power the "Meals Today" part of the
            // Personal View snapshot.
            val todaysMeals = mealEntryRepository.getMealsForDate(uid, today)

            // Step 4: check whether an election is already open, so the Fragment can
            // show its status instead of letting the Super Admin trigger a second,
            // overlapping poll.
            val activePoll = electionPollRepository.getActivePoll(currentMessId)

            // Step 5: publish everything as one immutable state object. The Fragment's
            // collector (in onViewCreated) fires the moment this value changes.
            _uiState.value = SuperAdminDashboardUiState(
                isLoading = false,
                totalMembers = members.size,
                activeManagers = activeManagers,
                personalBalance = user.balance,
                personalMealsOnCount = countMealsOn(todaysMeals),
                activeElectionTargetMonthId = activePoll?.targetMonthId
            )
        }
    }

    // "Trigger Manager Elections" (SRS section 3): opens a new poll for next month's
    // Finance and Meal Manager, nominating every currently approved member as an
    // eligible candidate. Does nothing if a poll is already open - the Fragment is
    // expected to only call this when activeElectionTargetMonthId is null, but this
    // guard keeps the ViewModel correct even if that check is ever bypassed.
    fun triggerElection() {
        val currentMessId = messId ?: return
        if (_uiState.value.activeElectionTargetMonthId != null) return

        viewModelScope.launch {
            val approvedMembers = userRepository.getUsersForMess(currentMessId)
                .filter { it.joinApproved }
            val targetMonthId = YearMonth.now().plusMonths(1).toString()

            electionPollRepository.createPoll(
                messId = currentMessId,
                targetMonthId = targetMonthId,
                eligibleCandidateUids = approvedMembers.map { it.uid }
            )

            // Reload so the new poll's status (and any other totals that may have
            // shifted) shows up immediately instead of waiting for the next visit.
            loadDashboard()
        }
    }

    // "Post Notice" (SRS section 9, Admins and Managers only): puts a message on the
    // Digital Notice Board every mess member sees read-only on their own dashboard.
    fun postNotice(message: String) {
        val currentMessId = messId ?: return
        val uid = authRepository.currentUser?.uid ?: return

        viewModelScope.launch {
            noticeRepository.postNotice(currentMessId, uid, message)
        }
    }

    // A standard meal (Breakfast/Lunch/Dinner) counts as "on" if no entry has been saved
    // yet for it (standard meals default to on) or if the saved entry's count is greater
    // than zero. Mirrors MemberDashboardViewModel.isMealOn(), just counted across all
    // three meal types instead of exposed as three separate switch states.
    private fun countMealsOn(meals: List<MealEntry>): Int =
        listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER).count { mealType ->
            val entry = meals.firstOrNull { it.mealType == mealType }
            entry?.let { it.count > 0.0 } ?: true
        }
}
