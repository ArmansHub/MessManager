package com.arman.messmanager.data.repository

import com.arman.messmanager.data.model.FixedBill
import com.arman.messmanager.data.model.FixedBillType
import com.arman.messmanager.data.remote.firebase.FirestoreCollections
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FixedBillRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val fixedBills = firestore.collection(FirestoreCollections.FIXED_BILLS)

    // Two equality filters (messId + monthId) don't need a composite Firestore index.
    suspend fun getFixedBills(messId: String, monthId: String): List<FixedBill> =
        fixedBills
            .whereEqualTo("messId", messId)
            .whereEqualTo("monthId", monthId)
            .get()
            .await()
            .toObjects(FixedBill::class.java)

    suspend fun addFixedBill(messId: String, monthId: String, type: FixedBillType, amount: Double, addedBy: String) {
        val doc = fixedBills.document()
        val bill = FixedBill(
            billId = doc.id,
            messId = messId,
            monthId = monthId,
            type = type,
            amount = amount,
            addedBy = addedBy
        )
        doc.set(bill).await()
    }
}
