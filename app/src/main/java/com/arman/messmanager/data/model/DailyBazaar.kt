package com.arman.messmanager.data.model

import com.google.firebase.Timestamp

data class DailyBazaar(
    val bazaarId: String = "",
    val messId: String = "",
    val cost: Double = 0.0,
    val date: Timestamp = Timestamp.now(),
    val addedBy: String = "" // User ID of the manager
)
