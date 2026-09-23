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
        root.setPadding(dp(22),dp(26),dp(22),dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1,-2));

        TextView title = label("SEMBULUNG NAVIGATOR", 25, true);
        root.addView(title);

        TextView tag = label("Navigasi Laut Presisi • BY WONG MBRAYU", 14, false);
        tag.setPadding(0,dp(6),0,dp(6));
        root.addView(tag);

        TextView status = label("STANDALONE MARINE NAVIGATION • PERANGKAT AKTIF", 12, true);
        status.setTextColor(Color.rgb(37,217,248));
        status.setPadding(0,dp(6),0,dp(18));
        root.addView(status);

        Button maps = button("MARINE MAP + SONAR CHART");
        maps.setOnClickListener(v -> startActivity(new Intent(this, MarineMapActivity.class)));
        root.addView(maps, lp());

        Button nav = button("NAVIGASI GPS • WAYPOINT • RUTE");
        nav.setOnClickListener(v -> startActivity(new Intent(this, NavigationActivity.class)));
        root.addView(nav, lp());

        Button search = button("CARI KOORDINAT");
        search.setOnClickListener(v -> startActivity(new Intent(this, SearchCoordinateActivity.class)));
        root.addView(search, lp());

        Button offline = button("PETA OFFLINE • RASTER MBTILES");
        offline.setOnClickListener(v -> startActivity(new Intent(this, OfflineMapActivity.class)));
        root.addView(offline, lp());

        Button vectorOffline = button("UNIFIED MARINE MAP • GPS • AIS • SONAR");
        vectorOffline.setOnClickListener(v -> startActivity(new Intent(this, VectorOfflineMapActivity.class)));
        root.addView(vectorOffline, lp());

        Button sonarSurvey = button("SONAR SURVEY CENTER");
        sonarSurvey.setOnClickListener(v -> startActivity(new Intent(this, SonarSurveyActivity.class)));
        root.addView(sonarSurvey, lp());

        Button nmea = button("NMEA • GPS / DEPTH INPUT");
        nmea.setOnClickListener(v -> startActivity(new Intent(this, NmeaActivity.class)));
        root.addView(nmea, lp());

        Button ais = button("AIS • CPA / TCPA");
        ais.setOnClickListener(v -> startActivity(new Intent(this, AisActivity.class)));
        root.addView(ais, lp());

        Button settings = button("PENGATURAN");
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        root.addView(settings, lp());

        TextView code = label("Device Code\n" + DeviceIdentity.formattedDeviceCode(this), 13, false);
        code.setTextIsSelectable(true);
        code.setPadding(0,dp(24),0,0);
        root.addView(code);

        TextView version = label("V27 FINAL • SEMBULUNG NAVIGATOR 3.0", 11, false);
        version.setPadding(0,dp(12),0,0);
        root.addView(version);

        setContentView(scroll);
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
        p.setMargins(0,dp(8),0,0);
        return p;
    }

    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
}
