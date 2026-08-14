package com.arman.messmanager.data.repository

import com.arman.messmanager.data.model.BazaarEntry
import com.arman.messmanager.data.remote.firebase.FirestoreCollections
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

class BazaarEntryRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val bazaarEntries = firestore.collection(FirestoreCollections.BAZAAR_ENTRIES)

    suspend fun getBazaarEntries(messId: String): List<BazaarEntry> =
        bazaarEntries.whereEqualTo("messId", messId).get().await().toObjects(BazaarEntry::class.java)

    // Saves one day's shopping total for "today". Receipt photo upload (Firebase
    // Storage) is a later step - only the amount is recorded for now. linkedPollId
    // optionally bills this expense to one Special Meal Poll's opted-in members
    // instead of the general mess fund (SRS section 6) - see BazaarEntry.linkedPollId.
    suspend fun addBazaarEntry(messId: String, amount: Double, addedByUid: String, linkedPollId: String? = null) {
        val doc = bazaarEntries.document()
        val entry = BazaarEntry(
            entryId = doc.id,
            messId = messId,
            date = LocalDate.now().toString(),
            amount = amount,
            addedByUid = addedByUid,
            linkedPollId = linkedPollId
        )
        doc.set(entry).await()
    }
}
