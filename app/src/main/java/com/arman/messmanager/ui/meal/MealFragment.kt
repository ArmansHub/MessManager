package com.arman.messmanager.ui.meal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.arman.messmanager.R
import com.arman.messmanager.data.model.Meal
import com.arman.messmanager.databinding.FragmentMealBinding
import com.arman.messmanager.databinding.ViewMealControlBinding
import java.text.SimpleDateFormat
import java.util.*

class MealFragment : Fragment() {

    private var _binding: FragmentMealBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MealViewModel by viewModels()

    private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())

    // Mock data for user roles and members. Replace with real data source.
    private val currentUserRole = "Admin" // Can be "Admin", "Meal Manager", or "General Member"
    private val currentUserId = "user_self_uid"
    private val members = mapOf("user_self_uid" to "My Meals", "uid2" to "John Doe", "uid3" to "Jane Smith")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMealBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAdminControls()
        setupDateControls()
        setupMealControls()
        observeViewModel()

        binding.saveMealsButton.setOnClickListener {
            viewModel.saveChanges()
            Toast.makeText(context, "Changes saved!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupAdminControls() {
        if (currentUserRole == "Admin" || currentUserRole == "Meal Manager") {
            binding.adminMealControls.visibility = View.VISIBLE
            val memberNames = members.values.toList()
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, memberNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.memberSpinner.adapter = adapter

            binding.memberSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val selectedMemberUid = members.keys.toList()[position]
                    viewModel.setSelectedUser(selectedMemberUid)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        } else {
            binding.adminMealControls.visibility = View.GONE
            viewModel.setSelectedUser(currentUserId)
        }
    }

    private fun setupDateControls() {
        binding.prevDateButton.setOnClickListener { viewModel.changeDate(-1) }
        binding.nextDateButton.setOnClickListener { viewModel.changeDate(1) }
    }

    private fun setupMealControls() {
        setupControl(binding.breakfastControl, "Breakfast", MealType.BREAKFAST)
        setupControl(binding.lunchControl, "Lunch", MealType.LUNCH)
        setupControl(binding.dinnerControl, "Dinner", MealType.DINNER)
    }

    private fun setupControl(controlBinding: ViewMealControlBinding, name: String, type: MealType) {
        controlBinding.mealNameTextView.text = name
        controlBinding.increaseButton.setOnClickListener { viewModel.updateMealCount(type, 0.5) }
        controlBinding.decreaseButton.setOnClickListener { viewModel.updateMealCount(type, -0.5) }
    }

    private fun observeViewModel() {
        viewModel.selectedDate.observe(viewLifecycleOwner) { date ->
            binding.dateTextView.text = dateFormat.format(date)
        }

        viewModel.meal.observe(viewLifecycleOwner) { meal ->
            updateMealUi(meal)
        }
    }

    private fun updateMealUi(meal: Meal?) {
        // Here you would implement logic to check for meal lock times
        val isLocked = false // Placeholder

        binding.breakfastControl.countTextView.text = meal?.breakfastCount?.toString() ?: "0.0"
        binding.lunchControl.countTextView.text = meal?.lunchCount?.toString() ?: "0.0"
        binding.dinnerControl.countTextView.text = meal?.dinnerCount?.toString() ?: "0.0"

        binding.breakfastControl.increaseButton.isEnabled = !isLocked
        binding.breakfastControl.decreaseButton.isEnabled = !isLocked
        binding.lunchControl.increaseButton.isEnabled = !isLocked
        binding.lunchControl.decreaseButton.isEnabled = !isLocked
        binding.dinnerControl.increaseButton.isEnabled = !isLocked
        binding.dinnerControl.decreaseButton.isEnabled = !isLocked
        binding.saveMealsButton.isEnabled = !isLocked
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
