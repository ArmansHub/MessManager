package com.arman.messmanager.data.repository

import com.arman.messmanager.data.model.InventoryItem
import com.arman.messmanager.data.remote.firebase.FirestoreCollections
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

// Reads and writes InventoryItem documents - the "Inventory Tracker" (SRS section 9).
// Any mess member can add an item or flag/clear its low-stock status; there's no
// approval step, this is a shared household list, not a financial record.
class InventoryItemRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val inventoryItems = firestore.collection(FirestoreCollections.INVENTORY_ITEMS)

    suspend fun getItems(messId: String): List<InventoryItem> =
        inventoryItems.whereEqualTo("messId", messId).get().await().toObjects(InventoryItem::class.java)

    suspend fun addItem(messId: String, name: String): InventoryItem {
        val doc = inventoryItems.document()
        val item = InventoryItem(itemId = doc.id, messId = messId, itemName = name)
        doc.set(item).await()
        return item
    }

    // Toggling low-stock off also clears flaggedByUid - "who flagged it" only means
    // something while the flag is still active.
    suspend fun setLowStock(itemId: String, lowStock: Boolean, flaggedByUid: String?) {
        if (itemId.isNullOrBlank()) return
        inventoryItems.document(itemId)
            .update(
                mapOf(
                    "lowStock" to lowStock,
                    "flaggedByUid" to if (lowStock) flaggedByUid else null
                )
            )
            .await()
    }
}
