package ch.casamk.smarthome;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecureConfig {
    private static final String KEY_ALIAS = "casamk-ha-token-v1";
    private final SharedPreferences prefs;

    public SecureConfig(Context context) {
        prefs = context.getSharedPreferences("connection", Context.MODE_PRIVATE);
    }

    public String url() { return prefs.getString("url", ""); }
    public boolean configured() { return !url().isEmpty() && !token().isEmpty(); }

    public void save(String url, String token) throws Exception {
        URI endpoint = URI.create(url.trim());
        if (!"https".equalsIgnoreCase(endpoint.getScheme())
                || endpoint.getHost() == null || endpoint.getHost().isEmpty()
                || endpoint.getUserInfo() != null || endpoint.getRawQuery() != null
                || endpoint.getRawFragment() != null
                || !(endpoint.getPath().isEmpty() || "/".equals(endpoint.getPath()))
                || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Gültige HTTPS-Serveradresse ohne Pfad sowie Token erforderlich");
        }
        String baseUrl = endpoint.toString().replaceAll("/+$", "");
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] sealed = cipher.doFinal(token.trim().getBytes(StandardCharsets.UTF_8));
        prefs.edit()
            .putString("url", baseUrl)
            .putString("token", Base64.encodeToString(sealed, Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
            .apply();
    }

    public String token() {
        String sealed = prefs.getString("token", "");
        String iv = prefs.getString("iv", "");
        if (sealed.isEmpty() || iv.isEmpty()) return "";
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(),
                    new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(sealed, Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    public void clear() { prefs.edit().clear().apply(); }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        KeyStore.Entry existing = store.getEntry(KEY_ALIAS, null);
        if (existing instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) existing).getSecretKey();
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
