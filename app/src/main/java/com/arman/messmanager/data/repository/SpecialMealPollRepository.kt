package com.arman.messmanager.data.repository

import com.arman.messmanager.data.model.SpecialMealPoll
import com.arman.messmanager.data.remote.firebase.FirestoreCollections
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

// Reads and writes SpecialMealPoll documents - "Special Meal Polls" (SRS section 6),
// e.g. "Friday Biryani - opt in?". Unlike the Manager Election, a mess can have several
// of these open at once (one per upcoming event), so callers work with a list rather
// than a single active poll.
class SpecialMealPollRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val specialMealPolls = firestore.collection(FirestoreCollections.SPECIAL_MEAL_POLLS)

    // Every still-open poll for this mess - used both by the Meal Manager's own list and
    // the mess-wide opt-in/opt-out screen every member votes from. A single equality
    // filter (messId) doesn't need a composite Firestore index; "closed" is filtered
    // client-side rather than compounding the query, same trade-off ElectionPollRepository
    // avoids by keeping its own filters to two simple equalities.
    // A single poll by id, regardless of its closed status - used by Close Month to
    // resolve a linked BazaarEntry's live participant list (SRS section 6).
    suspend fun getPoll(pollId: String): SpecialMealPoll? =
        specialMealPolls.document(pollId).get().await().toObject(SpecialMealPoll::class.java)

    suspend fun getOpenPolls(messId: String): List<SpecialMealPoll> =
        specialMealPolls
            .whereEqualTo("messId", messId)
            .get()
            .await()
            .toObjects(SpecialMealPoll::class.java)
            .filter { !it.closed }

    // "Create Special Meal Poll" (Meal Manager only, SRS section 6). eventDate is a
    // "yyyy-MM-dd" string, the same format every other date field in the app uses
    // (MealEntry, BazaarEntry, Deposit).
    suspend fun createPoll(messId: String, title: String, eventDate: String): SpecialMealPoll {
        val doc = specialMealPolls.document()
        val poll = SpecialMealPoll(
            pollId = doc.id,
            messId = messId,
            title = title,
            eventDate = eventDate
        )
        doc.set(poll).await()
        return poll
    }

    // Opting in ("Yes") and out ("No") both just add/remove this member's uid from
    // optedInUserIds - there's no separate "No" vote to record, since "not in the list"
    // already means "not eating" (SRS section 6: the cost is split only among members
    // who opted in, whether they voted early or joined in later).
    suspend fun optIn(pollId: String, uid: String) {
        specialMealPolls.document(pollId).update("optedInUserIds", FieldValue.arrayUnion(uid)).await()
    }

    suspend fun optOut(pollId: String, uid: String) {
        specialMealPolls.document(pollId).update("optedInUserIds", FieldValue.arrayRemove(uid)).await()
    }
}
