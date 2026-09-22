package com.sembulung.navigator;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public class NavigationActivity extends Activity implements LocationListener {
    private static final int REQ_LOCATION = 701;
    private LocationManager locationManager;
    private TextView position;
    private TextView speed;
    private TextView course;
    private TextView accuracy;
    private TextView provider;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(22),dp(26),dp(22),dp(26));
        root.setBackgroundColor(Color.rgb(3,27,61));

        TextView title = text("NAVIGASI GPS", 24, true);
        root.addView(title);

        TextView sub = text("Data posisi perangkat secara langsung", 14, false);
        sub.setPadding(0,dp(6),0,dp(22));
        root.addView(sub);

        position = metric("LAT / LON\nMenunggu GPS...");
        speed = metric("KECEPATAN\n0.0 kn");
        course = metric("ARAH\n---°");
        accuracy = metric("AKURASI\n--- m");
        provider = metric("STATUS\nMenghubungkan GPS...");

        root.addView(position, lp());
        root.addView(speed, lp());
        root.addView(course, lp());
        root.addView(accuracy, lp());
        root.addView(provider, lp());

        Button refresh = new Button(this);
        refresh.setText("MULAI / SEGARKAN GPS");
        refresh.setAllCaps(false);
        refresh.setOnClickListener(v -> startGps());
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);
        bp.setMargins(0,dp(18),0,0);
        root.addView(refresh,bp);

        Button back = new Button(this);
        back.setText("KEMBALI");
        back.setAllCaps(false);
        back.setOnClickListener(v -> finish());
        root.addView(back,bp);

        setContentView(root);
        startGps();
    }

    private void startGps() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        boolean gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (!gps) {
            provider.setText("STATUS\nGPS perangkat belum aktif");
            return;
        }
        provider.setText("STATUS\nMencari satelit...");
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0.5f, this);
        Location last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (last != null) update(last);
    }

    @Override public void onLocationChanged(Location l) {
        update(l);
    }

    private void update(Location l) {
        position.setText(String.format(Locale.US, "LAT / LON\n%.6f, %.6f", l.getLatitude(), l.getLongitude()));
        double knots = l.hasSpeed() ? l.getSpeed() * 1.943844492 : 0.0;
        speed.setText(String.format(Locale.US, "KECEPATAN\n%.1f kn", knots));
        course.setText(l.hasBearing() ? String.format(Locale.US, "ARAH\n%.0f°", l.getBearing()) : "ARAH\n---°");
        accuracy.setText(l.hasAccuracy() ? String.format(Locale.US, "AKURASI\n%.1f m", l.getAccuracy()) : "AKURASI\n--- m");
        provider.setText("STATUS\nGPS aktif • " + l.getProvider());
    }

    @Override public void onProviderEnabled(String p) {}
    @Override public void onProviderDisabled(String p) {
        provider.setText("STATUS\nGPS dinonaktifkan");
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startGps();
        } else {
            provider.setText("STATUS\nIzin lokasi diperlukan");
        }
    }

    @Override protected void onPause() {
        super.onPause();
        try {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.removeUpdates(this);
            }
        } catch (Exception ignored) {}
    }

    @Override protected void onResume() {
        super.onResume();
        if (locationManager != null) startGps();
    }

    private TextView metric(String s){
        TextView t=text(s,18,true);
        t.setPadding(dp(12),dp(15),dp(12),dp(15));
        t.setBackgroundColor(Color.rgb(7,57,94));
        return t;
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

    private LinearLayout.LayoutParams lp(){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,dp(8),0,0);
        return p;
    }

    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
}
