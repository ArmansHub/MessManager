package com.arman.messmanager.data.repository

import com.arman.messmanager.data.model.BazaarRoster
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.Date

class BazaarRosterRepository {
    private val db = FirebaseFirestore.getInstance()
    private val rosterCollection = db.collection("bazaarRoster")

    suspend fun getBazaarRoster(messId: String, startDate: Date): List<BazaarRoster> {
        return rosterCollection
            .whereEqualTo("messId", messId)
            .whereGreaterThanOrEqualTo("date", Timestamp(startDate))
            .orderBy("date", Query.Direction.ASCENDING)
            .get()
            .await()
            .toObjects(BazaarRoster::class.java)
    }

    suspend fun assignBazaarDuty(rosterEntry: BazaarRoster) {
        val docId = if (rosterEntry.rosterId.isEmpty()) rosterCollection.document().id else rosterEntry.rosterId
        val entryWithId = rosterEntry.copy(rosterId = docId)
        rosterCollection.document(docId).set(entryWithId).await()
    }
}
