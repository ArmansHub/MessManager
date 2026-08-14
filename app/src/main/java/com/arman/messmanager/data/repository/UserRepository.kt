package com.arman.messmanager.data.repository

import com.arman.messmanager.data.model.User
import com.arman.messmanager.data.remote.firebase.FirestoreCollections
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

// Reads user profile documents (name, role, mess, balance, ...) from the
// "users" collection in Firestore. Each document's ID is the Firebase Auth uid.
class UserRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun getUser(uid: String): User? =
        firestore.collection(FirestoreCollections.USERS).document(uid).get().await()
            // toObject() maps the Firestore document fields onto our User data class.
            .toObject(User::class.java)

    // Writes (or overwrites) one user's profile document. Used once "Profile Setup"
    // (SRS section 4) is built - the temporary dummy-mess code in AuthViewModel also
    // uses this to create a test profile right after registration.
    suspend fun createUser(user: User) {
        firestore.collection(FirestoreCollections.USERS).document(user.uid).set(user).await()
    }

    // Reads every member's profile document for one mess. Used by dashboards that need
    // mess-wide counts (e.g. the Super Admin's "Total Members" / "Active Managers" card)
    // instead of just the signed-in user's own profile. A single equality filter
    // (messId) doesn't need a composite Firestore index.
    suspend fun getUsersForMess(messId: String): List<User> =
        firestore.collection(FirestoreCollections.USERS)
            .whereEqualTo("messId", messId)
            .get()
            .await()
            .toObjects(User::class.java)
}
