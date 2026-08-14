package com.arman.messmanager.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavOptions
import com.arman.messmanager.R
import com.arman.messmanager.data.model.UserRole

// Single source of truth for "which dashboard does this role land on". Used right after
// Login, and again once a pending join request gets approved (PendingApprovalFragment),
// so both entry points route the same way instead of duplicating this "when" block.
fun NavController.navigateToRoleDashboard(role: UserRole) {
    val destinationId = when (role) {
        UserRole.SUPER_ADMIN -> R.id.superAdminDashboardFragment
        UserRole.FINANCE_MANAGER -> R.id.financeManagerDashboardFragment
        UserRole.MEAL_MANAGER -> R.id.mealManagerDashboardFragment
        UserRole.MEMBER -> R.id.memberDashboardFragment
    }

    // popUpTo(nav_graph, inclusive = true) clears Login/Register/Mess Setup from the
    // back stack, so pressing "back" from the dashboard exits the app instead of
    // returning to an earlier auth/setup screen.
    val navOptions = NavOptions.Builder()
        .setPopUpTo(R.id.nav_graph, true)
        .build()
    navigate(destinationId, null, navOptions)
}
