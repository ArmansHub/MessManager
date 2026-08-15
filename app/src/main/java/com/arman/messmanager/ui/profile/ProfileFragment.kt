package com.arman.messmanager.ui.profile

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.arman.messmanager.R
import com.arman.messmanager.data.repository.AuthRepository
import com.arman.messmanager.databinding.FragmentProfileBinding
import com.arman.messmanager.ui.navigation.safeNavigateToLogin
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()
    private val authRepository = AuthRepository()

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.uploadProfilePicture(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cardProfilePicture.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnUpdate.setOnClickListener {
            val newName = binding.etName.text.toString().trim()
            val newPhone = binding.etPhone.text.toString().trim()
            viewModel.updateProfile(newName, newPhone)
        }

        binding.btnSignOut.setOnClickListener {
            authRepository.signOut()
            findNavController().safeNavigateToLogin()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: ProfileUiState) {
        binding.progressBar.isVisible = state.isLoading || state.isUpdating
        binding.btnUpdate.isEnabled = !state.isLoading && !state.isUpdating

        state.user?.let { user ->
            if (binding.etName.text.isNullOrBlank()) {
                binding.etName.setText(user.name)
            }
            if (binding.etPhone.text.isNullOrBlank()) {
                binding.etPhone.setText(user.phone)
            }
            
            binding.tvEmail.text = "Email: ${user.email}"
            binding.tvRole.text = "Role: ${user.role.name.replace('_', ' ')}"

            Glide.with(this)
                .load(user.profilePictureUrl)
                .placeholder(R.drawable.ic_person)
                .circleCrop()
                .into(binding.ivProfilePicture)
        }

        if (state.updateSuccess) {
            Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show()
            viewModel.resetSuccess()
        }

        state.error?.let { error ->
            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
