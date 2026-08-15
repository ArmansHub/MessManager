package com.arman.messmanager.ui.dashboard.financemanager

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
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
import com.arman.messmanager.ui.navigation.safeNavigateToLogin
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.util.Locale

class FinanceManagerDashboardFragment : Fragment() {

    private var _binding: FragmentFinancemanagerDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FinanceManagerDashboardViewModel by viewModels()

    private val authRepository = AuthRepository()

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
        binding.rowAddFixedBill.setOnClickListener { showManageFixedCostsDialog() }
        binding.rowAddRegularBill.setOnClickListener { showAddRegularBillDialog() }
        binding.rowLogDeposit.setOnClickListener { showLogDepositDialog() }
        binding.rowCloseMonth.setOnClickListener { onCloseMonthTapped() }
        binding.tvSignOut.setOnClickListener { signOut() }
        
        binding.rowGoToMemberDashboard.setOnClickListener {
            findNavController().navigate(R.id.action_financeManagerDashboardFragment_to_memberDashboardFragment)
        }
        binding.rowProfile.setOnClickListener {
            findNavController().navigate(R.id.action_financeManagerDashboardFragment_to_profileFragment)
        }

        binding.cardPendingApprovals.setOnClickListener { showPendingApprovalsDialog() }
        binding.cardMessBalance.setOnClickListener { showMemberBalancesDialog() }
        binding.cardTotalExpenses.setOnClickListener { showExpensesBreakdownDialog() }

        viewModel.refresh()

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

        binding.tvProfileName.text = state.profileName
        com.bumptech.glide.Glide.with(this)
            .load(state.profilePictureUrl)
            .placeholder(R.drawable.ic_person)
            .circleCrop()
            .into(binding.ivSmallProfilePicture)

        binding.tvPendingDeposits.text = if (state.pendingApprovalsCount == 0) {
            "No deposits awaiting approval"
        } else {
            "${state.pendingApprovalsCount} deposit(s) awaiting approval"
        }

        binding.tvMessBalance.text = formatCurrency(state.messBalance)
        binding.tvTotalExpenses.text = formatCurrency(state.totalExpenses)
    }

    private fun showPendingApprovalsDialog() {
        val pending = viewModel.uiState.value.pendingDeposits
        if (pending.isEmpty()) {
            Toast.makeText(requireContext(), "No pending approvals", Toast.LENGTH_SHORT).show()
            return
        }

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 20)
        }

        pending.forEach { dep ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 15, 0, 15)
            }
            
            val infoText = TextView(requireContext()).apply {
                text = "${dep.name}: ${formatCurrency(dep.amount)}"
                setTextColor(ContextCompat.getColor(context, R.color.brand_primary))
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            
            val buttonRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 10, 0, 0)
            }
            
            val approveBtn = TextView(requireContext()).apply {
                text = "APPROVE"
                setTextColor(ContextCompat.getColor(context, android.R.color.holo_green_dark))
                setPadding(0, 0, 40, 0)
                setOnClickListener {
                    viewModel.approveDeposit(dep.id)
                    Toast.makeText(context, "Deposit Approved", Toast.LENGTH_SHORT).show()
                }
            }
            
            val rejectBtn = TextView(requireContext()).apply {
                text = "REJECT"
                setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
                setOnClickListener {
                    viewModel.rejectDeposit(dep.id)
                    Toast.makeText(context, "Deposit Rejected", Toast.LENGTH_SHORT).show()
                }
            }
            
            buttonRow.addView(approveBtn)
            buttonRow.addView(rejectBtn)
            
            row.addView(infoText)
            row.addView(buttonRow)
            container.addView(row)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Pending Deposit Requests")
            .setView(container)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showMemberBalancesDialog() {
        val balances = viewModel.uiState.value.memberBalances
        if (balances.isEmpty()) return

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 20)
        }

        balances.forEach { member ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 15, 0, 15)
            }
            
            val nameLabel = TextView(requireContext()).apply {
                text = member.name
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setTextColor(ContextCompat.getColor(context, R.color.brand_primary))
                textSize = 15f
            }
            
            val balanceValue = TextView(requireContext()).apply {
                text = formatCurrency(member.balance)
                setTextColor(if (member.balance >= 0) 
                    ContextCompat.getColor(context, android.R.color.holo_green_dark) 
                else 
                    ContextCompat.getColor(context, android.R.color.holo_red_dark))
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            
            row.addView(nameLabel)
            row.addView(balanceValue)
            container.addView(row)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Member Balances (Live)")
            .setView(container)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showExpensesBreakdownDialog() {
        val state = viewModel.uiState.value
        val bazaarCost = state.monthlyBazaarCost
        val fixedBills = state.currentFixedBills

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 20)
        }

        // Bazaar row
        addBreakdownRow(container, "Bazaar / Market", bazaarCost)
        
        // Fixed bills rows
        fixedBills.forEach { (type, amount) ->
            if (amount > 0) {
                addBreakdownRow(container, type.name.lowercase(Locale.getDefault()).replaceFirstChar { it.titlecase() }, amount)
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Expense Breakdown")
            .setView(container)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun addBreakdownRow(container: LinearLayout, label: String, amount: Double) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 15, 0, 15)
        }
        val nameText = TextView(requireContext()).apply {
            text = label
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTextColor(ContextCompat.getColor(context, R.color.brand_primary))
            textSize = 15f
        }
        val amountText = TextView(requireContext()).apply {
            text = formatCurrency(amount)
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            textSize = 15f
        }
        row.addView(nameText)
        row.addView(amountText)
        container.addView(row)
    }

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

    private fun onBazaarAmountEntered(amount: Double) {
        val polls = viewModel.uiState.value.openSpecialMealPolls
        if (polls.isEmpty()) {
            viewModel.addBazaarEntry(amount)
            return
        }

        val options = mutableListOf("General Fund (Default)")
        options.addAll(polls.map { it.title })

        AlertDialog.Builder(requireContext())
            .setTitle("Link to Special Meal?")
            .setItems(options.toTypedArray()) { _, which ->
                if (which == 0) {
                    viewModel.addBazaarEntry(amount)
                } else {
                    viewModel.addBazaarEntry(amount, polls[which - 1].pollId)
                }
            }
            .show()
    }

    private fun showManageFixedCostsDialog() {
        val fixedTypes = listOf(FixedBillType.RENT, FixedBillType.MAID, FixedBillType.WIFI, FixedBillType.GARBAGE, FixedBillType.WATER)
        
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 20)
        }

        fixedTypes.forEach { type ->
            val amount = viewModel.uiState.value.currentFixedBills[type] ?: 0.0
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 15, 0, 15)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            
            val label = TextView(requireContext()).apply {
                text = "${type.name.lowercase(Locale.getDefault()).replaceFirstChar { it.titlecase() }}: ৳$amount"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setTextColor(ContextCompat.getColor(context, R.color.brand_primary))
                textSize = 16f
            }
            
            val editBtn = TextView(requireContext()).apply {
                text = "EDIT"
                setTextColor(ContextCompat.getColor(context, R.color.brand_accent))
                setPadding(20, 10, 10, 10)
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setOnClickListener {
                    showFixedBillAmountDialog(type)
                }
            }
            
            row.addView(label)
            row.addView(editBtn)
            container.addView(row)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Manage Fixed Costs")
            .setView(container)
            .setPositiveButton("Done", null)
            .show()
    }

    private fun showAddRegularBillDialog() {
        val regularTypes = listOf(FixedBillType.ELECTRICITY, FixedBillType.GAS)
        val names = regularTypes.map { it.name.lowercase(Locale.getDefault()).replaceFirstChar { it.titlecase() } }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Add Regular Bill")
            .setItems(names) { _, which ->
                val type = regularTypes[which]
                val amountInput = createAmountEditText()
                
                AlertDialog.Builder(requireContext())
                    .setTitle("Enter ${type.name.lowercase(Locale.getDefault()).replaceFirstChar { it.titlecase() }} Amount")
                    .setView(amountInput)
                    .setPositiveButton("Add") { _, _ ->
                        val amount = amountInput.text.toString().toDoubleOrNull()
                        if (amount != null && amount > 0.0) {
                            viewModel.setFixedBill(type, amount)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showFixedBillAmountDialog(type: FixedBillType) {
        val amountInput = createAmountEditText()
        val currentAmount = viewModel.uiState.value.currentFixedBills[type] ?: 0.0
        amountInput.setText(currentAmount.toString())

        AlertDialog.Builder(requireContext())
            .setTitle("Set ${type.name.lowercase(Locale.getDefault()).replaceFirstChar { it.titlecase() }} Amount")
            .setView(amountInput)
            .setPositiveButton("Save") { _, _ ->
                val amount = amountInput.text.toString().toDoubleOrNull()
                if (amount != null && amount >= 0.0) {
                    viewModel.setFixedBill(type, amount)
                    showManageFixedCostsDialog()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLogDepositDialog() {
        val members = viewModel.uiState.value.messMembers
        if (members.isEmpty()) {
            Toast.makeText(requireContext(), "No members found", Toast.LENGTH_SHORT).show()
            return
        }

        val names = members.map { it.name }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Select Member")
            .setItems(names) { _, which ->
                showDepositAmountDialog(members[which])
            }
            .show()
    }

    private fun showDepositAmountDialog(member: MemberOption) {
        val amountInput = createAmountEditText()

        AlertDialog.Builder(requireContext())
            .setTitle("Deposit for ${member.name}")
            .setMessage("Enter the amount received")
            .setView(amountInput)
            .setPositiveButton("Log Deposit") { _, _ ->
                val amount = amountInput.text.toString().toDoubleOrNull()
                if (amount != null && amount > 0.0) {
                    viewModel.logDeposit(member.uid, amount)
                    Toast.makeText(requireContext(), "Deposit logged", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun onCloseMonthTapped() {
        val monthId = formatMonth(YearMonth.now().toString())
        AlertDialog.Builder(requireContext())
            .setTitle("Close $monthId?")
            .setMessage("This will settle all member balances, archive this month's data, and move to the next month. This cannot be undone.")
            .setPositiveButton("Close Month Now") { _, _ ->
                viewModel.closeMonth()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renderCloseMonthState(state: CloseMonthState) {
        when (state) {
            is CloseMonthState.Idle -> {
                closeMonthProgressDialog?.dismiss()
                closeMonthProgressDialog = null
            }
            is CloseMonthState.Loading -> {
                if (closeMonthProgressDialog == null) {
                    closeMonthProgressDialog = AlertDialog.Builder(requireContext())
                        .setTitle("Closing Month...")
                        .setMessage("Please wait while we calculate totals and settle balances.")
                        .setCancelable(false)
                        .show()
                }
            }
            is CloseMonthState.Success -> {
                closeMonthProgressDialog?.dismiss()
                AlertDialog.Builder(requireContext())
                    .setTitle("Month Closed!")
                    .setMessage("Successfully archived ${formatMonth(state.closedMonthId)}.")
                    .setPositiveButton("OK", null)
                    .show()
                viewModel.resetCloseMonthState()
            }
            is CloseMonthState.Error -> {
                closeMonthProgressDialog?.dismiss()
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                viewModel.resetCloseMonthState()
            }
        }
    }

    private fun formatMonth(monthId: String): String = try {
        val yearMonth = YearMonth.parse(monthId)
        val monthName = yearMonth.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.US)
        "$monthName ${yearMonth.year}"
    } catch (e: Exception) {
        monthId
    }

    private fun signOut() {
        authRepository.signOut()
        findNavController().safeNavigateToLogin()
    }

    private fun createAmountEditText(): EditText {
        return EditText(requireContext()).apply {
            hint = "0.00"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
    }

    private fun formatCurrency(amount: Double): String =
        String.format(Locale.US, "৳ %,.2f", amount)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
