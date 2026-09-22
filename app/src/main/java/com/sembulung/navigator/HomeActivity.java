package com.sembulung.navigator;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.sembulung.navigator.activation.DeviceIdentity;

public class HomeActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(3,27,61));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(22),dp(28),dp(22),dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1,-2));

        TextView title = label("SEMBULUNG NAVIGATOR", 25, true);
        root.addView(title);

        TextView tag = label("Navigasi Laut Presisi", 15, false);
        tag.setPadding(0,dp(6),0,dp(8));
        root.addView(tag);

        TextView status = label("PERANGKAT AKTIF", 14, true);
        status.setTextColor(Color.rgb(37,217,248));
        status.setPadding(0,dp(8),0,dp(22));
        root.addView(status);

        Button nav = button("NAVIGASI GPS");
        nav.setOnClickListener(v -> startActivity(new Intent(this, NavigationActivity.class)));
        root.addView(nav, lp());

        Button maps = button("PETA OFFLINE");
        maps.setOnClickListener(v -> startActivity(new Intent(this, OfflineMapActivity.class)));
        root.addView(maps, lp());

        Button waypoint = button("WAYPOINT & RUTE");
        waypoint.setOnClickListener(v -> startActivity(new Intent(this, NavigationActivity.class)));
        root.addView(waypoint, lp());

        Button sonar = button("SONAR / NMEA");
        sonar.setOnClickListener(v -> startActivity(new Intent(this, NmeaActivity.class)));
        root.addView(sonar, lp());

        TextView code = label("Device Code\n" + DeviceIdentity.formattedDeviceCode(this), 13, false);
        code.setPadding(0,dp(28),0,0);
        root.addView(code);

        TextView version = label("SEMBULUNG NAVIGATOR • V10 DEVELOPMENT", 11, false);
        version.setPadding(0,dp(14),0,0);
        root.addView(version);

        setContentView(scroll);
    }

    private void info(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show();
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

    private Button button(String s){
        Button b=new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams lp(){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,dp(9),0,0);
        return p;
    }

    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
}
