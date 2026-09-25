package com.sembulung.navigator;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OfflineMapActivity extends Activity implements LocationListener {
    private static final int REQ_LOCATION = 901;
    private static final int REQ_MBTILES = 902;
    private static final String WP_PREFS = "sembulung_waypoints";
    private static final String KEY_WAYPOINTS = "waypoints_json";
    private static final String KEY_ACTIVE = "active_index";
    private static final long NMEA_FRESH_MS = 5000L;

    private LocationManager locationManager;
    private Location currentLocation;
    private SQLiteDatabase mapDb;
    private TextView status;
    private TextView sourceStatus;
    private MbTilesView mapView;

    private final Handler liveHandler = new Handler(Looper.getMainLooper());
    private NmeaDataStore.Snapshot nmeaSnapshot;
    private int sourceMode = 0; // 0=AUTO, 1=GPS HP, 2=NMEA
    private final Runnable liveRefresh = new Runnable() {
        @Override public void run() {
            refreshLiveData();
            liveHandler.postDelayed(this, 500L);
        }
    };

    private int minZoom = 0;
    private int maxZoom = 18;
    private int zoom = 12;
    private String tileScheme = "tms";
    private final List<Integer> availableZooms = new ArrayList<>();

    private final List<Waypoint> waypoints = new ArrayList<>();
    private int activeIndex = -1;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        loadWaypoints();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(2,18,33));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(14),dp(8),dp(14),dp(6));
        header.setBackgroundColor(Color.rgb(3,27,61));

        TextView title = text("OFFLINE CHARTS",18,true);
        title.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        header.addView(title,new LinearLayout.LayoutParams(-1,dp(27)));

        TextView subtitle = text("MBTiles • GPS • NMEA • WAYPOINTS",9,false);
        subtitle.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        subtitle.setTextColor(0xff9fb4c5);
        header.addView(subtitle,new LinearLayout.LayoutParams(-1,dp(18)));
        root.addView(header,new LinearLayout.LayoutParams(-1,dp(59)));

        status = text("Belum ada chart offline",10,true);
        status.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        status.setPadding(dp(12),0,dp(12),0);
        status.setBackground(panelBg());
        root.addView(status,new LinearLayout.LayoutParams(-1,dp(34)));

        sourceStatus = text("AUTO • menunggu posisi",9,false);
        sourceStatus.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        sourceStatus.setTextColor(0xffb9c9d6);
        sourceStatus.setPadding(dp(12),0,dp(12),0);
        root.addView(sourceStatus,new LinearLayout.LayoutParams(-1,dp(28)));

        mapView = new MbTilesView(this);
        LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(-1,0,1f);
        mapParams.setMargins(0,dp(3),0,0);
        root.addView(mapView,mapParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(dp(7),dp(5),dp(7),dp(5));
        actions.setBackgroundColor(Color.rgb(3,27,61));

        Button source = button("AUTO");
        source.setOnClickListener(v -> {
            sourceMode=(sourceMode+1)%3;
            setSourceMode(sourceMode);
        });
        actions.addView(source,actionLp());

        Button importMap = button("IMPORT");
        importMap.setOnClickListener(v -> chooseMbTiles());
        actions.addView(importMap,actionLp());

        Button gps = button("POSITION");
        gps.setOnClickListener(v -> recenterActive());
        actions.addView(gps,actionLp());

        Button minus = button("−");
        minus.setTextSize(18);
        minus.setOnClickListener(v -> mapView.stepZoom(-1));
        actions.addView(minus,zoomLp());

        Button plus = button("+");
        plus.setTextSize(18);
        plus.setOnClickListener(v -> mapView.stepZoom(1));
        actions.addView(plus,zoomLp());

        Button close = button("CLOSE");
        close.setOnClickListener(v -> finish());
        actions.addView(close,actionLp());

        root.addView(actions,new LinearLayout.LayoutParams(-1,dp(52)));

        setContentView(root);

        openLocalMapIfPresent();
        startGps();
        refreshLiveData();
    }


    private void chooseMbTiles() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_MBTILES);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode != REQ_MBTILES || resultCode != RESULT_OK || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        File out = localMapFile();

        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(out,false)) {
            if(in == null) throw new IllegalStateException("File tidak dapat dibuka");
            byte[] buffer = new byte[1024 * 256];
            int n;
            long total = 0;
            while((n = in.read(buffer)) > 0) {
                fos.write(buffer,0,n);
                total += n;
            }
            fos.flush();
            openMap(out);
            Toast.makeText(this,String.format(Locale.US,"Peta tersimpan: %.1f MB",total/1048576.0),Toast.LENGTH_LONG).show();
        } catch(Exception e) {
            Toast.makeText(this,"Gagal mengimpor MBTiles: " + e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }

    private File localMapFile() {
        return new File(getFilesDir(),"sembulung_offline.mbtiles");
    }

    private void openLocalMapIfPresent() {
        File f = localMapFile();
        if(f.exists() && f.length() > 0) openMap(f);
    }

    private void openMap(File f) {
        closeDb();
        try {
            mapDb = SQLiteDatabase.openDatabase(f.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);

            Cursor c = mapDb.rawQuery("SELECT 1 FROM tiles LIMIT 1",null);
            boolean hasTiles = c.moveToFirst();
            c.close();
            if(!hasTiles) throw new IllegalArgumentException("Tabel tiles kosong");

            loadAvailableZooms();
            if(availableZooms.isEmpty()) throw new IllegalArgumentException("Tidak ada zoom tile yang valid");

            minZoom = availableZooms.get(0);
            maxZoom = availableZooms.get(availableZooms.size()-1);
            zoom = nearestAvailableZoom(zoom);

            String declaredScheme = readMetadata("scheme","").trim().toLowerCase(Locale.US);
            if("xyz".equals(declaredScheme) || "tms".equals(declaredScheme)) {
                tileScheme = declaredScheme;
            } else {
                tileScheme = detectTileScheme();
            }

            String format = readMetadata("format","").trim().toLowerCase(Locale.US);
            if(!format.isEmpty() && !("png".equals(format) || "jpg".equals(format) || "jpeg".equals(format) || "webp".equals(format))) {
                throw new IllegalArgumentException("Format tile " + format + " belum didukung reader raster");
            }

            status.setText("Peta aktif • zoom " + zoom + " • tersedia " + availableZoomsText()
                    + " • " + tileScheme.toUpperCase(Locale.US));
            mapView.invalidate();
        } catch(Exception e) {
            closeDb();
            availableZooms.clear();
            status.setText("File bukan MBTiles raster yang didukung");
            Toast.makeText(this,"MBTiles tidak valid: " + e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }

    private String readMetadata(String key,String fallback) {
        if(mapDb == null) return fallback;
        try {
            Cursor c = mapDb.rawQuery("SELECT value FROM metadata WHERE name=? LIMIT 1",new String[]{key});
            String value = c.moveToFirst() ? c.getString(0) : fallback;
            c.close();
            return value == null ? fallback : value;
        } catch(Exception e) {
            return fallback;
        }
    }

    private int readMetadataInt(String key,int fallback) {
        try {
            return Integer.parseInt(readMetadata(key,String.valueOf(fallback)).trim());
        } catch(Exception e) {
            return fallback;
        }
    }

    private void loadAvailableZooms() {
        availableZooms.clear();
        Cursor c = null;
        try {
            c = mapDb.rawQuery("SELECT DISTINCT zoom_level FROM tiles ORDER BY zoom_level",null);
            while(c.moveToNext()) availableZooms.add(c.getInt(0));
        } finally {
            if(c != null) c.close();
        }
    }

    private int nearestAvailableZoom(int requested) {
        if(availableZooms.isEmpty()) return requested;
        int best = availableZooms.get(0);
        int bestDistance = Math.abs(best-requested);
        for(int z : availableZooms) {
            int d = Math.abs(z-requested);
            if(d < bestDistance) {
                best = z;
                bestDistance = d;
            }
        }
        return best;
    }

    private int adjacentAvailableZoom(int current,int direction) {
        if(availableZooms.isEmpty()) return current;
        if(direction > 0) {
            for(int z : availableZooms) if(z > current) return z;
            return availableZooms.get(availableZooms.size()-1);
        } else {
            for(int i=availableZooms.size()-1;i>=0;i--) {
                int z=availableZooms.get(i);
                if(z < current) return z;
            }
            return availableZooms.get(0);
        }
    }

    private String availableZoomsText() {
        if(availableZooms.isEmpty()) return "-";
        StringBuilder b = new StringBuilder();
        for(int i=0;i<availableZooms.size();i++) {
            if(i>0) b.append("/");
            b.append(availableZooms.get(i));
        }
        return b.toString();
    }

    private String detectTileScheme() {
        String bounds = readMetadata("bounds","");
        if(bounds.trim().isEmpty()) return "tms";
        try {
            String[] p=bounds.split(",");
            if(p.length<4) return "tms";
            double west=Double.parseDouble(p[0].trim());
            double south=Double.parseDouble(p[1].trim());
            double east=Double.parseDouble(p[2].trim());
            double north=Double.parseDouble(p[3].trim());
            double[] lats={south+(north-south)*0.25,(south+north)/2.0,south+(north-south)*0.75};
            double[] lons={west+(east-west)*0.25,(west+east)/2.0,west+(east-west)*0.75};
            int tmsScore=0, xyzScore=0;

            for(int zi=availableZooms.size()-1;zi>=0 && zi>=availableZooms.size()-3;zi--) {
                int z=availableZooms.get(zi);
                int n=1<<z;
                for(double lat:lats) for(double lon:lons) {
                    int x=tileX(lon,z);
                    int y=tileY(lat,z);
                    if(tileExists(z,x,n-1-y)) tmsScore++;
                    if(tileExists(z,x,y)) xyzScore++;
                }
            }
            if(xyzScore>tmsScore) return "xyz";
        } catch(Exception ignored) {}
        return "tms";
    }

    private int tileX(double lon,int z) {
        int n=1<<z;
        int x=(int)Math.floor((lon+180.0)/360.0*n);
        return Math.max(0,Math.min(n-1,x));
    }

    private int tileY(double lat,int z) {
        lat=Math.max(-85.05112878,Math.min(85.05112878,lat));
        double r=Math.toRadians(lat);
        int n=1<<z;
        double mercator = Math.log(Math.tan(r) + (1.0 / Math.cos(r)));
        int y=(int)Math.floor((1.0-mercator/Math.PI)/2.0*n);
        return Math.max(0,Math.min(n-1,y));
    }

    private boolean tileExists(int z,int x,int row) {
        Cursor c=null;
        try {
            c=mapDb.rawQuery(
                    "SELECT 1 FROM tiles WHERE zoom_level=? AND tile_column=? AND tile_row=? LIMIT 1",
                    new String[]{String.valueOf(z),String.valueOf(x),String.valueOf(row)});
            return c.moveToFirst();
        } catch(Exception e) {
            return false;
        } finally {
            if(c!=null)c.close();
        }
    }

    private void startGps() {
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);
            return;
        }
        if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            status.setText("GPS belum aktif • peta offline tetap dapat digunakan");
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000L,0.5f,this);
        Location last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if(last != null) onLocationChanged(last);
    }

    private void setSourceMode(int mode) {
        sourceMode = mode;
        refreshLiveData();
        recenterActive();
    }

    private void recenterActive() {
        Double lat = activeLat();
        Double lon = activeLon();
        if(lat == null || lon == null) {
            Toast.makeText(this,"Posisi aktif belum tersedia",Toast.LENGTH_SHORT).show();
            if(sourceMode != 2) startGps();
            return;
        }
        mapView.centerOn(lat,lon);
    }

    private void refreshLiveData() {
        nmeaSnapshot = NmeaDataStore.read(this);
        boolean nmeaFresh = nmeaSnapshot != null && nmeaSnapshot.positionFresh(NMEA_FRESH_MS);
        String mode = sourceMode == 0 ? "AUTO" : (sourceMode == 1 ? "GPS HP" : "NMEA");
        String active = useNmeaPosition() ? "NMEA" : (currentLocation != null ? "GPS HP" : "BELUM ADA");
        String depthText = "";
        if(nmeaSnapshot != null && nmeaSnapshot.depthFresh(NMEA_FRESH_MS)) {
            depthText = String.format(Locale.US," • depth %.1f m",nmeaSnapshot.depth);
        }
        sourceStatus.setText("SUMBER: " + mode + " → " + active + (nmeaFresh ? " • NMEA fresh" : " • NMEA stale") + depthText);

        if(!mapView.hasInitialCenter) {
            Double lat = activeLat();
            Double lon = activeLon();
            if(lat != null && lon != null) {
                mapView.centerOn(lat,lon);
                mapView.hasInitialCenter = true;
            }
        }
        mapView.invalidate();
    }

    private boolean useNmeaPosition() {
        boolean fresh = nmeaSnapshot != null && nmeaSnapshot.positionFresh(NMEA_FRESH_MS);
        if(sourceMode == 2) return fresh;
        if(sourceMode == 1) return false;
        return fresh;
    }

    private Double activeLat() {
        if(useNmeaPosition()) return nmeaSnapshot.lat;
        return currentLocation == null ? null : currentLocation.getLatitude();
    }

    private Double activeLon() {
        if(useNmeaPosition()) return nmeaSnapshot.lon;
        return currentLocation == null ? null : currentLocation.getLongitude();
    }

    private Double activeHeading() {
        if(useNmeaPosition() && nmeaSnapshot != null && nmeaSnapshot.headingFresh(NMEA_FRESH_MS)) {
            return nmeaSnapshot.heading;
        }
        if(currentLocation != null && currentLocation.hasBearing()) return (double)currentLocation.getBearing();
        return null;
    }

    @Override public void onLocationChanged(Location location) {
        currentLocation = location;
        if(!mapView.hasInitialCenter && !useNmeaPosition()) {
            mapView.centerOn(location.getLatitude(),location.getLongitude());
            mapView.hasInitialCenter = true;
        }
        refreshLiveData();
    }

    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) {
        status.setText("GPS dinonaktifkan • peta offline tetap tersedia");
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode == REQ_LOCATION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startGps();
        }
    }

    private void loadWaypoints() {
        SharedPreferences p = getSharedPreferences(WP_PREFS,Context.MODE_PRIVATE);
        activeIndex = p.getInt(KEY_ACTIVE,-1);
        String raw = p.getString(KEY_WAYPOINTS,"[]");
        try {
            JSONArray a = new JSONArray(raw);
            for(int i=0;i<a.length();i++) {
                JSONObject o = a.getJSONObject(i);
                waypoints.add(new Waypoint(o.optString("name","WP "+(i+1)),o.getDouble("lat"),o.getDouble("lon")));
            }
        } catch(Exception ignored) {
            waypoints.clear();
            activeIndex = -1;
        }
        if(activeIndex >= waypoints.size()) activeIndex = -1;
    }

    @Override protected void onResume() {
        super.onResume();
        if(locationManager != null && mapView != null) startGps();
        liveHandler.removeCallbacks(liveRefresh);
        liveHandler.post(liveRefresh);
    }

    @Override protected void onPause() {
        super.onPause();
        liveHandler.removeCallbacks(liveRefresh);
        try {
            if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.removeUpdates(this);
            }
        } catch(Exception ignored) {}
    }

    @Override protected void onDestroy() {
        liveHandler.removeCallbacks(liveRefresh);
        closeDb();
        super.onDestroy();
    }

    private void closeDb() {
        if(mapDb != null) {
            try { mapDb.close(); } catch(Exception ignored) {}
            mapDb = null;
        }
    }

    private TextView text(String s,int sp,boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(sp);
        t.setGravity(Gravity.CENTER);
        if(bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(10);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(4),0,dp(4),0);
        b.setGravity(Gravity.CENTER);
        b.setBackground(panelBg());
        return b;
    }

    private GradientDrawable panelBg() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0xdd0a2136);
        d.setCornerRadius(dp(11));
        d.setStroke(dp(1),0x4457b9dd);
        return d;
    }

    private LinearLayout.LayoutParams actionLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,-1,1f);
        p.setMargins(dp(2),0,dp(2),0);
        return p;
    }

    private LinearLayout.LayoutParams zoomLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(44),-1);
        p.setMargins(dp(2),0,dp(2),0);
        return p;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

}
