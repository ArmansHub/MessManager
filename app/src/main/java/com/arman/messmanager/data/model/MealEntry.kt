package com.arman.messmanager.data.model

data class MealEntry(
    val entryId: String = "",
    val messId: String = "",
    val userId: String = "",
    val date: String = "",
    val mealType: MealType = MealType.BREAKFAST,
    val count: Double = 1.0,
    val isGuestMeal: Boolean = false,
    val overriddenByUid: String? = null
)
