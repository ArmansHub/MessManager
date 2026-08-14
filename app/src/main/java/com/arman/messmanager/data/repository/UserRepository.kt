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

    // Removes a member from their mess by clearing their messId. The user document and
    // auth account remain, but they are effectively "kicked" and would need a new
    // invite code to join another mess.
    suspend fun removeMember(uid: String) {
        firestore.collection(FirestoreCollections.USERS).document(uid)
            .update("messId", null)
            .await()
    }

    // Manually sets a user's role. Used by the Super Admin's "Assign Roles" control.
    suspend fun setRole(uid: String, role: UserRole) {
        firestore.collection(FirestoreCollections.USERS).document(uid)
            .update("role", role)
            .await()
    }

    // Used as part of the "Delete Mess" flow. Before the mess document itself can be
    // deleted, all its members must be disassociated from it.
    suspend fun removeAllMembersFromMess(messId: String) {
        val users = getUsersForMess(messId)
        val batch = firestore.batch()
        users.forEach { user ->
            val userRef = firestore.collection(FirestoreCollections.USERS).document(user.uid)
            batch.update(userRef, "messId", null)
        }
        batch.commit().await()
    }
}
