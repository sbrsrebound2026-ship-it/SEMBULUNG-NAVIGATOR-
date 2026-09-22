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
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.sembulung.navigator.HomeActivity;

public class ActivationActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (ActivationManager.isActivated(this)) { openHome(); return; }
        setContentView(buildUi());
    }

    private View buildUi() {
        int pad = dp(24);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(3,27,61));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(pad,pad,pad,pad);
        scroll.addView(root, new ScrollView.LayoutParams(-1,-2));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));
        root.addView(icon, new LinearLayout.LayoutParams(dp(112),dp(112)));

        TextView title = text("SEMBULUNG NAVIGATOR", 24, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0,dp(18),0,dp(4));
        root.addView(title);

        TextView by = text("BY WONG MBRAYU", 13, false);
        by.setGravity(Gravity.CENTER);
        root.addView(by);

        TextView sub = text("Aktivasi 1 perangkat = 1 key", 15, false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0,dp(18),0,dp(18));
        root.addView(sub);

        final String device = DeviceIdentity.formattedDeviceCode(this);
        TextView code = text(device, 20, true);
        code.setGravity(Gravity.CENTER);
        code.setTextIsSelectable(true);
        root.addView(code);

        Button copy = button("SALIN DEVICE CODE");
        copy.setOnClickListener(v -> {
            ClipboardManager cb=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText("Sembulung Device Code", device));
            Toast.makeText(this,"Device Code disalin",Toast.LENGTH_SHORT).show();
        });
        root.addView(copy, lp());

        Button whatsapp = button("HUBUNGI ADMIN VIA WHATSAPP");
        whatsapp.setOnClickListener(v -> {
            String message = "Halo Admin SEMBULUNG NAVIGATOR.\n" +
                    "Saya ingin meminta Activation Key.\n" +
                    "Device Code: " + device + "\n" +
                    "Mohon dibuatkan key untuk perangkat ini.";
            String url = "https://wa.me/6281234406456?text=" + Uri.encode(message);
            openExternal(url);
        });
        root.addView(whatsapp, lp());

        Button facebook = button("PROFIL FACEBOOK PENGEMBANG");
        facebook.setOnClickListener(v ->
                openExternal("https://www.facebook.com/share/1DZNvHrrQ1/"));
        root.addView(facebook, lp());

        EditText input = new EditText(this);
        input.setHint("Masukkan Activation Key");
        input.setHintTextColor(0xFF9CA3AF);
        input.setTextColor(Color.WHITE);
        input.setSingleLine(false);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setPadding(dp(12),dp(12),dp(12),dp(12));
        root.addView(input, lp());

        Button activate = button("AKTIFKAN");
        activate.setOnClickListener(v -> {
            if (ActivationManager.activate(this, input.getText().toString())) {
                Toast.makeText(this,"Aktivasi berhasil",Toast.LENGTH_SHORT).show();
                openHome();
            } else {
                Toast.makeText(this,"Key tidak valid untuk perangkat ini",Toast.LENGTH_LONG).show();
            }
        });
        root.addView(activate, lp());

        TextView note=text("Kirim Device Code kepada administrator untuk mendapatkan key khusus perangkat ini.",13,false);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0,dp(16),0,0);
        root.addView(note);
        return scroll;
    }

    private TextView text(String s,int sp,boolean bold){
        TextView t=new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(sp);
        if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }
    private Button button(String s){ Button b=new Button(this); b.setText(s); b.setAllCaps(false); return b; }
    private LinearLayout.LayoutParams lp(){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,dp(12),0,0); return p; }
    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }

    private void openExternal(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Tautan tidak dapat dibuka", Toast.LENGTH_LONG).show();
        }
    }

    private void openHome(){ startActivity(new Intent(this, HomeActivity.class)); finish(); }
}
