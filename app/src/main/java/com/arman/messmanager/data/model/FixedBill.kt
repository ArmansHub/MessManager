package com.arman.messmanager.data.model

import com.google.firebase.Timestamp

data class FixedBill(
    val billId: String = "",
    val messId: String = "",
    val monthId: String = "",
    val type: FixedBillType = FixedBillType.RENT,
    val amount: Double = 0.0,
    val date: Timestamp = Timestamp.now(),
    val addedBy: String = "" // User ID of the manager
)
