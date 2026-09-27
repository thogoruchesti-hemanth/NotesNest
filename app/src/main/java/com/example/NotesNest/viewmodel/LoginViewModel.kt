package com.example.NotesNest.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NotesNest.R
import com.example.NotesNest.repository.AuthRepository
import com.example.NotesNest.repository.AuthRepositoryImpl
import com.example.NotesNest.utils.ValidationUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository: AuthRepository = AuthRepositoryImpl()

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun setSignupMode(isSignup: Boolean) {
        _uiState.update { 
            it.copy(
                isSignupMode = isSignup,
                isLoading = false,
                isGoogleLoading = false,
                emailError = null,
                passwordError = null,
                usernameError = null,
                confirmPasswordError = null,
                globalError = null,
                loginSuccess = false
            )
        }
    }

    fun login(email: String, password: String) {
        clearErrors()
        
        var hasError = false
        var emailError: String? = null
        var passwordError: String? = null

        if (ValidationUtils.isValidEmail(email)) {
            emailError = getApplication<Application>().getString(R.string.error_invalid_email)
            hasError = true
        }

        if (ValidationUtils.isValidPassword(password)) {
            passwordError = getApplication<Application>().getString(R.string.error_empty_password)
            hasError = true
        }

        if (hasError) {
            updateStateWithErrors(
                emailError = emailError,
                passwordError = passwordError
            )
            return
        }

        _uiState.update { it.copy(isLoading = true) }
        
        viewModelScope.launch {
            val result = authRepository.login(email, password)
            if (result.isSuccess) {
                _uiState.update { it.copy(isLoading = false, loginSuccess = true) }
            } else {
                val errorMessage = result.exceptionOrNull()?.message ?: "Login failed"
                _uiState.update { it.copy(isLoading = false, globalError = errorMessage) }
            }
        }
    }

    fun signup(username: String, email: String, password: String, confirm: String, base64Image: String) {
        clearErrors()

        var hasError = false
        var usernameError: String? = null
        var emailError: String? = null
        var passwordError: String? = null
        var confirmError: String? = null

        if (ValidationUtils.isValidUsername(username)) {
            usernameError = getApplication<Application>().getString(R.string.error_invalid_username)
            hasError = true
        }
        if (ValidationUtils.isValidEmail(email)) {
            emailError = getApplication<Application>().getString(R.string.error_invalid_email)
            hasError = true
        }
        if (ValidationUtils.isValidPassword(password)) {
            passwordError = getApplication<Application>().getString(R.string.error_weak_password)
            hasError = true
        }
        if (!ValidationUtils.doPasswordsMatch(password, confirm)) {
            confirmError = getApplication<Application>().getString(R.string.error_password_mismatch)
            hasError = true
        }

        if (hasError) {
            updateStateWithErrors(
                emailError = emailError,
                passwordError = passwordError,
                usernameError = usernameError,
                confirmPasswordError = confirmError
            )
            return
        }

        _uiState.update { it.copy(isLoading = true) }
        
        viewModelScope.launch {
            val result = authRepository.signup(username, email, password, base64Image)
            if (result.isSuccess) {
                _uiState.update { it.copy(isLoading = false, loginSuccess = true) }
            } else {
                val errorMessage = result.exceptionOrNull()?.message ?: "Signup failed"
                _uiState.update { it.copy(isLoading = false, globalError = errorMessage) }
            }
        }
    }

    fun setGoogleLoading(isLoading: Boolean) {
        _uiState.update { it.copy(isGoogleLoading = isLoading) }
    }

    fun setGlobalError(error: String) {
        _uiState.update { it.copy(globalError = error) }
    }

    fun handleGoogleSignInResult(idToken: String?) {
        if (idToken == null) {
            setGoogleLoading(false)
            setGlobalError(getApplication<Application>().getString(R.string.error_google_sign_in_another_account))
            return
        }

        viewModelScope.launch {
            val result = authRepository.signInWithGoogle(idToken)
            setGoogleLoading(false)
            if (result.isSuccess) {
                _uiState.update { it.copy(loginSuccess = true) }
            } else {
                val errorMessage = result.exceptionOrNull()?.message ?: "Google Sign-In failed"
                setGlobalError(errorMessage)
            }
        }
    }

    fun forgotPassword(email: String) {
        if (email.isEmpty() || ValidationUtils.isValidEmail(email)) {
            setGlobalError(getApplication<Application>().getString(R.string.error_reset_password_email))
            return
        }
        
        viewModelScope.launch {
            val result = authRepository.resetPassword(email)
            if (result.isSuccess) {
                setGlobalError(getApplication<Application>().getString(R.string.msg_password_reset_sent))
            } else {
                setGlobalError("Error: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    private fun clearErrors() {
        _uiState.update { 
            it.copy(
                emailError = null,
                passwordError = null,
                usernameError = null,
                confirmPasswordError = null,
                globalError = null
            )
        }
    }

    private fun updateStateWithErrors(
        emailError: String? = null,
        passwordError: String? = null,
        usernameError: String? = null,
        confirmPasswordError: String? = null
    ) {
        _uiState.update { 
            it.copy(
                emailError = emailError,
                passwordError = passwordError,
                usernameError = usernameError,
                confirmPasswordError = confirmPasswordError
            )
        }
    }
}