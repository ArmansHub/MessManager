package com.arman.messmanager.ui.dashboard.financemanager

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.arman.messmanager.R
import com.arman.messmanager.data.model.FixedBillType
import com.arman.messmanager.data.repository.AuthRepository
import com.arman.messmanager.databinding.FragmentFinancemanagerDashboardBinding
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// Finance Manager Dashboard, built from the SRS's "Role-Specific Dashboards" section:
// a Top Card (Pending Approvals), an Overview (Mess Balance vs. Expenses), a Manager
// Election banner (SRS section 3, shown only while a poll is open), and Quick Actions
// to add a daily bazaar entry, a fixed bill, or close the month (SRS section 8).
//
// This version reads real totals from Firestore through FinanceManagerDashboardViewModel.
// The Personal View card is not wired up yet.
class FinanceManagerDashboardFragment : Fragment() {

    private var _binding: FragmentFinancemanagerDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FinanceManagerDashboardViewModel by viewModels()

    // Used only for the "Sign Out" button - a trivial one-off call, not really dashboard
    // business logic, so it doesn't need to go through the ViewModel (same reasoning as
    // MessSetupFragment's sign-out link).
    private val authRepository = AuthRepository()

    // Held so the "Closing the month…" dialog can be dismissed once a terminal
    // (Success/Error) state arrives - AlertDialog has no built-in way to await that.
    private var closeMonthProgressDialog: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFinancemanagerDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rowAddBazaar.setOnClickListener { showAddBazaarDialog() }
        binding.rowAddFixedBill.setOnClickListener { showAddFixedBillDialog() }
        binding.rowLogDeposit.setOnClickListener { showLogDepositDialog() }
        binding.rowCloseMonth.setOnClickListener { onCloseMonthTapped() }
        binding.rowPostNotice.setOnClickListener { showPostNoticeDialog() }
        binding.tvSignOut.setOnClickListener { signOut() }
        binding.cardElectionBanner.setOnClickListener {
            findNavController().navigate(R.id.action_financeManagerDashboardFragment_to_electionFragment)
        }
        binding.cardSpecialMealPollBanner.setOnClickListener {
            findNavController().navigate(R.id.action_financeManagerDashboardFragment_to_specialMealPollFragment)
        }

        // Same safe-collection pattern used on every other screen: only collect the
        // ViewModel's state while this screen is visible.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.closeMonthState.collect { state -> renderCloseMonthState(state) }
            }
        }
    }

    private fun render(state: FinanceDashboardUiState) {
        binding.progressBar.isVisible = state.isLoading

        binding.tvPendingDeposits.text = if (state.pendingApprovalsCount == 0) {
            "No deposits awaiting approval"
        } else {
            "${state.pendingApprovalsCount} deposit(s) awaiting approval"
        }

        binding.tvMessBalance.text = formatCurrency(state.messBalance)
        binding.tvTotalExpenses.text = formatCurrency(state.totalExpenses)

        binding.cardElectionBanner.isVisible = state.hasActiveElection

        val openPollCount = state.openSpecialMealPolls.size
        binding.cardSpecialMealPollBanner.isVisible = openPollCount > 0
        binding.tvSpecialMealPollBannerTitle.text = if (openPollCount == 1) {
            "1 special meal poll is open"
        } else {
            "$openPollCount special meal polls are open"
        }
    }

    // Simple AlertDialog with a single amount field - no custom layout needed.
    private fun showAddBazaarDialog() {
        val amountInput = createAmountEditText()

        AlertDialog.Builder(requireContext())
            .setTitle("Add Daily Bazaar")
            .setMessage("Enter today's total shopping amount")
            .setView(amountInput)
            .setPositiveButton("Save") { _, _ ->
                val amount = amountInput.text.toString().toDoubleOrNull()
                if (amount == null || amount <= 0.0) {
                    Toast.makeText(requireContext(), "Enter a valid amount", Toast.LENGTH_SHORT).show()
                } else {
                    onBazaarAmountEntered(amount)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Offers to bill this expense to one of the mess's currently open Special Meal
    // Polls (SRS section 6) instead of the general mess fund - skipped entirely when
    // there's nothing open to link to, so the common case (an ordinary daily bazaar
    // trip) stays a single dialog like every other "add" flow on this screen.
    //
    // Deliberately title-only (no setMessage): AlertDialog.Builder silently drops
    // setItems()'s list when setMessage() is also set, same reason
    // showAddFixedBillDialog()'s "Select Bill Type" picker below is title-only too.
    private fun onBazaarAmountEntered(amount: Double) {
        val openPolls = viewModel.uiState.value.openSpecialMealPolls
        if (openPolls.isEmpty()) {
            viewModel.addBazaarEntry(amount)
            return
        }

        val options = (listOf("General Mess Fund (default)") + openPolls.map { it.title }).toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Link to a Special Meal Poll?")
            .setItems(options) { _, index ->
                val linkedPollId = if (index == 0) null else openPolls[index - 1].pollId
                viewModel.addBazaarEntry(amount, linkedPollId)
            }
            .show()
    }

    // Two simple dialogs in sequence: first pick the bill type, then enter the amount.
    // This avoids needing a custom dialog layout with a Spinner.
    private fun showAddFixedBillDialog() {
        val billTypes = FixedBillType.entries.toTypedArray()
        val billTypeNames = billTypes.map { it.name }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Select Bill Type")
            .setItems(billTypeNames) { _, index ->
                showFixedBillAmountDialog(billTypes[index])
            }
            .show()
    }

    private fun showFixedBillAmountDialog(type: FixedBillType) {
        val amountInput = createAmountEditText()

        AlertDialog.Builder(requireContext())
            .setTitle("Add ${type.name} Bill")
            .setMessage("Enter this month's amount")
            .setView(amountInput)
            .setPositiveButton("Save") { _, _ ->
                val amount = amountInput.text.toString().toDoubleOrNull()
                if (amount == null || amount <= 0.0) {
                    Toast.makeText(requireContext(), "Enter a valid amount", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.addFixedBill(type, amount)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // "Log Deposit" (SRS section 7): first pick which member handed over the money,
    // then enter the amount - same two-dialogs-in-sequence shape as "Add Fixed Bill".
    // Title-only picker for the same reason showAddFixedBillDialog()'s is: setItems()
    // and setMessage() don't mix in AlertDialog.Builder.
    private fun showLogDepositDialog() {
        val members = viewModel.uiState.value.messMembers
        if (members.isEmpty()) {
            Toast.makeText(requireContext(), "No members to log a deposit for yet", Toast.LENGTH_SHORT).show()
            return
        }

        val memberNames = members.map { it.name }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Select Member")
            .setItems(memberNames) { _, index ->
                showDepositAmountDialog(members[index])
            }
            .show()
    }

    private fun showDepositAmountDialog(member: MemberOption) {
        val amountInput = createAmountEditText()

        AlertDialog.Builder(requireContext())
            .setTitle("Log Deposit for ${member.name}")
            .setMessage("Enter the amount received")
            .setView(amountInput)
            .setPositiveButton("Save") { _, _ ->
                val amount = amountInput.text.toString().toDoubleOrNull()
                if (amount == null || amount <= 0.0) {
                    Toast.makeText(requireContext(), "Enter a valid amount", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.logDeposit(member.uid, amount)
                    Toast.makeText(requireContext(), "Deposit logged for ${member.name}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Confirms before running Close Month - it archives the month, settles every
    // member's balance, and can reassign manager roles, none of which can be undone
    // from this screen.
    private fun onCloseMonthTapped() {
        AlertDialog.Builder(requireContext())
            .setTitle("Close Month")
            .setMessage(
                "This archives this month's numbers, settles every member's balance, " +
                    "hands the Manager roles to this month's election winners, and " +
                    "starts a fresh month. This can't be undone."
            )
            .setPositiveButton("Close Month") { _, _ -> viewModel.closeMonth() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renderCloseMonthState(state: CloseMonthState) {
        when (state) {
            is CloseMonthState.Idle -> Unit
            is CloseMonthState.Loading -> {
                closeMonthProgressDialog = AlertDialog.Builder(requireContext())
                    .setTitle("Closing the month…")
                    .setMessage("Archiving data and handing over manager roles.")
                    .setCancelable(false)
                    .show()
            }
            is CloseMonthState.Success -> {
                closeMonthProgressDialog?.dismiss()
                val handoverLine = if (state.newFinanceManagerName == null && state.newMealManagerName == null) {
                    "No election was open, so the current managers stay in place."
                } else {
                    "New Finance Manager: ${state.newFinanceManagerName ?: "unchanged"}\n" +
                        "New Meal Manager: ${state.newMealManagerName ?: "unchanged"}"
                }
                AlertDialog.Builder(requireContext())
                    .setTitle("${formatMonth(state.closedMonthId)} closed")
                    .setMessage(handoverLine)
                    .setCancelable(false)
                    .setPositiveButton("OK") { _, _ -> viewModel.resetCloseMonthState() }
                    .show()
            }
            is CloseMonthState.Error -> {
                closeMonthProgressDialog?.dismiss()
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                viewModel.resetCloseMonthState()
            }
        }
    }

    // "2026-09" -> "September 2026". Falls back to the raw id if parsing ever fails
    // (e.g. malformed data), so the screen degrades gracefully instead of crashing.
    private fun formatMonth(monthId: String): String = try {
        val yearMonth = YearMonth.parse(monthId)
        "${yearMonth.month.getDisplayName(TextStyle.FULL, Locale.US)} ${yearMonth.year}"
    } catch (e: Exception) {
        monthId
    }

    // "Post Notice" (SRS section 9): a single multi-line message field, no custom
    // dialog layout needed - same lightweight shape as every other "add" dialog here.
    private fun showPostNoticeDialog() {
        val messageInput = EditText(requireContext()).apply {
            hint = "Notice message"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Post Notice")
            .setView(messageInput)
            .setPositiveButton("Post") { _, _ ->
                val message = messageInput.text.toString().trim()
                if (message.isEmpty()) {
                    Toast.makeText(requireContext(), "Enter a message", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.postNotice(message)
                    Toast.makeText(requireContext(), "Notice posted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun signOut() {
        authRepository.signOut()
        findNavController().navigate(R.id.action_financeManagerDashboardFragment_to_loginFragment)
    }

    private fun createAmountEditText(): EditText =
        EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Amount (৳)"
        }

    private fun formatCurrency(amount: Double): String =
        String.format(Locale.US, "৳ %,.2f", amount)

    override fun onDestroyView() {
        super.onDestroyView()
        closeMonthProgressDialog?.dismiss()
        closeMonthProgressDialog = null
        _binding = null
    }
}
