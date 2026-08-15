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
import com.arman.messmanager.data.repository.DepositRepository
import com.arman.messmanager.data.repository.BazaarEntryRepository
import com.arman.messmanager.data.repository.FixedBillRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class MealManagerDashboardUiState(
    val isLoading: Boolean = true,
    // Profile
    val profileName: String = "",
    val profilePictureUrl: String? = null,
    // Meal Counts
    val breakfastCount: Double = 0.0,
    val lunchCount: Double = 0.0,
    val dinnerCount: Double = 0.0,
    val breakfastLockTime: String? = null,
    val lunchLockTime: String? = null,
    val dinnerLockTime: String? = null,
    val breakfastMenu: String = "",
    val lunchMenu: String = "",
    val dinnerMenu: String = "",
    val breakfastLabel: String = "Breakfast",
    val lunchLabel: String = "Lunch",
    val dinnerLabel: String = "Dinner",
    // Personal snapshot
    val personalBalance: Double = 0.0,
    val personalMealsToday: Int = 0,
    val hasActiveElection: Boolean = false,
    val openSpecialMealPollCount: Int = 0,
    val openPolls: List<PollOption> = emptyList(),
    val messMembers: List<MemberSummary> = emptyList()
)

data class PollOption(val id: String, val title: String, val count: Int)
data class MemberSummary(val uid: String, val name: String)

class MealManagerDashboardViewModel(
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

    private val _uiState = MutableStateFlow(MealManagerDashboardUiState())
    val uiState: StateFlow<MealManagerDashboardUiState> = _uiState.asStateFlow()

    private val today: String = LocalDate.now().toString()
    private var messId: String? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val uid = authRepository.currentUser?.uid ?: return@launch
            val user = userRepository.getUser(uid) ?: return@launch
            val currentMessId = user.messId ?: return@launch
            messId = currentMessId

            refreshSummary(currentMessId, user)
        }
    }

    private suspend fun refreshSummary(currentMessId: String, user: com.arman.messmanager.data.model.User) {
        val uid = user.uid
        val monthId = YearMonth.now().toString()
        
        val allDeposits = depositRepository.getDeposits(currentMessId)
        val bazaarEntries = bazaarEntryRepository.getBazaarEntries(currentMessId)
        val fixedBills = fixedBillRepository.getFixedBills(currentMessId, monthId)
        val mealEntriesMess = mealEntryRepository.getMealsForMess(currentMessId)
            .filter { it.date.startsWith(monthId) }
        
        val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneId.systemDefault())
        
        // 1. DYNAMIC MEAL RATE
        val monthlyStandardBazaarCost = bazaarEntries
            .filter { it.date.startsWith(monthId) && it.linkedPollId == null }
            .sumOf { it.amount }
        val totalStandardMeals = mealEntriesMess.sumOf { it.count }
        val liveMealRate = if (totalStandardMeals > 0.0) monthlyStandardBazaarCost / totalStandardMeals else 0.0
        
        // 2. PERSONAL USAGE
        val myMealsCountThisMonth = mealEntriesMess.filter { it.userId == uid }.sumOf { it.count }
        val myMealCost = myMealsCountThisMonth * liveMealRate
        
        val members = userRepository.getUsersForMess(currentMessId).filter { it.joinApproved }
        val monthlyFixedBillsCost = fixedBills.sumOf { it.amount }
        val fixedBillShare = if (members.isNotEmpty()) monthlyFixedBillsCost / members.size else 0.0
        
        val monthlyApprovedDeposits = allDeposits
            .filter { it.status == "approved" && timestampFormatter.format(it.date.toDate().toInstant()) == monthId }
        val myApprovedDeposits = monthlyApprovedDeposits.filter { it.memberUid == uid }.sumOf { it.amount }
        
        val personalBalance = (user.balance) + myApprovedDeposits - (myMealCost + fixedBillShare)
        
        // 3. TODAY'S SUMMARY (Universal totals)
        val todaysMeals = mealEntryRepository.getMealsForMessAndDate(currentMessId, today)
        val myTodayMeals = todaysMeals.filter { it.userId == uid }.sumOf { it.count }
        
        val breakfastTotal = countType(todaysMeals, MealType.BREAKFAST, members.size)
        val lunchTotal = countType(todaysMeals, MealType.LUNCH, members.size)
        val dinnerTotal = countType(todaysMeals, MealType.DINNER, members.size)
        
        val mess = messRepository.getMess(currentMessId)
        val isRamadan = mess?.ramadanModeEnabled ?: false
        val hasActiveElection = electionPollRepository.getActivePoll(currentMessId) != null
        val openPolls = specialMealPollRepository.getOpenPolls(currentMessId)

        _uiState.value = MealManagerDashboardUiState(
            isLoading = false,
            profileName = user.name.ifBlank { "User" },
            profilePictureUrl = user.profilePictureUrl,
            breakfastCount = breakfastTotal,
            lunchCount = lunchTotal,
            dinnerCount = dinnerTotal,
            breakfastLockTime = mess?.breakfastLockTime,
            lunchLockTime = mess?.lunchLockTime,
            dinnerLockTime = mess?.dinnerLockTime,
            breakfastMenu = mess?.breakfastMenu.orEmpty(),
            lunchMenu = mess?.lunchMenu.orEmpty(),
            dinnerMenu = mess?.dinnerMenu.orEmpty(),
            breakfastLabel = if (isRamadan) "Sehri" else "Breakfast",
            lunchLabel = if (isRamadan) "Iftar" else "Lunch",
            dinnerLabel = if (isRamadan) "Dinner" else "Dinner",
            personalBalance = personalBalance,
            personalMealsToday = myTodayMeals.toInt(),
            hasActiveElection = hasActiveElection,
            openSpecialMealPollCount = openPolls.size,
            openPolls = openPolls.map { PollOption(it.pollId, it.title, it.optedInUserIds.size) },
            messMembers = members.map { MemberSummary(it.uid, it.name.ifBlank { it.email }) }
        )
    }

    private fun countType(meals: List<MealEntry>, type: MealType, totalMembers: Int): Double {
        val overrides = meals.filter { it.mealType == type }
        val overridesUids = overrides.map { it.userId }.toSet()
        val defaultOnCount = (totalMembers - overridesUids.size).toDouble()
        val overrideCount = overrides.sumOf { it.count }
        return defaultOnCount + overrideCount
    }

    fun setLockTime(mealType: MealType, time: String) {
        val currentMessId = messId ?: return
        viewModelScope.launch {
            messRepository.setMealLockTime(currentMessId, mealType, time)
            userRepository.getUser(authRepository.currentUser?.uid)?.let { refreshSummary(currentMessId, it) }
        }
    }

    fun setMenu(mealType: MealType, menu: String) {
        val currentMessId = messId ?: return
        viewModelScope.launch {
            val mess = messRepository.getMess(currentMessId) ?: return@launch
            val updatedMess = when (mealType) {
                MealType.BREAKFAST -> mess.copy(breakfastMenu = menu)
                MealType.LUNCH -> mess.copy(lunchMenu = menu)
                MealType.DINNER -> mess.copy(dinnerMenu = menu)
                else -> mess
            }
            messRepository.updateMess(updatedMess)
            userRepository.getUser(authRepository.currentUser?.uid)?.let { refreshSummary(currentMessId, it) }
        }
    }

    fun createSpecialMealPoll(title: String, eventDate: String) {
        val currentMessId = messId ?: return
        viewModelScope.launch {
            specialMealPollRepository.createPoll(currentMessId, title, eventDate)
            userRepository.getUser(authRepository.currentUser?.uid)?.let { refreshSummary(currentMessId, it) }
        }
    }

    fun manuallyAddUserToSpecialMeal(pollId: String, userId: String) {
        viewModelScope.launch {
            specialMealPollRepository.optIn(pollId, userId)
            messId?.let { mid -> userRepository.getUser(authRepository.currentUser?.uid)?.let { u -> refreshSummary(mid, u) } }
        }
    }

    suspend fun getSpecialMealParticipants(pollId: String): List<String> {
        val poll = specialMealPollRepository.getPoll(pollId)
        return poll?.optedInUserIds.orEmpty()
    }

    suspend fun getMembersWithToggleOn(mealType: MealType): List<String> {
        val currentMessId = messId ?: return emptyList()
        val meals = mealEntryRepository.getMealsForMessAndDate(currentMessId, today)
        val overrides = meals.filter { it.mealType == mealType }
        val members = userRepository.getUsersForMess(currentMessId).filter { it.joinApproved }
        
        val offUids = overrides.filter { it.count <= 0.0 }.map { it.userId }.toSet()
        return members.filter { it.uid !in offUids }.map { it.name.ifBlank { "Member" } }
    }
}
