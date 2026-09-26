package com.example.NotesNest.viewmodel

data class LoginUiState(
    val isSignupMode: Boolean = false,
    val isLoading: Boolean = false,
    val isGoogleLoading: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val usernameError: String? = null,
    val confirmPasswordError: String? = null,
    val globalError: String? = null,
    val loginSuccess: Boolean = false
)