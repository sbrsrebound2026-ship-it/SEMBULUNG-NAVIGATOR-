package com.sembulung.navigator;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.sembulung.navigator.activation.ActivationActivity;
import com.sembulung.navigator.activation.ActivationManager;

public class SembulungSplashActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(3,27,61));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(32),dp(32),dp(32),dp(32));
        root.setBackgroundColor(Color.rgb(3,27,61));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.splash_icon_sembulung);
        root.addView(icon, new LinearLayout.LayoutParams(dp(160),dp(160)));

        TextView title = text("SEMBULUNG NAVIGATOR", 25, true);
        title.setPadding(0,dp(18),0,0);
        root.addView(title);

        TextView tag = text("Navigasi Laut Presisi", 15, false);
        tag.setPadding(0,dp(8),0,0);
        root.addView(tag);

        TextView credit = text("BY WONG MBRAYU", 12, false);
        credit.setPadding(0,dp(22),0,0);
        root.addView(credit);

        setContentView(root);
        new Handler(Looper.getMainLooper()).postDelayed(this::next, 850L);
    }

    private void next() {
        Class<?> target = ActivationManager.isActivated(this) ? HomeActivity.class : ActivationActivity.class;
        startActivity(new Intent(this, target));
        finish();
    }

    private TextView text(String s,int sp,boolean bold){
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
