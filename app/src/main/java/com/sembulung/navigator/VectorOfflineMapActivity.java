package com.sembulung.navigator;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
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
import org.maplibre.android.style.sources.VectorSource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

public class VectorOfflineMapActivity extends Activity {
    private static final int REQ_PMTILES = 940;
    private static final String FILE_NAME = "sembulung_jatim_bali.pmtiles";

    private MapView mapView;
    private MapLibreMap map;
    private TextView status;
    private Button importButton;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MapLibre.getInstance(this);

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
        top.setBackgroundColor(0xD904182B);

        TextView title = label("SEMBULUNG VECTOR MARINE MAP",16,true);
        top.addView(title);

        status = label("Menyiapkan MapLibre…",12,false);
        status.setPadding(0,dp(4),0,dp(6));
        top.addView(status);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        importButton = button("IMPOR .PMTILES");
        importButton.setOnClickListener(v -> choosePmtiles());
        buttons.addView(importButton, half());

        Button center = button("BANYUWANGI");
        center.setOnClickListener(v -> centerBanyuwangi());
        buttons.addView(center, half());

        Button back = button("KEMBALI");
        back.setOnClickListener(v -> finish());
        buttons.addView(back, half());

        top.addView(buttons);

        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        root.addView(top,topLp);

        TextView attribution = label("© OpenStreetMap contributors • Geofabrik • MapLibre",10,false);
        attribution.setPadding(dp(8),dp(4),dp(8),dp(4));
        attribution.setBackgroundColor(0xAA03172A);
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
    }

    private void loadBaseStyle() {
        if(map == null) return;
        String styleJson = "{"
                + "\"version\":8,"
                + "\"name\":\"SEMBULUNG OFFLINE\","
                + "\"sources\":{},"
                + "\"layers\":[{"
                + "\"id\":\"background\","
                + "\"type\":\"background\","
                + "\"paint\":{\"background-color\":\"#0A3654\"}"
                + "}]"
                + "}";
        map.setStyle(new Style.Builder().fromJson(styleJson), style -> {
            File f = localPmtiles();
            if(f.exists() && f.length() > 0) {
                addPmtiles(style,f);
            } else {
                status.setText("Belum ada peta detail. Tekan IMPOR .PMTILES.");
                centerBanyuwangi();
            }
        });
    }

    private void addPmtiles(Style style, File file) {
        try {
            String uri = "pmtiles://file://" + file.getAbsolutePath();
            VectorSource source = new VectorSource("sembulung-vector", uri);
            style.addSource(source);

            addFill(style,"ocean-fill","ocean",Color.rgb(10,74,112),1.0f);
            addFill(style,"land-fill","land",Color.rgb(213,207,177),1.0f);
            addFill(style,"water-fill","water_polygons",Color.rgb(41,132,176),1.0f);
            addFill(style,"building-fill","buildings",Color.rgb(184,177,157),0.88f);
            addFill(style,"pier-fill","pier_polygons",Color.rgb(146,139,119),0.95f);

            addLine(style,"boundary-line","boundaries",Color.rgb(140,154,162),1.1f);
            addLine(style,"water-line","water_lines",Color.rgb(38,142,190),1.2f);
            addLine(style,"pier-line","pier_lines",Color.rgb(112,103,82),1.4f);
            addLine(style,"street-line","streets",Color.rgb(93,83,66),1.4f);
            addLine(style,"ferry-line","ferries",Color.rgb(34,210,240),1.8f);

            status.setText(String.format(Locale.US,
                    "Peta detail aktif • %.1f MB • vector PMTiles • zoom 0–14",
                    file.length()/1048576.0));
            centerBanyuwangi();
        } catch(Exception e) {
            status.setText("Gagal memuat PMTiles: " + e.getMessage());
        }
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
        if(requestCode != REQ_PMTILES || resultCode != RESULT_OK || data == null || data.getData() == null) return;
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
                    status.setText(String.format(Locale.US,"Import selesai %.1f MB • memuat peta…",size/1048576.0));
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

    private void centerBanyuwangi() {
        if(map == null) return;
        map.setCameraPosition(new CameraPosition.Builder()
                .target(new LatLng(-8.2192,114.3691))
                .zoom(9.2)
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
    }

    @Override protected void onPause() {
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
