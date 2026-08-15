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
import java.util.Locale

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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: ElectionUiState) {
        binding.progressBar.isVisible = state.isLoading
        binding.tvNoActivePoll.isVisible = !state.isLoading && !state.hasActivePoll
        binding.sectionFinanceManager.isVisible = state.hasActivePoll && state.rolesToElect.contains("finance")
        binding.sectionMealManager.isVisible = state.hasActivePoll && state.rolesToElect.contains("meal")

        binding.tvSubtitle.text = when {
            state.isLoading -> "Checking for an open election…"
            state.hasActivePoll && state.isExpired -> "${state.title} (Ended)"
            state.hasActivePoll -> "${state.title} (Ends: ${formatEndTime(state.endTime)})"
            else -> "No election is currently open"
        }

        if (state.hasActivePoll) {
            renderBallot(
                binding.containerFinanceCandidates,
                state.candidates,
                state.myFinanceVote,
                state.financeVoteCounts,
                !state.isExpired
            ) { uid ->
                viewModel.voteFinanceManager(uid)
            }

            renderBallot(
                binding.containerMealCandidates,
                state.candidates,
                state.myMealVote,
                state.mealVoteCounts,
                !state.isExpired
            ) { uid ->
                viewModel.voteMealManager(uid)
            }
        }
    }

    private fun renderBallot(
        container: LinearLayout,
        candidates: List<CandidateOption>,
        selectedUid: String?,
        voteCounts: Map<String, Int>,
        canVote: Boolean,
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
            if (canVote) {
                row.root.setOnClickListener { onVote(candidate.uid) }
            } else {
                row.root.alpha = 0.8f
                row.root.setOnClickListener(null)
            }

            container.addView(row.root)
        }
    }

    private fun formatEndTime(endTime: Long): String {
        val sdf = java.text.SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        return sdf.format(java.util.Date(endTime))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
