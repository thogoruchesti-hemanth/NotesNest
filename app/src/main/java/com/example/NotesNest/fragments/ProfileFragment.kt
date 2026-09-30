package com.example.NotesNest.fragments

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatButton
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.NotesNest.FirebaseHelper
import com.example.NotesNest.R
import com.example.NotesNest.activity.DriveBackupActivity
import com.example.NotesNest.activity.HelpAndSupportActivity
import com.example.NotesNest.activity.PremiumActivity
import com.example.NotesNest.activity.SettingsActivity
import com.example.NotesNest.utils.AdManager
import com.example.NotesNest.utils.AnalyticsHelper
import com.example.NotesNest.utils.AppPreferences
import com.example.NotesNest.utils.CommonDialogs
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileFragment : Fragment() {

    private lateinit var appPreferences: AppPreferences
    private lateinit var firebaseHelper: FirebaseHelper

    private lateinit var profileImageView: ImageView
    private lateinit var userNameTextView: TextView
    private lateinit var emailTextView: TextView
    private lateinit var premiumRing: View
    private lateinit var premiumBadge: ImageView
    private lateinit var getProButton: AppCompatButton

    private lateinit var pickMediaLauncher: ActivityResultLauncher<PickVisualMediaRequest>
    private var currentDialogImageView: ImageView? = null

    private val premiumReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (AppPreferences.ACTION_PREMIUM_UPDATED == intent?.action) {
                updatePremiumUI()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        appPreferences = AppPreferences.getInstance()
        firebaseHelper = FirebaseHelper()

        userNameTextView = view.findViewById(R.id.tvUserName)
        emailTextView = view.findViewById(R.id.tvUserEmail)
        profileImageView = view.findViewById(R.id.ivProfileImage)
        premiumRing = view.findViewById(R.id.premiumRing)
        premiumBadge = view.findViewById(R.id.ivPremiumBadge)
        getProButton = view.findViewById(R.id.btnGetPro)

        view.findViewById<Button>(R.id.btnEditProfile).setOnClickListener {
            checkProfileEditLimit()
        }

        getProButton.setOnClickListener {
            startActivity(Intent(requireContext(), PremiumActivity::class.java))
        }

        view.findViewById<View>(R.id.layoutSettings).setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        view.findViewById<View>(R.id.layoutDriveBackup).setOnClickListener {
            startActivity(Intent(requireContext(), DriveBackupActivity::class.java))
        }

        view.findViewById<View>(R.id.layoutHelpAndSupport).setOnClickListener {
            startActivity(Intent(requireContext(), HelpAndSupportActivity::class.java))
        }

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val statusBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            val extraPaddingTop = (12 * resources.displayMetrics.density).toInt()
            v.setPadding(v.paddingLeft, statusBars.top + extraPaddingTop, v.paddingRight, v.paddingBottom)
            insets
        }

        setupPhotoPicker()
        loadUserData()
        updatePremiumUI()

        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            premiumReceiver, IntentFilter(AppPreferences.ACTION_PREMIUM_UPDATED)
        )

        return view
    }

    private fun loadUserData() {
        userNameTextView.text = appPreferences.userName ?: "User Name"
        emailTextView.text = appPreferences.userEmail ?: "user@email.com"
        updateProfileHeaderImage(appPreferences.userImage)
    }

    private fun updatePremiumUI() {
        val isPremium = appPreferences.isUserPremium
        if (isPremium) {
            getProButton.visibility = View.GONE
            premiumRing.visibility = View.VISIBLE
            premiumBadge.visibility = View.VISIBLE
        } else {
            getProButton.visibility = View.VISIBLE
            premiumRing.visibility = View.GONE
            premiumBadge.visibility = View.GONE
        }
    }

    private fun setupPhotoPicker() {
        pickMediaLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { uploadProfileImage(it) }
        }
    }

    private fun checkProfileEditLimit() {
        if (appPreferences.isUserPremium) {
            openEditDialog()
            return
        }

        val currentMonth = SimpleDateFormat("MM-yyyy", Locale.getDefault()).format(Date())
        val lastEditMonth = appPreferences.profileEditMonth
        var editCount = appPreferences.profileEditCount

        if (currentMonth != lastEditMonth) {
            appPreferences.profileEditMonth = currentMonth
            appPreferences.profileEditCount = 0
            editCount = 0
        }

        if (editCount >= 3) {
            CommonDialogs.showConfirmDialog(
                requireContext(),
                "Profile Edit Limit",
                "You've reached your free profile edit limit (3 per month). To edit again, please watch a video ad.",
                "Watch Ad",
                "Upgrade",
                { AdManager.showRewardedAd(requireActivity()) { openEditDialog() } },
                { startActivity(Intent(requireContext(), PremiumActivity::class.java)) }
            )
        } else {
            openEditDialog()
        }
    }

    private fun openEditDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.edit_dialog, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()

        val userNameEdit = dialogView.findViewById<EditText>(R.id.etUserName)
        val emailEdit = dialogView.findViewById<EditText>(R.id.etEmail)
        val profileImage = dialogView.findViewById<ImageView>(R.id.ivProfile)
        val profileUpdateButton = dialogView.findViewById<FloatingActionButton>(R.id.btnUploadImage)

        userNameEdit.setText(userNameTextView.text)
        emailEdit.setText(emailTextView.text)

        currentDialogImageView = profileImage

        val base64Image = appPreferences.imageUrl
        if (!base64Image.isNullOrEmpty()) {
            updateDialogImageView(base64Image)
        } else {
            profileImage.setImageResource(R.drawable.ic_profile)
        }

        profileUpdateButton.setOnClickListener {
            pickMediaLauncher.launch(
                PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    .build()
            )
        }

        dialogView.findViewById<View>(R.id.ivCancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btnSave).setOnClickListener {
            val name = userNameEdit.text.toString().trim()
            val mail = emailEdit.text.toString().trim()
            if (name.isNotEmpty() && mail.isNotEmpty()) {
                if (!appPreferences.isUserPremium) {
                    appPreferences.profileEditCount = appPreferences.profileEditCount + 1
                }
                updateUserIfChanged(name, mail, appPreferences.imageUrl)
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "Please fill in all fields.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUserIfChanged(name: String, email: String, base64Image: String?) {
        val isNameChanged = name != appPreferences.userName
        val isEmailChanged = email != appPreferences.userEmail
        val userId = appPreferences.userId

        if (isNameChanged || isEmailChanged) {
            firebaseHelper.updateUserData(userId, name, base64Image) {
                Toast.makeText(requireContext(), "Details Updated", Toast.LENGTH_SHORT).show()
            }
        }

        appPreferences.userName = name
        appPreferences.userEmail = email
        userNameTextView.text = name
        emailTextView.text = email
    }

    private fun uploadProfileImage(uri: Uri) {
        val base64Image = compressAndEncodeImage(uri) ?: return

        appPreferences.userImage = base64Image
        updateProfileHeaderImage(base64Image)

        val userId = appPreferences.userId
        if (!userId.isNullOrEmpty()) {
            firebaseHelper.updateUserData(userId, appPreferences.userName, base64Image) {}
        }

        currentDialogImageView?.let { updateDialogImageView(base64Image) }
    }

    private fun updateProfileHeaderImage(base64Image: String?) {
        val bitmap = decodeBase64ToBitmap(base64Image)
        if (bitmap != null) {
            profileImageView.setImageBitmap(bitmap)
        } else {
            profileImageView.setImageResource(R.drawable.ic_profile)
        }
    }

    private fun updateDialogImageView(base64Image: String?) {
        val target = currentDialogImageView ?: return
        val bitmap = decodeBase64ToBitmap(base64Image)
        if (bitmap != null) {
            target.setImageBitmap(bitmap)
        } else {
            target.setImageResource(R.drawable.ic_profile)
        }
    }

    private fun compressAndEncodeImage(uri: Uri): String? {
        return try {
            val source = ImageDecoder.createSource(requireContext().contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source)
            val outputStream = ByteArrayOutputStream()
            var quality = 80
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)

            while (outputStream.toByteArray().size > 100 * 1024 && quality > 10) {
                outputStream.reset()
                quality -= 10
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            }
            Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
        } catch (e: IOException) {
            null
        }
    }

    private fun decodeBase64ToBitmap(base64String: String?): Bitmap? {
        if (base64String.isNullOrEmpty()) return null
        return try {
            val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
        updatePremiumUI()
        AnalyticsHelper.logScreenView("Profile", "ProfileFragment")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(premiumReceiver)
    }
}
