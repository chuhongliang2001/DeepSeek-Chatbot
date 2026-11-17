package com.deepseek.chatbot;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class ApiKeyStore {
    private static final String KEY_ALIAS = "deepseek_api_key";
    private static final String PREFS = "secure_api_key";
    private static final String PREF_IV = "iv";
    private static final String PREF_DATA = "data";

    private final SharedPreferences preferences;

    ApiKeyStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized String get() {
        String encodedIv = preferences.getString(PREF_IV, null);
        String encodedData = preferences.getString(PREF_DATA, null);
        if (encodedIv == null || encodedData == null) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, Base64.decode(encodedIv, Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(encodedData, Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return null;
        }
    }

    synchronized boolean set(String apiKey) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] encrypted = cipher.doFinal(apiKey.getBytes(StandardCharsets.UTF_8));
            return preferences.edit()
                    .putString(PREF_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .putString(PREF_DATA, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .commit();
        } catch (Exception ignored) {
            return false;
        }
    }

    synchronized void clear() {
        preferences.edit().clear().apply();
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        java.security.Key existing = keyStore.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) {
            return (SecretKey) existing;
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }
}
