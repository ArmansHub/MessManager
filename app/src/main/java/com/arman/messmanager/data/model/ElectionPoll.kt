package com.arman.messmanager.data.model

data class ElectionPoll(
    val pollId: String = "",
    val messId: String = "",
    val title: String = "",
    val options: List<String> = emptyList(),
    val votesMap: Map<String, String> = emptyMap(), // voterUid -> option
    val status: String = "open" // "open" or "closed"
)
