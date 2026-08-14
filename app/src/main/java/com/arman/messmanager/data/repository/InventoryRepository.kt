package com.arman.messmanager.data.repository

import com.arman.messmanager.data.model.InventoryItem
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class InventoryRepository {
    private val db = FirebaseFirestore.getInstance()
    private val inventoryCollection = db.collection("inventory")

    suspend fun getInventory(messId: String): List<InventoryItem> {
        return inventoryCollection.whereEqualTo("messId", messId).get().await()
            .toObjects(InventoryItem::class.java)
    }

    suspend fun updateInventoryItem(item: InventoryItem) {
        inventoryCollection.document(item.itemId).set(item).await()
    }
}
