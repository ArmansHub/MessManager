package com.arman.messmanager.ui.dashboard.financemanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arman.messmanager.data.model.FixedBill
import com.arman.messmanager.data.model.FixedBillType
import com.arman.messmanager.data.model.MemberMonthSummary
import com.arman.messmanager.data.model.MonthSummary
import com.arman.messmanager.data.model.User
import com.arman.messmanager.data.model.UserRole
import com.arman.messmanager.data.repository.AuthRepository
import com.arman.messmanager.data.repository.BazaarEntryRepository
import com.arman.messmanager.data.repository.DepositRepository
import com.arman.messmanager.data.repository.ElectionPollRepository
import com.arman.messmanager.data.repository.FixedBillRepository
import com.arman.messmanager.data.repository.MealEntryRepository
import com.arman.messmanager.data.repository.MessRepository
import com.arman.messmanager.data.repository.MonthSummaryRepository
import com.arman.messmanager.data.repository.NoticeRepository
import com.arman.messmanager.data.repository.SpecialMealPollRepository
import com.arman.messmanager.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class FinanceDashboardUiState(
    val isLoading: Boolean = true,
    // Profile
    val profileName: String = "",
    val profilePictureUrl: String? = null,
    // Stats
    val pendingApprovalsCount: Int = 0,
    val messBalance: Double = 0.0,
    val totalExpenses: Double = 0.0,
    // Personal snapshot data (calculated but not shown on manager dash as requested)
    val personalBalance: Double = 0.0,
    val personalMealsToday: Int = 0,
    // Fixed bills for management
    val currentFixedBills: Map<FixedBillType, Double> = emptyMap(),
    // Breakdown data for detail dialogs
    val monthlyBazaarCost: Double = 0.0,
    val memberBalances: List<MemberBalanceBreakdown> = emptyList(),
    val pendingDeposits: List<PendingDepositOption> = emptyList(),
    // Data for selectors
    val openSpecialMealPolls: List<LinkablePollOption> = emptyList(),
    val messMembers: List<MemberOption> = emptyList()
)

data class PendingDepositOption(val id: String, val name: String, val amount: Double, val date: Long)
data class MemberBalanceBreakdown(val name: String, val balance: Double)
data class LinkablePollOption(val pollId: String, val title: String)
data class MemberOption(val uid: String, val name: String)

sealed interface CloseMonthState {
    data object Idle : CloseMonthState
    data object Loading : CloseMonthState
    data class Success(
        val closedMonthId: String,
        val newFinanceManagerName: String?,
        val newMealManagerName: String?
    ) : CloseMonthState
    data class Error(val message: String) : CloseMonthState
}

class FinanceManagerDashboardViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val messRepository: MessRepository = MessRepository(),
    private val depositRepository: DepositRepository = DepositRepository(),
    private val bazaarEntryRepository: BazaarEntryRepository = BazaarEntryRepository(),
    private val fixedBillRepository: FixedBillRepository = FixedBillRepository(),
    private val mealEntryRepository: MealEntryRepository = MealEntryRepository(),
    private val electionPollRepository: ElectionPollRepository = ElectionPollRepository(),
    private val monthSummaryRepository: MonthSummaryRepository = MonthSummaryRepository(),
    private val specialMealPollRepository: SpecialMealPollRepository = SpecialMealPollRepository(),
    private val noticeRepository: NoticeRepository = NoticeRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(FinanceDashboardUiState())
    val uiState: StateFlow<FinanceDashboardUiState> = _uiState.asStateFlow()

    private val _closeMonthState = MutableStateFlow<CloseMonthState>(CloseMonthState.Idle)
    val closeMonthState: StateFlow<CloseMonthState> = _closeMonthState.asStateFlow()

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

            refreshStats(currentMessId, user)
        }
    }

    private suspend fun refreshStats(currentMessId: String, user: com.arman.messmanager.data.model.User) {
        val uid = user.uid
        val today = java.time.LocalDate.now().toString()
        val todaysMeals = mealEntryRepository.getMealsForDate(uid, today)
        
        val monthId = currentMonthId(currentMessId)

        val deposits = depositRepository.getDeposits(currentMessId)
        val bazaarEntries = bazaarEntryRepository.getBazaarEntries(currentMessId)
        val fixedBills = fixedBillRepository.getFixedBills(currentMessId, monthId)
        val mealEntriesMess = mealEntryRepository.getMealsForMess(currentMessId)
            .filter { it.date.startsWith(monthId) }

        val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneId.systemDefault())
        
        // 1. Pending Approvals
        val pendingRaw = deposits.filter { it.status == "pending" }
        val pendingOptions = pendingRaw.map { dep ->
            val name = userRepository.getUser(dep.memberUid)?.name?.ifBlank { "Member" } ?: "Member"
            PendingDepositOption(dep.depositId, name, dep.amount, dep.date.toDate().time)
        }

        // 2. MESS FINANCIALS
        val monthlyApprovedDeposits = deposits
            .filter { it.status == "approved" && it.date != null && timestampFormatter.format(it.date.toDate().toInstant()) == monthId }
        val totalMessDeposits = monthlyApprovedDeposits.sumOf { it.amount }

        val monthlyBazaarEntries = bazaarEntries.filter { it.date.startsWith(monthId) }
        val monthlyBazaarCost = monthlyBazaarEntries.sumOf { it.amount }
        val monthlyFixedBillsCost = fixedBills.sumOf { it.amount }
        val totalExpenses = monthlyBazaarCost + monthlyFixedBillsCost

        val messBalance = totalMessDeposits - totalExpenses

        // 3. DYNAMIC MEAL RATE
        val monthlyStandardBazaarCost = monthlyBazaarEntries
            .filter { it.linkedPollId == null }
            .sumOf { it.amount }
        val totalStandardMeals = mealEntriesMess.sumOf { it.count }
        val mealRate = if (totalStandardMeals > 0.0) monthlyStandardBazaarCost / totalStandardMeals else 0.0
        
        // 4. MEMBERSHIP CALCULATIONS
        val users = userRepository.getUsersForMess(currentMessId)
        val members = users.filter { it.joinApproved }
        val fixedBillShare = if (members.isNotEmpty()) monthlyFixedBillsCost / members.size else 0.0
        
        val linkedBazaarEntries = monthlyBazaarEntries.filter { it.linkedPollId != null }
        val participantsByPollId = linkedBazaarEntries
            .mapNotNull { it.linkedPollId }
            .distinct()
            .associateWith { pollId -> specialMealPollRepository.getPoll(pollId)?.optedInUserIds.orEmpty() }

        val memberBalanceBreakdowns = members.map { member ->
            val personalMeals = mealEntriesMess.filter { it.userId == member.uid }.sumOf { it.count }
            val personalMealCost = personalMeals * mealRate
            val personalApprovedDeposits = monthlyApprovedDeposits.filter { it.memberUid == member.uid }.sumOf { it.amount }
            
            val specialCost = linkedBazaarEntries.sumOf { entry ->
                val participants = participantsByPollId[entry.linkedPollId].orEmpty()
                if (member.uid in participants) entry.amount / participants.size.coerceAtLeast(1) else 0.0
            }

            val liveBalance = member.balance + personalApprovedDeposits - (personalMealCost + fixedBillShare + specialCost)
            MemberBalanceBreakdown(member.name.ifBlank { "Member" }, liveBalance)
        }

        val myPersonalBalance = memberBalanceBreakdowns.find { members.find { m -> m.uid == uid }?.name == it.name }?.balance ?: 0.0

        // UI STATE
        val openSpecialMealPolls = specialMealPollRepository.getOpenPolls(currentMessId)
            .map { LinkablePollOption(it.pollId, it.title) }
        val messMemberOptions = members.map { MemberOption(it.uid, it.name.ifBlank { it.email }) }
        val billMap = fixedBills.associate { it.type to it.amount }

        _uiState.value = FinanceDashboardUiState(
            isLoading = false,
            profileName = user.name.ifBlank { "User" },
            profilePictureUrl = user.profilePictureUrl,
            pendingApprovalsCount = pendingOptions.size,
            messBalance = messBalance,
            totalExpenses = totalExpenses,
            personalBalance = myPersonalBalance,
            personalMealsToday = todaysMeals.count { it.count > 0 },
            currentFixedBills = billMap,
            monthlyBazaarCost = monthlyBazaarCost,
            memberBalances = memberBalanceBreakdowns,
            pendingDeposits = pendingOptions,
            openSpecialMealPolls = openSpecialMealPolls,
            messMembers = messMemberOptions
        )
    }

    private suspend fun currentMonthId(currentMessId: String): String =
        messRepository.getMess(currentMessId)?.currentMonthId?.ifBlank { null }
            ?: YearMonth.now().toString()

    fun approveDeposit(depositId: String) {
        val currentMessId = messId ?: return
        viewModelScope.launch {
            depositRepository.approveDeposit(depositId)
            userRepository.getUser(authRepository.currentUser?.uid)?.let { refreshStats(currentMessId, it) }
        }
    }

    fun rejectDeposit(depositId: String) {
        val currentMessId = messId ?: return
        viewModelScope.launch {
            depositRepository.rejectDeposit(depositId)
            userRepository.getUser(authRepository.currentUser?.uid)?.let { refreshStats(currentMessId, it) }
        }
    }

    fun addBazaarEntry(amount: Double, linkedPollId: String? = null) {
        val currentMessId = messId ?: return
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            bazaarEntryRepository.addBazaarEntry(currentMessId, amount, uid, linkedPollId)
            userRepository.getUser(uid)?.let { refreshStats(currentMessId, it) }
        }
    }

    fun setFixedBill(type: FixedBillType, amount: Double) {
        val currentMessId = messId ?: return
        val uid = authRepository.currentUser?.uid ?: return
        
        val updatedMap = _uiState.value.currentFixedBills.toMutableMap()
        updatedMap[type] = amount
        _uiState.value = _uiState.value.copy(currentFixedBills = updatedMap)

        viewModelScope.launch {
            val monthId = currentMonthId(currentMessId)
            val existingBills = fixedBillRepository.getFixedBills(currentMessId, monthId)
            val existing = existingBills.find { it.type == type }
            
            if (existing != null) {
                fixedBillRepository.updateFixedBill(existing.copy(amount = amount, addedBy = uid))
            } else {
                fixedBillRepository.addFixedBill(currentMessId, monthId, type, amount, uid)
            }
            userRepository.getUser(uid)?.let { refreshStats(currentMessId, it) }
        }
    }

    fun postNotice(title: String, message: String) {
        val currentMessId = messId ?: return
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            noticeRepository.postNotice(currentMessId, uid, title, message)
        }
    }

    fun logDeposit(userId: String, amount: Double) {
        val currentMessId = messId ?: return
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            depositRepository.addDeposit(currentMessId, userId, amount, status = "approved")
            userRepository.getUser(uid)?.let { refreshStats(currentMessId, it) }
        }
    }

    fun closeMonth() {
        val currentMessId = messId ?: return
        if (_closeMonthState.value == CloseMonthState.Loading) return
        _closeMonthState.value = CloseMonthState.Loading
        viewModelScope.launch {
            val uid = authRepository.currentUser?.uid ?: return@launch
            _closeMonthState.value = try {
                runClose(currentMessId, uid)
            } catch (e: Exception) {
                CloseMonthState.Error(e.message ?: "Could not close the month")
            }
            userRepository.getUser(uid)?.let { refreshStats(currentMessId, it) }
        }
    }

    private suspend fun runClose(currentMessId: String, closedByUid: String): CloseMonthState.Success {
        val mess = messRepository.getMess(currentMessId) ?: throw IllegalStateException("Mess not found")
        val monthId = mess.currentMonthId.ifBlank { YearMonth.now().toString() }

        val members = userRepository.getUsersForMess(currentMessId).filter { it.joinApproved }
        val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneId.systemDefault())
        
        val allDeposits = depositRepository.getDeposits(currentMessId)
        val monthlyApprovedDeposits = allDeposits
            .filter { it.status == "approved" && timestampFormatter.format(it.date.toDate().toInstant()) == monthId }
        
        val bazaarEntries = bazaarEntryRepository.getBazaarEntries(currentMessId)
            .filter { it.date.startsWith(monthId) }
        
        val fixedBills = fixedBillRepository.getFixedBills(currentMessId, monthId)
        val mealEntries = mealEntryRepository.getMealsForMess(currentMessId)
            .filter { it.date.startsWith(monthId) }

        val standardBazaarEntries = bazaarEntries.filter { it.linkedPollId == null }
        val linkedBazaarEntries = bazaarEntries.filter { it.linkedPollId != null }

        val totalStandardMeals = mealEntries.sumOf { it.count }
        val totalStandardBazaarCost = standardBazaarEntries.sumOf { it.amount }
        val mealRate = if (totalStandardMeals > 0) totalStandardBazaarCost / totalStandardMeals else 0.0

        val totalFixedBillsCost = fixedBills.sumOf { it.amount }
        val fixedBillShare = if (members.isNotEmpty()) totalFixedBillsCost / members.size else 0.0

        val participantsByPollId = linkedBazaarEntries
            .mapNotNull { it.linkedPollId }
            .distinct()
            .associateWith { pollId -> specialMealPollRepository.getPoll(pollId)?.optedInUserIds.orEmpty() }

        val totalSpecialMealCost = linkedBazaarEntries.sumOf { it.amount }
        val totalDeposits = monthlyApprovedDeposits.sumOf { it.amount }

        val memberSummaries = members.map { member ->
            val personalMeals = mealEntries.filter { it.userId == member.uid }.sumOf { it.count }
            val mealCost = personalMeals * mealRate
            val personalDeposits = monthlyApprovedDeposits.filter { it.memberUid == member.uid }.sumOf { it.amount }
            val specialCost = linkedBazaarEntries.sumOf { entry ->
                val participants = participantsByPollId[entry.linkedPollId].orEmpty()
                if (member.uid in participants) entry.amount / participants.size.coerceAtLeast(1) else 0.0
            }

            val openingBalance = member.balance
            val closingBalance = openingBalance + personalDeposits - (mealCost + fixedBillShare + specialCost)

            MemberMonthSummary(
                uid = member.uid,
                name = member.name,
                openingBalance = openingBalance,
                totalDeposits = personalDeposits,
                totalMeals = personalMeals,
                mealCost = mealCost,
                fixedBillShare = fixedBillShare,
                specialMealCost = specialCost,
                closingBalance = closingBalance
            )
        }

        val activePoll = electionPollRepository.getActivePoll(currentMessId)
        val financeWinnerUid: String? = null
        val mealWinnerUid: String? = null

        monthSummaryRepository.archiveMonth(
            MonthSummary(
                messId = currentMessId,
                monthId = monthId,
                totalStandardBazaarCost = totalStandardBazaarCost,
                totalSpecialMealCost = totalSpecialMealCost,
                totalFixedBillsCost = totalFixedBillsCost,
                totalExpenses = totalStandardBazaarCost + totalSpecialMealCost + totalFixedBillsCost,
                totalDeposits = totalDeposits,
                totalStandardMeals = totalStandardMeals,
                mealRate = mealRate,
                memberSummaries = memberSummaries,
                newFinanceManagerUid = financeWinnerUid,
                newMealManagerUid = mealWinnerUid,
                closedByUid = closedByUid,
                closedAtEpochMs = System.currentTimeMillis()
            )
        )

        memberSummaries.forEach { memberSummary ->
            val member = members.first { it.uid == memberSummary.uid }
            userRepository.createUser(member.copy(balance = memberSummary.closingBalance))
        }

        if (activePoll != null) {
            electionPollRepository.closePoll(activePoll.pollId)
        }
        val nextMonthId = YearMonth.parse(monthId).plusMonths(1).toString()
        messRepository.advanceToMonth(currentMessId, nextMonthId)

        return CloseMonthState.Success(monthId, null, null)
    }

    fun resetCloseMonthState() {
        _closeMonthState.value = CloseMonthState.Idle
    }
}
