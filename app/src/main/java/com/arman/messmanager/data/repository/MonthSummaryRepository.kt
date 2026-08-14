package com.arman.messmanager.data.repository

import com.arman.messmanager.data.model.MonthSummary
import com.arman.messmanager.data.remote.firebase.FirestoreCollections
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

// Archives one mess-month's final numbers - the "Archives the month's data into a
// summary report" step of Month Closing (SRS section 8). Read-only afterwards: nothing
// in the app updates a MonthSummary once "Close Month" has written it.
class MonthSummaryRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val monthSummaries = firestore.collection(FirestoreCollections.MONTH_SUMMARIES)

    suspend fun archiveMonth(summary: MonthSummary): MonthSummary {
        val doc = monthSummaries.document()
        val saved = summary.copy(summaryId = doc.id)
        doc.set(saved).await()
        return saved
    }
}
