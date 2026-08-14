package com.arman.messmanager.data.repository

import com.arman.messmanager.data.model.MealEntry
import com.arman.messmanager.data.model.MealType
import com.arman.messmanager.data.remote.firebase.FirestoreCollections
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

// Reads and writes MealEntry documents - one document per (user, date, meal type),
// e.g. a member's Breakfast entry for 2026-08-10.
class MealEntryRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val mealEntries = firestore.collection(FirestoreCollections.MEAL_ENTRIES)

    // We build the document ID ourselves from userId + date + mealType instead of
    // letting Firestore generate a random one. That way, toggling the same meal twice
    // just overwrites the same document instead of creating duplicates.
    private fun buildEntryId(userId: String, date: String, mealType: MealType): String =
        "${userId}_${date}_${mealType.name}"

    // Returns whatever meal entries already exist for this user on this date
    // (0 to 3 documents: Breakfast/Lunch/Dinner).
    suspend fun getMealsForDate(userId: String, date: String): List<MealEntry> =
        mealEntries
            .whereEqualTo("userId", userId)
            .whereEqualTo("date", date)
            .get()
            .await()
            .toObjects(MealEntry::class.java)

    // Returns every member's meal entries for one mess on one date, used by the Meal
    // Manager dashboard to total up "Today's Meal Summary". Two equality filters
    // (messId + date) don't need a composite Firestore index.
    suspend fun getMealsForMessAndDate(messId: String, date: String): List<MealEntry> =
        mealEntries
            .whereEqualTo("messId", messId)
            .whereEqualTo("date", date)
            .get()
            .await()
            .toObjects(MealEntry::class.java)

    // Every meal entry ever logged for one mess, across all dates - used by Close Month
    // to total up a whole month's meals. Mirrors BazaarEntryRepository.getBazaarEntries:
    // fetch by messId only, let the caller filter down to the month it cares about.
    suspend fun getMealsForMess(messId: String): List<MealEntry> =
        mealEntries.whereEqualTo("messId", messId).get().await().toObjects(MealEntry::class.java)

    // Turns a standard meal on (count = 1.0) or off (count = 0.0) for one day.
    suspend fun setMealOn(messId: String, userId: String, date: String, mealType: MealType, isOn: Boolean) {
        val id = buildEntryId(userId, date, mealType)
        val entry = MealEntry(
            entryId = id,
            messId = messId,
            userId = userId,
            date = date,
            mealType = mealType,
            count = if (isOn) 1.0 else 0.0
        )
        mealEntries.document(id).set(entry).await()
    }
}
