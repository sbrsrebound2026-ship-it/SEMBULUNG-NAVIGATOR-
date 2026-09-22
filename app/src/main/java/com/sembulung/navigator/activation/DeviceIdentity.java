package com.sembulung.navigator.activation;

import android.content.Context;
import android.provider.Settings;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public final class DeviceIdentity {
    private DeviceIdentity() {}

    public static String rawAndroidId(Context context) {
        String id = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        return id == null ? "UNKNOWN" : id;
    }

    public static String deviceCode(Context context) {
        try {
            String material = "SEMBULUNG-V1|" + context.getPackageName() + "|" + rawAndroidId(context);
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10; i++) sb.append(String.format(Locale.US, "%02X", digest[i]));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create device code", e);
        }
    }

    public static String formattedDeviceCode(Context context) {
        String c = deviceCode(context);
        return c.substring(0,4)+"-"+c.substring(4,8)+"-"+c.substring(8,12)+"-"+
                c.substring(12,16)+"-"+c.substring(16,20);
    }
}
