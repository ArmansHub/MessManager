package com.arman.messmanager.ui.dashboard.mealmanager

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import com.arman.messmanager.data.model.MealType
import com.arman.messmanager.data.repository.AuthRepository
import com.arman.messmanager.databinding.FragmentMealmanagerDashboardBinding
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

// Meal Manager Dashboard, built from the SRS's "Role-Specific Dashboards" section:
// a Top Card (Today's Meal Summary), a Manager Election banner (SRS section 3, shown
// only while a poll is open), a Special Meal Polls banner (SRS section 6, shown only
// while at least one is open), and Quick Actions to lock meals or create a special
// meal poll.
//
// This version reads real totals from Firestore through MealManagerDashboardViewModel.
// "Override Meals" and "Set Today's Menu" are not wired up yet.
class MealManagerDashboardFragment : Fragment() {

    private var _binding: FragmentMealmanagerDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MealManagerDashboardViewModel by viewModels()

    // Used only for the "Sign Out" button - a trivial one-off call, not really dashboard
    // business logic, so it doesn't need to go through the ViewModel (same reasoning as
    // MessSetupFragment's sign-out link).
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
        binding.rowPostNotice.setOnClickListener { showPostNoticeDialog() }
        binding.cardElectionBanner.setOnClickListener {
            findNavController().navigate(R.id.action_mealManagerDashboardFragment_to_electionFragment)
        }
        binding.cardSpecialMealPollBanner.setOnClickListener {
            findNavController().navigate(R.id.action_mealManagerDashboardFragment_to_specialMealPollFragment)
        }
        binding.tvSignOut.setOnClickListener { signOut() }

        // Same safe-collection pattern used on every other screen: only collect the
        // ViewModel's state while this screen is visible.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: MealManagerDashboardUiState) {
        binding.progressBar.isVisible = state.isLoading

        binding.tvBreakfastCount.text = formatMealCount(state.breakfastCount)
        binding.tvLunchCount.text = formatMealCount(state.lunchCount)
        binding.tvDinnerCount.text = formatMealCount(state.dinnerCount)

        binding.tvBreakfastLock.text = formatLockStatus(state.breakfastLockTime)
        binding.tvLunchLock.text = formatLockStatus(state.lunchLockTime)
        binding.tvDinnerLock.text = formatLockStatus(state.dinnerLockTime)

        binding.cardElectionBanner.isVisible = state.hasActiveElection

        binding.cardSpecialMealPollBanner.isVisible = state.openSpecialMealPollCount > 0
        binding.tvSpecialMealPollBannerTitle.text = if (state.openSpecialMealPollCount == 1) {
            "1 special meal poll is open"
        } else {
            "${state.openSpecialMealPollCount} special meal polls are open"
        }
    }

    // First pick which meal to lock, then pick a cut-off time for it. Two simple
    // dialogs in sequence, same style as the Finance Manager's "Add Fixed Bill" flow.
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

    // Standard Android TimePickerDialog - no custom UI needed to pick a cut-off time.
    private fun showTimePickerDialog(mealType: MealType) {
        TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                val time = String.format(Locale.US, "%02d:%02d", hourOfDay, minute)
                viewModel.setLockTime(mealType, time)
            },
            20, // default hour shown when the picker opens (8 PM)
            0,
            true // 24-hour format, matches how we store and display the time
        ).show()
    }

    // "Create Special Meal Poll" (SRS section 6, Meal Manager only): first the event
    // title, then a date to pin it to - two simple dialogs in sequence, same style as
    // "Lock Which Meal? -> pick a time" above.
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

    // Standard Android DatePickerDialog - no custom UI needed to pick the event date.
    private fun showEventDatePickerDialog(title: String) {
        val today = LocalDate.now()
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val eventDate = LocalDate.of(year, month + 1, dayOfMonth).toString()
                viewModel.createSpecialMealPoll(title, eventDate)
            },
            today.year,
            today.monthValue - 1, // DatePickerDialog months are 0-indexed
            today.dayOfMonth
        ).show()
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
                    viewModel.postNotice(message, message)
                    Toast.makeText(requireContext(), "Notice posted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun signOut() {
        authRepository.signOut()
        findNavController().navigate(R.id.action_mealManagerDashboardFragment_to_loginFragment)
    }

    private fun formatLockStatus(lockTime: String?): String =
        if (lockTime == null) "Not locked" else "Locked at $lockTime"

    // Shows whole numbers plainly ("10") and fractional counts with one decimal
    // ("10.5"), since guest/half meals can make the total non-whole.
    private fun formatMealCount(count: Double): String =
        if (count == count.toLong().toDouble()) {
            count.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", count)
        }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
