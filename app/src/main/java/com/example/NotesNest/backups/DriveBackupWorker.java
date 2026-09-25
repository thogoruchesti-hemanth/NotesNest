package com.example.NotesNest.backups;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.NotesNest.utils.AppPreferences;
import com.example.NotesNest.utils.constants.PrefKeys;
import com.example.NotesNest.utils.CryptoUtils;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DriveBackupWorker extends Worker {

    private static final String TAG = "DriveBackupWorker";
    private static final String BACKUP_FOLDER_NAME = "NotesNest_Backups";
    private static final String BACKUP_FILE_NAME = "NotesNest_Backup_Data.enc";

    public DriveBackupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting backup worker...");

        AppPreferences.init(getApplicationContext());
        AppPreferences appPrefs = AppPreferences.getInstance();

        String rawEmail = appPrefs.getString(PrefKeys.BACKUP_ACCOUNT_EMAIL, null);
        String email = rawEmail != null ? rawEmail.trim() : null;
        boolean isSignedIn = appPrefs.getBoolean(PrefKeys.IS_SIGNED_IN, false);

        Log.d(TAG, "Worker account check - Email: " + email + ", SignedIn: " + isSignedIn);

        if (TextUtils.isEmpty(email) || "null".equalsIgnoreCase(email) || !isSignedIn) {
            Log.e(TAG, "Backup failed: User not signed in or invalid account. Email: " + email);
            return Result.failure();
        }

        java.io.File localBackupFile = null;

        try {
            Drive driveService = getDriveService(email);
            localBackupFile = createLocalBackupFile(email);

            if (localBackupFile == null) return Result.failure();

            // 1. Get or Create the dedicated folder
            String folderId = getOrCreateBackupFolder(driveService);
            if (folderId == null) {
                Log.e(TAG, "Failed to create/find backup folder.");
                return Result.failure();
            }

            // 2. Find existing backup file INSIDE that folder
            String existingFileId = findFileInFolder(driveService, folderId);
            
            boolean success;
            if (existingFileId != null) {
                Log.d(TAG, "Existing backup found in folder. Updating file ID: " + existingFileId);
                success = updateExistingFile(driveService, existingFileId, localBackupFile);
            } else {
                Log.d(TAG, "No existing backup found in folder. Creating new file.");
                success = createFileInFolder(driveService, folderId, localBackupFile);
            }

            if (success) {
                updateLastBackupTimestamp(appPrefs);
                return Result.success();
            } else {
                return Result.failure();
            }

        } catch (UserRecoverableAuthIOException e) {
            Log.e(TAG, "User action required for Drive access: " + e.getMessage());
            return Result.failure();
        } catch (Exception e) {
            Log.e(TAG, "Worker Exception: " + e.getMessage(), e);
            return shouldRetry(e) ? Result.retry() : Result.failure();
        } finally {
            if (localBackupFile != null && localBackupFile.exists()) {
                if (!localBackupFile.delete()) {
                    Log.w(TAG, "Failed to delete temporary backup file: " + localBackupFile.getAbsolutePath());
                }
            }
        }
    }

    private Drive getDriveService(String email) {
        if (TextUtils.isEmpty(email) || "null".equalsIgnoreCase(email)) {
            Log.e(TAG, "getDriveService: Invalid email provided: " + email);
            throw new IllegalArgumentException("Valid email is required for Drive service");
        }

        Log.d(TAG, "Initializing Drive service for: " + email);

        try {
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
        } catch (Exception e) {
            Log.e(TAG, "Failed to create Drive service: " + e.getMessage());
            throw e;
        }
    }

    private String getOrCreateBackupFolder(Drive driveService) throws IOException {
        String query = "name = '" + BACKUP_FOLDER_NAME + "' and mimeType = 'application/vnd.google-apps.folder' and trashed = false";
        FileList result = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute();

        List<File> files = result.getFiles();
        if (files != null && !files.isEmpty()) {
            return files.get(0).getId();
        }

        // Create folder if not exists
        File folderMetadata = new File();
        folderMetadata.setName(BACKUP_FOLDER_NAME);
        folderMetadata.setMimeType("application/vnd.google-apps.folder");

        File folder = driveService.files().create(folderMetadata)
                .setFields("id")
                .execute();

        return folder.getId();
    }

    private String findFileInFolder(Drive driveService, String folderId) throws IOException {
        String query = "'" + folderId + "' in parents and name = '" + BACKUP_FILE_NAME + "' and trashed = false";
        FileList result = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute();

        List<File> files = result.getFiles();
        if (files == null || files.isEmpty()) {
            return null;
        }
        return files.get(0).getId();
    }

    private boolean createFileInFolder(Drive driveService, String folderId, java.io.File localFile) throws IOException {
        File fileMetadata = new File();
        fileMetadata.setName(BACKUP_FILE_NAME);
        fileMetadata.setParents(java.util.Collections.singletonList(folderId));
        fileMetadata.setMimeType("application/octet-stream");

        FileContent mediaContent = new FileContent("application/octet-stream", localFile);
        File file = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute();
        
        return file.getId() != null;
    }

    private boolean updateExistingFile(Drive driveService, String fileId, java.io.File localFile) throws IOException {
        File fileMetadata = new File();
        fileMetadata.setDescription("Last Updated: " + new Date());

        FileContent mediaContent = new FileContent("application/octet-stream", localFile);
        File updatedFile = driveService.files().update(fileId, fileMetadata, mediaContent)
                .setFields("id")
                .execute();
        
        return updatedFile.getId() != null;
    }

    private java.io.File createLocalBackupFile(String email) {
        try {
            Context context = getApplicationContext();
            BackupProcessor backupProcessor = new BackupProcessor(context);

            java.io.File cacheDir = context.getCacheDir();
            java.io.File encryptedFile = new java.io.File(cacheDir, "drive_backup.enc");

            String encodedPassword = AppPreferences.getInstance().getString(PrefKeys.DRIVE_BACKUP_PASSWORD, null);
            if (encodedPassword == null) {
                throw new IllegalStateException("Cloud backup password is not set");
            }
            char[] password = CryptoUtils.decodePassword(encodedPassword);
            
            try (FileOutputStream fos = new FileOutputStream(encryptedFile);
                 java.io.OutputStream cipherOut = CryptoUtils.getCipherOutputStream(fos, password)) {
                backupProcessor.exportToStream(cipherOut);
            } finally {
                CryptoUtils.clearPassword(password);
            }

            return encryptedFile;
        } catch (Exception e) {
            Log.e(TAG, "Local backup creation failed", e);
            return null;
        }
    }

    private void updateLastBackupTimestamp(AppPreferences appPrefs) {
        String ts = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(new Date());
        appPrefs.putString(PrefKeys.LAST_BACKUP_TIME, ts);
    }

    private boolean shouldRetry(Exception e) {
        return e instanceof IOException;
    }
}
