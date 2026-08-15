package com.arman.messmanager.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavOptions
import com.arman.messmanager.R
import com.arman.messmanager.data.model.UserRole

fun NavController.navigateToRoleDashboard(role: UserRole) {
    val destinationId = when (role) {
        UserRole.SUPER_ADMIN -> R.id.superAdminDashboardFragment
        UserRole.FINANCE_MANAGER -> R.id.financeManagerDashboardFragment
        UserRole.MEAL_MANAGER -> R.id.mealManagerDashboardFragment
        UserRole.MEMBER -> R.id.memberDashboardFragment
    }

    val navOptions = NavOptions.Builder()
        .setPopUpTo(R.id.nav_graph, true)
        .build()
    navigate(destinationId, null, navOptions)
}

fun NavController.safeNavigateToLogin() {
    val navOptions = NavOptions.Builder()
        .setPopUpTo(R.id.nav_graph, true)
        .build()
    navigate(R.id.loginFragment, null, navOptions)
}
