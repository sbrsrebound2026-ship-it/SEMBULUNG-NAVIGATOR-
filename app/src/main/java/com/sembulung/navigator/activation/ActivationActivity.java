package com.sembulung.navigator.activation;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.sembulung.navigator.HomeActivity;

public class ActivationActivity extends Activity {
    private static final String ADMIN_WA = "6281234406456";

    private EditText keyInput;
    private TextView statusText;
    private String deviceCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(3, 18, 31));
        deviceCode = DeviceIdentity.formattedDeviceCode(this);

        if (ActivationManager.isActivated(this)) {
            openHome();
            return;
        }
        setContentView(buildUi());
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(3, 18, 31));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(30), dp(24), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(96), dp(96));
        iconParams.bottomMargin = dp(14);
        root.addView(icon, iconParams);

        TextView brand = text("SEMBULUNG NAVIGATOR", 24, true);
        brand.setTextColor(Color.rgb(73, 211, 255));
        brand.setGravity(Gravity.CENTER);
        root.addView(brand, matchWrap(0, 2));

        TextView subtitle = text("Navigasi Laut Presisi  •  BY WONG MBRAYU", 13, false);
        subtitle.setTextColor(Color.rgb(151, 181, 195));
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, matchWrap(0, 24));

        TextView title = text("ACTIVATION REQUIRED", 20, true);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap(0, 8));

        TextView info = text(
                "Aktivasi diperlukan sebelum masuk ke aplikasi. Setiap perangkat mempunyai Device Code dan Activation Key yang berbeda.",
                14,
                false
        );
        info.setTextColor(Color.rgb(190, 207, 216));
        info.setGravity(Gravity.CENTER);
        root.addView(info, matchWrap(0, 24));

        TextView dcLabel = text("DEVICE CODE", 12, true);
        dcLabel.setTextColor(Color.rgb(117, 185, 213));
        root.addView(dcLabel, matchWrap(0, 7));

        TextView dcValue = text(deviceCode, 18, true);
        dcValue.setTextColor(Color.WHITE);
        dcValue.setGravity(Gravity.CENTER);
        dcValue.setTextIsSelectable(true);
        dcValue.setPadding(dp(12), dp(14), dp(12), dp(14));
        dcValue.setBackgroundColor(Color.rgb(10, 40, 56));
        root.addView(dcValue, matchWrap(0, 8));

        Button copy = button("SALIN DEVICE CODE");
        copy.setOnClickListener(v -> copyDeviceCode());
        root.addView(copy, matchWrap(0, 18));

        TextView keyLabel = text("ENTER ACTIVATION KEY", 12, true);
        keyLabel.setTextColor(Color.rgb(117, 185, 213));
        root.addView(keyLabel, matchWrap(0, 7));

        keyInput = new EditText(this);
        keyInput.setSingleLine(false);
        keyInput.setMinLines(2);
        keyInput.setTextColor(Color.WHITE);
        keyInput.setHintTextColor(Color.rgb(115, 135, 145));
        keyInput.setHint("SNV1-...");
        keyInput.setTextSize(14);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        keyInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        keyInput.setBackgroundColor(Color.rgb(10, 40, 56));
        root.addView(keyInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(78)
        ));

        Button activate = button("ACTIVATE DEVICE");
        activate.setOnClickListener(v -> activateEnteredKey());
        root.addView(activate, matchWrap(12, 8));

        Button whatsapp = button("MINTA ACTIVATION VIA WHATSAPP");
        whatsapp.setOnClickListener(v -> openWhatsApp());
        root.addView(whatsapp, matchWrap(0, 14));

        statusText = text(
                "Activation Key hanya berlaku untuk Device Code yang dikirim kepada admin dan tidak dapat digunakan di perangkat lain.",
                12,
                false
        );
        statusText.setTextColor(Color.rgb(151, 176, 188));
        statusText.setGravity(Gravity.CENTER);
        root.addView(statusText, matchWrap(0, 12));

        TextView footer = text("SECURE DEVICE-BOUND ACTIVATION • V14", 10, true);
        footer.setTextColor(Color.rgb(83, 124, 143));
        footer.setGravity(Gravity.CENTER);
        root.addView(footer, matchWrap(0, 0));

        return scroll;
    }

    private void copyDeviceCode() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("SEMBULUNG Device Code", deviceCode));
        }
        Toast.makeText(this, "Device Code disalin", Toast.LENGTH_SHORT).show();
    }

    private void activateEnteredKey() {
        String key = keyInput.getText().toString();
        if (!ActivationManager.activate(this, key)) {
            statusText.setText("Activation Key tidak valid untuk perangkat ini.");
            statusText.setTextColor(Color.rgb(255, 120, 120));
            return;
        }
        statusText.setText("Aktivasi berhasil. Membuka SEMBULUNG NAVIGATOR...");
        statusText.setTextColor(Color.rgb(120, 235, 165));
        Toast.makeText(this, "Aktivasi berhasil", Toast.LENGTH_SHORT).show();
        openHome();
    }

    private void openWhatsApp() {
        try {
            String message = "Halo Admin SEMBULUNG NAVIGATOR, saya ingin meminta Activation Key.\n\n" +
                    "Device Code: " + deviceCode + "\n\n" +
                    "Mohon dibuatkan Activation Key untuk perangkat ini.";
            Uri uri = Uri.parse("https://wa.me/" + ADMIN_WA + "?text=" + Uri.encode(message));
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) {
            Toast.makeText(this, "Tidak dapat membuka WhatsApp", Toast.LENGTH_LONG).show();
        }
    }

    private void openHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private LinearLayout.LayoutParams matchWrap(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(top);
        params.bottomMargin = dp(bottom);
        return params;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(Color.rgb(0, 119, 170));
        button.setPadding(dp(8), dp(11), dp(8), dp(11));
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
