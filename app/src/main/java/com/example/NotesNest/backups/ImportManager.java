package com.example.NotesNest.backups;

import static com.example.NotesNest.utils.CryptoUtils.clearPassword;
import static com.example.NotesNest.utils.CryptoUtils.decryptUriToBytes;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.view.View;

import com.google.android.material.snackbar.Snackbar;

import com.example.NotesNest.utils.CryptoUtils;
import java.io.InputStream;
import java.util.concurrent.Executor;

import javax.crypto.AEADBadTagException;

public class ImportManager {

    private final Executor executor;
    private final Handler uiHandler;

    public ImportManager(Executor executor, Handler uiHandler) {
        this.executor = executor;
        this.uiHandler = uiHandler;
    }

    // PUBLIC API
    public void importFromUri(Context context, Uri srcUri, char[] password, ImportCallback callback, View snackBarAnchor) {
        postUI(() -> callback.showProgress("Importing..."));
        executor.execute(() -> doImport(context, srcUri, password, callback, snackBarAnchor));
    }

    // INTERNAL IMPORT WORKFLOW
    private void doImport(Context context, Uri srcUri, char[] password, ImportCallback callback, View snackBarAnchor) {
        try {
            BackupProcessor processor = new BackupProcessor(context);
            
            try (InputStream in = context.getContentResolver().openInputStream(srcUri)) {
                if (in == null) throw new java.io.IOException("Cannot open source stream");
                try (InputStream cipherIn = CryptoUtils.getCipherInputStream(in, password)) {
                    processor.importFromStream(cipherIn);
                }
            }

            postUI(() -> {
                callback.hideProgress();
                if (snackBarAnchor != null && snackBarAnchor.isAttachedToWindow()) {
                    Snackbar.make(snackBarAnchor, "Import successful. Your data has been merged.", Snackbar.LENGTH_LONG).show();
                } else {
                    callback.postToast("Import successful. Your data has been merged.");
                }
            });

        } catch (AEADBadTagException wrongPw) {
            postUI(() -> {
                callback.hideProgress();
                callback.postToast("Wrong password or corrupted file.");
            });
        } catch (Exception e) {
            postUI(() -> {
                callback.hideProgress();
                callback.postToast("Import failed: " + e.getMessage());
            });
        } finally {
            clearPassword(password);
        }
    }

    // UI POST
    private void postUI(Runnable r) {
        uiHandler.post(r);
    }

    // CALLBACK INTERFACE
    public interface ImportCallback {
        void showProgress(String message);
        void hideProgress();
        void postToast(String message);
    }
}
