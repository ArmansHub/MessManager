package com.arman.messmanager.data.model

import com.google.firebase.Timestamp

data class BazaarRoster(
    val rosterId: String = "",
    val messId: String = "",
    val date: Timestamp = Timestamp.now(),
    val assignedMemberUid: String = "",
    val assignedMemberName: String = ""
)
