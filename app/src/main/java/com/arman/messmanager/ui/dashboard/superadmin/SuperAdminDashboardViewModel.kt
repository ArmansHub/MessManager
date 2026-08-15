package com.arman.messmanager.ui.dashboard.superadmin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arman.messmanager.data.model.ElectionPoll
import com.arman.messmanager.data.model.MealEntry
import com.arman.messmanager.data.model.UserRole
import com.arman.messmanager.data.repository.AuthRepository
import com.arman.messmanager.data.repository.ElectionPollRepository
import com.arman.messmanager.data.repository.MealEntryRepository
import com.arman.messmanager.data.repository.MessRepository
import com.arman.messmanager.data.repository.NoticeRepository
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

data class MemberOption(val uid: String, val name: String)

data class SuperAdminDashboardUiState(
    val isLoading: Boolean = true,
    // Profile
    val profileName: String = "",
    val profilePictureUrl: String? = null,
    // Mess stats
    val totalMembers: Int = 0,
    val activeManagers: Int = 0,
    // Personal snapshot
    val personalBalance: Double = 0.0,
    val personalMealsOnCount: Int = 0,
    // Action indicators
    val activeElectionTitle: String? = null,
    // Data for selectors
    val messMembers: List<MemberOption> = emptyList(),
    val dueThreshold: Double = -500.0
)

sealed interface AdminActionState {
    data object Idle : AdminActionState
    data object Loading : AdminActionState
    data class Success(val message: String) : AdminActionState
    data class Error(val message: String) : AdminActionState
}

class SuperAdminDashboardViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val messRepository: MessRepository = MessRepository(),
    private val mealEntryRepository: MealEntryRepository = MealEntryRepository(),
    private val electionPollRepository: ElectionPollRepository = ElectionPollRepository(),
    private val noticeRepository: NoticeRepository = NoticeRepository(),
    private val depositRepository: DepositRepository = DepositRepository(),
    private val bazaarEntryRepository: BazaarEntryRepository = BazaarEntryRepository(),
    private val fixedBillRepository: FixedBillRepository = FixedBillRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SuperAdminDashboardUiState())
    val uiState: StateFlow<SuperAdminDashboardUiState> = _uiState.asStateFlow()

    private val _adminActionState = MutableStateFlow<AdminActionState>(AdminActionState.Idle)
    val adminActionState: StateFlow<AdminActionState> = _adminActionState.asStateFlow()

    private val today: String = LocalDate.now().toString()
    private var messId: String? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val uid = authRepository.currentUser?.uid ?: return@launch
            val user = userRepository.getUser(uid) ?: return@launch
            messId = user.messId
            val currentMessId = messId ?: return@launch

            loadDashboardData(currentMessId, user)
        }
    }

    private suspend fun loadDashboardData(currentMessId: String, user: com.arman.messmanager.data.model.User) {
        val uid = user.uid
        
        val users = userRepository.getUsersForMess(currentMessId)
        val approvedMembers = users.filter { it.joinApproved }
        
        // Personal calculation
        val monthId = YearMonth.now().toString()
        val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneId.systemDefault())
        
        val deposits = depositRepository.getDeposits(currentMessId)
        val bazaarEntries = bazaarEntryRepository.getBazaarEntries(currentMessId)
        val fixedBills = fixedBillRepository.getFixedBills(currentMessId, monthId)
        val mealEntriesAll = mealEntryRepository.getMealsForMess(currentMessId)
            .filter { it.date.startsWith(monthId) }

        // APPROVED DEPOSITS
        val monthlyApprovedDeposits = deposits
            .filter { it.status == "approved" && timestampFormatter.format(it.date.toDate().toInstant()) == monthId }
        val myApprovedDeposits = monthlyApprovedDeposits.filter { it.memberUid == uid }.sumOf { it.amount }

        // DYNAMIC MEAL RATE
        val monthlyStandardBazaarCost = bazaarEntries
            .filter { it.date.startsWith(monthId) && it.linkedPollId == null }
            .sumOf { it.amount }
        val totalMessMeals = mealEntriesAll.sumOf { it.count }
        val mealRate = if (totalMessMeals > 0.0) monthlyStandardBazaarCost / totalMessMeals else 0.0
        
        // PERSONAL USAGE
        val myMealsCount = mealEntriesAll.filter { it.userId == uid }.sumOf { it.count }
        val myMealCost = myMealsCount * mealRate
        
        // FIXED BILLS
        val monthlyFixedBillsCost = fixedBills.sumOf { it.amount }
        val fixedBillShare = if (approvedMembers.isNotEmpty()) monthlyFixedBillsCost / approvedMembers.size else 0.0
        
        val personalBalance = (user.balance) + myApprovedDeposits - (myMealCost + fixedBillShare)
        
        val todayMeals = mealEntryRepository.getMealsForDate(uid, today)
        val myTodayMealsCount = countMealsOn(todayMeals)

        val activePoll = electionPollRepository.getActivePoll(currentMessId)
        val mess = messRepository.getMess(currentMessId)
        
        _uiState.value = SuperAdminDashboardUiState(
            isLoading = false,
            profileName = user.name.ifBlank { "User" },
            profilePictureUrl = user.profilePictureUrl,
            totalMembers = approvedMembers.size,
            activeManagers = users.count { it.role == UserRole.FINANCE_MANAGER || it.role == UserRole.MEAL_MANAGER },
            personalBalance = personalBalance,
            personalMealsOnCount = myTodayMealsCount,
            activeElectionTitle = activePoll?.title,
            messMembers = approvedMembers.filter { it.uid != uid }.map { MemberOption(it.uid, it.name.ifBlank { it.email }) },
            dueThreshold = -(mess?.dueThresholdBdt ?: 500.0)
        )
    }

    fun triggerElection(monthId: String, durationHours: Int, roles: List<String>) {
        val currentMessId = messId ?: return
        viewModelScope.launch {
            _adminActionState.value = AdminActionState.Loading
            try {
                val members = userRepository.getUsersForMess(currentMessId).filter { it.joinApproved }
                val candidateUids = members.map { it.uid }
                electionPollRepository.createPoll(
                    messId = currentMessId,
                    title = "Manager Election for $monthId",
                    options = candidateUids,
                    monthId = monthId,
                    durationHours = durationHours,
                    roles = roles
                )
                _adminActionState.value = AdminActionState.Success("Election started for $monthId")
                messId?.let { userRepository.getUser(authRepository.currentUser?.uid)?.let { u -> loadDashboardData(it, u) } }
            } catch (e: Exception) {
                _adminActionState.value = AdminActionState.Error(e.message ?: "Failed to trigger election")
            }
        }
    }

    fun closeElection() {
        val currentMessId = messId ?: return
        viewModelScope.launch {
            _adminActionState.value = AdminActionState.Loading
            try {
                val poll = electionPollRepository.getActivePoll(currentMessId)
                if (poll != null) {
                    electionPollRepository.closePoll(poll.pollId)
                    _adminActionState.value = AdminActionState.Success("Election closed")
                    messId?.let { userRepository.getUser(authRepository.currentUser?.uid)?.let { u -> loadDashboardData(it, u) } }
                }
            } catch (e: Exception) {
                _adminActionState.value = AdminActionState.Error(e.message ?: "Failed to close election")
            }
        }
    }

    suspend fun getPastElections(): List<ElectionPoll> {
        val currentMessId = messId ?: return emptyList()
        return electionPollRepository.getPastResults(currentMessId)
    }

    fun postNotice(title: String, message: String) {
        val currentMessId = messId ?: return
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            noticeRepository.postNotice(currentMessId, uid, title, message)
            _adminActionState.value = AdminActionState.Success("Notice posted")
        }
    }

    fun removeMember(memberUid: String) {
        val currentMessId = messId ?: return
        viewModelScope.launch {
            _adminActionState.value = AdminActionState.Loading
            try {
                userRepository.removeMember(memberUid)
                _adminActionState.value = AdminActionState.Success("Member removed")
                messId?.let { userRepository.getUser(authRepository.currentUser?.uid)?.let { u -> loadDashboardData(it, u) } }
            } catch (e: Exception) {
                _adminActionState.value = AdminActionState.Error(e.message ?: "Failed to remove member")
            }
        }
    }

    fun assignRole(memberUid: String, role: UserRole) {
        val currentMessId = messId ?: return
        viewModelScope.launch {
            _adminActionState.value = AdminActionState.Loading
            try {
                userRepository.setRole(memberUid, role)
                _adminActionState.value = AdminActionState.Success("Role assigned successfully")
                messId?.let { userRepository.getUser(authRepository.currentUser?.uid)?.let { u -> loadDashboardData(it, u) } }
            } catch (e: Exception) {
                _adminActionState.value = AdminActionState.Error(e.message ?: "Failed to assign role")
            }
        }
    }

    fun deleteMess() {
        val currentMessId = messId ?: return
        viewModelScope.launch {
            _adminActionState.value = AdminActionState.Loading
            try {
                messRepository.deleteMess(currentMessId)
                _adminActionState.value = AdminActionState.Success("Mess deleted successfully")
            } catch (e: Exception) {
                _adminActionState.value = AdminActionState.Error(e.message ?: "Failed to delete mess")
            }
        }
    }

    fun resetAdminActionState() {
        _adminActionState.value = AdminActionState.Idle
    }

    fun countMealsOn(meals: List<MealEntry>): Int {
        val mealTypes = listOf(com.arman.messmanager.data.model.MealType.BREAKFAST, com.arman.messmanager.data.model.MealType.LUNCH, com.arman.messmanager.data.model.MealType.DINNER)
        return mealTypes.count { type ->
            val entry = meals.find { it.mealType == type }
            entry?.let { it.count > 0.0 } ?: true
        }
    }
}
