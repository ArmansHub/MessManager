package com.arman.messmanager.data.repository

import com.arman.messmanager.data.model.ElectionPoll
import com.arman.messmanager.data.remote.firebase.FirestoreCollections
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

// Reads and writes ElectionPoll documents - the "Manager Election & Rotation System"
// (SRS section 3). A mess is only ever meant to have one open (closed == false) poll at
// a time, so most reads here look for that single active poll rather than a full history.
class ElectionPollRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val electionPolls = firestore.collection(FirestoreCollections.ELECTION_POLLS)

    // The poll members currently vote in, if the Super Admin has triggered one and
    // hasn't closed it yet. Two equality filters (messId + status) don't need a
    // composite Firestore index.
    suspend fun getActivePoll(messId: String): ElectionPoll? =
        electionPolls
            .whereEqualTo("messId", messId)
            .whereEqualTo("status", "open")
            .limit(1)
            .get()
            .await()
            .documents.firstOrNull()?.toObject(ElectionPoll::class.java)

    // "Trigger Manager Elections" (Super Admin only). Every currently approved member of
    // the mess is eligible to be nominated for either manager role - the SRS doesn't
    // carve out further exclusions, so eligibleCandidateUids is simply the caller's list.
    suspend fun createPoll(
        messId: String,
        title: String,
        options: List<String>
    ): ElectionPoll {
        val doc = electionPolls.document()
        val poll = ElectionPoll(
            pollId = doc.id,
            messId = messId,
            title = title,
            options = options
        )
        doc.set(poll).await()
        return poll
    }

    // Casts (or changes) one member's vote. Votes are
    // stored as a Map<voterUid, option>, so updating just this voter's entry with
    // dotted-field-path syntax overwrites their previous choice in place without
    // touching anyone else's vote or needing to read the whole document first.
    suspend fun castVote(pollId: String, voterUid: String, option: String) {
        electionPolls.document(pollId)
            .update("votesMap.$voterUid", option)
            .await()
    }

    // Marks the poll as finished once Close Month has read and acted on its results, so
    // a new one can be triggered for the following month - getActivePoll only ever looks
    // for status == "open", so a mess never has two open polls at once.
    suspend fun closePoll(pollId: String) {
        electionPolls.document(pollId).update("status", "closed").await()
    }
}
