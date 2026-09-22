package com.sembulung.navigator;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public class HomeActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28),dp(28),dp(28),dp(28));
        root.setBackgroundColor(Color.rgb(3,27,61));
        root.addView(label("SEMBULUNG NAVIGATOR", 26, true));
        TextView tag = label("Navigasi Laut Presisi", 16, false);
        tag.setPadding(0,dp(8),0,dp(28));
        root.addView(tag);
        root.addView(label("Aktivasi perangkat VALID", 18, true));
        TextView core = label("Build shell V5 aktif. Modul navigasi inti bersih siap ditautkan pada tahap integrasi.", 14, false);
        core.setPadding(0,dp(20),0,0);
        root.addView(core);
        setContentView(root);
    }

    private TextView label(String s,int sp,boolean bold){
        TextView t=new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(sp);
        t.setGravity(Gravity.CENTER);
        if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }
    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
}
