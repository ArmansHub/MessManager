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
import com.arman.messmanager.data.repository.DepositRepository
import com.arman.messmanager.data.repository.BazaarEntryRepository
import com.arman.messmanager.data.repository.FixedBillRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.util.Locale

data class MemberDashboardUiState(
    val isLoading: Boolean = true,
    // Profile
    val profileName: String = "",
    val profilePictureUrl: String? = null,
    // Data
    val balance: Double = 0.0,
    val mealRate: Double = 0.0,
    val isBreakfastOn: Boolean = true,
    val isLunchOn: Boolean = true,
    val isDinnerOn: Boolean = true,
    val breakfastMenu: String = "",
    val lunchMenu: String = "",
    val dinnerMenu: String = "",
    val breakfastLabel: String = "Breakfast",
    val lunchLabel: String = "Lunch",
    val dinnerLabel: String = "Dinner",
    val hasActiveElection: Boolean = false,
    val openSpecialMealPollCount: Int = 0,
    val notices: List<NoticeOption> = emptyList(),
    val personalMealsToday: Int = 0
)

data class NoticeOption(val title: String, val content: String, val authorName: String, val timestamp: Long)

sealed interface ToggleError {
    data class TimeLocked(val mealName: String, val lockTime: String) : ToggleError
}

class MemberDashboardViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val messRepository: MessRepository = MessRepository(),
    private val mealEntryRepository: MealEntryRepository = MealEntryRepository(),
    private val electionPollRepository: ElectionPollRepository = ElectionPollRepository(),
    private val specialMealPollRepository: SpecialMealPollRepository = SpecialMealPollRepository(),
    private val noticeRepository: NoticeRepository = NoticeRepository(),
    private val depositRepository: DepositRepository = DepositRepository(),
    private val bazaarEntryRepository: BazaarEntryRepository = BazaarEntryRepository(),
    private val fixedBillRepository: FixedBillRepository = FixedBillRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemberDashboardUiState())
    val uiState: StateFlow<MemberDashboardUiState> = _uiState.asStateFlow()

    private val _toggleError = MutableStateFlow<ToggleError?>(null)
    val toggleError: StateFlow<ToggleError?> = _toggleError.asStateFlow()

    private val today: String = LocalDate.now().toString()
    private var messId: String? = null

    init {
        loadDashboard()
    }

    fun loadDashboard() {
        viewModelScope.launch {
            val uid = authRepository.currentUser?.uid ?: return@launch
            val user = userRepository.getUser(uid) ?: return@launch
            messId = user.messId
            val currentMessId = messId ?: return@launch
            val monthId = YearMonth.now().toString()

            val deposits = depositRepository.getDeposits(currentMessId)
            val bazaarEntries = bazaarEntryRepository.getBazaarEntries(currentMessId)
            val fixedBills = fixedBillRepository.getFixedBills(currentMessId, monthId)
            val mealEntriesAll = mealEntryRepository.getMealsForMess(currentMessId)
                .filter { it.date.startsWith(monthId) }

            val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneId.systemDefault())
            
            val monthlyStandardBazaarCost = bazaarEntries
                .filter { it.date.startsWith(monthId) && it.linkedPollId == null }
                .sumOf { it.amount }
            
            val totalMessMealsThisMonth = mealEntriesAll.sumOf { it.count }
            val liveMealRate = if (totalMessMealsThisMonth > 0.0) monthlyStandardBazaarCost / totalMessMealsThisMonth else 0.0
            
            val myMealsCountThisMonth = mealEntriesAll.filter { it.userId == uid }.sumOf { it.count }
            val myMealCost = myMealsCountThisMonth * liveMealRate
            
            val monthlyFixedBillsCost = fixedBills.sumOf { it.amount }
            val membersCount = userRepository.getUsersForMess(currentMessId).filter { it.joinApproved }.size
            val fixedBillShare = if (membersCount > 0) monthlyFixedBillsCost / membersCount else 0.0
            
            val monthlyApprovedDeposits = deposits
                .filter { it.status == "approved" && timestampFormatter.format(it.date.toDate().toInstant()) == monthId }
            val myApprovedDeposits = monthlyApprovedDeposits.filter { it.memberUid == uid }.sumOf { it.amount }
            
            val currentBalance = user.balance + myApprovedDeposits - (myMealCost + fixedBillShare)

            val todayMeals = mealEntryRepository.getMealsForDate(uid, today)
            val isBreakfastOn = isMealOn(todayMeals, MealType.BREAKFAST)
            val isLunchOn = isMealOn(todayMeals, MealType.LUNCH)
            val isDinnerOn = isMealOn(todayMeals, MealType.DINNER)
            
            var enabledMealsCount = 0
            if (isBreakfastOn) enabledMealsCount++
            if (isLunchOn) enabledMealsCount++
            if (isDinnerOn) enabledMealsCount++
            
            val hasActiveElection = electionPollRepository.getActivePoll(currentMessId) != null
            val openSpecialMealPollCount = specialMealPollRepository.getOpenPolls(currentMessId).size

            val noticesRaw = noticeRepository.getNotices(currentMessId)
            val filteredNotices = noticesRaw.filter { 
                it.title.isNotBlank() || it.content.isNotBlank() || it.message != null 
            }
            val noticeOptions = filteredNotices.take(5).map { notice ->
                val authorId = notice.postedBy.ifBlank { notice.authorUid }.orEmpty()
                val authorName = if (authorId.isNotBlank()) {
                    userRepository.getUser(authorId)?.name?.ifBlank { null } ?: "A manager"
                } else {
                    "A manager"
                }
                NoticeOption(
                    title = notice.title.ifBlank { notice.message }.orEmpty(),
                    content = notice.content.ifBlank { notice.message }.orEmpty(),
                    authorName = authorName,
                    timestamp = if (notice.date != 0L) notice.date else (notice.timestamp ?: 0L)
                )
            }

            val mess = messRepository.getMess(currentMessId)
            val isRamadan = mess?.ramadanModeEnabled ?: false

            _uiState.value = MemberDashboardUiState(
                isLoading = false,
                profileName = user.name.ifBlank { "User" },
                profilePictureUrl = user.profilePictureUrl,
                balance = currentBalance,
                mealRate = liveMealRate,
                isBreakfastOn = isBreakfastOn,
                isLunchOn = isLunchOn,
                isDinnerOn = isDinnerOn,
                breakfastMenu = mess?.breakfastMenu.orEmpty(),
                lunchMenu = mess?.lunchMenu.orEmpty(),
                dinnerMenu = mess?.dinnerMenu.orEmpty(),
                breakfastLabel = if (isRamadan) "Sehri" else "Breakfast",
                lunchLabel = if (isRamadan) "Iftar" else "Lunch",
                dinnerLabel = if (isRamadan) "Dinner" else "Dinner",
                hasActiveElection = hasActiveElection,
                openSpecialMealPollCount = openSpecialMealPollCount,
                notices = noticeOptions,
                personalMealsToday = enabledMealsCount
            )
        }
    }

    private fun isMealOn(meals: List<MealEntry>, mealType: MealType): Boolean {
        val entry = meals.firstOrNull { it.mealType == mealType }
        return entry?.let { it.count > 0.0 } ?: true
    }

    fun toggleMeal(mealType: MealType, isOn: Boolean) {
        val uid = authRepository.currentUser?.uid ?: return
        val currentMessId = messId ?: return
        
        viewModelScope.launch {
            val mess = messRepository.getMess(currentMessId)
            val lockTimeStr = when (mealType) {
                MealType.BREAKFAST -> mess?.breakfastLockTime
                MealType.LUNCH -> mess?.lunchLockTime
                MealType.DINNER -> mess?.dinnerLockTime
                else -> null
            }

            if (!isOn && lockTimeStr != null) {
                try {
                    val now = LocalTime.now()
                    val lockTime = LocalTime.parse(lockTimeStr)
                    
                    // The lock ONLY applies to "Today's" meals. 
                    // Members can always toggle meals for tomorrow.
                    if (now.isAfter(lockTime)) {
                        val mealLabel = when(mealType) {
                            MealType.BREAKFAST -> _uiState.value.breakfastLabel
                            MealType.LUNCH -> _uiState.value.lunchLabel
                            MealType.DINNER -> _uiState.value.dinnerLabel
                            else -> mealType.name
                        }
                        _toggleError.value = ToggleError.TimeLocked(mealLabel, lockTimeStr)
                        // Trigger a state emission to force UI switches to reset to current DB state
                        loadDashboard()
                        return@launch
                    }
                } catch (e: Exception) {
                    // If time parsing fails, allow the toggle but log it
                    android.util.Log.e("MealLock", "Failed to parse lock time: $lockTimeStr")
                }
            }

            _uiState.value = applyToggle(_uiState.value, mealType, isOn)
            mealEntryRepository.setMealOn(currentMessId, uid, today, mealType, isOn)
            loadDashboard()
        }
    }

    fun clearToggleError() {
        _toggleError.value = null
    }

    fun submitDepositRequest(amount: Double) {
        val uid = authRepository.currentUser?.uid ?: return
        val currentMessId = messId ?: return
        viewModelScope.launch {
            depositRepository.addDeposit(currentMessId, uid, amount, status = "pending")
        }
    }

    private fun applyToggle(state: MemberDashboardUiState, mealType: MealType, isOn: Boolean): MemberDashboardUiState {
        val newState = when (mealType) {
            MealType.BREAKFAST -> state.copy(isBreakfastOn = isOn)
            MealType.LUNCH -> state.copy(isLunchOn = isOn)
            MealType.DINNER -> state.copy(isDinnerOn = isOn)
            else -> state
        }
        var count = 0
        if (newState.isBreakfastOn) count++
        if (newState.isLunchOn) count++
        if (newState.isDinnerOn) count++
        return newState.copy(personalMealsToday = count)
    }
}
