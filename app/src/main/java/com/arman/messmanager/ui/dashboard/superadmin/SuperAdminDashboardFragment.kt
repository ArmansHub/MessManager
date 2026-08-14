package com.arman.messmanager.ui.dashboard.superadmin

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
import com.arman.messmanager.data.model.UserRole
import com.arman.messmanager.data.repository.AuthRepository
import com.arman.messmanager.databinding.FragmentSuperadminDashboardBinding
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.util.Locale

// Super Admin Dashboard, built from the SRS's "Role-Specific Dashboards" section:
// a Top Card (Mess Overview), Quick Actions (Manage Members, Trigger Manager Elections,
// Mess Settings), and a Personal View (own balance and meal status).
//
// This version reads real totals from Firestore through SuperAdminDashboardViewModel,
// the same MVVM pattern used by the Member/Finance/Meal Manager dashboards: the Fragment
// only renders whatever state the ViewModel publishes, it never touches Firestore itself.
// "Manage Members" and "Trigger Manager Elections" are wired up; "Mess Settings" is not
// clickable yet - that comes once that feature is built.
class SuperAdminDashboardFragment : Fragment() {

    private var _binding: FragmentSuperadminDashboardBinding? = null
    private val binding get() = _binding!!

    // "by viewModels()" asks the Fragment framework for a SuperAdminDashboardViewModel
    // instance. It survives screen rotation and is destroyed automatically with the
    // Fragment, so Firestore isn't re-queried on every configuration change.
    private val viewModel: SuperAdminDashboardViewModel by viewModels()

    // Used only for the "Sign Out" button - a trivial one-off call, not really dashboard
    // business logic, so it doesn't need to go through the ViewModel (same reasoning as
    // MessSetupFragment's sign-out link).
    private val authRepository = AuthRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSuperadminDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rowManageMembers.setOnClickListener {
            findNavController().navigate(R.id.action_superAdminDashboardFragment_to_manageMembersFragment)
        }
        binding.rowTriggerElection.setOnClickListener { onTriggerElectionTapped() }
        binding.rowPostNotice.setOnClickListener { showPostNoticeDialog() }
        binding.rowAssignRoles.setOnClickListener { onAssignRolesTapped() }
        binding.rowRemoveMember.setOnClickListener { onRemoveMemberTapped() }
        binding.rowDeleteMess.setOnClickListener { onDeleteMessTapped() }
        binding.tvSignOut.setOnClickListener { signOut() }

        // Same safe-collection pattern used on every other screen: repeatOnLifecycle
        // (STARTED) means we only collect the ViewModel's StateFlow while this screen
        // is actually visible, which is the recommended way to collect a Flow from a
        // Fragment without leaking collection while it's backgrounded.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.adminActionState.collect { state ->
                    when (state) {
                        is AdminActionState.Idle -> { /* Do nothing */ }
                        is AdminActionState.Loading -> {
                            // In a real app, show a loading dialog
                            binding.progressBar.isVisible = true
                        }
                        is AdminActionState.Success -> {
                            binding.progressBar.isVisible = false
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                            if (state.message.contains("Mess deleted")) {
                                findNavController().navigate(R.id.action_superAdminDashboardFragment_to_loginFragment)
                            }
                            viewModel.resetAdminActionState()
                        }
                        is AdminActionState.Error -> {
                            binding.progressBar.isVisible = false
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                            viewModel.resetAdminActionState()
                        }
                    }
                }
            }
        }
    }

    // Pushes one SuperAdminDashboardUiState onto the screen. This runs every time the
    // ViewModel publishes a new state - first after the initial Firestore load
    // completes, and again on any future refresh.
    private fun render(state: SuperAdminDashboardUiState) {
        binding.progressBar.isVisible = state.isLoading

        // Mess Overview card.
        binding.tvTotalMembers.text = state.totalMembers.toString()
        binding.tvActiveManagers.text = state.activeManagers.toString()

        // Your Snapshot card (Personal View).
        binding.tvPersonalBalance.text = formatCurrency(state.personalBalance)
        binding.tvPersonalMeals.text = "${state.personalMealsOnCount} / 3"

        // Trigger Manager Elections status - shown once a poll is open, so the Super
        // Admin can see it's in progress instead of tapping the row again expecting
        // something to happen.
        val activeMonth = state.activeElectionTargetMonthId
        binding.tvElectionStatus.isVisible = activeMonth != null
        binding.tvElectionStatus.text = activeMonth?.let { "Election open for ${formatMonth(it)}" }
    }

    // Confirms before creating a poll (it immediately becomes visible/votable by every
    // member), or explains why the row didn't do anything if one is already open.
    private fun onTriggerElectionTapped() {
        val activeMonth = viewModel.uiState.value.activeElectionTargetMonthId
        if (activeMonth != null) {
            Toast.makeText(
                requireContext(),
                "An election for ${formatMonth(activeMonth)} is already open",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val nextMonthLabel = formatMonth(YearMonth.now().plusMonths(1).toString())
        AlertDialog.Builder(requireContext())
            .setTitle("Trigger Manager Elections")
            .setMessage(
                "This opens voting for $nextMonthLabel's Finance Manager and Meal " +
                    "Manager. Every approved member becomes an eligible candidate and " +
                    "can vote."
            )
            .setPositiveButton("Start Election") { _, _ -> viewModel.triggerElection() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // "Post Notice" (SRS section 9): a single multi-line message field, no custom
    // dialog layout needed - same lightweight shape as every other "add" dialog here.
    private fun showPostNoticeDialog() {
        val messageInput = EditText(requireContext()).apply {
            hint = "Notice message"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Post Notice")
            .setView(messageInput)
            .setPositiveButton("Post") { _, _ ->
                val message = messageInput.text.toString().trim()
                if (message.isEmpty()) {
                    Toast.makeText(requireContext(), "Enter a message", Toast.LENGTH_SHORT).show()
                } else {
                    // The previous version of this dialog was missing the title field.
                    // For now, we'll just pass the message as the title too.
                    viewModel.postNotice(message, message)
                    Toast.makeText(requireContext(), "Notice posted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun onAssignRolesTapped() {
        val roles = arrayOf("Finance Manager", "Meal Manager")
        val userRoles = arrayOf(UserRole.FINANCE_MANAGER, UserRole.MEAL_MANAGER)

        AlertDialog.Builder(requireContext())
            .setTitle("Assign Role")
            .setItems(roles) { _, which ->
                showMemberSelectionDialogForRole(userRoles[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMemberSelectionDialogForRole(role: UserRole) {
        val members = viewModel.uiState.value.messMembers
        if (members.isEmpty()) {
            Toast.makeText(requireContext(), "No other members to assign roles to.", Toast.LENGTH_SHORT).show()
            return
        }
        val memberNames = members.map { it.name }.toTypedArray()

        val roleName = role.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }
        AlertDialog.Builder(requireContext())
            .setTitle("Select Member for $roleName")
            .setItems(memberNames) { _, which ->
                val selectedMember = members[which]
                viewModel.assignRole(selectedMember.uid, role)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun onRemoveMemberTapped() {
        val members = viewModel.uiState.value.messMembers
        if (members.isEmpty()) {
            Toast.makeText(requireContext(), "No members to remove.", Toast.LENGTH_SHORT).show()
            return
        }
        val memberNames = members.map { it.name }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Remove Member")
            .setItems(memberNames) { _, which ->
                val selectedMember = members[which]
                AlertDialog.Builder(requireContext())
                    .setTitle("Confirm Removal")
                    .setMessage("Are you sure you want to remove ${selectedMember.name} from the mess?")
                    .setPositiveButton("Remove") { _, _ -> viewModel.removeMember(selectedMember.uid) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun onDeleteMessTapped() {
        AlertDialog.Builder(requireContext())
            .setTitle("DELETE MESS")
            .setMessage("This action is irreversible and will delete all data associated with this mess. Are you absolutely sure?")
            .setPositiveButton("DELETE") { _, _ -> viewModel.deleteMess() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun formatCurrency(amount: Double): String =
        String.format(Locale.US, "৳ %,.2f", amount)

    private fun signOut() {
        authRepository.signOut()
        findNavController().navigate(R.id.action_superAdminDashboardFragment_to_loginFragment)
    }

    // "2026-09" -> "September 2026". Falls back to the raw id if parsing ever fails
    // (e.g. malformed data), so the screen degrades gracefully instead of crashing.
    private fun formatMonth(monthId: String): String = try {
        val yearMonth = YearMonth.parse(monthId)
        val monthName = yearMonth.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.US)
        "$monthName ${yearMonth.year}"
    } catch (e: Exception) {
        monthId
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
