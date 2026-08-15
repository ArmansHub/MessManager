package com.arman.messmanager.data.model

data class Notice(
    val noticeId: String = "",
    val messId: String = "",
    val title: String = "",
    val content: String = "",
    val date: Long = 0L,
    val postedBy: String = "", // authorUid
    // Add fields to match Firestore if they differ from what was expected
    val authorUid: String? = null,
    val message: String? = null,
    val timestamp: Long? = null,
    val pinned: Boolean = false
)
