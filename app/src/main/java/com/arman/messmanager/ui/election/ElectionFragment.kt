package com.arman.messmanager.ui.election

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
import com.arman.messmanager.databinding.FragmentElectionBinding
import com.arman.messmanager.databinding.ItemElectionCandidateRowBinding
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// Voting screen for the "Manager Election & Rotation System" (SRS section 3). Reachable
// by any mess member (currently wired in from the Member Dashboard); shows the mess's
// open election, if any, as two independent ballots - one for Finance Manager, one for
// Meal Manager - built from the same list of eligible candidates.
//
// Same MVVM shape as every other screen: this Fragment only calls
// ElectionViewModel.voteFinanceManager()/voteMealManager() and renders whatever state
// comes back. Candidate counts are small (one mess's worth of members), so plain
// LinearLayout rows built with view binding are enough - no RecyclerView/adapter needed.
class ElectionFragment : Fragment() {

    private var _binding: FragmentElectionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ElectionViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentElectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Same safe-collection pattern used on every other screen: only collect the
        // ViewModel's StateFlow while this screen is visible (STARTED).
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: ElectionUiState) {
        binding.progressBar.isVisible = state.isLoading
        binding.tvNoActivePoll.isVisible = !state.isLoading && !state.hasActivePoll
        binding.sectionFinanceManager.isVisible = state.hasActivePoll
        binding.sectionMealManager.isVisible = false // Only one ballot now

        binding.tvSubtitle.text = when {
            state.isLoading -> "Checking for an open election…"
            state.hasActivePoll -> state.title
            else -> "No election is currently open"
        }

        renderBallot(
            binding.containerFinanceCandidates,
            state.candidates,
            state.myVote,
            state.voteCounts
        ) { uid ->
            viewModel.vote(uid)
        }
        // Clear the second ballot container
        binding.containerMealCandidates.removeAllViews()
    }

    // Rebuilds one ballot's rows from scratch on every state update - simple and correct
    // for the small candidate lists this screen deals with, no diffing needed. Each row
    // highlights itself when it matches this voter's current pick for that role.
    private fun renderBallot(
        container: LinearLayout,
        candidates: List<CandidateOption>,
        selectedUid: String?,
        voteCounts: Map<String, Int>,
        onVote: (String) -> Unit
    ) {
        container.removeAllViews()
        candidates.forEach { candidate ->
            val row = ItemElectionCandidateRowBinding.inflate(layoutInflater, container, false)
            val isSelected = candidate.uid == selectedUid
            val votes = voteCounts[candidate.uid] ?: 0
            val voteText = resources.getQuantityString(R.plurals.vote_count, votes, votes)

            row.tvCandidateName.text = "${candidate.name} ($voteText)"
            row.tvSelectedCheck.isVisible = isSelected
            row.root.setCardBackgroundColor(
                ContextCompat.getColor(requireContext(), if (isSelected) R.color.brand_accent else R.color.white)
            )
            row.tvCandidateName.setTextColor(
                ContextCompat.getColor(requireContext(), if (isSelected) R.color.white else R.color.brand_primary)
            )
            row.root.setOnClickListener { onVote(candidate.uid) }

            container.addView(row.root)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
