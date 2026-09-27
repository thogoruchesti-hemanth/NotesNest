package com.example.NotesNest.activity

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.NotesNest.AnimatedRunningBorderLayout
import com.example.NotesNest.R
import com.example.NotesNest.SingleColorRunningBorderLayout
import com.example.NotesNest.databinding.ActivityLoginBinding
import com.example.NotesNest.utils.AnalyticsHelper
import com.example.NotesNest.utils.AppLog
import com.example.NotesNest.utils.AppPreferences
import com.example.NotesNest.utils.ThemeManager
import com.example.NotesNest.viewmodel.LoginViewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID

class LoginActivity : AppCompatActivity() {

    private val TAG = LoginActivity::class.java.simpleName
    private var _binding: ActivityLoginBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: LoginViewModel
    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var credentialManager: CredentialManager
    private lateinit var appPreferences: AppPreferences
    
    private lateinit var loginBorder: SingleColorRunningBorderLayout
    private lateinit var googleBorder: AnimatedRunningBorderLayout
    private var selectedImageBase64 = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        _binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.loginLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom.coerceAtLeast(ime.bottom)
            )
            WindowInsetsCompat.CONSUMED
        }

        viewModel = ViewModelProvider(this)[LoginViewModel::class.java]
        credentialManager = CredentialManager.create(this)
        appPreferences = AppPreferences.getInstance()

        initUi()
        registerLaunchers()
        bindListeners()
        observeViewModel()
    }

    private fun initUi() {
        loginBorder = binding.loginBorderLayout
        googleBorder = binding.googleBorderLayout

        binding.loginPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        binding.confirmPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        setupTermsAndConditionsText()
    }

    private fun registerLaunchers() {
        imagePickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data?.data != null) {
                handleImageResult(result.data!!.data!!)
            }
        }
    }

    private fun bindListeners() {
        binding.oldUserTextView.setOnClickListener { viewModel.setSignupMode(false) }
        binding.goToSignup.setOnClickListener { viewModel.setSignupMode(true) }

        binding.loginButton.setOnClickListener {
            clearFocusAndHideKeyboard()
            val isSignup = viewModel.uiState.value.isSignupMode

            val email = binding.loginEmail.text?.toString()?.trim() ?: ""
            val password = binding.loginPassword.text?.toString()?.trim() ?: ""

            if (isSignup) {
                val username = binding.userNameEditText.text?.toString()?.trim() ?: ""
                val confirm = binding.confirmPassword.text?.toString()?.trim() ?: ""
                viewModel.signup(username, email, password, confirm, selectedImageBase64)
            } else {
                viewModel.login(email, password)
            }
        }

        binding.forgotPasswordTextView.setOnClickListener {
            val email = binding.loginEmail.text?.toString()?.trim() ?: ""
            viewModel.forgotPassword(email)
        }

        binding.uploadImageButton.setOnClickListener { openImageSelector() }

        binding.googleSignInButton.setOnClickListener {
            clearFocusAndHideKeyboard()
            signInWithGoogle()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                // UI Mode (Login vs Signup)
                if (state.isSignupMode) {
                    binding.editTextUserNameLayout.visibility = View.VISIBLE
                    binding.confirmPasswordLayout.visibility = View.VISIBLE
                    binding.forgotPasswordTextView.visibility = View.GONE
                    binding.googleSignInButton.visibility = View.GONE
                    binding.profileLayout.visibility = View.VISIBLE
                    binding.uploadImageButton.visibility = View.VISIBLE

                    binding.oldUserTextView.setTextColor(ContextCompat.getColor(this@LoginActivity, R.color.textColor))
                    binding.goToSignup.setTextColor(ContextCompat.getColor(this@LoginActivity, R.color.textselectedColor))
                    binding.headerTitleTextView.setText(R.string.sign_up_now)
                    binding.loginButton.setText(R.string.create_an_account)
                } else {
                    binding.forgotPasswordTextView.visibility = View.VISIBLE
                    binding.googleSignInButton.visibility = View.VISIBLE
                    binding.editTextUserNameLayout.visibility = View.GONE
                    binding.confirmPasswordLayout.visibility = View.GONE
                    binding.profileLayout.visibility = View.GONE
                    binding.uploadImageButton.visibility = View.GONE

                    binding.oldUserTextView.setTextColor(ContextCompat.getColor(this@LoginActivity, R.color.textselectedColor))
                    binding.goToSignup.setTextColor(ContextCompat.getColor(this@LoginActivity, R.color.textColor))
                    binding.headerTitleTextView.setText(R.string.hey_login_now)
                    binding.loginButton.setText(R.string.login)
                }

                // Loaders
                if (state.isLoading) {
                    loginBorder.startLoading()
                    binding.loginButton.setTextColor(ThemeManager.getThemeColor(this@LoginActivity, R.color.black, R.color.white))
                    binding.loginButton.setBackgroundColor(ThemeManager.getThemeColor(this@LoginActivity, R.color.backgroundLight, R.color.black))
                    binding.loginButton.isEnabled = false
                } else {
                    loginBorder.stopLoading()
                    binding.loginButton.setTextColor(ThemeManager.getThemeColor(this@LoginActivity, R.color.white, R.color.black))
                    binding.loginButton.setBackgroundColor(ThemeManager.getThemeColor(this@LoginActivity, R.color.black, R.color.white))
                    binding.loginButton.isEnabled = true
                }

                if (state.isGoogleLoading) {
                    googleBorder.startLoading()
                    binding.googleSignInButton.setTextColor(ThemeManager.getThemeColor(this@LoginActivity, R.color.black, R.color.white))
                    binding.googleSignInButton.setBackgroundColor(ThemeManager.getThemeColor(this@LoginActivity, R.color.backgroundLight, R.color.black))
                    binding.googleSignInButton.isEnabled = false
                } else {
                    googleBorder.stopLoading()
                    binding.googleSignInButton.setTextColor(ThemeManager.getThemeColor(this@LoginActivity, R.color.white, R.color.black))
                    binding.googleSignInButton.setBackgroundColor(ThemeManager.getThemeColor(this@LoginActivity, R.color.black, R.color.white))
                    binding.googleSignInButton.isEnabled = true
                }

                // Input Errors
                binding.loginEmailLayout.error = state.emailError
                binding.loginPasswordLayout.error = state.passwordError
                binding.editTextUserNameLayout.error = state.usernameError
                binding.confirmPasswordLayout.error = state.confirmPasswordError

                // Global Error
                if (!state.globalError.isNullOrEmpty()) {
                    binding.errorTextView.text = state.globalError
                    binding.errorTextView.visibility = View.VISIBLE
                } else {
                    binding.errorTextView.text = ""
                    binding.errorTextView.visibility = View.GONE
                }
                
                // Success Navigation
                if (state.loginSuccess) {
                    navigateToMain()
                }
            }
        }
    }

    private fun signInWithGoogle() {
        viewModel.setGoogleLoading(true)

        val rawNonce = UUID.randomUUID().toString()
        var hashedNonce: String? = null
        try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(rawNonce.toByteArray())
            hashedNonce = digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating nonce", e)
        }

        val googleIdOptionBuilder = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(getString(R.string.default_web_client_id))
            .setAutoSelectEnabled(false)

        hashedNonce?.let { googleIdOptionBuilder.setNonce(it) }

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOptionBuilder.build())
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@LoginActivity,
                )
                
                val credential = result.credential
                var idToken: String? = null
                
                if (credential is GoogleIdTokenCredential) {
                    idToken = credential.idToken
                } else if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                }
                
                viewModel.handleGoogleSignInResult(idToken)
                
            } catch (e: Exception) {
                viewModel.setGoogleLoading(false)
                when (e) {
                    is NoCredentialException -> viewModel.setGlobalError(getString(R.string.error_no_google_accounts))
                    is GetCredentialCancellationException -> Log.d(TAG, "Sign-in cancelled by user.")
                    else -> viewModel.setGlobalError(getString(R.string.error_google_sign_in_failed, e.message))
                }
            }
        }
    }

    // ----------------- Image Picker & Compression -----------------

    private fun openImageSelector() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun handleImageResult(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                if (originalBitmap == null) {
                    viewModel.setGlobalError(getString(R.string.error_failed_to_load_image))
                    return
                }

                val maxWidth = 500
                val maxHeight = 500
                val width = originalBitmap.width
                val height = originalBitmap.height
                val ratioBitmap = width.toFloat() / height.toFloat()
                val ratioMax = maxWidth.toFloat() / maxHeight.toFloat()

                var finalWidth = maxWidth
                var finalHeight = maxHeight
                if (ratioMax > ratioBitmap) {
                    finalWidth = (maxHeight.toFloat() * ratioBitmap).toInt()
                } else {
                    finalHeight = (maxWidth.toFloat() / ratioBitmap).toInt()
                }

                val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, finalWidth, finalHeight, true)

                val bos = ByteArrayOutputStream()
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, bos)
                selectedImageBase64 = Base64.encodeToString(bos.toByteArray(), Base64.DEFAULT)

                binding.profileImageView.setImageURI(uri)
                AppLog.i(TAG, "✅ Image successfully compressed & converted. Size: ${selectedImageBase64.length} bytes")
            } ?: run {
                viewModel.setGlobalError(getString(R.string.error_unable_to_open_image))
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "❌ Error loading image: ${e.message}")
            viewModel.setGlobalError(getString(R.string.error_failed_to_load_image))
        }
    }

    private fun navigateToMain() {
        val userId = appPreferences.userId
        com.example.NotesNest.utils.DBSeedUtil.seedDefaultCategories(this, userId)

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    private fun setupTermsAndConditionsText() {
        val fullText = getString(R.string.msg_by_continuing)
        val plainText = fullText.replace(Regex("<[^>]*>"), "")
        val spannableString = SpannableString(plainText)

        val termsStart = plainText.indexOf("Terms & Conditions")
        val privacyStart = plainText.indexOf("Privacy Policy")

        if (termsStart != -1 && privacyStart != -1) {
            val termsEnd = termsStart + "Terms & Conditions".length
            val privacyEnd = privacyStart + "Privacy Policy".length

            val termsClickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://notesnest-app.web.app/terms.html")))
                }
                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = ContextCompat.getColor(this@LoginActivity, R.color.textselectedColor)
                    ds.isUnderlineText = true
                }
            }

            val privacyClickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://notesnest-app.web.app/privacy.html")))
                }
                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = ContextCompat.getColor(this@LoginActivity, R.color.textselectedColor)
                    ds.isUnderlineText = true
                }
            }

            spannableString.setSpan(termsClickableSpan, termsStart, termsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannableString.setSpan(privacyClickableSpan, privacyStart, privacyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        binding.termsTextView.text = spannableString
        binding.termsTextView.movementMethod = LinkMovementMethod.getInstance()
        binding.termsTextView.highlightColor = Color.TRANSPARENT
    }

    private fun clearFocusAndHideKeyboard() {
        val currentFocus = currentFocus
        binding.loginEmail.clearFocus()
        binding.loginPassword.clearFocus()
        binding.userNameEditText.clearFocus()
        binding.confirmPassword.clearFocus()

        currentFocus?.let {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    override fun onResume() {
        super.onResume()
        AnalyticsHelper.logScreenView(javaClass.simpleName, javaClass.simpleName)
    }
}