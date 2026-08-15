package com.arman.messmanager.data.model

data class ElectionPoll(
    val pollId: String = "",
    val messId: String = "",
    val title: String = "",
    val options: List<String> = emptyList(),
    val financeVotes: Map<String, String> = emptyMap(), // voterUid -> candidateUid
    val mealVotes: Map<String, String> = emptyMap(),    // voterUid -> candidateUid
    val status: String = "open", // "open" or "closed"
    val monthId: String = "",    // "yyyy-MM"
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val roles: List<String> = emptyList(), // "finance", "meal"
    val winners: Map<String, String> = emptyMap() // role -> userUid
)
