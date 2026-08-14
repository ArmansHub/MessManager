package com.arman.messmanager.data.model

import com.google.firebase.Timestamp

data class Deposit(
    val depositId: String = "",
    val messId: String = "",
    val memberUid: String = "",
    val amount: Double = 0.0,
    val date: Timestamp = Timestamp.now(),
    val status: String = "pending" // "pending" or "approved"
)
