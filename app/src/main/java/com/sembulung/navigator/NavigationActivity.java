package com.sembulung.navigator;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NavigationActivity extends Activity implements LocationListener {
    private static final int REQ_LOCATION = 701;
    private static final String PREFS = "sembulung_waypoints";
    private static final String KEY_WAYPOINTS = "waypoints_json";
    private static final String KEY_ACTIVE = "active_index";

    private LocationManager locationManager;
    private Location currentLocation;

    private TextView position;
    private TextView speed;
    private TextView course;
    private TextView accuracy;
    private TextView provider;
    private TextView routeStatus;
    private LinearLayout waypointList;

    private final List<Waypoint> waypoints = new ArrayList<>();
    private int activeIndex = -1;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        loadWaypoints();
        setContentView(buildUi());
        startGps();
    }

    private ScrollView buildUi() {
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(2,18,33));
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12),dp(12),dp(12),dp(20));
        scroll.addView(root,new ScrollView.LayoutParams(-1,-2));

        TextView title=text("ROUTE & WAYPOINTS",20,true);
        title.setGravity(Gravity.LEFT);
        root.addView(title);
        TextView sub=text("Rute, titik tujuan, track dan navigasi",10,false);
        sub.setGravity(Gravity.LEFT); sub.setTextColor(0xff9fb4c5);
        root.addView(sub,top(2));

        LinearLayout tabs=new LinearLayout(this);
        Button routeTab=button("ROUTE"); routeTab.setEnabled(false); tabs.addView(routeTab,half());
        Button wpTab=button("WAYPOINT"); wpTab.setOnClickListener(v->waypointList.requestFocus()); tabs.addView(wpTab,half());
        Button trackTab=button("TRACK"); trackTab.setOnClickListener(v->Toast.makeText(this,"Track mengikuti rekaman posisi GPS.",Toast.LENGTH_SHORT).show()); tabs.addView(trackTab,half());
        root.addView(tabs,top(12));

        LinearLayout metrics=new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        position=metric("POSITION\nMenunggu GPS");
        speed=metric("SOG\n0.0 kn");
        course=metric("COG\n---°");
        metrics.addView(position,metricLp()); metrics.addView(speed,metricLp()); metrics.addView(course,metricLp());
        root.addView(metrics,top(8));

        LinearLayout metrics2=new LinearLayout(this);
        accuracy=metric("AKURASI\n--- m"); provider=metric("GPS\nMencari"); routeStatus=metric("TUJUAN\nBelum dipilih");
        metrics2.addView(accuracy,metricLp()); metrics2.addView(provider,metricLp()); metrics2.addView(routeStatus,metricLp());
        root.addView(metrics2,lp());

        Button refresh=button("REFRESH GPS"); refresh.setOnClickListener(v->startGps()); root.addView(refresh,top(10));
        Button saveCurrent=button("SIMPAN POSISI"); saveCurrent.setOnClickListener(v->saveCurrentPosition()); root.addView(saveCurrent,lp());
        Button addManual=button("TAMBAH WAYPOINT"); addManual.setOnClickListener(v->showManualWaypointDialog()); root.addView(addManual,lp());
        Button openMap=button("BUKA CHART • MULAI NAVIGASI"); openMap.setOnClickListener(v->startActivity(new Intent(this,MarineMapActivity.class))); root.addView(openMap,lp());
        Button clearTarget=button("HAPUS TUJUAN AKTIF"); clearTarget.setOnClickListener(v->{activeIndex=-1;persistActive();renderWaypoints();updateRouteStatus();}); root.addView(clearTarget,lp());

        TextView listTitle=text("WAYPOINT TERSIMPAN",12,true); listTitle.setGravity(Gravity.LEFT); root.addView(listTitle,top(16));
        waypointList=new LinearLayout(this); waypointList.setOrientation(LinearLayout.VERTICAL); root.addView(waypointList,new LinearLayout.LayoutParams(-1,-2));
        renderWaypoints();

        Button back=button("KEMBALI"); back.setOnClickListener(v->finish()); root.addView(back,top(14));
        return scroll;
    }

    private void startGps() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
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
        currentLocation = l;
        position.setText(String.format(Locale.US, "LAT / LON\n%.6f, %.6f", l.getLatitude(), l.getLongitude()));
        double knots = l.hasSpeed() ? l.getSpeed() * 1.943844492 : 0.0;
        speed.setText(String.format(Locale.US, "KECEPATAN\n%.1f kn", knots));
        course.setText(l.hasBearing() ? String.format(Locale.US, "ARAH\n%.0f°", l.getBearing()) : "ARAH\n---°");
        accuracy.setText(l.hasAccuracy() ? String.format(Locale.US, "AKURASI\n%.1f m", l.getAccuracy()) : "AKURASI\n--- m");
        provider.setText("STATUS\nGPS aktif • " + l.getProvider());
        updateRouteStatus();
    }

    private void saveCurrentPosition() {
        if (currentLocation == null) {
            Toast.makeText(this, "Posisi GPS belum tersedia", Toast.LENGTH_SHORT).show();
            return;
        }
        final EditText input = new EditText(this);
        input.setHint("Nama waypoint");
        input.setText("Waypoint " + (waypoints.size() + 1));
        new AlertDialog.Builder(this)
                .setTitle("Simpan posisi saat ini")
                .setView(input)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan", (d,w) -> {
                    String name=input.getText().toString().trim();
                    if(name.isEmpty()) name="Waypoint " + (waypoints.size() + 1);
                    waypoints.add(new Waypoint(name,currentLocation.getLatitude(),currentLocation.getLongitude()));
                    saveWaypoints();
                    renderWaypoints();
                    Toast.makeText(this,"Waypoint disimpan",Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showManualWaypointDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20),dp(6),dp(20),0);

        EditText name = field("Nama waypoint");
        EditText lat = field("Latitude, contoh -8.123456");
        EditText lon = field("Longitude, contoh 114.123456");
        lat.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        lon.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);

        box.addView(name);
        box.addView(lat);
        box.addView(lon);

        new AlertDialog.Builder(this)
                .setTitle("Tambah waypoint manual")
                .setView(box)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan", (d,w) -> {
                    try {
                        double la=Double.parseDouble(lat.getText().toString().trim());
                        double lo=Double.parseDouble(lon.getText().toString().trim());
                        if(la < -90 || la > 90 || lo < -180 || lo > 180) throw new IllegalArgumentException();
                        String n=name.getText().toString().trim();
                        if(n.isEmpty()) n="Waypoint " + (waypoints.size() + 1);
                        waypoints.add(new Waypoint(n,la,lo));
                        saveWaypoints();
                        renderWaypoints();
                    } catch(Exception e) {
                        Toast.makeText(this,"Koordinat tidak valid",Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void renderWaypoints() {
        if(waypointList == null) return;
        waypointList.removeAllViews();

        if(waypoints.isEmpty()) {
            TextView empty=text("Belum ada waypoint.",14,false);
            empty.setPadding(0,dp(10),0,dp(12));
            waypointList.addView(empty);
            return;
        }

        for(int i=0;i<waypoints.size();i++) {
            final int index=i;
            Waypoint wp=waypoints.get(i);

            LinearLayout card=new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12),dp(12),dp(12),dp(12));
            card.setBackgroundColor(Color.rgb(7,57,94));

            String active=index==activeIndex ? " • TUJUAN AKTIF" : "";
            TextView info=text(wp.name + active + "\n" +
                    String.format(Locale.US,"%.6f, %.6f",wp.lat,wp.lon),15,index==activeIndex);
            card.addView(info);

            LinearLayout actions=new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);

            Button target=button(index==activeIndex ? "AKTIF" : "JADIKAN TUJUAN");
            target.setEnabled(index!=activeIndex);
            target.setOnClickListener(v -> {
                activeIndex=index;
                persistActive();
                renderWaypoints();
                updateRouteStatus();
            });

            Button delete=button("HAPUS");
            delete.setOnClickListener(v -> confirmDelete(index));

            LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,-2,1f);
            a.setMargins(0,dp(8),dp(4),0);
            actions.addView(target,a);
            LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(0,-2,1f);
            b.setMargins(dp(4),dp(8),0,0);
            actions.addView(delete,b);

            card.addView(actions);
            waypointList.addView(card, lp());
        }
    }

    private void confirmDelete(int index) {
        new AlertDialog.Builder(this)
                .setTitle("Hapus waypoint?")
                .setMessage(waypoints.get(index).name)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Hapus", (d,w) -> {
                    waypoints.remove(index);
                    if(activeIndex==index) activeIndex=-1;
                    else if(activeIndex>index) activeIndex--;
                    saveWaypoints();
                    persistActive();
                    renderWaypoints();
                    updateRouteStatus();
                })
                .show();
    }

    private void updateRouteStatus() {
        if(routeStatus == null) return;
        if(activeIndex < 0 || activeIndex >= waypoints.size()) {
            routeStatus.setText("TUJUAN AKTIF\nBelum dipilih");
            return;
        }

        Waypoint target=waypoints.get(activeIndex);
        if(currentLocation == null) {
            routeStatus.setText("TUJUAN: " + target.name + "\nMenunggu posisi GPS...");
            return;
        }

        float[] r=new float[2];
        Location.distanceBetween(currentLocation.getLatitude(),currentLocation.getLongitude(),target.lat,target.lon,r);
        double nm=r[0]/1852.0;
        double bearing=(r[1]+360.0)%360.0;
        double knots=currentLocation.hasSpeed() ? currentLocation.getSpeed()*1.943844492 : 0.0;
        String eta=knots >= 0.5 ? formatEta(nm/knots) : "ETA --";

        routeStatus.setText(String.format(Locale.US,
                "TUJUAN: %s\nJarak %.2f NM • Bearing %.0f° %s\n%s",
                target.name,nm,bearing,cardinal(bearing),eta));
    }

    private String formatEta(double hours) {
        if(Double.isNaN(hours) || Double.isInfinite(hours) || hours < 0) return "ETA --";
        long minutes=Math.round(hours*60.0);
        return String.format(Locale.US,"ETA %02d:%02d",minutes/60,minutes%60);
    }

    private String cardinal(double bearing) {
        String[] dirs={"N","NE","E","SE","S","SW","W","NW"};
        return dirs[((int)Math.round(bearing/45.0))%8];
    }

    private void loadWaypoints() {
        SharedPreferences p=getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        activeIndex=p.getInt(KEY_ACTIVE,-1);
        String raw=p.getString(KEY_WAYPOINTS,"[]");
        try {
            JSONArray a=new JSONArray(raw);
            for(int i=0;i<a.length();i++) {
                JSONObject o=a.getJSONObject(i);
                waypoints.add(new Waypoint(o.optString("name","Waypoint "+(i+1)),o.getDouble("lat"),o.getDouble("lon")));
            }
        } catch(Exception ignored) {
            waypoints.clear();
            activeIndex=-1;
        }
        if(activeIndex>=waypoints.size()) activeIndex=-1;
    }

    private void saveWaypoints() {
        JSONArray a=new JSONArray();
        try {
            for(Waypoint wp:waypoints) {
                JSONObject o=new JSONObject();
                o.put("name",wp.name);
                o.put("lat",wp.lat);
                o.put("lon",wp.lon);
                a.put(o);
            }
        } catch(Exception ignored) {}
        getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
                .putString(KEY_WAYPOINTS,a.toString())
                .putInt(KEY_ACTIVE,activeIndex)
                .apply();
    }

    private void persistActive() {
        getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putInt(KEY_ACTIVE,activeIndex).apply();
    }

    @Override public void onProviderEnabled(String p) {}

    @Override public void onProviderDisabled(String p) {
        provider.setText("STATUS\nGPS dinonaktifkan");
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==REQ_LOCATION && grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED) {
            startGps();
        } else if(requestCode==REQ_LOCATION) {
            provider.setText("STATUS\nIzin lokasi diperlukan");
        }
    }

    @Override protected void onPause() {
        super.onPause();
        try {
            if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) {
                locationManager.removeUpdates(this);
            }
        } catch(Exception ignored) {}
    }

    @Override protected void onResume() {
        super.onResume();
        if(locationManager!=null && provider!=null) startGps();
    }

    private TextView metric(String s){TextView t=text(s,11,true);t.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);t.setPadding(dp(9),dp(8),dp(9),dp(8));t.setBackgroundColor(Color.rgb(7,43,70));return t;}
    private LinearLayout.LayoutParams metricLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(58),1f);p.setMargins(dp(3),0,dp(3),0);return p;}

    private TextView text(String s,int sp,boolean bold){
        TextView t=new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(sp);
        t.setGravity(Gravity.CENTER);
        if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(11);b.setTextColor(Color.WHITE);b.setMinHeight(0);b.setMinWidth(0);b.setPadding(dp(8),0,dp(8),0);return b;}

    private EditText field(String hint){
        EditText e=new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(0xFF9CA3AF);
        e.setTextColor(Color.BLACK);
        e.setSingleLine(true);
        return e;
    }

    private LinearLayout.LayoutParams lp(){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,dp(8),0,0);
        return p;
    }

    private LinearLayout.LayoutParams top(int margin){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,dp(margin),0,0);
        return p;
    }

    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }

    private static class Waypoint {
        final String name;
        final double lat;
        final double lon;
        Waypoint(String name,double lat,double lon){
            this.name=name;
            this.lat=lat;
            this.lon=lon;
        }
    }
}
