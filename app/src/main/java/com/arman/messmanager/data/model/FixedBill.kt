package com.arman.messmanager.data.model

import com.google.firebase.Timestamp

data class FixedBill(
    val billId: String = "",
    val messId: String = "",
    val amount: Double = 0.0,
    val description: String = "",
    val date: Timestamp = Timestamp.now(),
    val addedBy: String = "" // User ID of the manager
)
