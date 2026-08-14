package com.arman.messmanager.data.repository

import com.arman.messmanager.data.model.Meal
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.*

class MealRepository {
    private val db = FirebaseFirestore.getInstance()
    private val mealsCollection = db.collection("meals")

    // Get or create a meal entry for a specific user and date
    suspend fun getMealForUser(messId: String, userId: String, date: Date): Meal {
        val startOfDay = getStartOfDay(date)
        val endOfDay = getEndOfDay(date)

        val query = mealsCollection
            .whereEqualTo("messId", messId)
            .whereEqualTo("userId", userId)
            .whereGreaterThanOrEqualTo("date", startOfDay)
            .whereLessThanOrEqualTo("date", endOfDay)
            .limit(1)
            .get()
            .await()

        return if (query.isEmpty) {
            // No meal entry found, return a new one for that day
            Meal(
                messId = messId,
                userId = userId,
                date = Timestamp(date)
            )
        } else {
            query.documents.first().toObject(Meal::class.java) ?: Meal(messId = messId, userId = userId, date = Timestamp(date))
        }
    }

    suspend fun updateMeal(meal: Meal) {
        val docId = if (meal.mealId.isEmpty()) {
            mealsCollection.document().id
        } else {
            meal.mealId
        }
        val mealWithId = meal.copy(mealId = docId)
        mealsCollection.document(docId).set(mealWithId).await()
    }

    private fun getStartOfDay(date: Date): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time
    }

    private fun getEndOfDay(date: Date): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.time
    }
}
