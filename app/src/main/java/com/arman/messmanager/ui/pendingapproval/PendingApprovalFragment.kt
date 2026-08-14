package com.arman.messmanager.ui.pendingapproval

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.arman.messmanager.R
import com.arman.messmanager.databinding.FragmentPendingApprovalBinding
import com.arman.messmanager.ui.navigation.navigateToRoleDashboard
import kotlinx.coroutines.launch

// Shown while a user's join request (SRS section 4) is waiting on the mess's Super
// Admin to approve it. Reached either right after MessSetupFragment's "Join an Existing
// Mess" flow, or straight from Login on any later sign-in while still unapproved.
//
// There's no realtime listener wired up yet, so approval doesn't push to this screen the
// instant it happens - "Check Approval Status" re-reads Firestore on demand instead.
class PendingApprovalFragment : Fragment() {

    private var _binding: FragmentPendingApprovalBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PendingApprovalViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPendingApprovalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCheckStatus.setOnClickListener { viewModel.refresh() }
        binding.btnSignOut.setOnClickListener {
            viewModel.signOut()
            findNavController().navigate(R.id.action_pendingApprovalFragment_to_loginFragment)
        }

        // Same safe-collection pattern used on every other screen: only collect the
        // ViewModel's StateFlow while this screen is visible (STARTED).
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: PendingApprovalUiState) {
        binding.progressBar.isVisible = state.isLoading
        binding.btnCheckStatus.isEnabled = !state.isLoading

        binding.tvMessage.text = if (state.messName.isEmpty()) {
            "Your request to join is waiting for Super Admin approval."
        } else {
            "Your request to join ${state.messName} is waiting for Super Admin approval."
        }

        // The moment a refresh reports approval, immediately continue on to the correct
        // dashboard for this user's (possibly manager) role instead of leaving them
        // stranded on this screen after their request has already gone through.
        if (state.isApproved && !state.isLoading) {
            Toast.makeText(requireContext(), "You're approved! Taking you to your dashboard.", Toast.LENGTH_SHORT).show()
            findNavController().navigateToRoleDashboard(state.role)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
