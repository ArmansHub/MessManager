package com.arman.messmanager.ui.dashboard.member

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
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
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

// General Member Dashboard, built from the SRS's "Role-Specific Dashboards" section:
// a Top Card (balance + live meal rate), Quick Actions (toggle today's meals), a Manager
// Election banner (SRS section 3, shown only while a poll is open), and a read-only
// Notices section.
//
// This version reads real data from Firestore through MemberDashboardViewModel and
// lets the member turn today's standard meals on/off.
class MemberDashboardFragment : Fragment() {

    private var _binding: FragmentMemberDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MemberDashboardViewModel by viewModels()

    // Used only for the "Sign Out" button - a trivial one-off call, not really dashboard
    // business logic, so it doesn't need to go through the ViewModel (same reasoning as
    // MessSetupFragment's sign-out link).
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

        binding.cardElectionBanner.setOnClickListener {
            findNavController().navigate(R.id.action_memberDashboardFragment_to_electionFragment)
        }
        binding.cardSpecialMealPollBanner.setOnClickListener {
            findNavController().navigate(R.id.action_memberDashboardFragment_to_specialMealPollFragment)
        }
        binding.tvSignOut.setOnClickListener { signOut() }

        // Same safe-collection pattern used on the Login/Register screens: only
        // collect the ViewModel's state while this screen is visible.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: MemberDashboardUiState) {
        binding.progressBar.isVisible = state.isLoading

        binding.tvBalance.text = formatCurrency(state.balance)
        binding.tvMealRate.text = "Live Meal Rate: ${formatCurrency(state.mealRate)} / meal"

        bindMealSwitch(binding.switchBreakfast, state.isBreakfastOn, MealType.BREAKFAST)
        bindMealSwitch(binding.switchLunch, state.isLunchOn, MealType.LUNCH)
        bindMealSwitch(binding.switchDinner, state.isDinnerOn, MealType.DINNER)

        binding.cardElectionBanner.isVisible = state.hasActiveElection

        binding.cardSpecialMealPollBanner.isVisible = state.openSpecialMealPollCount > 0
        binding.tvSpecialMealPollBannerTitle.text = if (state.openSpecialMealPollCount == 1) {
            "1 special meal poll is open"
        } else {
            "${state.openSpecialMealPollCount} special meal polls are open"
        }

        binding.tvNoNotices.isVisible = !state.isLoading && state.notices.isEmpty()
        renderNotices(binding.containerNotices, state.notices)
    }

    // Rebuilds the Notice Board from scratch on each state update - simple and correct
    // for the small, infrequently-changing list this screen deals with, same trade-off
    // ElectionFragment/SpecialMealPollFragment make for their own card lists.
    private fun renderNotices(container: LinearLayout, notices: List<NoticeOption>) {
        container.removeAllViews()
        notices.forEach { notice ->
            val row = ItemNoticeRowBinding.inflate(layoutInflater, container, false)
            row.tvNoticeMessage.text = notice.message
            row.tvNoticeMeta.text = "Posted by ${notice.authorName} · ${formatTimestamp(notice.timestamp)}"
            container.addView(row.root)
        }
    }

    // Epoch millis -> "Aug 14, 2026".
    private fun formatTimestamp(epochMs: Long): String {
        val date = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()
        val month = date.month.getDisplayName(TextStyle.SHORT, Locale.US)
        return "$month ${date.dayOfMonth}, ${date.year}"
    }

    // Updates a switch to match the ViewModel's state and (re)attaches its listener.
    // We clear the listener before changing "isChecked" so that loading data from
    // Firestore doesn't get mistaken for the user tapping the switch, which would
    // otherwise cause a pointless extra write straight back to Firestore.
    private fun bindMealSwitch(switchView: SwitchMaterial, isOn: Boolean, mealType: MealType) {
        switchView.setOnCheckedChangeListener(null)
        switchView.isChecked = isOn
        switchView.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleMeal(mealType, isChecked)
        }
    }

    private fun formatCurrency(amount: Double): String =
        String.format(Locale.US, "৳ %,.2f", amount)

    private fun signOut() {
        authRepository.signOut()
        findNavController().navigate(R.id.action_memberDashboardFragment_to_loginFragment)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
