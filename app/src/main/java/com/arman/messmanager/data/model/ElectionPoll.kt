package com.arman.messmanager.data.model

data class ElectionPoll(
    val pollId: String = "",
    val messId: String = "",
    val targetMonthId: String = "",
    val eligibleCandidateUids: List<String> = emptyList(),
    val financeManagerVotes: Map<String, String> = emptyMap(),
    val mealManagerVotes: Map<String, String> = emptyMap(),
    val closed: Boolean = false
)
