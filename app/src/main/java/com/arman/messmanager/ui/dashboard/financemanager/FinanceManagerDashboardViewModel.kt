package com.arman.messmanager.ui.dashboard.financemanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

// Everything the Finance Manager Dashboard needs to draw itself. One plain data class
// is enough here too, same reasoning as MemberDashboardUiState: this screen displays
// live totals and reacts to two "add" actions, it isn't a multi-step flow.
data class FinanceDashboardUiState(
    val isLoading: Boolean = true,
    val pendingApprovalsCount: Int = 0,
    val messBalance: Double = 0.0,
    val totalExpenses: Double = 0.0,
    // Whether the Super Admin has an open Manager Election poll right now (SRS section
    // 3) - the Finance Manager is also a mess member and can vote, same as everyone
    // else (RBAC matrix: "Update Own Meals & Vote" is Yes for every role).
    val hasActiveElection: Boolean = false,
    // Every Special Meal Poll (SRS section 6) currently open for this mess - drives both
    // the "opt in or out" banner (via .size) and the "Link to a Special Meal Poll?"
    // picker in the "Add Daily Bazaar" dialog, so the Finance Manager can bill an
    // expense to one of these instead of the general mess fund.
    val openSpecialMealPolls: List<LinkablePollOption> = emptyList(),
    // Every approved member of the mess (including managers - they're mess members
    // too) - powers the "Select Member" step of "Log Deposit" (SRS section 7).
    val messMembers: List<MemberOption> = emptyList()
)

// One open poll as offered in the "Add Daily Bazaar" dialog's link picker - just enough
// to show a title and pass its id back to addBazaarEntry().
data class LinkablePollOption(val pollId: String, val title: String)

// One mess member as offered in the "Log Deposit" dialog's member picker.
data class MemberOption(val uid: String, val name: String)

// Result of tapping "Close Month" (SRS section 8). Kept as a separate sealed interface +
// StateFlow from FinanceDashboardUiState - same split AuthViewModel/MessSetupViewModel
// use between a screen's continuously-refreshing state and a discrete action's
// Idle/Loading/Success/Error lifecycle. The dashboard totals keep updating on their own;
// this only changes in response to a single button tap.
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

    // Remembered after the first load so addBazaarEntry()/addFixedBill()/closeMonth()
    // don't have to re-fetch the manager's own profile every time.
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

            refreshStats(currentMessId)
        }
    }

    // Re-reads deposits/bazaar entries/fixed bills and recalculates the numbers shown
    // on screen. Called on first load, and again after adding a bazaar entry, a fixed
    // bill, or closing the month, so the totals update immediately instead of waiting
    // for a manual refresh.
    private suspend fun refreshStats(currentMessId: String) {
        val monthId = currentMonthId(currentMessId)

        val deposits = depositRepository.getDeposits(currentMessId)
        val bazaarEntries = bazaarEntryRepository.getBazaarEntries(currentMessId)
        val fixedBills = fixedBillRepository.getFixedBills(currentMessId, monthId)

        // Pending Approvals: every deposit a member has submitted that the Finance
        // Manager hasn't approved yet, regardless of which month it was submitted in.
        val pendingApprovalsCount = deposits.count { it.status == "pending" }

        val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneId.systemDefault())
        val monthlyApprovedDeposits = deposits
            .filter { it.status == "approved" && timestampFormatter.format(it.date.toDate().toInstant()) == monthId }
            .sumOf { it.amount }
        val monthlyBazaarCost = bazaarEntries
            .filter { it.date.startsWith(monthId) }
            .sumOf { it.amount }
        val monthlyFixedBillsCost = fixedBills.sumOf { it.amount }

        val totalExpenses = monthlyBazaarCost + monthlyFixedBillsCost
        val messBalance = monthlyApprovedDeposits - totalExpenses

        val hasActiveElection = electionPollRepository.getActivePoll(currentMessId) != null
        val openSpecialMealPolls = specialMealPollRepository.getOpenPolls(currentMessId)
            .map { LinkablePollOption(it.pollId, it.title) }
        val messMembers = userRepository.getUsersForMess(currentMessId)
            .filter { it.joinApproved }
            .map { MemberOption(it.uid, it.name.ifBlank { it.email }) }

        _uiState.value = FinanceDashboardUiState(
            isLoading = false,
            pendingApprovalsCount = pendingApprovalsCount,
            messBalance = messBalance,
            totalExpenses = totalExpenses,
            hasActiveElection = hasActiveElection,
            openSpecialMealPolls = openSpecialMealPolls,
            messMembers = messMembers
        )
    }

    // The "yyyy-MM" every monthly figure on this screen is scoped to. Reads
    // Mess.currentMonthId rather than the device's real-world clock, so that Close
    // Month (which advances that field) actually changes what "this month" means for
    // the app instead of everything silently reverting to today's real calendar month
    // on the next load. Falls back to today's real month for messes created before this
    // field existed.
    private suspend fun currentMonthId(currentMessId: String): String =
        messRepository.getMess(currentMessId)?.currentMonthId?.ifBlank { null }
            ?: YearMonth.now().toString()

    // Called from the "Add Daily Bazaar" dialog. linkedPollId optionally bills this
    // expense to one Special Meal Poll's opted-in members instead of the general mess
    // fund (SRS section 6) - null (the default) is the ordinary case.
    fun addBazaarEntry(amount: Double, linkedPollId: String? = null) {
        val currentMessId = messId ?: return
        val uid = authRepository.currentUser?.uid ?: return

        viewModelScope.launch {
            bazaarEntryRepository.addBazaarEntry(currentMessId, amount, uid, linkedPollId)
            refreshStats(currentMessId)
        }
    }

    // Called from the "Add Fixed Bill" dialog.
    fun addFixedBill(type: FixedBillType, amount: Double) {
        val currentMessId = messId ?: return
        val uid = authRepository.currentUser?.uid ?: return

        viewModelScope.launch {
            val monthId = currentMonthId(currentMessId)
            fixedBillRepository.addFixedBill(currentMessId, monthId, type, amount, uid)
            refreshStats(currentMessId)
        }
    }

    // "Post Notice" (SRS section 9, Admins and Managers only): puts a message on the
    // Digital Notice Board every mess member sees read-only on their own dashboard.
    fun postNotice(title: String, message: String) {
        val currentMessId = messId ?: return
        val uid = authRepository.currentUser?.uid ?: return

        viewModelScope.launch {
            noticeRepository.postNotice(currentMessId, uid, title, message)
        }
    }

    // "Log Deposit" (SRS section 7): the Finance Manager directly records cash/mobile
    // banking funds a member just handed over. Saved already approved - the Finance
    // Manager logging it *is* the approval, so it "reflects immediately" the way the SRS
    // describes, and Close Month will pick it up like any other approved deposit dated
    // in the current month.
    fun logDeposit(userId: String, amount: Double) {
        val currentMessId = messId ?: return

        viewModelScope.launch {
            depositRepository.addDeposit(currentMessId, userId, amount)
            refreshStats(currentMessId)
        }
    }

    // "Close Month" (SRS section 8): archives the month's numbers, settles every
    // member's balance, hands the Manager roles over to this month's election winners,
    // and advances the mess to a new current month. Ignored while a close is already in
    // flight so a double-tap can't archive/settle the same month twice.
    fun closeMonth() {
        val currentMessId = messId ?: return
        if (_closeMonthState.value == CloseMonthState.Loading) return

        _closeMonthState.value = CloseMonthState.Loading
        viewModelScope.launch {
            _closeMonthState.value = try {
                val closedByUid = authRepository.currentUser?.uid
                    ?: throw IllegalStateException("Not signed in")
                runClose(currentMessId, closedByUid)
            } catch (e: Exception) {
                CloseMonthState.Error(e.message ?: "Could not close the month")
            }
            // Whether it succeeded or failed, the underlying numbers may have moved -
            // reload so the dashboard reflects reality either way.
            refreshStats(currentMessId)
        }
    }

    private suspend fun runClose(currentMessId: String, closedByUid: String): CloseMonthState.Success {
        val mess = messRepository.getMess(currentMessId)
            ?: throw IllegalStateException("Mess not found")
        val monthId = mess.currentMonthId.ifBlank { YearMonth.now().toString() }

        // Step 1: gather this month's raw data. Every read here mirrors the same
        // repository methods the live dashboards already use - Close Month doesn't
        // introduce a second source of truth for what "this month" contains.
        val members = userRepository.getUsersForMess(currentMessId).filter { it.joinApproved }
        val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneId.systemDefault())
        val deposits = depositRepository.getDeposits(currentMessId)
            .filter { it.status == "approved" && timestampFormatter.format(it.date.toDate().toInstant()) == monthId }
        val bazaarEntries = bazaarEntryRepository.getBazaarEntries(currentMessId)
            .filter { it.date.startsWith(monthId) }
        val fixedBills = fixedBillRepository.getFixedBills(currentMessId, monthId)
        val mealEntries = mealEntryRepository.getMealsForMess(currentMessId)
            .filter { it.date.startsWith(monthId) }

        // Step 2: the dynamic meal rate (SRS section 7). Bazaar entries linked to a
        // Special Meal Poll are billed separately to just that poll's members (SRS
        // section 6), so they're excluded from both the standard bazaar cost and the
        // rate calculation.
        val standardBazaarEntries = bazaarEntries.filter { it.linkedPollId == null }
        val linkedBazaarEntries = bazaarEntries.filter { it.linkedPollId != null }

        val totalStandardMeals = mealEntries.sumOf { it.count }
        val totalStandardBazaarCost = standardBazaarEntries.sumOf { it.amount }
        val mealRate = if (totalStandardMeals > 0) totalStandardBazaarCost / totalStandardMeals else 0.0

        val totalFixedBillsCost = fixedBills.sumOf { it.amount }
        val fixedBillShare = if (members.isNotEmpty()) totalFixedBillsCost / members.size else 0.0

        // Resolve each linked entry's participants from the poll's *current*
        // optedInUserIds rather than a snapshot taken when the expense was logged - SRS
        // section 6: "including those who didn't vote but later joined in", so a member
        // opting in right up until Close Month still gets counted in.
        val participantsByPollId = linkedBazaarEntries
            .mapNotNull { it.linkedPollId }
            .distinct()
            .associateWith { pollId -> specialMealPollRepository.getPoll(pollId)?.optedInUserIds.orEmpty() }

        val totalSpecialMealCost = linkedBazaarEntries.sumOf { it.amount }
        val totalDeposits = deposits.sumOf { it.amount }

        // Step 3: settle every member's own numbers using the SRS's balance formula
        // (section 8): Total Deposits - [(Personal Meals x Meal Rate) + Fixed Bill
        // Share + Personal Special Meal Costs] - added on top of whatever they were
        // already carrying (User.balance), since that field is never zeroed out - it
        // *is* the running opening-balance-for-the-new-month the SRS describes.
        val memberSummaries = members.map { member ->
            val personalMeals = mealEntries.filter { it.userId == member.uid }.sumOf { it.count }
            val mealCost = personalMeals * mealRate
            val personalDeposits = deposits.filter { it.memberUid == member.uid }.sumOf { it.amount }
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

        // Step 4: tally the open Manager Election (SRS section 3), if there is one. A
        // member winning the role they already hold is treated the same as anyone else
        // winning it for the first time by the demote/promote pass below.
        val activePoll = electionPollRepository.getActivePoll(currentMessId)
        // TODO: With generic polls, manager election and role rotation needs a new implementation.
        val financeWinnerUid: String? = null
        val mealWinnerUid: String? = null

        // Step 5: archive the month before touching any member document, so a crash or
        // Firestore failure partway through the balance/role updates below still leaves
        // a record of what this month's numbers were.
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

        // Step 6: write each member's new balance and (if an election was held) new
        // role in a single document write per member, so a manager's demotion can never
        // land on top of a stale balance from before this function started.
        memberSummaries.forEach { memberSummary ->
            val member = members.first { it.uid == memberSummary.uid }
            userRepository.createUser(
                member.copy(balance = memberSummary.closingBalance)
            )
        }

        // Step 7: close the poll (so a new one can be triggered for next month) and
        // move the mess's "current month" forward - fixed bills and every monthly
        // total on this dashboard start counting from zero again from here.
        if (activePoll != null) {
            electionPollRepository.closePoll(activePoll.pollId)
        }
        val nextMonthId = YearMonth.parse(monthId).plusMonths(1).toString()
        messRepository.advanceToMonth(currentMessId, nextMonthId)

        return CloseMonthState.Success(
            closedMonthId = monthId,
            newFinanceManagerName = null,
            newMealManagerName = null
        )
    }

    // Called after the Fragment reacts to a terminal Close Month state (Success/Error),
    // so the same state doesn't get handled twice (e.g. on a configuration change).
    fun resetCloseMonthState() {
        _closeMonthState.value = CloseMonthState.Idle
    }
}
