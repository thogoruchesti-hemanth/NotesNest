package com.example.NotesNest.activity;

import static com.example.NotesNest.utils.ValidationUtils.isNetworkAvailable;

import android.accounts.Account;
import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ArrayAdapter;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import com.example.NotesNest.utils.CryptoUtils;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.example.NotesNest.R;
import com.example.NotesNest.backups.DriveBackupWorker;
import com.example.NotesNest.backups.ImportManager;
import com.example.NotesNest.databinding.ActivityDriveBackupBinding;
import com.example.NotesNest.models.BackupMode;
import com.example.NotesNest.utils.AppLog;
import com.example.NotesNest.utils.AppPreferences;
import com.example.NotesNest.utils.AppToast;
import com.example.NotesNest.utils.CommonDialogs;
import com.example.NotesNest.utils.PremiumManager;
import com.example.NotesNest.utils.constants.PrefDefaults;
import com.example.NotesNest.utils.constants.PrefKeys;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.Scope;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.FileList;
import com.squareup.picasso.Picasso;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DriveBackupActivity extends AppCompatActivity {

    private static final String TAG = DriveBackupActivity.class.getSimpleName();
    private static final String UNIQUE_WORK_NAME = "DriveAutoBackupWork";
    private AppPreferences appPreferences;
    private boolean isSignedIn = false;
    private PremiumManager premiumManager;
    private androidx.credentials.CredentialManager credentialManager;
    private ActivityResultLauncher<IntentSenderRequest> authorizationLauncher;
    private ActivityDriveBackupBinding binding;
    private RotateAnimation syncAnimation;
    private AlertDialog progressDialog;
    private java.util.concurrent.ExecutorService backgroundExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityDriveBackupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        premiumManager = new PremiumManager(this);
        appPreferences = AppPreferences.getInstance();
        SpannableString s = new SpannableString(getString(R.string.text_drive_backup));
        credentialManager = CredentialManager.create(this);
        s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        binding.toolbar.setTitle(s);
        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());


        initAnimations();
        registerAuthorizationLauncher();
        validateCloudBackupPremiumAccess();
        setupDropdown();
        restoreUIState();
        setupClickListeners();
        backgroundExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    protected void onDestroy() {
        if (backgroundExecutor != null) {
            backgroundExecutor.shutdown();
        }
        super.onDestroy();
    }

    private void initAnimations() {
        syncAnimation = new RotateAnimation(0, 360,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        syncAnimation.setDuration(1000);
        syncAnimation.setRepeatCount(Animation.INFINITE);
    }

    private void registerAuthorizationLauncher() {
        authorizationLauncher = registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        try {
                            String email = appPreferences.getString(PrefKeys.BACKUP_ACCOUNT_EMAIL, null);
                            if (email != null) {
                                onAuthorizationSuccess(email);
                            }
                        } catch (Exception e) {
                            AppLog.e(TAG, "Authorization failed", e);
                        }
                    }
                }
        );
    }

    private void validateCloudBackupPremiumAccess() {
        if (!premiumManager.canUseCloudBackup()) {
            CommonDialogs.showPremiumRequiredDialog(this, getString(R.string.text_cloud_backup_is_a_premium_feature_secure_your_notes_across_all_your_devices_by_upgrading_today));
            applyPremiumLockUI();
        }
    }
    private void setupDropdown() {
        BackupMode[] modes = BackupMode.values();
        ArrayAdapter<BackupMode> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, modes);
        binding.dropdownFrequency.setAdapter(adapter);

        binding.dropdownFrequency.setOnItemClickListener((parent, view, position, id) -> {
            BackupMode selectedMode = (BackupMode) parent.getItemAtPosition(position);
            appPreferences.putString(PrefKeys.BACKUP_MODE, selectedMode.getDisplayName());
            updateBackupSettings();
            
            // Re-schedule if auto backup is already enabled
            if (appPreferences.getBoolean(PrefKeys.AUTO_BACKUP_ENABLED, false)) {
                scheduleAutoBackup(true);
            }
        });
    }

    private void updateBackupSettings() {
        String savedMode = appPreferences.getString(PrefKeys.BACKUP_MODE, BackupMode.MANUAL.getDisplayName());
        BackupMode mode = BackupMode.fromString(savedMode);

        if (mode == BackupMode.MANUAL || mode == BackupMode.OFF) {
            binding.switchAutoBackup.setEnabled(false);
            binding.switchAutoBackup.setChecked(false);
            appPreferences.putBoolean(PrefKeys.AUTO_BACKUP_ENABLED, false);
            scheduleAutoBackup(false);
        } else if (premiumManager.canUseCloudBackup()) {
            binding.switchAutoBackup.setEnabled(true);
        }
    }

    private void restoreUIState() {
        String email = appPreferences.getString(PrefKeys.BACKUP_ACCOUNT_EMAIL, null);
        if (email != null && appPreferences.getBoolean(PrefKeys.IS_SIGNED_IN, false)) {
            updateUIForSignedIn(email);
        } else {
            updateUIForSignedOut();
        }

        String savedMode = appPreferences.getString(PrefKeys.BACKUP_MODE, BackupMode.MANUAL.getDisplayName());
        BackupMode mode = BackupMode.fromString(savedMode);
        binding.dropdownFrequency.setText(mode.toString(), false);
        updateBackupSettings();

        binding.switchAttachments.setChecked(appPreferences.getBoolean(PrefKeys.INCLUDE_ATTACHMENTS, false));
        binding.switchAutoBackup.setChecked(appPreferences.getBoolean(PrefKeys.AUTO_BACKUP_ENABLED, false));

        binding.switchAutoBackup.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!binding.switchAutoBackup.isEnabled()) return;
            
            if (isChecked) {
                // Ensure we have a password saved for auto backup
                String savedPassword = appPreferences.getString(PrefKeys.DRIVE_BACKUP_PASSWORD, null);
                if (savedPassword == null) {
                    binding.switchAutoBackup.setChecked(false); // revert until password is provided
                    CommonDialogs.showPasswordDialog(this, "Set Cloud Backup Password", password -> {
                        appPreferences.putString(PrefKeys.DRIVE_BACKUP_PASSWORD, CryptoUtils.encodePassword(password.toCharArray()));
                        appPreferences.putBoolean(PrefKeys.AUTO_BACKUP_ENABLED, true);
                        binding.switchAutoBackup.setChecked(true); // Now we can check it
                        scheduleAutoBackup(true);
                        AppToast.s(getString(R.string.text_auto_backup_enabled));
                    });
                    return;
                }
            }
            
            appPreferences.putBoolean(PrefKeys.AUTO_BACKUP_ENABLED, isChecked);
            scheduleAutoBackup(isChecked);
            if (isSignedIn && isChecked) {
                AppToast.s(getString(R.string.text_auto_backup_enabled));
            }
        });

        binding.switchAttachments.setOnCheckedChangeListener((buttonView, isChecked) -> appPreferences.putBoolean(PrefKeys.INCLUDE_ATTACHMENTS, isChecked));

        binding.txtLastBackupTime.setText(appPreferences.getString(PrefKeys.LAST_BACKUP_TIME, PrefDefaults.LAST_BACKUP_TIME));
    }

    private void scheduleAutoBackup(boolean enable) {
        WorkManager workManager = WorkManager.getInstance(this);
        if (!enable) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME);
            AppLog.d(TAG, "Auto backup disabled, work cancelled.");
            return;
        }

        String savedMode = appPreferences.getString(PrefKeys.BACKUP_MODE, BackupMode.DAILY.getDisplayName());
        BackupMode mode = BackupMode.fromString(savedMode);
        
        long intervalHours;
        switch (mode) {
            case DAILY:
                intervalHours = TimeUnit.DAYS.toHours(1);
                break;

            case WEEKLY:
                intervalHours = TimeUnit.DAYS.toHours(7);
                break;

            case MONTHLY:
                intervalHours = TimeUnit.DAYS.toHours(30);
                break;

            default:
                AppLog.d(TAG, "Auto backup not scheduled (mode: " + mode + ")");
                return;
        }

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                DriveBackupWorker.class, intervalHours, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build();

        workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
        );
        AppLog.d(TAG, "Auto backup scheduled: " + mode.getDisplayName());
    }

    private void setupClickListeners() {
        binding.btnAddAccount.setOnClickListener(v -> {
            if (premiumManager.canUseCloudBackup()) signIn();
            else
                CommonDialogs.showPremiumRequiredDialog(this, getString(R.string.text_upgrade_to_premium_to_enable_cloud_backup));
        });

        binding.btnBackupNow.setOnClickListener(v -> {
            if (!premiumManager.canUseCloudBackup()) {
                CommonDialogs.showPremiumRequiredDialog(this, getString(R.string.text_manual_cloud_backup_is_a_premium_feature));
                return;
            }
            
            String email = appPreferences.getString(PrefKeys.BACKUP_ACCOUNT_EMAIL, null);
            if (TextUtils.isEmpty(email) || "null".equalsIgnoreCase(email)) {
                AppToast.s("Please sign in to a backup account first.");
                updateUIForSignedOut();
                return;
            }

            if (!isSignedIn) {
                signIn();
                return;
            }
            
            if (!isNetworkAvailable(this)) {
                AppToast.s("No internet connection available. Please check your network.");
                return;
            }
            
            String savedPassword = appPreferences.getString(PrefKeys.DRIVE_BACKUP_PASSWORD, null);
            if (savedPassword == null) {
                CommonDialogs.showPasswordDialog(this, "Set Cloud Backup Password", password -> {
                    appPreferences.putString(PrefKeys.DRIVE_BACKUP_PASSWORD, CryptoUtils.encodePassword(password.toCharArray()));
                    runBackupNow();
                });
            } else {
                runBackupNow();
            }
        });

        binding.btnRestoreNow.setOnClickListener(v -> {
            if (!premiumManager.canUseCloudBackup()) {
                CommonDialogs.showPremiumRequiredDialog(this, "Restore from cloud is a premium feature.");
                return;
            }
            
            String email = appPreferences.getString(PrefKeys.BACKUP_ACCOUNT_EMAIL, null);
            if (TextUtils.isEmpty(email)) {
                AppToast.s("Please sign in first.");
                return;
            }

            if (!isNetworkAvailable(this)) {
                AppToast.s("No internet connection.");
                return;
            }

            CommonDialogs.showConfirmDialog(this, "Restore Data", 
                "This will replace your current notes with the data from Google Drive. Are you sure?", 
                "Restore", "Cancel", () -> {
                    CommonDialogs.showPasswordDialog(this, "Enter Cloud Backup Password", password -> {
                        runRestoreNow(password.toCharArray());
                    });
                });
        });

        binding.btnDisconnect.setOnClickListener(v -> signOut());
    }

    private void signIn() {
        // Generate a nonce for the request (Recommended for Credential Manager)
        String nonce = java.util.UUID.randomUUID().toString();

        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(getString(R.string.default_web_client_id))
                .setAutoSelectEnabled(false)
                .setNonce(nonce)
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        credentialManager.getCredentialAsync(this, request, null, androidx.core.content.ContextCompat.getMainExecutor(this), new androidx.credentials.CredentialManagerCallback<>() {
            @Override
            public void onResult(GetCredentialResponse result) {
                handleCredentialResult(result.getCredential());
            }

            @Override
            public void onError(@NonNull GetCredentialException e) {
                runOnUiThread(() -> {
                    if (e instanceof androidx.credentials.exceptions.NoCredentialException) {
                        AppLog.w(TAG, "No credentials found on device: " + e.getMessage());
                        AppToast.s("No Google account found. Please add an account in device settings.");
                    } else if (e instanceof androidx.credentials.exceptions.GetCredentialCancellationException) {
                        AppLog.d(TAG, "Sign-in cancelled by user.");
                    } else {
                        AppLog.e(TAG, "Credential Manager Error: " + e.getMessage(), e);
                        AppToast.s("Sign-in failed: " + e.getMessage());
                    }
                });
            }
        });
    }

    private void handleCredentialResult(Credential credential) {
        try {
            GoogleIdTokenCredential googleIdTokenCredential = null;
            if (credential instanceof GoogleIdTokenCredential) {
                googleIdTokenCredential = (GoogleIdTokenCredential) credential;
            } else if (credential.getType().equals(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)) {
                googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.getData());
            }

            if (googleIdTokenCredential != null) {
                String email = googleIdTokenCredential.getEmail();
                
                if (TextUtils.isEmpty(email) || "null".equalsIgnoreCase(email)) {
                    AppLog.e(TAG, "Invalid email from credential: " + email);
                    runOnUiThread(() -> AppToast.s("Sign-in failed: Invalid account info."));
                    return;
                }

                String name = googleIdTokenCredential.getDisplayName();
                String profilePic = googleIdTokenCredential.getProfilePictureUri() != null ? googleIdTokenCredential.getProfilePictureUri().toString() : null;

                appPreferences.putString(PrefKeys.BACKUP_ACCOUNT_EMAIL, email);
                appPreferences.putString(PrefKeys.BACKUP_USER_NAME, name);
                appPreferences.putString(PrefKeys.BACKUP_USER_IMAGE, profilePic);

                runOnUiThread(() -> requestDriveAuthorization(email));
            } else {
                AppLog.e(TAG, "Unexpected credential type: " + credential.getType());
                runOnUiThread(() -> AppToast.s("Unexpected login error. Please try again."));
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Error parsing credential", e);
            runOnUiThread(() -> AppToast.s("Failed to parse login info."));
        }
    }

    private void requestDriveAuthorization(String email) {
        if (TextUtils.isEmpty(email) || "null".equalsIgnoreCase(email)) {
            AppLog.e(TAG, "Cannot request authorization for null/empty email");
            AppToast.s("Authorization failed: account not found.");
            return;
        }

        AuthorizationRequest request = AuthorizationRequest.builder()
                .setRequestedScopes(Arrays.asList(new Scope(DriveScopes.DRIVE_FILE), new Scope(DriveScopes.DRIVE_APPDATA)))
                .setAccount(new Account(email, "com.google"))
                .build();

        Identity.getAuthorizationClient(this)
                .authorize(request)
                .addOnSuccessListener(result -> {
                    if (result.hasResolution()) {
                        try {
                            authorizationLauncher.launch(new IntentSenderRequest.Builder(Objects.requireNonNull(result.getPendingIntent()).getIntentSender()).build());
                        } catch (Exception e) {
                            AppLog.e(TAG, "Authorization resolution failed", e);
                        }
                    } else {
                        onAuthorizationSuccess(email);
                    }
                })
                .addOnFailureListener(e -> {
                    AppLog.e(TAG, "Drive authorization failed", e);
                    AppToast.s("Drive authorization failed: " + e.getMessage());
                });
    }

    private void onAuthorizationSuccess(String email) {
        updateUIForSignedIn(email);
        appPreferences.putString(PrefKeys.BACKUP_ACCOUNT_EMAIL, email);
        appPreferences.putBoolean(PrefKeys.IS_SIGNED_IN, true);
        AppToast.s("Connected as " + email);
    }

    private void updateUIForSignedIn(String email) {
        isSignedIn = true;
        
        String name = appPreferences.getString(PrefKeys.BACKUP_USER_NAME, "User");
        String profilePic = appPreferences.getString(PrefKeys.BACKUP_USER_IMAGE, null);

        binding.txtName.setText(name);
        binding.txtEmail.setText(email);
        
        if (profilePic != null && !profilePic.isEmpty()) {
            Picasso.get().load(profilePic).placeholder(R.drawable.ic_profile).into(binding.imgProfile);
        } else {
            binding.imgProfile.setImageResource(R.drawable.ic_profile);
        }

        binding.txtStatus.setText(R.string.text_connected);
        binding.layoutSetup.setVisibility(View.GONE);
        binding.layoutAccountInfo.setVisibility(View.VISIBLE);
        binding.cardSettings.setVisibility(View.VISIBLE);
        binding.cardStatus.setVisibility(View.VISIBLE);
        binding.layoutBackupButtons.setVisibility(View.VISIBLE);
        binding.btnBackupNow.setText(R.string.text_backup_now);
        binding.btnDisconnect.setVisibility(View.VISIBLE);
        binding.textSettingsTitle.setVisibility(View.VISIBLE);
        binding.textStatusTitle.setVisibility(View.VISIBLE);

        if (!premiumManager.canUseCloudBackup()) applyPremiumLockUI();
    }

    private void updateUIForSignedOut() {
        isSignedIn = false;
        binding.layoutSetup.setVisibility(View.VISIBLE);
        binding.layoutAccountInfo.setVisibility(View.GONE);
        binding.cardSettings.setVisibility(View.GONE);
        binding.cardStatus.setVisibility(View.GONE);
        binding.layoutBackupButtons.setVisibility(View.GONE);
        binding.btnDisconnect.setVisibility(View.GONE);
        binding.textSettingsTitle.setVisibility(View.GONE);
        binding.textStatusTitle.setVisibility(View.GONE);
        
        binding.imgProfile.setImageResource(R.drawable.ic_profile);
    }

    private void runBackupNow() {
        // Start UI response
        binding.btnBackupNow.setEnabled(false);
        binding.btnBackupNow.setText(R.string.text_backing_up);
        binding.txtStatus.setText(R.string.text_backing_up);
        binding.imgTick.setVisibility(View.GONE);
        binding.imgStatusIcon.startAnimation(syncAnimation);

        progressDialog = CommonDialogs.showProgressDialog(this, getString(R.string.text_backing_up));

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DriveBackupWorker.class).build();
        WorkManager.getInstance(this).enqueue(request);
        WorkManager.getInstance(this).getWorkInfoByIdLiveData(request.getId()).observe(this, info -> {
            if (info != null) {
                if (info.getState() == WorkInfo.State.SUCCEEDED) {
                    onBackupSuccess();
                } else if (info.getState() == WorkInfo.State.FAILED) {
                    onBackupFailure();
                }
            }
        });
    }

    private void onBackupSuccess() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        binding.imgStatusIcon.clearAnimation();
        binding.btnBackupNow.setEnabled(true);
        binding.btnBackupNow.setText(R.string.text_backup_now);
        binding.txtStatus.setText(R.string.text_backup_complete);
        binding.imgTick.setVisibility(View.VISIBLE);
        
        String ts = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(new Date());
        appPreferences.putString(PrefKeys.LAST_BACKUP_TIME, ts);
        binding.txtLastBackupTime.setText(ts);
        
        AppToast.s(getString(R.string.text_backup_completed_successfully));
    }

    private void onBackupFailure() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        binding.imgStatusIcon.clearAnimation();
        binding.btnBackupNow.setEnabled(true);
        binding.btnBackupNow.setText(R.string.text_backup_now);
        binding.txtStatus.setText(R.string.text_backup_failed);
        binding.imgTick.setVisibility(View.GONE);
        
        AppToast.s("Backup failed. Please try again.");
    }

    private void runRestoreNow(char[] password) {
        progressDialog = CommonDialogs.showProgressDialog(this, getString(R.string.text_restoring_from_drive));
        String email = appPreferences.getString(PrefKeys.BACKUP_ACCOUNT_EMAIL, null);
        
        backgroundExecutor.execute(() -> {
            try {
                Drive driveService = getDriveService(email);
                
                // 1. Find the backup file
                String fileId = findBackupFile(driveService);
                if (fileId == null) {
                    runOnUiThread(() -> {
                        if (progressDialog != null) progressDialog.dismiss();
                        AppToast.s("No backup file found on Drive.");
                        CryptoUtils.clearPassword(password);
                    });
                    return;
                }

                // 2. Download to cache
                java.io.File tempFile = new java.io.File(getCacheDir(), "drive_restore.enc");
                try (OutputStream outputStream = new FileOutputStream(tempFile)) {
                    driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream);
                }

                // 3. Use ImportManager logic to restore
                runOnUiThread(() -> {
                    if (progressDialog != null) progressDialog.dismiss();
                    performRestoreFromFile(tempFile, password);
                });

            } catch (Exception e) {
                AppLog.e(TAG, "Restore failed", e);
                runOnUiThread(() -> {
                    if (progressDialog != null) progressDialog.dismiss();
                    AppToast.s("Restore failed: " + e.getMessage());
                });
                CryptoUtils.clearPassword(password);
            }
        });
    }

    private void performRestoreFromFile(java.io.File file, char[] password) {
        ImportManager manager = new ImportManager(backgroundExecutor, new Handler(Looper.getMainLooper()));
        manager.importFromUri(this, android.net.Uri.fromFile(file), password, new ImportManager.ImportCallback() {
            @Override
            public void showProgress(String message) {
                progressDialog = CommonDialogs.showProgressDialog(DriveBackupActivity.this, getString(R.string.text_importing_data));
            }

            @Override
            public void hideProgress() {
                if (progressDialog != null) progressDialog.dismiss();
            }

            @Override
            public void postToast(String message) {
                AppToast.s(message);
            }
        }, binding.getRoot());
    }

    private Drive getDriveService(String email) {
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(),
                Arrays.asList(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_APPDATA)
        )
        .setBackOff(new ExponentialBackOff())
        .setSelectedAccount(new android.accounts.Account(email, "com.google"));

        return new Drive.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential)
                .setApplicationName("NotesNest")
                .build();
    }

    private String findBackupFile(Drive driveService) throws java.io.IOException {
        String query = "name = 'NotesNest_Backup_Data.enc' and trashed = false";
        FileList result = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute();

        java.util.List<com.google.api.services.drive.model.File> files = result.getFiles();
        if (files == null || files.isEmpty()) {
            return null;
        }
        return files.get(0).getId();
    }

    private void signOut() {
        // Replace deprecated Identity.getSignInClient().signOut() with CredentialManager.clearCredentialStateAsync()
        credentialManager.clearCredentialStateAsync(
                new ClearCredentialStateRequest(),
                null,
                Runnable::run,
                new androidx.credentials.CredentialManagerCallback<>() {
                    @Override
                    public void onResult(Void result) {
                        runOnUiThread(() -> {
                            updateUIForSignedOut();
                            appPreferences.remove(PrefKeys.BACKUP_ACCOUNT_EMAIL);
                            appPreferences.remove(PrefKeys.BACKUP_USER_NAME);
                            appPreferences.remove(PrefKeys.BACKUP_USER_IMAGE);
                            appPreferences.remove(PrefKeys.IS_SIGNED_IN);
                            appPreferences.remove(PrefKeys.DRIVE_BACKUP_PASSWORD);
                            
                            // Cancel any scheduled auto backup
                            WorkManager.getInstance(DriveBackupActivity.this).cancelUniqueWork(UNIQUE_WORK_NAME);
                        });
                    }

                    @Override
                    public void onError(@NonNull androidx.credentials.exceptions.ClearCredentialException e) {
                        AppLog.e(TAG, "Failed to clear credential state", e);
                        // Still update UI
                        runOnUiThread(() -> {
                            updateUIForSignedOut();
                            appPreferences.remove(PrefKeys.BACKUP_ACCOUNT_EMAIL);
                            appPreferences.remove(PrefKeys.IS_SIGNED_IN);
                        });
                    }
                }
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (premiumManager.isPremium()) {
            binding.btnBackupNow.setEnabled(true);
            binding.btnBackupNow.setAlpha(1.0f);
            binding.btnAddAccount.setEnabled(true);
            binding.btnAddAccount.setAlpha(1.0f);
            binding.cardSettings.setAlpha(1.0f);
            binding.inputLayoutFrequency.setEnabled(true);
            binding.switchAttachments.setEnabled(true);
            updateBackupSettings();
        }
        restoreUIState();
    }

    private void applyPremiumLockUI() {
        binding.btnBackupNow.setEnabled(false);
        binding.btnBackupNow.setAlpha(0.5f);
        binding.btnAddAccount.setEnabled(false);
        binding.btnAddAccount.setAlpha(0.5f);
        binding.cardSettings.setAlpha(0.5f);
        binding.inputLayoutFrequency.setEnabled(false);
        binding.switchAttachments.setEnabled(false);
        binding.switchAutoBackup.setEnabled(false);
    }
}