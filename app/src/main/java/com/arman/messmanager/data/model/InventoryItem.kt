package com.arman.messmanager.data.model

data class InventoryItem(
    val itemId: String = "",
    val messId: String = "",
    val itemName: String = "",
    val isLowStock: Boolean = false
)
