package com.example.NotesNest.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import android.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class CryptoUtils {
    public static final int SALT_LENGTH = 16;
    public static final int IV_LENGTH = 12; // recommended for GCM
    public static final int AES_KEY_SIZE = 256; // bits
    public static final int PBKDF2_ITERATIONS = 200_000; // high-ish for security / performance tradeoff
    private static final String KDF_ALGO = "PBKDF2WithHmacSHA256";
    private static final String CIPHER_ALGO = "AES/GCM/NoPadding";
    private static final SecureRandom secureRandom = new SecureRandom();

    private CryptoUtils() {
    }

    // generate random salt
    public static byte[] generateSalt() {
        byte[] s = new byte[SALT_LENGTH];
        secureRandom.nextBytes(s);
        return s;
    }

    // generate random IV
    public static byte[] generateIv() {
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);
        return iv;
    }

    // derive AES key from password + salt
    public static SecretKey deriveKey(char[] password, byte[] salt) throws GeneralSecurityException {
        SecretKeyFactory skf = SecretKeyFactory.getInstance(KDF_ALGO);
        KeySpec spec = new PBEKeySpec(password, salt, PBKDF2_ITERATIONS, AES_KEY_SIZE);
        byte[] keyBytes = skf.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    // Encryption: write header (salt + iv) followed by ciphertext
    // Header layout: [16 bytes salt][12 bytes iv][...ciphertext...]
    public static void encryptStream(InputStream plainIn, OutputStream out, char[] password) throws GeneralSecurityException, IOException {
        byte[] salt = generateSalt();
        byte[] iv = generateIv();
        SecretKey key = deriveKey(password, salt);

        // Write header
        out.write(salt);
        out.write(iv);

        Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);

        // CipherOutputStream will append GCM tag (auth tag) at the end
        try (CipherOutputStream cipherOut = new CipherOutputStream(out, cipher)) {
            byte[] buffer = new byte[16 * 1024];
            int r;
            while ((r = plainIn.read(buffer)) != -1) {
                cipherOut.write(buffer, 0, r);
            }
            cipherOut.flush();
        }
    }

    public static CipherOutputStream getCipherOutputStream(OutputStream out, char[] password) throws GeneralSecurityException, IOException {
        byte[] salt = generateSalt();
        byte[] iv = generateIv();
        SecretKey key = deriveKey(password, salt);

        out.write(salt);
        out.write(iv);

        Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);

        return new CipherOutputStream(out, cipher);
    }
    
    public static CipherInputStream getCipherInputStream(InputStream in, char[] password) throws GeneralSecurityException, IOException {
        byte[] salt = new byte[SALT_LENGTH];
        byte[] iv = new byte[IV_LENGTH];

        int read = 0;
        while (read < SALT_LENGTH) {
            int r = in.read(salt, read, SALT_LENGTH - read);
            if (r == -1) throw new IOException("Unexpected EOF while reading salt");
            read += r;
        }
        read = 0;
        while (read < IV_LENGTH) {
            int r = in.read(iv, read, IV_LENGTH - read);
            if (r == -1) throw new IOException("Unexpected EOF while reading iv");
            read += r;
        }

        SecretKey key = deriveKey(password, salt);

        Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);

        return new CipherInputStream(in, cipher);
    }
    public static void decryptStream(InputStream encryptedIn, OutputStream plainOut, char[] password) throws GeneralSecurityException, IOException {
        byte[] salt = new byte[SALT_LENGTH];
        byte[] iv = new byte[IV_LENGTH];

        // Read header
        int read = 0;
        while (read < SALT_LENGTH) {
            int r = encryptedIn.read(salt, read, SALT_LENGTH - read);
            if (r == -1) throw new IOException("Unexpected EOF while reading salt");
            read += r;
        }
        read = 0;
        while (read < IV_LENGTH) {
            int r = encryptedIn.read(iv, read, IV_LENGTH - read);
            if (r == -1) throw new IOException("Unexpected EOF while reading iv");
            read += r;
        }

        SecretKey key = deriveKey(password, salt);

        Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);

        try (CipherInputStream cipherIn = new CipherInputStream(encryptedIn, cipher)) {
            byte[] buffer = new byte[16 * 1024];
            int r;
            while ((r = cipherIn.read(buffer)) != -1) {
                plainOut.write(buffer, 0, r);
            }
            plainOut.flush();
        }
    }

    // Optional: helper to zero out password char[] after use
    public static void clearPassword(char[] password) {
        if (password == null) return;
        Arrays.fill(password, '\0');
    }

    public static String encodePassword(char[] password) {
        if (password == null) return null;
        byte[] bytes = new String(password).getBytes(StandardCharsets.UTF_8);
        return Base64.encodeToString(bytes, Base64.DEFAULT);
    }

    public static char[] decodePassword(String encodedPassword) {
        if (encodedPassword == null) return null;
        byte[] bytes = Base64.decode(encodedPassword, Base64.DEFAULT);
        return new String(bytes, StandardCharsets.UTF_8).toCharArray();
    }

    public static void encryptBytesToUri(Context context, byte[] data, Uri destUri, char[] password) throws GeneralSecurityException, IOException {
        ContentResolver resolver = context.getContentResolver();
        try (InputStream in = new ByteArrayInputStream(data);
             OutputStream out = resolver.openOutputStream(destUri)) {
            if (out == null) throw new IOException("Unable to open destination URI");
            encryptStream(in, out, password);
        }
    }

    public static byte[] decryptUriToBytes(Context context, Uri srcUri, char[] password) throws GeneralSecurityException, IOException {
        try (InputStream rawIn = context.getContentResolver().openInputStream(srcUri)) {
            if (rawIn == null) throw new IOException("Cannot open source stream");
            try (BufferedInputStream in = new BufferedInputStream(rawIn);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                decryptStream(in, out, password);
                return out.toByteArray();
            }
        }
    }

}
