package com.arman.messmanager.data.model

import com.google.firebase.Timestamp

data class Meal(
    val mealId: String = "",
    val messId: String = "",
    val userId: String = "",
    val date: Timestamp = Timestamp.now(),
    val breakfastCount: Double = 0.0,
    val lunchCount: Double = 0.0,
    val dinnerCount: Double = 0.0
)
