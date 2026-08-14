package com.arman.messmanager.data.model

data class SpecialMealPoll(
    val pollId: String = "",
    val messId: String = "",
    val title: String = "",
    val eventDate: String = "",
    val optedInUserIds: List<String> = emptyList(),
    val costPerHead: Double? = null,
    val closed: Boolean = false
)
