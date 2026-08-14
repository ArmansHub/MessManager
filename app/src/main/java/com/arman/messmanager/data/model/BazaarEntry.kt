package com.arman.messmanager.data.model

data class BazaarEntry(
    val entryId: String = "",
    val messId: String = "",
    val date: String = "",
    val amount: Double = 0.0,
    val receiptPhotoUrl: String? = null,
    val addedByUid: String = "",
    // Null (the default) means this expense is billed to the general mess fund and
    // folds into the standard meal rate, same as any other daily bazaar trip. Set to a
    // SpecialMealPoll's pollId (SRS section 6, e.g. "Friday Biryani") to bill it only to
    // that poll's opted-in members instead - Close Month resolves the participant list
    // from the poll's *current* optedInUserIds at close time, not a snapshot taken here,
    // so members can still opt in right up until the month closes.
    val linkedPollId: String? = null
)
