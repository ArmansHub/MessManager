package com.arman.messmanager.data.model

data class Mess(
    val messId: String = "",
    val name: String = "",
    val inviteCode: String = "",
    val superAdminUid: String = "",
    val currentMonthId: String = "",
    val dueThresholdBdt: Double = 500.0,
    val ramadanModeEnabled: Boolean = false,
    val language: String = "en",
    // Total Bazaar Cost / Total Standard Meals Consumed this month. The Finance/Meal
    // Management modules will recalculate and save this whenever bazaar or meal data
    // changes - dashboards only ever read it.
    val mealRate: Double = 0.0,

    // Meal lock cut-off times set by the Meal Manager, stored as "HH:mm" (24-hour), e.g.
    // "20:00". Null means that meal has no lock time set yet. These live on the Mess
    // document (not per-device SharedPreferences) because every member's app needs to
    // see the same cut-off, not just the Meal Manager's own phone.
    val breakfastLockTime: String? = null,
    val lunchLockTime: String? = null,
    val dinnerLockTime: String? = null
)
