package com.arman.messmanager.ui.messsetup

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
import com.arman.messmanager.data.repository.AuthRepository
import com.arman.messmanager.databinding.FragmentMessSetupBinding
import kotlinx.coroutines.launch

// "Mess Creation & Joining" (SRS section 4). Shown right after Registration - and again
// on Login for any account that still has no mess - with two options: create a brand new
// mess (the tapper becomes its Super Admin immediately) or join an existing one with an
// invite code (attaches as an unapproved General Member, pending Super Admin approval).
//
// Same MVVM shape as every other screen in the app: this Fragment only calls
// MessSetupViewModel.createMess()/joinMess() and renders whatever state comes back - it
// never talks to Firestore directly.
class MessSetupFragment : Fragment() {

    private var _binding: FragmentMessSetupBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MessSetupViewModel by viewModels()

    // Used only for the "Not you? Sign out" link - a trivial one-off call, not really
    // "Mess Setup" business logic, so it doesn't need to go through the ViewModel.
    private val authRepository = AuthRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMessSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rowCreateMess.setOnClickListener { showCreateMessDialog() }
        binding.rowJoinMess.setOnClickListener { showJoinMessDialog() }
        binding.tvSignOut.setOnClickListener { signOut() }

        // Same safe-collection pattern used on every other screen: only collect the
        // ViewModel's StateFlow while this screen is visible (STARTED).
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: MessSetupUiState) {
        binding.progressBar.isVisible = state is MessSetupUiState.Loading
        binding.rowCreateMess.isEnabled = state !is MessSetupUiState.Loading
        binding.rowJoinMess.isEnabled = state !is MessSetupUiState.Loading

        when (state) {
            is MessSetupUiState.MessCreated -> {
                viewModel.resetState()
                showInviteCodeThenContinue(state.inviteCode)
            }
            is MessSetupUiState.JoinRequestSubmitted -> {
                viewModel.resetState()
                findNavController().navigate(R.id.action_messSetupFragment_to_pendingApprovalFragment)
            }
            is MessSetupUiState.Error -> {
                viewModel.resetState()
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
            }
            else -> Unit
        }
    }

    // Simple AlertDialog with a single name field, same lightweight pattern the Finance
    // Manager dashboard uses for "Add Daily Bazaar" - no custom dialog layout needed.
    private fun showCreateMessDialog() {
        val nameInput = EditText(requireContext()).apply {
            hint = "Mess Name (e.g. Sunrise Mess)"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Create a New Mess")
            .setMessage("You'll become this mess's Super Admin.")
            .setView(nameInput)
            .setPositiveButton("Create") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), "Enter a mess name", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.createMess(name)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showJoinMessDialog() {
        val codeInput = EditText(requireContext()).apply {
            hint = "Invite Code"
            // Invite codes are generated in uppercase (MessRepository), so hint the
            // keyboard to match - purely cosmetic, MessSetupViewModel normalizes the
            // typed value again before looking it up either way.
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Join an Existing Mess")
            .setMessage("Ask your mess's Super Admin for the invite code.")
            .setView(codeInput)
            .setPositiveButton("Join") { _, _ ->
                val code = codeInput.text.toString().trim()
                if (code.isEmpty()) {
                    Toast.makeText(requireContext(), "Enter an invite code", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.joinMess(code)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Shown once right after a successful "Create a New Mess" - this is the only time
    // the invite code is ever displayed unprompted, so the new Super Admin has a chance
    // to copy/share it before moving on to their dashboard.
    private fun showInviteCodeThenContinue(inviteCode: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Mess Created!")
            .setMessage("Share this invite code with your mess members:\n\n$inviteCode")
            .setCancelable(false)
            .setPositiveButton("Continue") { _, _ ->
                findNavController().navigate(R.id.action_messSetupFragment_to_superAdminDashboardFragment)
            }
            .show()
    }

    private fun signOut() {
        authRepository.signOut()
        findNavController().navigate(R.id.action_messSetupFragment_to_loginFragment)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
