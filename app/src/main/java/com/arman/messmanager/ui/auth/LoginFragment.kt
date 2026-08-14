package com.arman.messmanager.ui.auth

import android.os.Bundle
import android.util.Patterns
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
import com.arman.messmanager.databinding.FragmentLoginBinding
import com.arman.messmanager.ui.navigation.navigateToRoleDashboard
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    // View Binding gives type-safe access to the views declared in fragment_login.xml.
    // It is only valid between onCreateView and onDestroyView, so we keep it nullable
    // and clear it in onDestroyView to avoid holding a reference to a destroyed view.
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    // "by viewModels()" asks the Fragment framework for an AuthViewModel instance.
    // It survives screen rotation and is destroyed automatically when the fragment is.
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.checkCurrentUserSession()

        binding.btnLogin.setOnClickListener { attemptLogin() }
        binding.tvRegister.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }

        // The ViewModel exposes login progress as a StateFlow. We collect it here so the
        // UI updates whenever the state changes (Idle -> Loading -> Success/Error).
        // repeatOnLifecycle(STARTED) makes sure we only collect while the screen is visible,
        // which is the recommended, safe way to collect a Flow from a Fragment.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun attemptLogin() {
        val email = binding.etEmail.text?.toString().orEmpty().trim()
        val password = binding.etPassword.text?.toString().orEmpty()

        binding.textInputLayoutEmail.error =
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) "Enter a valid email" else null
        binding.textInputLayoutPassword.error =
            if (password.length < 6) "Password must be at least 6 characters" else null

        val hasNoErrors = binding.textInputLayoutEmail.error == null &&
            binding.textInputLayoutPassword.error == null
        if (hasNoErrors) {
            viewModel.login(email, password)
        }
    }

    // Reacts to every new state coming from the ViewModel and updates the screen accordingly.
    private fun render(state: AuthViewModel.AuthUiState) {
        binding.progressBar.isVisible = state is AuthViewModel.AuthUiState.Loading
        binding.btnLogin.isEnabled = state !is AuthViewModel.AuthUiState.Loading

        when (state) {
            is AuthViewModel.AuthUiState.LoginSuccess -> {
                viewModel.resetState()
                findNavController().navigateToRoleDashboard(state.role)
            }
            is AuthViewModel.AuthUiState.NeedsMessSetup -> {
                viewModel.resetState()
                findNavController().navigate(R.id.action_loginFragment_to_messSetupFragment)
            }
            is AuthViewModel.AuthUiState.PendingApproval -> {
                viewModel.resetState()
                findNavController().navigate(R.id.action_loginFragment_to_pendingApprovalFragment)
            }
            is AuthViewModel.AuthUiState.Error -> {
                viewModel.resetState()
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
            }
            else -> Unit
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
