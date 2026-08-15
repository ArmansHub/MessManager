package com.arman.messmanager.ui.dashboard.superadmin

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
import androidx.lifecycle.viewModelScope
import com.arman.messmanager.data.model.Mess
import com.arman.messmanager.data.repository.AuthRepository
import com.arman.messmanager.data.repository.MessRepository
import com.arman.messmanager.data.repository.UserRepository
import com.arman.messmanager.databinding.FragmentMessSettingsBinding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MessSettingsUiState(
    val isLoading: Boolean = true,
    val mess: Mess? = null,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
)

class MessSettingsViewModel : androidx.lifecycle.ViewModel() {
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    private val messRepository = MessRepository()

    private val _uiState = MutableStateFlow(MessSettingsUiState())
    val uiState: StateFlow<MessSettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val uid = authRepository.currentUser?.uid ?: return@launch
            val user = userRepository.getUser(uid) ?: return@launch
            val messId = user.messId ?: return@launch
            val mess = messRepository.getMess(messId)
            _uiState.value = MessSettingsUiState(isLoading = false, mess = mess)
        }
    }

    fun saveSettings(name: String, threshold: Double, ramadanMode: Boolean) {
        val currentMess = _uiState.value.mess ?: return
        _uiState.value = _uiState.value.copy(isSaving = true)
        viewModelScope.launch {
            try {
                val updatedMess = currentMess.copy(
                    name = name,
                    dueThresholdBdt = threshold,
                    ramadanModeEnabled = ramadanMode
                )
                messRepository.updateMess(updatedMess)
                _uiState.value = _uiState.value.copy(isSaving = false, saveSuccess = true, mess = updatedMess)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.message)
            }
        }
    }

    fun resetSuccess() {
        _uiState.value = _uiState.value.copy(saveSuccess = false)
    }
}

class MessSettingsFragment : Fragment() {

    private var _binding: FragmentMessSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MessSettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMessSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSave.setOnClickListener {
            val name = binding.etMessName.text.toString().trim()
            val threshold = binding.etThreshold.text.toString().toDoubleOrNull() ?: 500.0
            val ramadanMode = binding.switchRamadanMode.isChecked
            if (name.isEmpty()) {
                Toast.makeText(requireContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.saveSettings(name, threshold, ramadanMode)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: MessSettingsUiState) {
        binding.progressBar.isVisible = state.isLoading || state.isSaving
        
        if (!state.isLoading && state.mess != null) {
            if (binding.etMessName.text.isNullOrEmpty()) {
                binding.etMessName.setText(state.mess.name)
                binding.etThreshold.setText(state.mess.dueThresholdBdt.toString())
                binding.switchRamadanMode.isChecked = state.mess.ramadanModeEnabled
                binding.tvInviteCode.text = state.mess.inviteCode
            }
        }

        if (state.saveSuccess) {
            Toast.makeText(requireContext(), "Settings saved", Toast.LENGTH_SHORT).show()
            viewModel.resetSuccess()
        }

        if (state.error != null) {
            Toast.makeText(requireContext(), state.error, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
