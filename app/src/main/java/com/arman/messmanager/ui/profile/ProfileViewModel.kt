package com.arman.messmanager.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arman.messmanager.data.model.User
import com.arman.messmanager.data.repository.AuthRepository
import com.arman.messmanager.data.repository.UserRepository
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ProfileUiState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val isUpdating: Boolean = false,
    val updateSuccess: Boolean = false,
    val error: String? = null
)

class ProfileViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val storage = FirebaseStorage.getInstance()

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            val uid = authRepository.currentUser?.uid ?: return@launch
            try {
                val user = userRepository.getUser(uid)
                _uiState.value = _uiState.value.copy(isLoading = false, user = user)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun updateProfile(newName: String, newPhone: String) {
        val currentUser = _uiState.value.user ?: return
        if (newName.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Name cannot be empty")
            return
        }

        _uiState.value = _uiState.value.copy(isUpdating = true, error = null)
        viewModelScope.launch {
            try {
                val updatedUser = currentUser.copy(name = newName, phone = newPhone)
                userRepository.createUser(updatedUser)
                _uiState.value = _uiState.value.copy(
                    isUpdating = false,
                    user = updatedUser,
                    updateSuccess = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isUpdating = false, error = e.message)
            }
        }
    }

    fun uploadProfilePicture(imageUri: Uri) {
        val uid = authRepository.currentUser?.uid ?: return
        val currentUser = _uiState.value.user ?: return

        _uiState.value = _uiState.value.copy(isUpdating = true, error = null)
        viewModelScope.launch {
            try {
                val ref = storage.reference.child("profile_pictures/$uid.jpg")
                ref.putFile(imageUri).await()
                val downloadUrl = ref.downloadUrl.await().toString()

                val updatedUser = currentUser.copy(profilePictureUrl = downloadUrl)
                userRepository.createUser(updatedUser)

                _uiState.value = _uiState.value.copy(
                    isUpdating = false,
                    user = updatedUser,
                    updateSuccess = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isUpdating = false, error = e.message)
            }
        }
    }

    fun resetSuccess() {
        _uiState.value = _uiState.value.copy(updateSuccess = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
