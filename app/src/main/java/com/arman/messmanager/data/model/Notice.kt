package com.arman.messmanager.data.model

data class Notice(
    val noticeId: String = "",
    val messId: String = "",
    val authorUid: String = "",
    val message: String = "",
    val timestamp: Long = 0L,
    val pinned: Boolean = true
)
