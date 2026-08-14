package com.arman.messmanager.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

// Wraps Firebase Authentication so the rest of the app never talks to FirebaseAuth directly.
class AuthRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    // Firebase methods return a "Task", not a suspend function. ".await()" (from the
    // kotlinx-coroutines-play-services library) lets us pause the coroutine until the
    // Task finishes, so this reads like normal sequential code instead of callbacks.
    suspend fun signIn(email: String, password: String): FirebaseUser? =
        firebaseAuth.signInWithEmailAndPassword(email, password).await().user

    suspend fun register(email: String, password: String): FirebaseUser? =
        firebaseAuth.createUserWithEmailAndPassword(email, password).await().user

    fun signOut() = firebaseAuth.signOut()
}
