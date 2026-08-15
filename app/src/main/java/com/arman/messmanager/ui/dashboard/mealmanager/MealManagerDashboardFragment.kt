package com.arman.messmanager.ui.dashboard.mealmanager

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
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
import com.arman.messmanager.data.model.MealType
import com.arman.messmanager.data.repository.AuthRepository
import com.arman.messmanager.databinding.FragmentMealmanagerDashboardBinding
import com.arman.messmanager.ui.navigation.safeNavigateToLogin
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

class MealManagerDashboardFragment : Fragment() {

    private var _binding: FragmentMealmanagerDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MealManagerDashboardViewModel by viewModels()

    private val authRepository = AuthRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMealmanagerDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rowLockMeals.setOnClickListener { showLockMealsDialog() }
        binding.rowCreateSpecialMealPoll.setOnClickListener { showCreateSpecialMealPollDialog() }
        binding.rowSpecialMealManagement.setOnClickListener { showSpecialMealManagementDialog() }
        binding.cardMealSummary.setOnClickListener { showMealAttendanceBreakdownDialog() }
        
        binding.rowGoToMemberDashboard.setOnClickListener {
            findNavController().navigate(R.id.action_mealManagerDashboardFragment_to_memberDashboardFragment)
        }
        binding.rowProfile.setOnClickListener {
            findNavController().navigate(R.id.action_mealManagerDashboardFragment_to_profileFragment)
        }
        binding.tvSignOut.setOnClickListener { signOut() }

        viewModel.refresh()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: MealManagerDashboardUiState) {
        binding.progressBar.isVisible = state.isLoading

        binding.tvProfileName.text = state.profileName
        com.bumptech.glide.Glide.with(this)
            .load(state.profilePictureUrl)
            .placeholder(R.drawable.ic_person)
            .circleCrop()
            .into(binding.ivSmallProfilePicture)

        binding.tvLabelSummaryBreakfast.text = state.breakfastLabel
        binding.tvLabelSummaryLunch.text = state.lunchLabel
        binding.tvLabelSummaryDinner.text = state.dinnerLabel

        binding.tvBreakfastCount.text = formatMealCount(state.breakfastCount)
        binding.tvLunchCount.text = formatMealCount(state.lunchCount)
        binding.tvDinnerCount.text = formatMealCount(state.dinnerCount)

        binding.tvBreakfastLock.text = formatLockStatus(state.breakfastLockTime)
        binding.tvLunchLock.text = formatLockStatus(state.lunchLockTime)
        binding.tvDinnerLock.text = formatLockStatus(state.dinnerLockTime)
    }

    private fun showMealAttendanceBreakdownDialog() {
        val state = viewModel.uiState.value
        val mealTypes = arrayOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)
        val mealNames = arrayOf(state.breakfastLabel, state.lunchLabel, state.dinnerLabel)

        AlertDialog.Builder(requireContext())
            .setTitle("Meal Attendance")
            .setItems(mealNames) { _, index ->
                showMemberAttendanceList(mealTypes[index], mealNames[index])
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showMemberAttendanceList(type: MealType, name: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val members = viewModel.getMembersWithToggleOn(type)
            val message = if (members.isEmpty()) {
                "No one is eating $name today."
            } else {
                "Members eating $name today (${members.size}):\n\n" + members.joinToString("\n") { "• $it" }
            }

            AlertDialog.Builder(requireContext())
                .setTitle("$name Attendance")
                .setMessage(message)
                .setPositiveButton("Set Menu") { _, _ -> showSetMenuDialog(type, name) }
                .setNeutralButton("Back", { _, _ -> showMealAttendanceBreakdownDialog() })
                .setNegativeButton("Close", null)
                .show()
        }
    }

    private fun showSetMenuDialog(type: MealType, name: String) {
        val menuInput = EditText(requireContext()).apply {
            hint = "e.g. Rice, Dal, Chicken"
            val currentMenu = when(type) {
                MealType.BREAKFAST -> viewModel.uiState.value.breakfastMenu
                MealType.LUNCH -> viewModel.uiState.value.lunchMenu
                MealType.DINNER -> viewModel.uiState.value.dinnerMenu
                else -> ""
            }
            setText(currentMenu)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Set $name Menu")
            .setView(menuInput)
            .setPositiveButton("Save") { _, _ ->
                viewModel.setMenu(type, menuInput.text.toString().trim())
                Toast.makeText(context, "Menu saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSpecialMealManagementDialog() {
        val state = viewModel.uiState.value
        val polls = state.openPolls
        if (polls.isEmpty()) {
            Toast.makeText(requireContext(), "No open special meal polls", Toast.LENGTH_SHORT).show()
            return
        }

        val items = polls.map { "${it.title} (${it.count} In)" }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Manage Special Meals")
            .setItems(items) { _, index ->
                showPollOptionsDialog(polls[index])
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showPollOptionsDialog(poll: PollOption) {
        val options = arrayOf("View In/Out List", "Manually Add Member")
        AlertDialog.Builder(requireContext())
            .setTitle(poll.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showPollParticipants(poll)
                    1 -> showMemberSelectionForPoll(poll)
                }
            }
        .show()
    }

    private fun showPollParticipants(poll: PollOption) {
        viewLifecycleOwner.lifecycleScope.launch {
            val optedInUids = viewModel.getSpecialMealParticipants(poll.id)
            val members = viewModel.uiState.value.messMembers
            
            val message = StringBuilder()
            message.append("Members who are IN:\n")
            optedInUids.forEach { uid ->
                val name = members.find { it.uid == uid }?.name ?: "Unknown"
                message.append("- $name\n")
            }
            if (optedInUids.isEmpty()) message.append("- No one yet\n")

            AlertDialog.Builder(requireContext())
                .setTitle(poll.title)
                .setMessage(message.toString())
                .setPositiveButton("Close", null)
                .show()
        }
    }

    private fun showMemberSelectionForPoll(poll: PollOption) {
        val members = viewModel.uiState.value.messMembers
        val names = members.map { it.name }.toTypedArray()
        
        AlertDialog.Builder(requireContext())
            .setTitle("Manually Add Member")
            .setItems(names) { _, index ->
                viewModel.manuallyAddUserToSpecialMeal(poll.id, members[index].uid)
                Toast.makeText(requireContext(), "Added ${members[index].name}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showLockMealsDialog() {
        val mealTypes = arrayOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)
        val mealNames = arrayOf("Breakfast", "Lunch", "Dinner")

        AlertDialog.Builder(requireContext())
            .setTitle("Lock Which Meal?")
            .setItems(mealNames) { _, index ->
                showTimePickerDialog(mealTypes[index])
            }
            .show()
    }

    private fun showTimePickerDialog(mealType: MealType) {
        TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                val time = String.format(Locale.US, "%02d:%02d", hourOfDay, minute)
                viewModel.setLockTime(mealType, time)
            },
            20,
            0,
            true
        ).show()
    }

    private fun showCreateSpecialMealPollDialog() {
        val titleInput = EditText(requireContext()).apply {
            hint = "e.g. Friday Biryani"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Create Special Meal Poll")
            .setMessage("What's the event?")
            .setView(titleInput)
            .setPositiveButton("Next") { _, _ ->
                val title = titleInput.text.toString().trim()
                if (title.isEmpty()) {
                    Toast.makeText(requireContext(), "Enter a title for the event", Toast.LENGTH_SHORT).show()
                } else {
                    showEventDatePickerDialog(title)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEventDatePickerDialog(title: String) {
        val today = LocalDate.now()
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val eventDate = LocalDate.of(year, month + 1, dayOfMonth).toString()
                viewModel.createSpecialMealPoll(title, eventDate)
            },
            today.year,
            today.monthValue - 1,
            today.dayOfMonth
        ).show()
    }

    private fun signOut() {
        authRepository.signOut()
        findNavController().safeNavigateToLogin()
    }

    private fun formatLockStatus(lockTime: String?): String =
        if (lockTime == null) "Not locked" else "Locked at $lockTime"

    private fun formatMealCount(count: Double): String =
        if (count == count.toLong().toDouble()) {
            count.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", count)
        }

    private fun formatCurrency(amount: Double): String =
        String.format(Locale.US, "৳ %,.2f", amount)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
