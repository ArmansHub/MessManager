package com.arman.messmanager.data.repository

import com.arman.messmanager.data.model.Notice
import com.arman.messmanager.data.remote.firebase.FirestoreCollections
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

// Reads and writes Notice documents - the "Digital Notice Board" (SRS section 9). Any
// Admin or Manager can post one from their own dashboard; every mess member (including
// those managers) reads the same list back read-only on the Member Dashboard.
class NoticeRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val notices = firestore.collection(FirestoreCollections.NOTICES)

    // Newest first - sorted client-side rather than an orderBy() that would need a
    // composite index alongside the messId equality filter, same trade-off every other
    // repository here makes (BazaarEntryRepository, DepositRepository, ...).
    suspend fun getNotices(messId: String): List<Notice> =
        notices.whereEqualTo("messId", messId).get().await()
            .toObjects(Notice::class.java)
            .sortedByDescending { it.timestamp }

    suspend fun postNotice(messId: String, authorUid: String, message: String): Notice {
        val doc = notices.document()
        val notice = Notice(
            noticeId = doc.id,
            messId = messId,
            authorUid = authorUid,
            message = message,
            timestamp = System.currentTimeMillis()
        )
        doc.set(notice).await()
        return notice
    }
}
