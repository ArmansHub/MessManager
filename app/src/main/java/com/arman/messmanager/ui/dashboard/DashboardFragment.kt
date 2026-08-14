package com.arman.messmanager.ui.dashboard

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.arman.messmanager.R
import com.arman.messmanager.databinding.FragmentDashboardBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels()
    private lateinit var pendingDepositAdapter: PendingDepositAdapter
    private var defaultCardColor: ColorStateList? = null

    // Placeholder for user role - replace with real authentication data
    private val currentUserRole = "Finance Manager"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        defaultCardColor = binding.personalCard.cardBackgroundColor
        setupRecyclerView()
        observeViewModel()
        setupAdminControls()
    }

    private fun setupRecyclerView() {
        pendingDepositAdapter = PendingDepositAdapter { deposit ->
            viewModel.approveDeposit(deposit.depositId)
        }
        binding.pendingDepositsRecyclerView.adapter = pendingDepositAdapter
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            binding.balanceTextView.text = String.format("৳ %.2f", state.myBalance)
            binding.mealRateTextView.text = String.format("Live Meal Rate: ৳ %.2f", state.mealRate)
            binding.totalBazaarTextView.text = String.format("Total Bazaar: ৳ %.2f", state.totalBazaar)
            binding.totalFixedBillsTextView.text = String.format("Total Fixed Bills: ৳ %.2f", state.totalFixedBills)
            binding.totalMealsTextView.text = String.format("Total Meals: %.1f", state.totalMeals)

            if (state.myBalance < 500) { // Red Zone threshold
                binding.personalCard.setCardBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_light))
            } else {
                defaultCardColor?.let { binding.personalCard.setCardBackgroundColor(it) }
            }

            pendingDepositAdapter.submitList(state.pendingDeposits)
        }
    }

    private fun setupAdminControls() {
        if (currentUserRole == "Finance Manager" || currentUserRole == "Admin") {
            binding.financeManagerControls.visibility = View.VISIBLE
        }

        binding.addBazaarButton.setOnClickListener { showAddBazaarDialog() }
        binding.addFixedBillButton.setOnClickListener { showAddFixedBillDialog() }
        binding.closeMonthButton.setOnClickListener { showCloseMonthConfirmation() }
    }

    private fun showAddBazaarDialog() {
        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val padding = (19 * resources.displayMetrics.density).toInt()
        input.setPadding(padding, input.paddingTop, padding, input.paddingBottom)


        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Daily Bazaar")
            .setMessage("Enter the total cost for today's bazaar.")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val cost = input.text.toString().toDoubleOrNull()
                if (cost != null && cost > 0) {
                    viewModel.addBazaar(cost)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddFixedBillDialog() {
        val billTypes = com.arman.messmanager.data.model.FixedBillType.entries.toTypedArray()
        val billTypeNames = billTypes.map { it.name.replace('_', ' ').lowercase(Locale.getDefault()).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Bill Type")
            .setItems(billTypeNames) { _, index ->
                showFixedBillAmountDialog(billTypes[index])
            }
            .show()
    }

    private fun showFixedBillAmountDialog(type: com.arman.messmanager.data.model.FixedBillType) {
        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val padding = (19 * resources.displayMetrics.density).toInt()
        input.setPadding(padding, input.paddingTop, padding, input.paddingBottom)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add ${type.name} Bill")
            .setMessage("Enter the amount for this bill.")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val amount = input.text.toString().toDoubleOrNull()
                if (amount != null && amount > 0) {
                    viewModel.addFixedBill(amount, type)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCloseMonthConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Close Month?")
            .setMessage("This will archive all data for the current month and carry over balances. This action cannot be undone.")
            .setPositiveButton("Confirm") { _, _ ->
                viewModel.closeMonth()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
