package com.arman.messmanager.ui.dashboard.superadmin

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
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
import com.arman.messmanager.ui.navigation.safeNavigateToLogin
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.util.Locale

class SuperAdminDashboardFragment : Fragment() {

    private var _binding: FragmentSuperadminDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SuperAdminDashboardViewModel by viewModels()

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
        binding.rowGoToMemberDashboard.setOnClickListener {
            findNavController().navigate(R.id.action_superAdminDashboardFragment_to_memberDashboardFragment)
        }
        binding.rowProfile.setOnClickListener {
            findNavController().navigate(R.id.action_superAdminDashboardFragment_to_profileFragment)
        }
        binding.rowTriggerElection.setOnClickListener { onTriggerElectionTapped() }
        binding.rowMessSettings.setOnClickListener {
            findNavController().navigate(R.id.action_superAdminDashboardFragment_to_messSettingsFragment)
        }
        binding.rowAssignRoles.setOnClickListener { onAssignRolesTapped() }
        binding.rowRemoveMember.setOnClickListener { onRemoveMemberTapped() }
        binding.rowDeleteMess.setOnClickListener { onDeleteMessTapped() }
        binding.rowViewPastElections.setOnClickListener { onViewPastElectionsTapped() }
        binding.tvSignOut.setOnClickListener { signOut() }

        viewModel.refresh()

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
                            binding.progressBar.isVisible = true
                        }
                        is AdminActionState.Success -> {
                            binding.progressBar.isVisible = false
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                            if (state.message.contains("Mess deleted")) {
                                findNavController().safeNavigateToLogin()
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

    private fun render(state: SuperAdminDashboardUiState) {
        binding.progressBar.isVisible = state.isLoading

        binding.tvProfileName.text = state.profileName
        com.bumptech.glide.Glide.with(this)
            .load(state.profilePictureUrl)
            .placeholder(R.drawable.ic_person)
            .circleCrop()
            .into(binding.ivSmallProfilePicture)

        binding.tvTotalMembers.text = state.totalMembers.toString()
        binding.tvActiveManagers.text = state.activeManagers.toString()

        val activeTitle = state.activeElectionTitle
        binding.tvElectionStatus.isVisible = activeTitle != null
        binding.tvElectionStatus.text = activeTitle
    }

    private fun onTriggerElectionTapped() {
        val activeTitle = viewModel.uiState.value.activeElectionTitle
        if (activeTitle != null) {
            AlertDialog.Builder(requireContext())
                .setTitle("Election in Progress")
                .setMessage("Current election: $activeTitle\n\nWhat would you like to do?")
                .setPositiveButton("Vote / View Results") { _, _ ->
                    findNavController().navigate(R.id.action_superAdminDashboardFragment_to_electionFragment)
                }
                .setNegativeButton("Close Election") { _, _ ->
                    viewModel.closeElection()
                }
                .setNeutralButton("Cancel", null)
                .show()
            return
        }

        showTriggerElectionDialog()
    }

    private fun showTriggerElectionDialog() {
        val nextMonthId = YearMonth.now().plusMonths(1).toString()

        val dialogView = layoutInflater.inflate(R.layout.dialog_trigger_election, null)
        val etDuration = dialogView.findViewById<EditText>(R.id.etDuration)
        val cbFinance = dialogView.findViewById<android.widget.CheckBox>(R.id.cbFinance)
        val cbMeal = dialogView.findViewById<android.widget.CheckBox>(R.id.cbMeal)

        AlertDialog.Builder(requireContext())
            .setTitle("Trigger Manager Elections")
            .setView(dialogView)
            .setPositiveButton("Start Election") { _, _ ->
                val selectedRoles = mutableListOf<String>()
                if (cbFinance.isChecked) selectedRoles.add("finance")
                if (cbMeal.isChecked) selectedRoles.add("meal")
                val hours = etDuration.text.toString().toIntOrNull() ?: 24
                
                if (selectedRoles.isEmpty()) {
                    Toast.makeText(requireContext(), "Select at least one role", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.triggerElection(nextMonthId, hours, selectedRoles)
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

    private fun onViewPastElectionsTapped() {
        viewLifecycleOwner.lifecycleScope.launch {
            val pastElections = viewModel.getPastElections()
            if (pastElections.isEmpty()) {
                Toast.makeText(requireContext(), "No past elections found.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val items = pastElections.map { poll ->
                "${formatMonth(poll.monthId)}: ${poll.title}"
            }.toTypedArray()

            AlertDialog.Builder(requireContext())
                .setTitle("Past Election Results")
                .setItems(items) { _, which ->
                    val selected = pastElections[which]
                    showElectionResultDetails(selected)
                }
                .setNegativeButton("Close", null)
                .show()
        }
    }

    private fun showElectionResultDetails(poll: com.arman.messmanager.data.model.ElectionPoll) {
        val message = StringBuilder()
        message.append("Status: ${poll.status.uppercase()}\n")
        message.append("Month: ${formatMonth(poll.monthId)}\n\n")

        if (poll.roles.contains("finance")) {
            message.append("Finance Manager Votes:\n")
            val results = poll.financeVotes.values.groupingBy { it }.eachCount()
            if (results.isEmpty()) message.append("- No votes cast\n")
            results.forEach { (uid, count) ->
                message.append("- User ($uid): $count votes\n")
            }
            message.append("\n")
        }

        if (poll.roles.contains("meal")) {
            message.append("Meal Manager Votes:\n")
            val results = poll.mealVotes.values.groupingBy { it }.eachCount()
            if (results.isEmpty()) message.append("- No votes cast\n")
            results.forEach { (uid, count) ->
                message.append("- User ($uid): $count votes\n")
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle(poll.title)
            .setMessage(message.toString())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun formatCurrency(amount: Double): String =
        String.format(Locale.US, "৳ %,.2f", amount)

    private fun signOut() {
        authRepository.signOut()
        findNavController().safeNavigateToLogin()
    }

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
