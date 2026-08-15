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
    val mealRate: Double = 0.0,

    val breakfastLockTime: String? = null,
    val lunchLockTime: String? = null,
    val dinnerLockTime: String? = null,

    // Daily Menu fields
    val breakfastMenu: String = "",
    val lunchMenu: String = "",
    val dinnerMenu: String = ""
)
