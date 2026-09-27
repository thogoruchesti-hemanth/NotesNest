package com.example.NotesNest.backups;

import android.content.Context;
import android.net.Uri;
import com.example.NotesNest.utils.CryptoUtils;
import com.example.NotesNest.notifications.helper.NotificationHelper;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalBackupManager {

    private final Context context;
    private final ExecutorService executor;
    private final BackupProcessor backupProcessor;

    public LocalBackupManager(Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
        this.backupProcessor = new BackupProcessor(context);
    }

    public void startBackup(char[] password, BackupCallback callback, Uri targetUri) {
        callback.showProgress("Preparing backup...");

        executor.execute(() -> {
            try {
                // Stream data directly to CipherOutputStream wrapping the destination URI
                try (OutputStream out = context.getContentResolver().openOutputStream(targetUri)) {
                    if (out == null) throw new java.io.IOException("Unable to open destination URI");
                    
                    try (OutputStream cipherOut = CryptoUtils.getCipherOutputStream(out, password)) {
                        backupProcessor.exportToStream(cipherOut);
                    }
                }

                callback.postToast("Backup saved successfully!");
            } catch (Exception e) {
                callback.postToast("Backup failed: " + e.getMessage());
            } finally {
                CryptoUtils.clearPassword(password);
                callback.hideProgress();
            }
        });
    }

    public interface BackupCallback {
        void showProgress(String message);
        void hideProgress();
        void postToast(String message);
    }
}
