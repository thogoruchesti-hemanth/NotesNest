package com.example.NotesNest.repository

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<Unit>
    suspend fun signup(username: String, email: String, password: String, base64Image: String): Result<Unit>
    suspend fun signInWithGoogle(idToken: String): Result<Unit>
    suspend fun resetPassword(email: String): Result<Unit>
}