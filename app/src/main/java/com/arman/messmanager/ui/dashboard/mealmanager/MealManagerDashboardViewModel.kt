package com.arman.messmanager.ui.dashboard.mealmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arman.messmanager.data.model.MealEntry
import com.arman.messmanager.data.model.MealType
import com.arman.messmanager.data.repository.AuthRepository
import com.arman.messmanager.data.repository.ElectionPollRepository
import com.arman.messmanager.data.repository.MealEntryRepository
import com.arman.messmanager.data.repository.MessRepository
import com.arman.messmanager.data.repository.NoticeRepository
import com.arman.messmanager.data.repository.SpecialMealPollRepository
import com.arman.messmanager.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

// Everything the Meal Manager Dashboard needs to draw itself. Same reasoning as the
// other dashboards: one plain data class, no sealed states, since this screen just
// displays live totals and reacts to setting a lock time.
data class MealManagerDashboardUiState(
    val isLoading: Boolean = true,
    val breakfastCount: Double = 0.0,
    val lunchCount: Double = 0.0,
    val dinnerCount: Double = 0.0,
    val breakfastLockTime: String? = null,
    val lunchLockTime: String? = null,
    val dinnerLockTime: String? = null,
    // Whether the Super Admin has an open Manager Election poll right now (SRS section
    // 3) - the Meal Manager is also a mess member and can vote, same as everyone else
    // (RBAC matrix: "Update Own Meals & Vote" is Yes for every role).
    val hasActiveElection: Boolean = false,
    // How many Special Meal Polls (SRS section 6) are currently open for this mess -
    // drives the "opt in or out" banner. 0 hides it, same as hasActiveElection but a
    // count instead of a flag since, unlike the Manager Election, several of these can
    // be open at once.
    val openSpecialMealPollCount: Int = 0
)

class MealManagerDashboardViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val messRepository: MessRepository = MessRepository(),
    private val mealEntryRepository: MealEntryRepository = MealEntryRepository(),
    private val electionPollRepository: ElectionPollRepository = ElectionPollRepository(),
    private val specialMealPollRepository: SpecialMealPollRepository = SpecialMealPollRepository(),
    private val noticeRepository: NoticeRepository = NoticeRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MealManagerDashboardUiState())
    val uiState: StateFlow<MealManagerDashboardUiState> = _uiState.asStateFlow()

    // Today's date as "yyyy-MM-dd" - the same format every MealEntry document uses.
    private val today: String = LocalDate.now().toString()

    // Remembered after the first load so setLockTime() doesn't have to re-fetch the
    // manager's own profile every time.
    private var messId: String? = null

    init {
        loadDashboard()
    }

    private fun loadDashboard() {
        viewModelScope.launch {
            val uid = authRepository.currentUser?.uid ?: return@launch
            val user = userRepository.getUser(uid) ?: return@launch
            val currentMessId = user.messId ?: return@launch
            messId = currentMessId

            refreshSummary(currentMessId)
        }
    }

    // Re-reads today's meal entries for the whole mess and the mess's lock times, then
    // updates the screen. Called on first load and again after setting a lock time.
    private suspend fun refreshSummary(currentMessId: String) {
        // NOTE (simplification for now): this only counts meals that already have a
        // saved entry in Firestore. A member who hasn't opened their dashboard yet
        // today - and so never saved their default "on" choice - isn't counted here
        // until they do. Counting every member by default is a natural next step.
        val todaysMeals = mealEntryRepository.getMealsForMessAndDate(currentMessId, today)
        val mess = messRepository.getMess(currentMessId)
        val hasActiveElection = electionPollRepository.getActivePoll(currentMessId) != null
        val openSpecialMealPollCount = specialMealPollRepository.getOpenPolls(currentMessId).size

        _uiState.value = MealManagerDashboardUiState(
            isLoading = false,
            breakfastCount = totalFor(todaysMeals, MealType.BREAKFAST),
            lunchCount = totalFor(todaysMeals, MealType.LUNCH),
            dinnerCount = totalFor(todaysMeals, MealType.DINNER),
            breakfastLockTime = mess?.breakfastLockTime,
            lunchLockTime = mess?.lunchLockTime,
            dinnerLockTime = mess?.dinnerLockTime,
            hasActiveElection = hasActiveElection,
            openSpecialMealPollCount = openSpecialMealPollCount
        )
    }

    // Adds up every member's "count" for one meal type. count can be 0.5 (half meal)
    // or more than 1 (guest meals), so a simple sum already handles those cases.
    private fun totalFor(meals: List<MealEntry>, mealType: MealType): Double =
        meals.filter { it.mealType == mealType }.sumOf { it.count }

    // Called from the "Lock Meals" dialog after the manager picks a meal and time.
    fun setLockTime(mealType: MealType, time: String) {
        val currentMessId = messId ?: return

        viewModelScope.launch {
            messRepository.setMealLockTime(currentMessId, mealType, time)
            refreshSummary(currentMessId) // reload so the new lock time shows immediately
        }
    }

    // "Create Special Meal Poll" (SRS section 6, Meal Manager only): opens a new opt-in
    // poll for an upcoming event, e.g. "Friday Biryani". eventDate is a "yyyy-MM-dd"
    // string picked from the Fragment's DatePickerDialog.
    fun createSpecialMealPoll(title: String, eventDate: String) {
        val currentMessId = messId ?: return

        viewModelScope.launch {
            specialMealPollRepository.createPoll(currentMessId, title, eventDate)
            refreshSummary(currentMessId) // reload so the new poll's banner shows immediately
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
}
