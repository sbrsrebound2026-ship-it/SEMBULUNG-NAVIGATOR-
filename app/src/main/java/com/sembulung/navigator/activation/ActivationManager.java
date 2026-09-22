package com.sembulung.navigator.activation;

import android.content.Context;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Locale;

/**
 * Device-bound offline activation verifier.
 *
 * The APK contains only the public key. The corresponding private key must stay
 * with the administrator and is used to sign the Device Code shown on screen.
 */
public final class ActivationManager {
    private static final String PREF = "sembulung_activation_v14";
    private static final String KEY = "activation_key";
    private static final String PREFIX = "SNV1-";
    private static final String SIGNING_DOMAIN = "SEMBULUNG_NAVIGATOR|ACTIVATION|V1|";

    // Public key paired with SEMBULUNG_NAVIGATOR_ACTIVATION_ADMIN_PRIVATE_V1.zip.
    // Safe to embed in the APK. Never embed the private key.
    private static final String PUBLIC_KEY_B64 =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEuxb7ClPm3tcnaOTC5o5bQbQlUq+Q0QDgZx2PXNsNZ7AN1oGC29vSOWmZS45PPPIWfWb2B/19Hn86JigPW2D6Ew==";

    private ActivationManager() {}

    public static boolean isActivated(Context context) {
        if (!AppIntegrity.isExpectedSignature(context)) return false;
        String key = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString(KEY, null);
        return key != null && verify(context, key);
    }

    public static boolean activate(Context context, String activationKey) {
        if (!AppIntegrity.isExpectedSignature(context) || !verify(context, activationKey)) {
            return false;
        }
        String normalized = normalizeActivationKey(activationKey);
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, normalized)
                .apply();
        return true;
    }

    public static void deactivate(Context context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply();
    }

    public static boolean verify(Context context, String activationKey) {
        try {
            String key = normalizeActivationKey(activationKey);
            if (!key.toUpperCase(Locale.US).startsWith(PREFIX)) return false;

            String token = key.substring(PREFIX.length());
            if (token.isEmpty()) return false;

            byte[] signatureBytes = Base64.decode(
                    token,
                    Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING
            );
            byte[] publicKeyBytes = Base64.decode(PUBLIC_KEY_B64, Base64.DEFAULT);
            PublicKey publicKey = KeyFactory.getInstance("EC")
                    .generatePublic(new X509EncodedKeySpec(publicKeyBytes));

            String deviceCode = DeviceIdentity.formattedDeviceCode(context);
            String message = SIGNING_DOMAIN + normalizeDeviceCode(deviceCode);

            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(publicKey);
            verifier.update(message.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(signatureBytes);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String normalizeActivationKey(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", "");
    }

    private static String normalizeDeviceCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.US).replace(" ", "");
    }
}
