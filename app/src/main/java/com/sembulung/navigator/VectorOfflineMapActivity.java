package com.sembulung.navigator;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.FillLayer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.layers.RasterLayer;
import org.maplibre.android.style.sources.RasterSource;
import org.maplibre.android.style.sources.TileSet;
import org.maplibre.android.style.sources.VectorSource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

public class VectorOfflineMapActivity extends Activity implements LocationListener {
    private static final int REQ_PMTILES = 940;
    private static final int REQ_LOCATION = 941;
    private static final long NMEA_FRESH_MS = 5000L;
    private static final String FILE_NAME = "sembulung_jatim_bali.pmtiles";
    private static final String SEAMARK_TILES =
            "https://tiles.openseamap.org/seamark/{z}/{x}/{y}.png";
    private static final String GEBCO_WMS =
            "https://geoserver.openseamap.org/geoserver/gwc/service/wms"
            + "?SERVICE=WMS&REQUEST=GetMap&VERSION=1.1.1"
            + "&LAYERS=gebco2021%3Agebco_2021&STYLES="
            + "&FORMAT=image%2Fpng&TRANSPARENT=true"
            + "&SRS=EPSG%3A3857&BBOX={bbox-epsg-3857}&WIDTH=256&HEIGHT=256";

    private MapView mapView;
    private MapLibreMap map;
    private TextView status;
    private TextView safety;
    private TextView navStatus;
    private LocationManager locationManager;
    private Location phoneLocation;
    private NmeaDataStore.Snapshot nmeaSnapshot;
    private final Handler liveHandler = new Handler(Looper.getMainLooper());
    private final Runnable liveRefresh = new Runnable() {
        @Override public void run() {
            refreshNavStatus();
            liveHandler.postDelayed(this,1000L);
        }
    };
    private Button importButton;
    private Button marineButton;
    private Button depthButton;
    private boolean marineOverlayEnabled = true;
    private boolean depthOverlayEnabled = false;
    private boolean pmtilesLoaded = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MapLibre.getInstance(this);
        locationManager = (LocationManager)getSystemService(LOCATION_SERVICE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(4,24,43));

        mapView = new MapView(this);
        mapView.onCreate(savedInstanceState);
        root.addView(mapView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(10),dp(8),dp(10),dp(8));
        top.setBackgroundColor(0xE604182B);

        TextView title = label("SEMBULUNG MARINE CHART • V24",16,true);
        top.addView(title);

        status = label("Menyiapkan MapLibre + marine layers…",12,false);
        status.setPadding(0,dp(4),0,dp(5));
        top.addView(status);

        safety = label("OPEN DATA • gunakan bersama peta laut resmi",10,true);
        safety.setTextColor(0xffffd764);
        safety.setPadding(0,0,0,dp(4));
        top.addView(safety);

        navStatus = label("NAV • menunggu GPS/NMEA",11,true);
        navStatus.setTextColor(0xff8fffc0);
        navStatus.setPadding(0,0,0,dp(6));
        top.addView(navStatus);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);

        importButton = button("IMPOR .PMTILES");
        importButton.setOnClickListener(v -> choosePmtiles());
        row1.addView(importButton, third());

        marineButton = button("MARINE ON");
        marineButton.setOnClickListener(v -> {
            marineOverlayEnabled = !marineOverlayEnabled;
            updateMarineButton();
            loadBaseStyle();
        });
        row1.addView(marineButton, third());

        depthButton = button("DEPTH OFF");
        depthButton.setOnClickListener(v -> {
            depthOverlayEnabled = !depthOverlayEnabled;
            updateDepthButton();
            loadBaseStyle();
        });
        row1.addView(depthButton, third());
        top.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0,dp(4),0,0);

        Button position = button("POSISI");
        position.setOnClickListener(v -> centerCurrentPosition());
        row2.addView(position, third());

        Button center = button("BANYUWANGI");
        center.setOnClickListener(v -> centerBanyuwangi());
        row2.addView(center, third());

        Button sonar = button("SONAR / DEPTH");
        sonar.setOnClickListener(v -> startActivity(new Intent(this,MarineMapActivity.class)));
        row2.addView(sonar, third());

        Button back = button("KEMBALI");
        back.setOnClickListener(v -> finish());
        row2.addView(back, third());

        top.addView(row2);

        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        root.addView(top,topLp);

        TextView attribution = label(
                "© OSM contributors • Geofabrik • OpenSeaMap • GEBCO • MapLibre",10,false);
        attribution.setPadding(dp(8),dp(4),dp(8),dp(4));
        attribution.setBackgroundColor(0xC003172A);
        FrameLayout.LayoutParams attrLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        root.addView(attribution,attrLp);

        setContentView(root);

        mapView.getMapAsync(mapLibreMap -> {
            map = mapLibreMap;
            map.setMinZoomPreference(0.0);
            map.setMaxZoomPreference(18.0);
            loadBaseStyle();
        });

        startGps();
        refreshNavStatus();
    }

    private void loadBaseStyle() {
        if(map == null) return;
        pmtilesLoaded = false;

        String styleJson = "{"
                + "\"version\":8,"
                + "\"name\":\"SEMBULUNG MARINE V24\","
                + "\"sources\":{},"
                + "\"layers\":[{"
                + "\"id\":\"background\","
                + "\"type\":\"background\","
                + "\"paint\":{\"background-color\":\"#0A3654\"}"
                + "}]"
                + "}";

        map.setStyle(new Style.Builder().fromJson(styleJson), style -> {
            File f = localPmtiles();
            if(f.exists() && f.length() > 0) addPmtiles(style,f);
            addDepthOverlay(style);
            addMarineOverlay(style);
            updateStatus(f);
            centerBanyuwangi();
        });
    }

    private void addPmtiles(Style style, File file) {
        try {
            String uri = "pmtiles://file://" + file.getAbsolutePath();
            VectorSource source = new VectorSource("sembulung-vector", uri);
            style.addSource(source);

            addFill(style,"ocean-fill","ocean",Color.rgb(8,70,108),1.0f);
            addFill(style,"land-fill","land",Color.rgb(222,216,188),1.0f);
            addFill(style,"water-fill","water_polygons",Color.rgb(44,142,184),1.0f);
            addFill(style,"building-fill","buildings",Color.rgb(190,181,158),0.82f);
            addFill(style,"pier-fill","pier_polygons",Color.rgb(145,136,112),0.95f);

            addLine(style,"boundary-line","boundaries",Color.rgb(131,148,158),1.0f);
            addLine(style,"water-line","water_lines",Color.rgb(44,151,195),1.2f);
            addLine(style,"pier-line","pier_lines",Color.rgb(100,91,69),1.5f);
            addLine(style,"street-line","streets",Color.rgb(100,89,69),1.25f);
            addLine(style,"ferry-line","ferries",Color.rgb(35,223,245),2.1f);

            pmtilesLoaded = true;
        } catch(Exception e) {
            pmtilesLoaded = false;
            status.setText("PMTiles gagal dimuat: " + e.getMessage());
        }
    }

    private void addDepthOverlay(Style style) {
        if(!depthOverlayEnabled) return;
        try {
            TileSet tileSet = new TileSet("2.1.0", GEBCO_WMS);
            RasterSource source = new RasterSource("openseamap-gebco",tileSet,256);
            style.addSource(source);

            RasterLayer layer = new RasterLayer(
                    "openseamap-gebco-layer","openseamap-gebco");
            layer.setProperties(
                    PropertyFactory.rasterOpacity(0.62f),
                    PropertyFactory.rasterFadeDuration(0f)
            );
            style.addLayer(layer);
        } catch(Exception e) {
            safety.setText("Depth overlay gagal • " + e.getClass().getSimpleName());
        }
    }

    private void addMarineOverlay(Style style) {
        if(!marineOverlayEnabled) return;
        try {
            TileSet tileSet = new TileSet("2.1.0", SEAMARK_TILES);
            RasterSource source = new RasterSource("openseamap-seamarks",tileSet,256);
            style.addSource(source);

            RasterLayer layer = new RasterLayer(
                    "openseamap-seamarks-layer","openseamap-seamarks");
            layer.setProperties(
                    PropertyFactory.rasterOpacity(0.97f),
                    PropertyFactory.rasterFadeDuration(0f)
            );
            style.addLayer(layer);
        } catch(Exception e) {
            safety.setText("Marine overlay gagal • " + e.getClass().getSimpleName());
        }
    }

    private void updateStatus(File f) {
        String base = pmtilesLoaded && f.exists()
                ? String.format(Locale.US,"PMTiles OFFLINE %.1f MB",f.length()/1048576.0)
                : "Basemap kosong • impor PMTiles";
        String marine = marineOverlayEnabled
                ? "seamarks ONLINE"
                : "seamarks OFF";
        String depth = depthOverlayEnabled
                ? "GEBCO depth ONLINE"
                : "depth OFF";
        status.setText(base + " • " + marine + " • " + depth);
        safety.setText((marineOverlayEnabled || depthOverlayEnabled)
                ? "BUOY • BEACON • LIGHT • DEPTH RELIEF • OPEN DATA"
                : "OPEN DATA • gunakan bersama peta laut resmi");
    }

    private void updateMarineButton() {
        marineButton.setText(marineOverlayEnabled ? "MARINE ON" : "MARINE OFF");
        marineButton.setAlpha(marineOverlayEnabled ? 1f : 0.6f);
    }

    private void updateDepthButton() {
        depthButton.setText(depthOverlayEnabled ? "DEPTH ON" : "DEPTH OFF");
        depthButton.setAlpha(depthOverlayEnabled ? 1f : 0.6f);
    }

    private void addFill(Style style,String id,String sourceLayer,int color,float opacity) {
        FillLayer layer = new FillLayer(id,"sembulung-vector");
        layer.setSourceLayer(sourceLayer);
        layer.setProperties(
                PropertyFactory.fillColor(color),
                PropertyFactory.fillOpacity(opacity)
        );
        style.addLayer(layer);
    }

    private void addLine(Style style,String id,String sourceLayer,int color,float width) {
        LineLayer layer = new LineLayer(id,"sembulung-vector");
        layer.setSourceLayer(sourceLayer);
        layer.setProperties(
                PropertyFactory.lineColor(color),
                PropertyFactory.lineWidth(width)
        );
        style.addLayer(layer);
    }

    private void choosePmtiles() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i,REQ_PMTILES);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode != REQ_PMTILES || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        importButton.setEnabled(false);
        status.setText("Menyalin PMTiles ke penyimpanan aplikasi…");

        new Thread(() -> {
            File out = localPmtiles();
            long total = 0;
            try(InputStream in = getContentResolver().openInputStream(uri);
                FileOutputStream fos = new FileOutputStream(out,false)) {
                if(in == null) throw new IllegalStateException("File tidak dapat dibuka");
                byte[] buffer = new byte[1024*1024];
                int n;
                while((n=in.read(buffer))>0) {
                    fos.write(buffer,0,n);
                    total += n;
                }
                fos.flush();
                final long size=total;
                runOnUiThread(() -> {
                    importButton.setEnabled(true);
                    status.setText(String.format(Locale.US,
                            "Import selesai %.1f MB • memuat marine chart…",
                            size/1048576.0));
                    loadBaseStyle();
                });
            } catch(Exception e) {
                if(out.exists()) out.delete();
                runOnUiThread(() -> {
                    importButton.setEnabled(true);
                    status.setText("Import gagal: " + e.getMessage());
                });
            }
        }).start();
    }

    private File localPmtiles() {
        return new File(getFilesDir(),FILE_NAME);
    }

    private void startGps() {
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);
            return;
        }
        try {
            if(locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,1000L,0.5f,this);
                Location last=locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if(last!=null) phoneLocation=last;
            }
        } catch(Exception ignored) {}
    }

    private void refreshNavStatus() {
        if(navStatus==null) return;
        nmeaSnapshot=NmeaDataStore.read(this);
        boolean nmeaPos=nmeaSnapshot!=null && nmeaSnapshot.positionFresh(NMEA_FRESH_MS);
        boolean nmeaDepth=nmeaSnapshot!=null && nmeaSnapshot.depthFresh(NMEA_FRESH_MS);

        String source=nmeaPos ? "NMEA" : (phoneLocation!=null ? "GPS HP" : "NO FIX");
        String pos="--";
        if(nmeaPos) {
            pos=String.format(Locale.US,"%.5f %.5f",nmeaSnapshot.lat,nmeaSnapshot.lon);
        } else if(phoneLocation!=null) {
            pos=String.format(Locale.US,"%.5f %.5f",
                    phoneLocation.getLatitude(),phoneLocation.getLongitude());
        }

        String depth=nmeaDepth
                ? String.format(Locale.US," • DEPTH %.1f m",nmeaSnapshot.depth)
                : " • DEPTH --";
        navStatus.setText("NAV • "+source+" • "+pos+depth);
    }

    private void centerCurrentPosition() {
        Double lat=null,lon=null;
        nmeaSnapshot=NmeaDataStore.read(this);
        if(nmeaSnapshot!=null && nmeaSnapshot.positionFresh(NMEA_FRESH_MS)) {
            lat=nmeaSnapshot.lat;
            lon=nmeaSnapshot.lon;
        } else if(phoneLocation!=null) {
            lat=phoneLocation.getLatitude();
            lon=phoneLocation.getLongitude();
        }

        if(lat==null || lon==null) {
            navStatus.setText("NAV • posisi belum tersedia • aktifkan GPS atau NMEA");
            startGps();
            return;
        }
        if(map!=null) {
            map.setCameraPosition(new CameraPosition.Builder()
                    .target(new LatLng(lat,lon))
                    .zoom(Math.max(13.0,map.getCameraPosition().zoom))
                    .bearing(0)
                    .tilt(0)
                    .build());
        }
    }

    @Override public void onLocationChanged(Location location) {
        phoneLocation=location;
        refreshNavStatus();
    }

    @Override public void onProviderEnabled(String provider) {}

    @Override public void onProviderDisabled(String provider) {
        refreshNavStatus();
    }

    @Override public void onRequestPermissionsResult(
            int requestCode,String[] permissions,int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==REQ_LOCATION && grantResults.length>0
                && grantResults[0]==PackageManager.PERMISSION_GRANTED) {
            startGps();
        }
    }

    private void centerBanyuwangi() {
        if(map == null) return;
        map.setCameraPosition(new CameraPosition.Builder()
                .target(new LatLng(-8.2192,114.3691))
                .zoom(11.0)
                .bearing(0)
                .tilt(0)
                .build());
    }

    private TextView label(String text,int sp,boolean bold) {
        TextView v=new TextView(this);
        v.setText(text);
        v.setTextColor(Color.WHITE);
        v.setTextSize(sp);
        v.setGravity(Gravity.CENTER);
        if(bold)v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return v;
    }

    private Button button(String text) {
        Button b=new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams half() {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1f);
        p.setMargins(dp(2),0,dp(2),0);
        return p;
    }

    private LinearLayout.LayoutParams third() {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1f);
        p.setMargins(dp(2),0,dp(2),0);
        return p;
    }

    private int dp(int v) {
        return Math.round(v*getResources().getDisplayMetrics().density);
    }

    @Override protected void onStart() {
        super.onStart();
        if(mapView!=null) mapView.onStart();
    }

    @Override protected void onResume() {
        super.onResume();
        if(mapView!=null) mapView.onResume();
        liveHandler.removeCallbacks(liveRefresh);
        liveHandler.post(liveRefresh);
        startGps();
    }

    @Override protected void onPause() {
        liveHandler.removeCallbacks(liveRefresh);
        if(locationManager!=null && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            try { locationManager.removeUpdates(this); } catch(Exception ignored) {}
        }
        if(mapView!=null) mapView.onPause();
        super.onPause();
    }

    @Override protected void onStop() {
        if(mapView!=null) mapView.onStop();
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if(mapView!=null) mapView.onSaveInstanceState(outState);
    }

    @Override public void onLowMemory() {
        super.onLowMemory();
        if(mapView!=null) mapView.onLowMemory();
    }

    @Override protected void onDestroy() {
        if(mapView!=null) mapView.onDestroy();
        super.onDestroy();
    }
}
