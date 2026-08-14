package com.arman.messmanager.data.repository

import com.arman.messmanager.data.model.Mess
import com.arman.messmanager.data.model.MealType
import com.arman.messmanager.data.remote.firebase.FirestoreCollections
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.YearMonth
import kotlin.random.Random

class MessRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val messes = firestore.collection(FirestoreCollections.MESSES)

    suspend fun createMess(name: String, superAdminUid: String): Mess {
        val doc = messes.document()
        val mess = Mess(
            messId = doc.id,
            name = name,
            inviteCode = generateInviteCode(),
            superAdminUid = superAdminUid,
            currentMonthId = YearMonth.now().toString()
        )
        doc.set(mess).await()
        return mess
    }

    suspend fun findByInviteCode(inviteCode: String): Mess? =
        messes.whereEqualTo("inviteCode", inviteCode).limit(1).get().await()
            .documents.firstOrNull()?.toObject(Mess::class.java)

    suspend fun getMess(messId: String): Mess? =
        messes.document(messId).get().await().toObject(Mess::class.java)

    // Saves a cut-off time (e.g. "20:00") for one meal type. Ramadan meal types
    // (Sehri/Iftar) aren't supported yet, so those are simply ignored for now.
    suspend fun setMealLockTime(messId: String, mealType: MealType, time: String) {
        val field = when (mealType) {
            MealType.BREAKFAST -> "breakfastLockTime"
            MealType.LUNCH -> "lunchLockTime"
            MealType.DINNER -> "dinnerLockTime"
            else -> return
        }
        messes.document(messId).update(field, time).await()
    }

    // Advances the mess to a new "current" month once Close Month finishes archiving the
    // old one (SRS section 8). Everything that scopes itself to this field - Fixed
    // Bills, and the Finance Manager Dashboard's monthly totals - starts counting from
    // zero for the new id.
    suspend fun advanceToMonth(messId: String, newMonthId: String) {
        messes.document(messId).update("currentMonthId", newMonthId).await()
    }

    private fun generateInviteCode(): String =
        (1..6).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random(Random) }.joinToString("")
}
