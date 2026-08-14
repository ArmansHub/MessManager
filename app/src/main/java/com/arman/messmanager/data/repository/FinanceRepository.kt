package com.arman.messmanager.data.repository

import com.arman.messmanager.data.model.DailyBazaar
import com.arman.messmanager.data.model.Deposit
import com.arman.messmanager.data.model.FixedBill
import com.arman.messmanager.data.model.Meal
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.*

class FinanceRepository {
    private val db = FirebaseFirestore.getInstance()
    private val bazaarCollection = db.collection("bazaar")
    private val billsCollection = db.collection("fixedBills")
    private val depositsCollection = db.collection("deposits")
    private val mealsCollection = db.collection("meals")

    // --- Bazaar ---
    suspend fun addBazaar(bazaar: DailyBazaar) {
        val docId = bazaarCollection.document().id
        bazaarCollection.document(docId).set(bazaar.copy(bazaarId = docId)).await()
    }

    suspend fun getBazaarForMonth(messId: String, month: Date): List<DailyBazaar> {
        val (start, end) = getMonthStartEnd(month)
        return bazaarCollection
            .whereEqualTo("messId", messId)
            .whereGreaterThanOrEqualTo("date", start)
            .whereLessThanOrEqualTo("date", end)
            .get().await().toObjects(DailyBazaar::class.java)
    }

    // --- Fixed Bills ---
    suspend fun addFixedBill(bill: FixedBill) {
        val docId = billsCollection.document().id
        billsCollection.document(docId).set(bill.copy(billId = docId)).await()
    }

    suspend fun getFixedBillsForMonth(messId: String, month: Date): List<FixedBill> {
        val (start, end) = getMonthStartEnd(month)
        return billsCollection
            .whereEqualTo("messId", messId)
            .whereGreaterThanOrEqualTo("date", start)
            .whereLessThanOrEqualTo("date", end)
            .get().await().toObjects(FixedBill::class.java)
    }

    // --- Deposits ---
    suspend fun getDepositsForUser(messId: String, userId: String, month: Date): List<Deposit> {
        val (start, end) = getMonthStartEnd(month)
        return depositsCollection
            .whereEqualTo("messId", messId)
            .whereEqualTo("memberUid", userId)
            .whereGreaterThanOrEqualTo("date", start)
            .whereLessThanOrEqualTo("date", end)
            .get().await().toObjects(Deposit::class.java)
    }

    suspend fun getPendingDeposits(messId: String): List<Deposit> {
        return depositsCollection
            .whereEqualTo("messId", messId)
            .whereEqualTo("status", "pending")
            .get().await().toObjects(Deposit::class.java)
    }

    suspend fun approveDeposit(depositId: String) {
        depositsCollection.document(depositId).update("status", "approved").await()
    }

    // --- Meals (for calculation) ---
    suspend fun getMealsForMonth(messId: String, month: Date): List<Meal> {
        val (start, end) = getMonthStartEnd(month)
        return mealsCollection
            .whereEqualTo("messId", messId)
            .whereGreaterThanOrEqualTo("date", start)
            .whereLessThanOrEqualTo("date", end)
            .get().await().toObjects(Meal::class.java)
    }

    // --- Month Closing ---
    suspend fun closeMonth(messId: String, month: Date) {
        // In a real app, this would involve archiving data, calculating final balances,
        // and carrying them over.
    }

    private fun getMonthStartEnd(date: Date): Pair<Date, Date> {
        val cal = Calendar.getInstance()
        cal.time = date
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        val start = cal.time

        cal.add(Calendar.MONTH, 1)
        cal.add(Calendar.DAY_OF_MONTH, -1)
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
        val end = cal.time
        return Pair(start, end)
    }
}
