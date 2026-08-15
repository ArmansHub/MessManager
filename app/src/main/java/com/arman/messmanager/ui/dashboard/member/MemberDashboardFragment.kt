package com.arman.messmanager.ui.dashboard.member

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
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
import com.arman.messmanager.databinding.FragmentMemberDashboardBinding
import com.arman.messmanager.databinding.ItemNoticeRowBinding
import com.arman.messmanager.ui.navigation.safeNavigateToLogin
import com.bumptech.glide.Glide
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import java.util.Locale

class MemberDashboardFragment : Fragment() {

    private var _binding: FragmentMemberDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MemberDashboardViewModel by viewModels()

    private val authRepository = AuthRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMemberDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rowProfile.setOnClickListener {
            findNavController().navigate(R.id.action_memberDashboardFragment_to_profileFragment)
        }
        
        binding.tvSignOut.setOnClickListener { signOut() }
        
        binding.tvViewAllNotices.setOnClickListener {
            showAllNoticesDialog(viewModel.uiState.value.notices)
        }

        binding.rowDepositRequest.setOnClickListener { showDepositRequestDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.toggleError.collect { error ->
                    if (error != null) {
                        when (error) {
                            is ToggleError.TimeLocked -> {
                                Toast.makeText(requireContext(), 
                                    "Cannot turn off ${error.mealName}. Locked since ${error.lockTime}", 
                                    Toast.LENGTH_LONG).show()
                                viewModel.clearToggleError()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showDepositRequestDialog() {
        val amountInput = android.widget.EditText(requireContext()).apply {
            hint = "0.00"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Send Deposit Request")
            .setMessage("Enter the amount you've paid to the mess")
            .setView(amountInput)
            .setPositiveButton("Send Request") { _, _ ->
                val amount = amountInput.text.toString().toDoubleOrNull()
                if (amount != null && amount > 0.0) {
                    viewModel.submitDepositRequest(amount)
                    Toast.makeText(requireContext(), "Request sent to Finance Manager", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Enter a valid amount", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAllNoticesDialog(notices: List<NoticeOption>) {
        if (notices.isEmpty()) return
        val message = StringBuilder()
        notices.forEach { notice ->
            message.append("📌 ${notice.title}\n")
            message.append("${notice.content}\n")
            message.append("By ${notice.authorName} · ${formatTimestamp(notice.timestamp)}\n")
            message.append("----------------------------\n\n")
        }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("All Notices")
            .setMessage(message.toString())
            .setPositiveButton("Close", null)
            .show()
    }

    private fun render(state: MemberDashboardUiState) {
        binding.progressBar.isVisible = state.isLoading

        binding.tvProfileName.text = state.profileName
        Glide.with(this)
            .load(state.profilePictureUrl)
            .placeholder(R.drawable.ic_person)
            .circleCrop()
            .into(binding.ivSmallProfilePicture)

        binding.tvBalance.text = formatCurrency(state.balance)
        binding.tvPersonalMeals.text = "Meals Today: ${state.personalMealsToday} / 3"
        binding.tvMealRate.text = "Live Meal Rate: ${formatCurrency(state.mealRate)} / meal"

        binding.tvLabelBreakfast.text = state.breakfastLabel
        binding.tvLabelLunch.text = state.lunchLabel
        binding.tvLabelDinner.text = state.dinnerLabel

        binding.tvMenuBreakfast.text = "${state.breakfastLabel}: ${state.breakfastMenu.ifBlank { "Not set" }}"
        binding.tvMenuLunch.text = "${state.lunchLabel}: ${state.lunchMenu.ifBlank { "Not set" }}"
        binding.tvMenuDinner.text = "${state.dinnerLabel}: ${state.dinnerMenu.ifBlank { "Not set" }}"

        bindMealSwitch(binding.switchBreakfast, state.isBreakfastOn, MealType.BREAKFAST)
        bindMealSwitch(binding.switchLunch, state.isLunchOn, MealType.LUNCH)
        bindMealSwitch(binding.switchDinner, state.isDinnerOn, MealType.DINNER)

        // Force update the text in case toggle states changed
        binding.tvPersonalMeals.text = "Meals Today: ${state.personalMealsToday} / 3"

        binding.cardElectionBanner.isVisible = state.hasActiveElection
        binding.cardElectionBanner.setOnClickListener {
            findNavController().navigate(R.id.action_memberDashboardFragment_to_electionFragment)
        }

        binding.cardSpecialMealPollBanner.isVisible = state.openSpecialMealPollCount > 0
        binding.tvSpecialMealPollBannerTitle.text = if (state.openSpecialMealPollCount == 1) {
            "1 special meal poll is open"
        } else {
            "${state.openSpecialMealPollCount} special meal polls are open"
        }
        binding.cardSpecialMealPollBanner.setOnClickListener {
            findNavController().navigate(R.id.action_memberDashboardFragment_to_specialMealPollFragment)
        }

        renderNotices(binding.containerNotices, state.notices)
    }

    private fun renderNotices(container: LinearLayout, notices: List<NoticeOption>) {
        container.removeAllViews()
        notices.forEach { notice ->
            val row = ItemNoticeRowBinding.inflate(layoutInflater, container, false)
            row.tvNoticeMessage.text = notice.content
            row.tvNoticeMeta.text = "Posted by ${notice.authorName} · ${formatTimestamp(notice.timestamp)}"
            container.addView(row.root)
        }
    }

    private fun formatTimestamp(epochMs: Long): String {
        val date = java.time.Instant.ofEpochMilli(epochMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val month = date.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.US)
        return "$month ${date.dayOfMonth}, ${date.year}"
    }

    private fun bindMealSwitch(switch: SwitchMaterial, isOn: Boolean, mealType: MealType) {
        switch.setOnCheckedChangeListener(null)
        switch.isChecked = isOn
        switch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleMeal(mealType, isChecked)
        }
    }

    private fun formatCurrency(amount: Double): String =
        String.format(Locale.US, "৳ %,.2f", amount)

    private fun signOut() {
        authRepository.signOut()
        findNavController().safeNavigateToLogin()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
