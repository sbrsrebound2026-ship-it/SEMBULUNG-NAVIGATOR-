package com.sembulung.navigator.activation;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import com.sembulung.navigator.BuildConfig;
import java.security.MessageDigest;
import java.util.Locale;

public final class AppIntegrity {
    private static final String EXPECTED_CERT_SHA256 = "BADFDF7F8C0031D82CDC20430A29ECD3F6E7D821FEE0478314D1128A6D5E887C";

    private AppIntegrity() {}

    public static boolean isExpectedSignature(Context context) {
        if (BuildConfig.DEBUG) return true;
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info;
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= 28) {
                info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                signatures = info.signingInfo != null ? info.signingInfo.getApkContentsSigners() : new Signature[0];
            } else {
                info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
                signatures = info.signatures;
            }
            for (Signature sig : signatures) {
                String fp = sha256(sig.toByteArray());
                if (EXPECTED_CERT_SHA256.equalsIgnoreCase(fp)) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format(Locale.US, "%02X", b));
        return sb.toString();
    }
}
