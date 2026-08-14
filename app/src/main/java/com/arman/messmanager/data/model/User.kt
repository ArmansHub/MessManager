package com.arman.messmanager.data.model

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val emergencyContact: String = "",
    val profilePictureUrl: String? = null,
    val messId: String? = null,
    val role: UserRole = UserRole.MEMBER,
    val balance: Double = 0.0,
    val joinApproved: Boolean = false
)
