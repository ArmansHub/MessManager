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
import com.arman.messmanager.data.model.User
import com.arman.messmanager.data.model.UserRole
import com.arman.messmanager.databinding.FragmentManageMembersBinding
import com.arman.messmanager.databinding.ItemManageMemberRowBinding
import kotlinx.coroutines.launch

// "Manage Members" (Super Admin Quick Action). Shows every other member of the mess in
// two sections - Pending Requests (SRS section 4 join approvals) and Current Members
// (SRS section 1 "Delete Mess / Remove Members", Super Admin only) - and lets the Super
// Admin approve, reject, or remove them.
//
// Same MVVM shape as every other screen: this Fragment only calls
// ManageMembersViewModel.approve()/removeFromMess() and renders whatever lists come back.
// Member counts here are small (one mess's worth of people), so plain LinearLayout rows
// built with view binding are enough - no RecyclerView/adapter needed for this scale.
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

        // Same safe-collection pattern used on every other screen: only collect the
        // ViewModel's StateFlow while this screen is visible (STARTED).
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: ManageMembersUiState) {
        binding.progressBar.isVisible = state.isLoading

        binding.tvNoPending.isVisible = state.pendingMembers.isEmpty()
        renderPendingRows(state.pendingMembers)

        binding.tvNoApproved.isVisible = state.approvedMembers.isEmpty()
        renderApprovedRows(state.approvedMembers)
    }

    // Rebuilds the "Pending Requests" rows from scratch on every state update. Simple
    // and correct for the small lists this screen deals with - no diffing needed.
    private fun renderPendingRows(members: List<User>) {
        binding.containerPending.removeAllViews()
        members.forEach { member ->
            val row = ItemManageMemberRowBinding.inflate(layoutInflater, binding.containerPending, false)
            bindMemberInfo(row, member)

            row.btnPrimaryAction.isVisible = true
            row.btnPrimaryAction.text = "Approve"
            row.btnPrimaryAction.setOnClickListener { viewModel.approve(member.uid) }

            row.btnSecondaryAction.text = "Reject"
            row.btnSecondaryAction.setOnClickListener { viewModel.removeFromMess(member.uid) }

            binding.containerPending.addView(row.root)
        }
    }

    private fun renderApprovedRows(members: List<User>) {
        binding.containerApproved.removeAllViews()
        members.forEach { member ->
            val row = ItemManageMemberRowBinding.inflate(layoutInflater, binding.containerApproved, false)
            bindMemberInfo(row, member)

            // No "Approve" action for an already-approved member - only "Remove". A
            // GONE view is excluded from LinearLayout's weight distribution entirely,
            // so btnSecondaryAction automatically stretches to fill the row on its own.
            row.btnPrimaryAction.isVisible = false
            row.btnSecondaryAction.text = "Remove"
            row.btnSecondaryAction.setOnClickListener { viewModel.removeFromMess(member.uid) }

            binding.containerApproved.addView(row.root)
        }
    }

    private fun bindMemberInfo(row: ItemManageMemberRowBinding, member: User) {
        row.tvMemberName.text = member.name.ifBlank { member.email }
        row.tvMemberRole.text = formatRole(member.role)
    }

    private fun formatRole(role: UserRole): String = when (role) {
        UserRole.SUPER_ADMIN -> "Super Admin"
        UserRole.FINANCE_MANAGER -> "Finance Manager"
        UserRole.MEAL_MANAGER -> "Meal Manager"
        UserRole.MEMBER -> "General Member"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
