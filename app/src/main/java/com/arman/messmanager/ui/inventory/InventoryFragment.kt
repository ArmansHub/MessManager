package com.arman.messmanager.ui.inventory

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.arman.messmanager.databinding.FragmentInventoryBinding
import java.util.Calendar
import java.util.Date

class InventoryFragment : Fragment() {

    private var _binding: FragmentInventoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InventoryViewModel by viewModels()
    private lateinit var inventoryAdapter: InventoryAdapter
    private lateinit var bazaarRosterAdapter: BazaarRosterAdapter

    private var selectedDate: Date? = null

    // Mock member data. In a real app, this would come from a repository.
    private val members = mapOf("uid1" to "Arman", "uid2" to "John Doe", "uid3" to "Jane Smith")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInventoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        observeViewModel()
        setupAdminControls()
    }

    private fun setupRecyclerViews() {
        inventoryAdapter = InventoryAdapter { item, isChecked ->
            viewModel.updateInventoryStockStatus(item, isChecked)
        }
        binding.inventoryRecyclerView.adapter = inventoryAdapter

        bazaarRosterAdapter = BazaarRosterAdapter()
        binding.bazaarRosterRecyclerView.adapter = bazaarRosterAdapter
    }

    private fun observeViewModel() {
        viewModel.inventoryItems.observe(viewLifecycleOwner) { items ->
            inventoryAdapter.submitList(items)
        }
        viewModel.bazaarRoster.observe(viewLifecycleOwner) { roster ->
            bazaarRosterAdapter.submitList(roster)
        }
    }

    private fun setupAdminControls() {
        // In a real app, check user role (Super Admin/Meal Manager) from a user repository/session manager
        val isAdmin = true // Placeholder for demonstration
        if (isAdmin) {
            binding.adminControls.visibility = View.VISIBLE
        }

        binding.selectDateButton.setOnClickListener {
            showDatePicker()
        }

        val memberNames = members.values.toList()
        val memberAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, memberNames)
        memberAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.memberSpinner.adapter = memberAdapter

        binding.assignDutyButton.setOnClickListener {
            assignDuty()
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(year, month, dayOfMonth)
                selectedDate = selectedCalendar.time
                binding.selectDateButton.text = android.text.format.DateFormat.getDateFormat(context).format(selectedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun assignDuty() {
        val selectedMemberName = binding.memberSpinner.selectedItem as String
        val selectedMemberUid = members.entries.find { it.value == selectedMemberName }?.key

        if (selectedDate != null && selectedMemberUid != null) {
            viewModel.assignBazaarDuty(selectedDate!!, selectedMemberUid, selectedMemberName)
            Toast.makeText(context, "Duty assigned to $selectedMemberName", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Please select a date and member", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
