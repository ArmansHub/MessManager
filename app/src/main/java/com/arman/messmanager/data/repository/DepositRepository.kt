package com.arman.messmanager.data.repository

import com.arman.messmanager.data.model.Deposit
import com.arman.messmanager.data.remote.firebase.FirestoreCollections
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class DepositRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val deposits = firestore.collection(FirestoreCollections.DEPOSITS)

    suspend fun getDeposits(messId: String): List<Deposit> =
        deposits.whereEqualTo("messId", messId).get().await().toObjects(Deposit::class.java)

    suspend fun addDeposit(messId: String, memberUid: String, amount: Double, status: String = "approved"): Deposit {
        val doc = deposits.document()
        val deposit = Deposit(
            depositId = doc.id,
            messId = messId,
            memberUid = memberUid,
            amount = amount,
            date = Timestamp.now(),
            status = status
        )
        doc.set(deposit).await()
        return deposit
    }

    suspend fun approveDeposit(depositId: String) {
        if (depositId.isBlank()) return
        deposits.document(depositId).update("status", "approved").await()
    }

    suspend fun rejectDeposit(depositId: String) {
        if (depositId.isBlank()) return
        deposits.document(depositId).delete().await()
    }
}
