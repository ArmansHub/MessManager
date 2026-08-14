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
import com.arman.messmanager.databinding.FragmentRegisterBinding
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {

    // Same View Binding pattern as LoginFragment: valid only between onCreateView and
    // onDestroyView, cleared afterwards to avoid leaking the destroyed view.
    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRegister.setOnClickListener { attemptRegister() }
        binding.tvLogin.setOnClickListener {
            findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
        }

        // Collect the shared ViewModel state only while the screen is visible (STARTED).
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun attemptRegister() {
        val email = binding.etEmail.text?.toString().orEmpty().trim()
        val password = binding.etPassword.text?.toString().orEmpty()
        val confirmPassword = binding.etConfirmPassword.text?.toString().orEmpty()

        binding.textInputLayoutEmail.error =
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) "Enter a valid email" else null
        binding.textInputLayoutPassword.error =
            if (password.length < 6) "Password must be at least 6 characters" else null
        binding.textInputLayoutConfirmPassword.error =
            if (confirmPassword != password) "Passwords do not match" else null

        val hasNoErrors = binding.textInputLayoutEmail.error == null &&
            binding.textInputLayoutPassword.error == null &&
            binding.textInputLayoutConfirmPassword.error == null
        if (hasNoErrors) {
            viewModel.register(email, password)
        }
    }

    private fun render(state: AuthViewModel.AuthUiState) {
        binding.progressBar.isVisible = state is AuthViewModel.AuthUiState.Loading
        binding.btnRegister.isEnabled = state !is AuthViewModel.AuthUiState.Loading

        when (state) {
            is AuthViewModel.AuthUiState.RegisterSuccess -> {
                viewModel.resetState()
                findNavController().navigate(R.id.action_registerFragment_to_messSetupFragment)
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
