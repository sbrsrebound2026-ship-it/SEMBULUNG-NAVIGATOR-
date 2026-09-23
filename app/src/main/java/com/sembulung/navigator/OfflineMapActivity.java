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
        root.setBackgroundColor(Color.rgb(3,27,61));

        TextView title = text("PETA OFFLINE", 22, true);
        title.setPadding(dp(12),dp(14),dp(12),dp(4));
        root.addView(title);

        TextView subtitle = text("MBTiles • GPS HP / NMEA • Waypoint • Rute aktif", 13, false);
        subtitle.setPadding(dp(12),0,dp(12),dp(8));
        root.addView(subtitle);

        status = text("Belum ada peta MBTiles. Tekan IMPOR PETA.", 12, false);
        status.setPadding(dp(12),dp(6),dp(12),dp(4));
        root.addView(status);

        sourceStatus = text("SUMBER POSISI: AUTO • menunggu data", 12, true);
        sourceStatus.setPadding(dp(12),0,dp(12),dp(8));
        root.addView(sourceStatus);

        mapView = new MbTilesView(this);
        LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(-1,0,1f);
        root.addView(mapView,mapParams);

        LinearLayout sourceRow = new LinearLayout(this);
        sourceRow.setOrientation(LinearLayout.HORIZONTAL);
        sourceRow.setPadding(dp(8),dp(8),dp(8),0);

        Button autoSource = button("AUTO");
        autoSource.setOnClickListener(v -> setSourceMode(0));
        sourceRow.addView(autoSource,half());

        Button gpsSource = button("GPS HP");
        gpsSource.setOnClickListener(v -> setSourceMode(1));
        sourceRow.addView(gpsSource,half());

        Button nmeaSource = button("NMEA");
        nmeaSource.setOnClickListener(v -> setSourceMode(2));
        sourceRow.addView(nmeaSource,half());
        root.addView(sourceRow);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(dp(8),dp(6),dp(8),0);

        Button importMap = button("IMPOR PETA");
        importMap.setOnClickListener(v -> chooseMbTiles());
        row1.addView(importMap,half());

        Button gps = button("KE POSISI AKTIF");
        gps.setOnClickListener(v -> recenterActive());
        row1.addView(gps,half());
        root.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(dp(8),dp(6),dp(8),dp(8));

        Button minus = button("ZOOM −");
        minus.setOnClickListener(v -> mapView.stepZoom(-1));
        row2.addView(minus,half());

        Button plus = button("ZOOM +");
        plus.setOnClickListener(v -> mapView.stepZoom(1));
        row2.addView(plus,half());

        Button back = button("KEMBALI");
        back.setOnClickListener(v -> finish());
        row2.addView(back,half());
        root.addView(row2);

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
        if(bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams half() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,-2,1f);
        p.setMargins(dp(3),0,dp(3),0);
        return p;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private class MbTilesView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private double centerLat = 0.0;
        private double centerLon = 0.0;
        private float lastX;
        private float lastY;
        private boolean dragging = false;
        private boolean hasInitialCenter = false;

        MbTilesView(Context context) {
            super(context);
            paint.setTextSize(dp(12));
            paint.setStrokeWidth(dp(2));
            setBackgroundColor(Color.rgb(1,15,35));
        }

        void setZoom(int z) {
            zoom = nearestAvailableZoom(z);
            status.setText("Peta aktif • zoom " + zoom + " • tersedia " + availableZoomsText()
                    + " • " + tileScheme.toUpperCase(Locale.US));
            invalidate();
        }

        void stepZoom(int direction) {
            zoom = adjacentAvailableZoom(zoom,direction);
            status.setText("Peta aktif • zoom " + zoom + " • tersedia " + availableZoomsText()
                    + " • " + tileScheme.toUpperCase(Locale.US));
            invalidate();
        }

        void centerOn(double lat,double lon) {
            centerLat = clampLat(lat);
            centerLon = normalizeLon(lon);
            hasInitialCenter = true;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if(mapDb == null) {
                paint.setColor(Color.LTGRAY);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(dp(15));
                canvas.drawText("IMPOR FILE .MBTILES UNTUK MEMULAI",getWidth()/2f,getHeight()/2f,paint);
                return;
            }

            drawTiles(canvas);
            drawRoute(canvas);
            drawWaypoints(canvas);
            drawVessel(canvas);
            drawTelemetry(canvas);
            drawCrosshair(canvas);
        }

        private void drawTiles(Canvas canvas) {
            double world = 256.0 * (1 << zoom);
            double cx = lonToWorldX(centerLon,zoom);
            double cy = latToWorldY(centerLat,zoom);

            double left = cx - getWidth()/2.0;
            double top = cy - getHeight()/2.0;

            int minX = (int)Math.floor(left/256.0);
            int maxX = (int)Math.floor((left+getWidth())/256.0);
            int minY = (int)Math.floor(top/256.0);
            int maxY = (int)Math.floor((top+getHeight())/256.0);
            int n = 1 << zoom;

            paint.setColor(Color.rgb(8,31,51));
            canvas.drawRect(0,0,getWidth(),getHeight(),paint);

            for(int ty=minY;ty<=maxY;ty++) {
                if(ty < 0 || ty >= n) continue;
                for(int tx=minX;tx<=maxX;tx++) {
                    int wrappedX = ((tx % n) + n) % n;
                    Bitmap tile = loadTile(zoom,wrappedX,ty);
                    float dx = (float)(tx*256.0-left);
                    float dy = (float)(ty*256.0-top);
                    if(tile != null) {
                        canvas.drawBitmap(tile,null,new android.graphics.RectF(dx,dy,dx+256,dy+256),paint);
                        tile.recycle();
                    }
                }
            }
        }

        private Bitmap loadTile(int z,int x,int y) {
            if(mapDb == null) return null;
            int n = 1 << z;
            int row = "xyz".equals(tileScheme) ? y : (n - 1 - y);
            Cursor c = null;
            try {
                c = mapDb.rawQuery(
                        "SELECT tile_data FROM tiles WHERE zoom_level=? AND tile_column=? AND tile_row=? LIMIT 1",
                        new String[]{String.valueOf(z),String.valueOf(x),String.valueOf(row)});
                if(c.moveToFirst()) {
                    byte[] data = c.getBlob(0);
                    return BitmapFactory.decodeByteArray(data,0,data.length);
                }
            } catch(Exception ignored) {
            } finally {
                if(c != null) c.close();
            }
            return null;
        }

        private void drawVessel(Canvas canvas) {
            Double lat = activeLat();
            Double lon = activeLon();
            if(lat == null || lon == null) return;

            float[] p = pointFor(lat,lon);
            Double h = activeHeading();
            float heading = h == null ? 0f : h.floatValue();

            paint.setColor(useNmeaPosition() ? Color.MAGENTA : Color.CYAN);
            paint.setStyle(Paint.Style.FILL);

            Path boat = new Path();
            boat.moveTo(p[0],p[1]-dp(14));
            boat.lineTo(p[0]-dp(9),p[1]+dp(11));
            boat.lineTo(p[0],p[1]+dp(6));
            boat.lineTo(p[0]+dp(9),p[1]+dp(11));
            boat.close();

            canvas.save();
            canvas.rotate(heading,p[0],p[1]);
            canvas.drawPath(boat,paint);
            canvas.restore();

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.WHITE);
            canvas.drawCircle(p[0],p[1],dp(16),paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawTelemetry(Canvas canvas) {
            String source = useNmeaPosition() ? "NMEA" : "GPS HP";
            Double h = activeHeading();
            String headingText = h == null ? "---°" : String.format(Locale.US,"%.0f°",h);
            String depthText = "--- m";
            if(nmeaSnapshot != null && nmeaSnapshot.depthFresh(NMEA_FRESH_MS)) {
                depthText = String.format(Locale.US,"%.1f m",nmeaSnapshot.depth);
            }

            paint.setColor(0xCC001326);
            canvas.drawRect(dp(8),dp(8),dp(190),dp(72),paint);
            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSize(dp(12));
            canvas.drawText("SOURCE " + source,dp(16),dp(28),paint);
            canvas.drawText("HDG " + headingText + "   DEPTH " + depthText,dp(16),dp(50),paint);
        }

        private void drawWaypoints(Canvas canvas) {
            paint.setTextSize(dp(12));
            paint.setTextAlign(Paint.Align.LEFT);
            for(int i=0;i<waypoints.size();i++) {
                Waypoint wp = waypoints.get(i);
                float[] p = pointFor(wp.lat,wp.lon);
                if(p[0] < -50 || p[0] > getWidth()+50 || p[1] < -50 || p[1] > getHeight()+50) continue;
                paint.setColor(i == activeIndex ? Color.YELLOW : Color.WHITE);
                canvas.drawCircle(p[0],p[1],dp(i == activeIndex ? 7 : 5),paint);
                canvas.drawText(wp.name,p[0]+dp(9),p[1]-dp(7),paint);
            }
        }

        private void drawRoute(Canvas canvas) {
            Double lat = activeLat();
            Double lon = activeLon();
            if(lat == null || lon == null || activeIndex < 0 || activeIndex >= waypoints.size()) return;
            Waypoint wp = waypoints.get(activeIndex);
            float[] a = pointFor(lat,lon);
            float[] b = pointFor(wp.lat,wp.lon);
            paint.setColor(Color.YELLOW);
            paint.setStrokeWidth(dp(3));
            canvas.drawLine(a[0],a[1],b[0],b[1],paint);
        }

        private void drawCrosshair(Canvas canvas) {
            float x = getWidth()/2f;
            float y = getHeight()/2f;
            paint.setColor(0x99FFFFFF);
            paint.setStrokeWidth(dp(1));
            canvas.drawLine(x-dp(10),y,x+dp(10),y,paint);
            canvas.drawLine(x,y-dp(10),x,y+dp(10),paint);
        }

        private float[] pointFor(double lat,double lon) {
            double cx = lonToWorldX(centerLon,zoom);
            double cy = latToWorldY(centerLat,zoom);
            double px = lonToWorldX(lon,zoom);
            double py = latToWorldY(lat,zoom);
            double world = 256.0 * (1 << zoom);
            double dx = px - cx;
            if(dx > world/2) dx -= world;
            if(dx < -world/2) dx += world;
            return new float[]{
                    (float)(getWidth()/2.0 + dx),
                    (float)(getHeight()/2.0 + (py-cy))
            };
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch(e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = e.getX();
                    lastY = e.getY();
                    dragging = true;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if(!dragging) return true;
                    float dx = e.getX()-lastX;
                    float dy = e.getY()-lastY;
                    lastX = e.getX();
                    lastY = e.getY();

                    double cx = lonToWorldX(centerLon,zoom) - dx;
                    double cy = latToWorldY(centerLat,zoom) - dy;
                    centerLon = worldXToLon(cx,zoom);
                    centerLat = worldYToLat(cy,zoom);
                    hasInitialCenter = true;
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    return true;
            }
            return true;
        }

        private double lonToWorldX(double lon,int z) {
            double world = 256.0 * (1 << z);
            return (normalizeLon(lon)+180.0)/360.0*world;
        }

        private double latToWorldY(double lat,int z) {
            lat = clampLat(lat);
            double sin = Math.sin(Math.toRadians(lat));
            double world = 256.0 * (1 << z);
            return (0.5 - Math.log((1+sin)/(1-sin))/(4*Math.PI))*world;
        }

        private double worldXToLon(double x,int z) {
            double world = 256.0 * (1 << z);
            x = ((x % world) + world) % world;
            return x/world*360.0-180.0;
        }

        private double worldYToLat(double y,int z) {
            double world = 256.0 * (1 << z);
            y = Math.max(0,Math.min(world,y));
            double n = Math.PI - 2.0*Math.PI*y/world;
            return Math.toDegrees(Math.atan(Math.sinh(n)));
        }

        private double clampLat(double lat) {
            return Math.max(-85.05112878,Math.min(85.05112878,lat));
        }

        private double normalizeLon(double lon) {
            double r = lon % 360.0;
            if(r > 180) r -= 360;
            if(r < -180) r += 360;
            return r;
        }
    }

    private static class Waypoint {
        final String name;
        final double lat;
        final double lon;
        Waypoint(String name,double lat,double lon) {
            this.name=name;
            this.lat=lat;
            this.lon=lon;
        }
    }
}
