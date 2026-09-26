package com.example.NotesNest.repository

import com.example.NotesNest.FirebaseHelper
import com.example.NotesNest.utils.AppPreferences
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import java.util.HashMap

class AuthRepositoryImpl : AuthRepository {

    private val mAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val databaseReference = FirebaseDatabase.getInstance().getReference("Users")
    private val firebaseHelper = FirebaseHelper() // For reuse of `downloadImageAndConvertToBase64` if needed

    override suspend fun login(email: String, password: String): Result<Unit> {
        AppPreferences.getInstance().resetPremium()

        return try {
            mAuth.signInWithEmailAndPassword(email, password).await()
            val user = mAuth.currentUser ?: throw Exception("User not found after login")
            val uid = user.uid
            
            val snapshot = databaseReference.child(uid).get().await()
            if (snapshot.exists()) {
                val userName = snapshot.child("userName").getValue(String::class.java)
                val userEmail = snapshot.child("email").getValue(String::class.java)
                val userImage = snapshot.child("userImage").getValue(String::class.java)
                
                val isPremium = snapshot.child("isPremium").getValue(Boolean::class.java) ?: false
                val premiumPlan = snapshot.child("premiumPlan").getValue(String::class.java) ?: FirebaseHelper.PLAN_NONE
                val premiumExpiry = snapshot.child("premiumExpiry").getValue(String::class.java) ?: ""
                val purchaseDate = snapshot.child("purchaseDate").getValue(String::class.java) ?: ""
                val planType = snapshot.child("planType").getValue(String::class.java) ?: FirebaseHelper.PLAN_NONE

                AppPreferences.getInstance().saveUserSession(
                    userName, userEmail, userImage, uid,
                    isPremium, premiumPlan, premiumExpiry, purchaseDate, planType
                )
            } else {
                createUserDataAfterLogin(uid, email, user)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(getLoginErrorMessage(e)))
        }
    }

    override suspend fun signup(username: String, email: String, password: String, base64Image: String): Result<Unit> {
        AppPreferences.getInstance().resetPremium()

        return try {
            mAuth.createUserWithEmailAndPassword(email, password).await()
            val user = mAuth.currentUser ?: throw Exception("User not found after signup")
            val uid = user.uid

            val userData = HashMap<String, Any>()
            userData["userName"] = username
            userData["email"] = email
            userData["userImage"] = base64Image
            userData["password"] = password
            userData["userId"] = uid
            userData["isPremium"] = false
            userData["premiumPlan"] = FirebaseHelper.PLAN_NONE
            userData["premiumExpiry"] = ""
            userData["purchaseDate"] = ""
            userData["planType"] = FirebaseHelper.PLAN_NONE
            userData["createdAt"] = System.currentTimeMillis()

            databaseReference.child(uid).setValue(userData).await()

            AppPreferences.getInstance().saveUserSession(
                username, email, base64Image, uid,
                false, FirebaseHelper.PLAN_NONE, "", "", FirebaseHelper.PLAN_NONE
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(getSignupErrorMessage(e)))
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<Unit> {
        AppPreferences.getInstance().resetPremium()

        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            mAuth.signInWithCredential(credential).await()
            
            val firebaseUser = mAuth.currentUser ?: throw Exception("User not found after Google login")
            val email = firebaseUser.email ?: ""
            val userName = firebaseUser.displayName ?: ""
            val photoUrl = firebaseUser.photoUrl?.toString() ?: ""
            val uid = firebaseUser.uid

            // Using the existing callback wrapper in FirebaseHelper for image downloading to not duplicate HttpUrlConnection logic
            val base64Image = downloadImageSuspend(photoUrl)

            val snapshot = databaseReference.child(uid).get().await()
            if (snapshot.exists()) {
                val existingName = snapshot.child("userName").getValue(String::class.java)
                val existingImage = snapshot.child("userImage").getValue(String::class.java)
                val isPremium = snapshot.child("isPremium").getValue(Boolean::class.java) ?: false
                val premiumPlan = snapshot.child("premiumPlan").getValue(String::class.java) ?: FirebaseHelper.PLAN_NONE
                val premiumExpiry = snapshot.child("premiumExpiry").getValue(String::class.java) ?: ""
                val purchaseDate = snapshot.child("purchaseDate").getValue(String::class.java) ?: ""
                val planType = snapshot.child("planType").getValue(String::class.java) ?: FirebaseHelper.PLAN_NONE

                AppPreferences.getInstance().saveUserSession(
                    existingName, email, existingImage, uid,
                    isPremium, premiumPlan, premiumExpiry, purchaseDate, planType
                )
            } else {
                val userData = HashMap<String, Any>()
                userData["userName"] = userName
                userData["email"] = email
                userData["userImage"] = base64Image
                userData["password"] = ""
                userData["userId"] = uid
                userData["isPremium"] = false
                userData["premiumPlan"] = FirebaseHelper.PLAN_NONE
                userData["premiumExpiry"] = ""
                userData["purchaseDate"] = ""
                userData["planType"] = FirebaseHelper.PLAN_NONE
                userData["createdAt"] = System.currentTimeMillis()

                databaseReference.child(uid).setValue(userData).await()

                AppPreferences.getInstance().saveUserSession(
                    userName, email, base64Image, uid,
                    false, FirebaseHelper.PLAN_NONE, "", "", FirebaseHelper.PLAN_NONE
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Google Sign-In Failed: ${e.message}"))
        }
    }

    override suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            mAuth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(e.message))
        }
    }
    
    // --- Private Helpers ---

    private suspend fun createUserDataAfterLogin(uid: String, email: String, user: FirebaseUser) {
        val displayName = user.displayName ?: "User"
        val userData = HashMap<String, Any>()
        userData["userName"] = displayName
        userData["email"] = email
        userData["userImage"] = ""
        userData["password"] = ""
        userData["userId"] = uid
        userData["isPremium"] = false
        userData["premiumPlan"] = FirebaseHelper.PLAN_NONE
        userData["premiumExpiry"] = ""
        userData["purchaseDate"] = ""
        userData["planType"] = FirebaseHelper.PLAN_NONE
        userData["createdAt"] = System.currentTimeMillis()

        databaseReference.child(uid).setValue(userData).await()

        AppPreferences.getInstance().saveUserSession(
            displayName, email, "", uid,
            false, FirebaseHelper.PLAN_NONE, "", "", FirebaseHelper.PLAN_NONE
        )
    }
    
    private suspend fun downloadImageSuspend(url: String): String = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        firebaseHelper.downloadImageAndConvertToBase64(url) { base64 ->
            continuation.resume(base64) {}
        }
    }

    private fun getLoginErrorMessage(exception: Exception): String {
        return when (exception) {
            is FirebaseAuthInvalidCredentialsException -> "Invalid email or password"
            is FirebaseAuthInvalidUserException -> "No account found with this email"
            is FirebaseNetworkException -> "No internet connection. Please try again"
            is FirebaseTooManyRequestsException -> "Too many attempts. Please try again later"
            else -> exception.message ?: "Login failed. Please try again"
        }
    }

    private fun getSignupErrorMessage(exception: Exception): String {
        return when (exception) {
            is FirebaseAuthWeakPasswordException -> "The password is too weak"
            is FirebaseAuthInvalidCredentialsException -> "The email address is badly formatted"
            is FirebaseAuthUserCollisionException -> "The email address is already in use by another account"
            else -> exception.message ?: "Signup failed. Please try again"
        }
    }
}