package com.arman.messmanager.ui.managemembers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.arman.messmanager.R
import com.arman.messmanager.data.model.User
import com.arman.messmanager.data.model.UserRole
import com.arman.messmanager.databinding.FragmentManageMembersBinding
import com.arman.messmanager.databinding.ItemManageMemberRowBinding
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch

class ManageMembersFragment : Fragment() {

    private var _binding: FragmentManageMembersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ManageMembersViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManageMembersBinding.inflate(inflater, container, false)
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

    private fun render(state: ManageMembersUiState) {
        binding.progressBar.isVisible = state.isLoading
        
        binding.tvNoPending.isVisible = !state.isLoading && state.pendingMembers.isEmpty()
        binding.tvNoApproved.isVisible = !state.isLoading && state.approvedMembers.isEmpty()

        renderPendingRows(state.pendingMembers)
        renderApprovedRows(state.approvedMembers)
    }

    private fun renderPendingRows(members: List<User>) {
        binding.containerPending.removeAllViews()
        members.forEach { user ->
            val row = ItemManageMemberRowBinding.inflate(layoutInflater, binding.containerPending, false)
            bindMemberInfo(row, user)
            row.containerActions.isVisible = true
            
            row.btnApprove.setOnClickListener { viewModel.approve(user.uid) }
            row.btnReject.setOnClickListener { viewModel.removeFromMess(user.uid) }
            
            binding.containerPending.addView(row.root)
        }
    }

    private fun renderApprovedRows(members: List<User>) {
        binding.containerApproved.removeAllViews()
        members.forEach { user ->
            val row = ItemManageMemberRowBinding.inflate(layoutInflater, binding.containerApproved, false)
            bindMemberInfo(row, user)
            row.containerActions.isVisible = false // No approve/reject for already approved members
            
            binding.containerApproved.addView(row.root)
        }
    }

    private fun bindMemberInfo(row: ItemManageMemberRowBinding, user: User) {
        row.tvMemberName.text = user.name.ifBlank { "New Member" }
        row.tvMemberPhone.text = user.phone.ifBlank { "No phone added" }
        row.tvMemberRole.text = formatRole(user.role)

        Glide.with(this)
            .load(user.profilePictureUrl)
            .placeholder(R.drawable.ic_person)
            .circleCrop()
            .into(row.ivProfilePicture)
    }

    private fun formatRole(role: UserRole): String = when (role) {
        UserRole.SUPER_ADMIN -> "Super Admin"
        UserRole.FINANCE_MANAGER -> "Finance Manager"
        UserRole.MEAL_MANAGER -> "Meal Manager"
        UserRole.MEMBER -> "Member"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
