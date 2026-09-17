package com.example.NotesNest.utils;

import android.content.Context;
import androidx.annotation.NonNull;

import com.google.android.play.core.integrity.IntegrityManager;
import com.google.android.play.core.integrity.IntegrityManagerFactory;
import com.google.android.play.core.integrity.IntegrityTokenRequest;
import com.google.android.play.core.integrity.IntegrityTokenResponse;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

/**
 * Utility class for Google Play Integrity API integration.
 * Requests integrity tokens to protect against unauthorized access and app tampering.
 */
public class PlayIntegrityManager {

    public interface OnIntegrityCheckListener {
        void onSuccess(String integrityToken);
        void onFailure(Exception e);
    }

    private final IntegrityManager integrityManager;

    public PlayIntegrityManager(@NonNull Context context) {
        this.integrityManager = IntegrityManagerFactory.create(context);
    }

    /**
     * Requests a Play Integrity token.
     * @param cloudProjectNumber Your Google Cloud project number from Google Cloud Console.
     * @param nonce A unique string/nonce for this request to prevent replay attacks.
     * @param listener Callback for success or failure.
     */
    public void requestIntegrityToken(long cloudProjectNumber, @NonNull String nonce, @NonNull OnIntegrityCheckListener listener) {
        IntegrityTokenRequest request = IntegrityTokenRequest.builder()
                .setCloudProjectNumber(cloudProjectNumber)
                .setNonce(nonce)
                .build();

        integrityManager.requestIntegrityToken(request)
                .addOnSuccessListener(new OnSuccessListener<IntegrityTokenResponse>() {
                    @Override
                    public void onSuccess(IntegrityTokenResponse response) {
                        listener.onSuccess(response.token());
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        listener.onFailure(e);
                    }
                });
    }
}
