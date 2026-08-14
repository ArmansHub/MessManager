package com.arman.messmanager.ui.specialmealpoll

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.arman.messmanager.R
import com.arman.messmanager.databinding.FragmentSpecialMealPollBinding
import com.arman.messmanager.databinding.ItemSpecialMealPollRowBinding
import kotlinx.coroutines.launch

// Opt-in/opt-out screen for "Special Meal Polls" (SRS section 6). Reachable by any mess
// member (wired in from the Member, Finance Manager, and Meal Manager dashboards - same
// subset SuperAdminDashboardFragment sits out of, matching ElectionFragment's existing
// wiring). Shows every currently open special-event poll for the mess as its own card,
// each with independent Yes/No buttons.
//
// Same MVVM shape as every other screen: this Fragment only calls
// SpecialMealPollViewModel.optIn()/optOut() and renders whatever state comes back.
// Poll counts are small (one mess's worth of events at a time), so plain LinearLayout
// rows built with view binding are enough - no RecyclerView/adapter needed, same
// reasoning ElectionFragment uses for its candidate rows.
class SpecialMealPollFragment : Fragment() {

    private var _binding: FragmentSpecialMealPollBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SpecialMealPollViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSpecialMealPollBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: SpecialMealPollUiState) {
        binding.progressBar.isVisible = state.isLoading
        binding.tvNoOpenPolls.isVisible = !state.isLoading && state.polls.isEmpty()

        renderPolls(binding.containerPolls, state.polls)
    }

    // Rebuilds every poll card from scratch on each state update - simple and correct
    // for the small, infrequently-changing list of open events this screen deals with,
    // same trade-off ElectionFragment makes for its candidate rows.
    private fun renderPolls(container: LinearLayout, polls: List<SpecialMealPollOption>) {
        container.removeAllViews()
        polls.forEach { poll ->
            val row = ItemSpecialMealPollRowBinding.inflate(layoutInflater, container, false)

            row.tvPollTitle.text = poll.title
            row.tvPollSubtitle.text = "${poll.eventDate} · ${poll.optedInCount} opted in"

            styleVoteButton(row.btnYes, isSelected = poll.isCurrentUserOptedIn)
            styleVoteButton(row.btnNo, isSelected = !poll.isCurrentUserOptedIn)

            row.btnYes.setOnClickListener { viewModel.optIn(poll.pollId) }
            row.btnNo.setOnClickListener { viewModel.optOut(poll.pollId) }

            container.addView(row.root)
        }
    }

    private fun styleVoteButton(button: com.google.android.material.button.MaterialButton, isSelected: Boolean) {
        val backgroundColor = if (isSelected) R.color.brand_accent else R.color.gradient_start
        val textColor = if (isSelected) R.color.white else R.color.brand_primary
        button.backgroundTintList = ContextCompat.getColorStateList(requireContext(), backgroundColor)
        button.setTextColor(ContextCompat.getColor(requireContext(), textColor))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
