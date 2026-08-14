package com.arman.messmanager.data.repository

import com.arman.messmanager.data.model.Deposit
import com.arman.messmanager.data.remote.firebase.FirestoreCollections
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import kotlinx.coroutines.tasks.await

// Reads and writes Deposit documents for a mess. Approving a deposit a member submitted
// themselves (a separate future self-service flow) would need its own review step, but
// "Deposit Logging" (SRS section 7) is the Finance Manager directly recording cash/mobile
// banking funds a member just handed over - the Finance Manager *is* the approval, so
// addDeposit() saves it already approved, "reflecting immediately in the member's
// account" as the SRS puts it.
class DepositRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val deposits = firestore.collection(FirestoreCollections.DEPOSITS)

    suspend fun getDeposits(messId: String): List<Deposit> =
        deposits.whereEqualTo("messId", messId).get().await().toObjects(Deposit::class.java)

    suspend fun addDeposit(messId: String, memberUid: String, amount: Double): Deposit {
        val doc = deposits.document()
        val deposit = Deposit(
            depositId = doc.id,
            messId = messId,
            memberUid = memberUid,
            amount = amount,
            date = Timestamp.now(),
            status = "approved"
        )
        doc.set(deposit).await()
        return deposit
    }
}
