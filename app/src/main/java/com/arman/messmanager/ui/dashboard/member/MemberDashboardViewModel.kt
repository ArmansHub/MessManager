package com.arman.messmanager.ui.dashboard.member

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

// Everything the Member Dashboard screen needs to draw itself. We use one plain data
// class (instead of several sealed states like AuthViewModel) because this screen just
// shows live data and reacts to switch taps - there is no multi-step Loading/Success flow.
data class MemberDashboardUiState(
    val isLoading: Boolean = true,
    val balance: Double = 0.0,
    val mealRate: Double = 0.0,
    val isBreakfastOn: Boolean = true,
    val isLunchOn: Boolean = true,
    val isDinnerOn: Boolean = true,
    // Whether the Super Admin has an open Manager Election poll right now (SRS section
    // 3) - drives the "vote now" banner. The ballot itself lives in ElectionFragment;
    // this dashboard only needs to know whether to point the member at it.
    val hasActiveElection: Boolean = false,
    // How many Special Meal Polls (SRS section 6) are currently open for this mess -
    // drives the "opt in or out" banner, same reasoning as hasActiveElection but a count
    // since several of these can be open at once.
    val openSpecialMealPollCount: Int = 0,
    // The Digital Notice Board (SRS section 9), newest first - posted by Admins/Managers
    // from their own dashboards, read-only here.
    val notices: List<NoticeOption> = emptyList()
)

// One notice as shown on this screen - just enough to render a card. timestamp is left
// raw (epoch millis) so the Fragment formats it, same split every other screen here uses
// between ViewModel data and Fragment display formatting (e.g. formatMonth()).
data class NoticeOption(val title: String, val content: String, val authorName: String, val timestamp: Long)

class MemberDashboardViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val messRepository: MessRepository = MessRepository(),
    private val mealEntryRepository: MealEntryRepository = MealEntryRepository(),
    private val electionPollRepository: ElectionPollRepository = ElectionPollRepository(),
    private val specialMealPollRepository: SpecialMealPollRepository = SpecialMealPollRepository(),
    private val noticeRepository: NoticeRepository = NoticeRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemberDashboardUiState())
    val uiState: StateFlow<MemberDashboardUiState> = _uiState.asStateFlow()

    // Today's date as "yyyy-MM-dd" - the same format we use for every MealEntry document.
    // java.time works here without extra setup because minSdk is 26 (Android 8.0+).
    private val today: String = LocalDate.now().toString()

    // Remembered after the first load so toggleMeal() doesn't have to re-fetch the
    // user's profile every time a switch is tapped.
    private var messId: String? = null

    init {
        loadDashboard()
    }

    private fun loadDashboard() {
        viewModelScope.launch {
            val uid = authRepository.currentUser?.uid ?: return@launch

            // Step 1: read this member's own profile for their cached balance and
            // which mess they belong to.
            val user = userRepository.getUser(uid) ?: return@launch
            messId = user.messId

            // Step 2: read the mess document for the current live meal rate.
            val mealRate = user.messId?.let { messRepository.getMess(it)?.mealRate } ?: 0.0

            // Step 3: read today's saved meal choices, if any, so the switches start
            // in the correct position instead of always defaulting to on.
            val todayMeals = mealEntryRepository.getMealsForDate(uid, today)

            // Step 4: check whether a Manager Election is open for this mess, to power
            // the "vote now" banner.
            val hasActiveElection = user.messId
                ?.let { electionPollRepository.getActivePoll(it) } != null

            // Step 5: count this mess's currently open Special Meal Polls (SRS section
            // 6), to power the "opt in or out" banner.
            val openSpecialMealPollCount = user.messId
                ?.let { specialMealPollRepository.getOpenPolls(it).size } ?: 0

            // Step 6: read the Digital Notice Board (SRS section 9). One profile read
            // per notice to resolve its author's display name - the same "fine at this
            // scale" trade-off ElectionViewModel makes for candidate names.
            val notices = user.messId?.let { noticeRepository.getNotices(it) }.orEmpty()
            val noticeOptions = notices.map { notice ->
                val authorName = userRepository.getUser(notice.postedBy)
                    ?.name?.ifBlank { null } ?: "A mess manager"
                NoticeOption(
                    title = notice.title,
                    content = notice.content,
                    authorName = authorName,
                    timestamp = notice.date
                )
            }

            _uiState.value = MemberDashboardUiState(
                isLoading = false,
                balance = user.balance,
                mealRate = mealRate,
                isBreakfastOn = isMealOn(todayMeals, MealType.BREAKFAST),
                isLunchOn = isMealOn(todayMeals, MealType.LUNCH),
                isDinnerOn = isMealOn(todayMeals, MealType.DINNER),
                hasActiveElection = hasActiveElection,
                openSpecialMealPollCount = openSpecialMealPollCount,
                notices = noticeOptions
            )
        }
    }

    // A meal counts as "on" if no entry has been saved yet (standard meals default to
    // on) or if the saved entry's count is greater than zero.
    private fun isMealOn(meals: List<MealEntry>, mealType: MealType): Boolean {
        val entry = meals.firstOrNull { it.mealType == mealType }
        return entry?.let { it.count > 0.0 } ?: true
    }

    // Called when the member flips a Breakfast/Lunch/Dinner switch.
    fun toggleMeal(mealType: MealType, isOn: Boolean) {
        val uid = authRepository.currentUser?.uid ?: return
        val currentMessId = messId ?: return

        // Update the screen immediately so the switch feels instant, then save the
        // change to Firestore in the background.
        _uiState.value = applyToggle(_uiState.value, mealType, isOn)

        viewModelScope.launch {
            mealEntryRepository.setMealOn(currentMessId, uid, today, mealType, isOn)
        }
    }

    private fun applyToggle(state: MemberDashboardUiState, mealType: MealType, isOn: Boolean): MemberDashboardUiState =
        when (mealType) {
            MealType.BREAKFAST -> state.copy(isBreakfastOn = isOn)
            MealType.LUNCH -> state.copy(isLunchOn = isOn)
            MealType.DINNER -> state.copy(isDinnerOn = isOn)
            else -> state // SEHRI/IFTAR belong to Ramadan Mode, not used yet
        }
}
