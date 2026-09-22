package com.sembulung.navigator.activation;

import android.content.Context;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

public final class ActivationManager {
    private static final String PREF = "sembulung_activation_v1";
    private static final String KEY = "activation_key";
    private static final String PREFIX = "SN1";
    private static final String PUBLIC_KEY_B64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE5SJcuTYtFsNFf+ntaKqxT/+iOJ6mqobjKIPLebfqCLwehGpJMy9ofcWS0ABYDC1ebV/6YrOH+AD/Nx+SX2YEjA==";

    private ActivationManager() {}

    public static boolean isActivated(Context context) {
        if (!AppIntegrity.isExpectedSignature(context)) return false;
        String key = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null);
        return key != null && verify(context, key);
    }

    public static boolean activate(Context context, String key) {
        if (!AppIntegrity.isExpectedSignature(context) || !verify(context, key)) return false;
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, key.trim()).apply();
        return true;
    }

    public static void deactivate(Context context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply();
    }

    public static boolean verify(Context context, String activationKey) {
        try {
            String key = activationKey == null ? "" : activationKey.trim();
            String[] p = key.split("\\.");
            if (p.length != 4 || !PREFIX.equals(p[0])) return false;

            String expectedDevice = DeviceIdentity.deviceCode(context);
            String device = p[1].replace("-", "").toUpperCase();
            if (!expectedDevice.equals(device)) return false;

            long expiry = Long.parseLong(p[2]);
            if (expiry != 0L && System.currentTimeMillis() / 1000L > expiry) return false;

            String message = PREFIX + "|" + device + "|" + expiry;
            byte[] pubDer = Base64.decode(PUBLIC_KEY_B64, Base64.DEFAULT);
            PublicKey publicKey = KeyFactory.getInstance("EC")
                    .generatePublic(new X509EncodedKeySpec(pubDer));
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(publicKey);
            verifier.update(message.getBytes(StandardCharsets.UTF_8));
            byte[] sig = Base64.decode(p[3], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            return verifier.verify(sig);
        } catch (Exception e) {
            return false;
        }
    }
}
