package com.sembulung.navigator;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.sembulung.navigator.ais.AisTarget;
import com.sembulung.navigator.ais.AisTargetStore;
import com.sembulung.navigator.sonar.BathymetryStyle;
import com.sembulung.navigator.sonar.DepthSample;
import com.sembulung.navigator.sonar.SonarChartEngine;
import com.sembulung.navigator.sonar.SonarChartStore;
import com.sembulung.navigator.sonar.SonarHazardEngine;
import com.sembulung.navigator.sonar.SonarSampleFilter;
import com.sembulung.navigator.sonar.SonarSessionStore;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class MarineMapActivity extends Activity implements LocationListener {
    private static final int REQ_LOCATION=1301;
    private static final long FRESH_MS=5000L;

    private LocationManager locationManager;
    private Location phoneLocation;
    private NmeaDataStore.Snapshot nmeaSnapshot;
    private MarineTileLoader tileLoader;
    private MarineMapView map;

    private TextView title;
    private TextView gpsChip;
    private TextView nmeaChip;
    private TextView depthChip;
    private TextView aisChip;
    private Button sourceButton;
    private Button sonarButton;
    private Button bathyButton;
    private LinearLayout guidancePanel;
    private TextView routeGuidance;
    private TextView safetyGuidance;
    private Button skipWaypointButton;
    private Button endRouteButton;
    private int lastArrivalIndex=-2;
    private long lastArrivalAt=0L;
    private String lastSafetyKey="";
    private static final String BATHY_PREFS="sembulung_bathymetry";
    private static final String BATHY_AREAS="areas";
    private static final int REQ_EXPORT_MB=4401;
    private int exportRadiusKm;
    private double exportLat,exportLon;
    private int exportZoom;

    private int sourceMode=0; // 0 auto, 1 device GPS, 2 NMEA

    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){
        @Override public void run(){
            refresh();
            handler.postDelayed(this,500L);
        }
    };

    private SonarChartStore sonarStore;
    private SonarSessionStore sonarSessions;
    private final ArrayList<DepthSample> sonarSamples=new ArrayList<>();
    private SonarChartEngine.Chart sonarChart;
    private final AtomicBoolean chartBuilding=new AtomicBoolean(false);
    private long lastSoundingAt;
    private Double lastSoundingLat;
    private Double lastSoundingLon;
    private int newSoundings;
    private String lastBathymetryProfile="";

    private boolean sonarEnabled=true;
    private boolean sonarShading=true;
    private boolean sonarContours=true;
    private boolean sonarSoundings=false;
    private boolean aisEnabled=true;

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(3,27,61));
        getWindow().setNavigationBarColor(Color.rgb(3,27,61));

        locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);
        applyWindowSettings();

        FrameLayout root=new FrameLayout(this);

        tileLoader=new MarineTileLoader(this,()->{
            if(map!=null)map.postInvalidate();
        });
        map=new MarineMapView(this,tileLoader);
        root.addView(map,new FrameLayout.LayoutParams(-1,-1));

        buildHeader(root);
        buildRightToolbar(root);
        buildGuidanceOverlay(root);
        buildBottomNavigation(root);

        setContentView(root);

        Intent intent=getIntent();
        if(intent!=null&&intent.hasExtra("focus_lat")&&intent.hasExtra("focus_lon")){
            map.focus(
                    intent.getDoubleExtra("focus_lat",0.0),
                    intent.getDoubleExtra("focus_lon",0.0));
        }

        sonarStore=new SonarChartStore(this);
        sonarSessions=new SonarSessionStore(this);
        sonarSamples.addAll(sonarStore.load(5000));
        applySonarLayers();
        loadMapOverlays();
        if(sonarSamples.size()>=3)rebuildSonarChart();

        startGps();
        refresh();
    }

    private void buildHeader(FrameLayout root){
        LinearLayout top=new LinearLayout(this); top.setOrientation(LinearLayout.VERTICAL); top.setPadding(dp(12),dp(8),dp(12),0);
        LinearLayout brand=new LinearLayout(this); brand.setOrientation(LinearLayout.VERTICAL);
        title=chip("SEMBULUNG NAVIGATOR",17,true); title.setGravity(Gravity.START|Gravity.CENTER_VERTICAL); title.setBackgroundColor(Color.TRANSPARENT); title.setPadding(0,0,0,0);
        brand.addView(title,new LinearLayout.LayoutParams(-1,dp(30)));
        TextView sub=chip("Navigasi Laut Presisi",10,false); sub.setGravity(Gravity.START|Gravity.CENTER_VERTICAL); sub.setTextColor(0xffb9c9d6); sub.setBackgroundColor(Color.TRANSPARENT); sub.setPadding(0,0,0,0);
        brand.addView(sub,new LinearLayout.LayoutParams(-1,dp(20))); top.addView(brand,new LinearLayout.LayoutParams(-1,dp(50)));
        LinearLayout row=new LinearLayout(this);
        gpsChip=chip("GPS --",8,true); nmeaChip=chip("NMEA --",8,true); depthChip=chip("DEPTH --",8,true); aisChip=chip("AIS --",8,true);
        nmeaChip.setVisibility(android.view.View.GONE); row.addView(gpsChip,chipLp()); row.addView(depthChip,chipLp()); row.addView(aisChip,chipLp());
        top.addView(row,new LinearLayout.LayoutParams(-1,dp(30)));
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-2); p.gravity=Gravity.TOP; root.addView(top,p);
    }

    private void buildRightToolbar(FrameLayout root){
        LinearLayout right=new LinearLayout(this); right.setOrientation(LinearLayout.VERTICAL); right.setGravity(Gravity.CENTER_HORIZONTAL);
        Button layers=floating("LAYERS",8); layers.setOnClickListener(v->layerMenu()); right.addView(layers,square());
        Button search=floating("⌕",22); search.setOnClickListener(v->startActivity(new Intent(this,SearchCoordinateActivity.class))); right.addView(search,square());
        Button center=floating("◎",20); center.setOnClickListener(v->recenter()); right.addView(center,square());
        Button plus=floating("+",22); plus.setOnClickListener(v->map.zoom(1)); right.addView(plus,square());
        Button minus=floating("−",22); minus.setOnClickListener(v->map.zoom(-1)); right.addView(minus,square());
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-2,-2); p.gravity=Gravity.END|Gravity.CENTER_VERTICAL; p.setMargins(0,dp(8),dp(10),dp(76)); root.addView(right,p);
    }

    private void buildBottomNavigation(FrameLayout root){
        LinearLayout bottom=new LinearLayout(this); bottom.setOrientation(LinearLayout.HORIZONTAL); bottom.setPadding(dp(5),dp(4),dp(5),dp(4)); bottom.setBackground(bg());
        Button peta=navButton("CHART"); peta.setEnabled(false); bottom.addView(peta,navLp());
        Button route=navButton("ROUTE"); route.setOnClickListener(v->startActivity(new Intent(this,NavigationActivity.class))); bottom.addView(route,navLp());
        Button sonar=navButton("SONAR"); sonar.setOnClickListener(v->startActivity(new Intent(this,NmeaActivity.class))); bottom.addView(sonar,navLp());
        Button ais=navButton("AIS"); ais.setOnClickListener(v->startActivity(new Intent(this,AisActivity.class))); bottom.addView(ais,navLp());
        Button menu=navButton("MENU"); menu.setOnClickListener(v->appMenu()); bottom.addView(menu,navLp());
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,dp(56)); p.gravity=Gravity.BOTTOM; p.setMargins(dp(8),0,dp(8),dp(8)); root.addView(bottom,p);
        sourceButton=floating("AUTO",9); sourceButton.setOnClickListener(v->{sourceMode=(sourceMode+1)%3;refresh();});
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(dp(72),dp(38)); sp.gravity=Gravity.START|Gravity.BOTTOM; sp.setMargins(dp(12),0,0,dp(76)); root.addView(sourceButton,sp);
    }

    private void buildGuidanceOverlay(FrameLayout root){
        guidancePanel=new LinearLayout(this);
        guidancePanel.setOrientation(LinearLayout.VERTICAL);
        guidancePanel.setPadding(dp(10),dp(4),dp(10),dp(4));
        guidancePanel.setBackground(bg());

        routeGuidance=chip("NAVIGASI",10,true);
        routeGuidance.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        guidancePanel.addView(routeGuidance,new LinearLayout.LayoutParams(-1,dp(28)));

        safetyGuidance=chip("SAFETY • normal",9,true);
        safetyGuidance.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        safetyGuidance.setTextColor(0xff8fffc0);
        guidancePanel.addView(safetyGuidance,new LinearLayout.LayoutParams(-1,dp(24)));

        LinearLayout controls=new LinearLayout(this);
        skipWaypointButton=navButton("SKIP");
        skipWaypointButton.setOnClickListener(v->skipWaypoint());
        endRouteButton=navButton("END");
        endRouteButton.setOnClickListener(v->endRoute());
        controls.addView(skipWaypointButton,new LinearLayout.LayoutParams(0,dp(28),1f));
        controls.addView(endRouteButton,new LinearLayout.LayoutParams(0,dp(28),1f));
        guidancePanel.addView(controls,new LinearLayout.LayoutParams(-1,dp(29)));

        guidancePanel.setVisibility(android.view.View.GONE);
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,dp(89));
        p.gravity=Gravity.BOTTOM;
        p.setMargins(dp(8),0,dp(8),dp(70));
        root.addView(guidancePanel,p);
    }

    private void skipWaypoint(){
        List<WaypointStore.Waypoint> w=WaypointStore.load(this);
        int active=WaypointStore.activeIndex(this);
        if(active<0||active>=w.size())return;
        int next=active+1<w.size()?active+1:-1;
        WaypointStore.setActiveIndex(this,next);
        Toast.makeText(this,next>=0?"Lanjut ke "+w.get(next).name:"Rute selesai",Toast.LENGTH_SHORT).show();
        loadMapOverlays();
        updateGuidanceAndSafety();
    }

    private void endRoute(){
        WaypointStore.clearActive(this);
        Toast.makeText(this,"Navigasi rute diakhiri",Toast.LENGTH_SHORT).show();
        loadMapOverlays();
        updateGuidanceAndSafety();
    }

    private void menu(){layerMenu();}

    private void showBathymetryDownloadMenu(){
        String[] options={
                "Download radius 1 km",
                "Download radius 5 km",
                "Download radius 10 km",
                "Download radius 25 km",
                "Download radius 50 km",
                "Download area tampilan",
                "Lihat area tersimpan"
        };
        new AlertDialog.Builder(this)
                .setTitle("DOWNLOAD KONTUR KEDALAMAN")
                .setMessage("Sumber: bathymetry GEBCO yang dirender OpenSeaMap. Terpisah dari sounding sonar lokal.")
                .setItems(options,(d,which)->{
                    if(which==6){showSavedBathymetryAreas();return;}
                    if(which==5){
                        int total=map.downloadVisibleBathymetry();
                        showBathymetryProgressKm(0,total);
                        return;
                    }
                    int[] radiiKm={1,5,10,25,50};
                    int radiusKm=radiiKm[which];
                    showBathymetryRadiusConfirm(radiusKm);
                })
                .setNegativeButton("Batal",null)
                .show();
    }

    private void showBathymetryRadiusConfirm(int radiusKm){
        map.downloadPreviewRadiusKm(radiusKm);
        String message="Lingkaran download: radius "+radiusKm+" km"
                +"\\nPusat: "+String.format(Locale.US,"%.5f°, %.5f°",map.centerLatitude(),map.centerLongitude())
                +"\\nZoom: "+map.zoomLevel()
                +"\\n\\nGeser/zoom peta bila perlu, lalu tekan DOWNLOAD.";
        new AlertDialog.Builder(this)
                .setTitle("PREVIEW AREA BATHYMETRY")
                .setMessage(message)
                .setNegativeButton("BATAL",(d,w)->map.downloadPreviewRadiusKm(0))
                .setPositiveButton("DOWNLOAD",(d,w)->{
                    int total=map.downloadBathymetryRadiusKm(radiusKm);
                    map.downloadPreviewRadiusKm(0);
                    showBathymetryProgressKm(radiusKm,total);
                })
                .setOnCancelListener(d->map.downloadPreviewRadiusKm(0))
                .show();
    }

    private void showBathymetryProgressKm(int radiusKm,int total){
        final TextView text=chip("Menyiapkan download…",13,true);
        text.setPadding(dp(8),dp(8),dp(8),dp(8));
        final AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("DOWNLOAD BATHYMETRY OFFLINE")
                .setView(text)
                .setNegativeButton("Tutup",null)
                .setPositiveButton("EXPORT MBTILES",(d,w)->startMbtilesExport())
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(radiusKm>0);

        final long started=System.currentTimeMillis();
        final Runnable[] poll=new Runnable[1];
        poll[0]=()->{
            int done=map.cachedBathymetryRadiusKmCount(radiusKm);
            int pct=total<=0?100:Math.min(100,(done*100)/total);
            long sec=Math.max(1,(System.currentTimeMillis()-started)/1000L);
            String area=radiusKm<=0?"tampilan":radiusKm+" km dari pusat peta";
            text.setText("Area: "+area
                    +"\nPusat: "+String.format(Locale.US,"%.5f°, %.5f°",map.centerLatitude(),map.centerLongitude())
                    +"\nZoom: "+map.zoomLevel()
                    +"\nProgress: "+done+" / "+total+" tile ("+pct+"%)"
                    +"\nWaktu: "+sec+" detik");
            if(done>=total){
                saveBathymetryArea(radiusKm,total);
                exportRadiusKm=radiusKm;
                exportLat=map.centerLatitude();
                exportLon=map.centerLongitude();
                exportZoom=map.zoomLevel();
                text.append("\n\n✓ Area kontur tersimpan untuk penggunaan offline.");
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(radiusKm>0);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xff00c8ff);
                return;
            }
            handler.postDelayed(poll[0],700L);
        };
        handler.post(poll[0]);
    }

    private void startMbtilesExport(){
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/x-sqlite3");
        i.putExtra(Intent.EXTRA_TITLE,String.format(Locale.US,
                "SEMBULUNG_BATHY_Z%d_%dkm.mbtiles",exportZoom,exportRadiusKm));
        startActivityForResult(i,REQ_EXPORT_MB);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=REQ_EXPORT_MB||resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        final android.net.Uri uri=data.getData();
        Toast.makeText(this,"Membuat MBTiles…",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            File tmp=new File(getCacheDir(),"export_bathymetry.mbtiles");
            try{
                int tiles=BathymetryMbtilesExporter.export(tmp,tileLoader,exportLat,exportLon,exportRadiusKm,exportZoom);
                try(java.io.InputStream in=new java.io.FileInputStream(tmp);
                    java.io.OutputStream out=getContentResolver().openOutputStream(uri)){
                    if(out==null)throw new java.io.IOException("Lokasi penyimpanan tidak tersedia");
                    byte[] buf=new byte[32768];int n;
                    while((n=in.read(buf))>0)out.write(buf,0,n);
                    out.flush();
                }
                tmp.delete();
                runOnUiThread(()->Toast.makeText(this,"✓ MBTiles tersimpan: "+tiles+" tile",Toast.LENGTH_LONG).show());
            }catch(Exception e){
                tmp.delete();
                runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Export MBTiles gagal")
                        .setMessage(String.valueOf(e.getMessage()))
                        .setPositiveButton("OK",null).show());
            }
        }).start();
    }

    private void saveBathymetryArea(int radiusKm,int total){
        try{
            android.content.SharedPreferences p=getSharedPreferences(BATHY_PREFS,MODE_PRIVATE);
            JSONArray a=new JSONArray(p.getString(BATHY_AREAS,"[]"));
            JSONObject o=new JSONObject();
            o.put("zoom",map.zoomLevel());
            o.put("radius_km",radiusKm);
            o.put("lat",map.centerLatitude());
            o.put("lon",map.centerLongitude());
            o.put("tiles",total);
            o.put("saved_at",System.currentTimeMillis());
            JSONArray b=new JSONArray();
            b.put(o);
            for(int i=0;i<a.length()&&i<9;i++)b.put(a.get(i));
            p.edit().putString(BATHY_AREAS,b.toString()).apply();
        }catch(Exception ignored){}
    }

    private void showSavedBathymetryAreas(){
        try{
            JSONArray a=new JSONArray(getSharedPreferences(BATHY_PREFS,MODE_PRIVATE).getString(BATHY_AREAS,"[]"));
            if(a.length()==0){
                new AlertDialog.Builder(this).setTitle("AREA OFFLINE").setMessage("Belum ada area kontur yang selesai diunduh.")
                        .setPositiveButton("OK",null).show();
                return;
            }
            StringBuilder b=new StringBuilder();
            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i);
                long ts=o.optLong("saved_at",0);
                String date=new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.US).format(new java.util.Date(ts));
                b.append("• Zoom ").append(o.optInt("zoom",0))
                 .append(" • radius ").append(o.optInt("radius_km",0)).append(" km")
                 .append(" • ").append(o.optInt("tiles",0)).append(" tile")
                 .append(" • ").append(date).append("\n");
            }
            new AlertDialog.Builder(this).setTitle("AREA BATHYMETRY OFFLINE")
                    .setMessage(b.toString())
                    .setPositiveButton("OK",null).show();
        }catch(Exception e){
            Toast.makeText(this,"Daftar area tidak dapat dibaca",Toast.LENGTH_SHORT).show();
        }
    }

    private void appMenu(){
        String[] items={
                "Offline Charts",
                "Route & Waypoints",
                "Sonar & NMEA",
                "AIS Targets",
                "Search Coordinate",
                "Settings"
        };
        new AlertDialog.Builder(this)
                .setTitle("SEMBULUNG NAVIGATOR")
                .setItems(items,(d,w)->{
                    if(w==0) startActivity(new Intent(this,OfflineMapActivity.class));
                    else if(w==1) startActivity(new Intent(this,NavigationActivity.class));
                    else if(w==2) startActivity(new Intent(this,NmeaActivity.class));
                    else if(w==3) startActivity(new Intent(this,AisActivity.class));
                    else if(w==4) startActivity(new Intent(this,SearchCoordinateActivity.class));
                    else if(w==5) startActivity(new Intent(this,SettingsActivity.class));
                })
                .show();
    }

    private void layerMenu(){
        String[] items={
                "Bathymetry GEBCO: "+(map!=null&&map.globalBathymetry()?"ON":"OFF"),
                "Sonar Chart (sounding lokal): "+(sonarEnabled?"ON":"OFF"),
                "Depth Shading: "+(sonarShading?"ON":"OFF"),
                "Contour Vector: "+(sonarContours?"ON":"OFF"),
                "Angka Sounding: "+(sonarSoundings?"ON":"OFF"),
                "Seamarks: "+(map.seamarks()?"ON":"OFF"),
                "AIS Kapal: "+(aisEnabled?"ON":"OFF"),
                "Buka Pengaturan"
        };
        new AlertDialog.Builder(this)
                .setTitle("LAYER PETA")
                .setItems(items,(d,w)->{
                    if(w==0){
                        boolean on=!(map!=null&&map.globalBathymetry());
                        if(map!=null)map.globalBathymetry(on);
                    }else if(w==1){
                        sonarEnabled=!sonarEnabled;
                        AppSettings.sonarEnabled(this,sonarEnabled);
                    }else if(w==2){
                        sonarShading=!sonarShading;
                    }else if(w==3){
                        sonarContours=!sonarContours;
                    }else if(w==4){
                        sonarSoundings=!sonarSoundings;
                    }else if(w==5){
                        map.seamarks(!map.seamarks());
                    }else if(w==6){
                        aisEnabled=!aisEnabled;
                        AppSettings.aisEnabled(this,aisEnabled);
                    }else if(w==7){
                        startActivity(new Intent(this,SettingsActivity.class));
                    }
                    applySonarLayers();
                    loadMapOverlays();
                }).show();
    }

    private void sonarMenu(){
        String stats=sonarChart==null?"Belum ada chart":
                String.format(Locale.US,"%d sounding • %.1f–%.1f m • %.2f ha",
                        sonarChart.stats.acceptedSoundings,
                        sonarChart.stats.minDepth,
                        sonarChart.stats.maxDepth,
                        sonarChart.stats.coverageSquareMeters/10000.0);

        String[] items={
                "Layer Sonar Chart: "+(sonarEnabled?"ON":"OFF"),
                "Depth Shading: "+(sonarShading?"ON":"OFF"),
                "Contour Vector: "+(sonarContours?"ON":"OFF"),
                "Soundings: "+(sonarSoundings?"ON":"OFF"),
                "Relief Bathymetry: "+(AppSettings.sonarRelief(this)?"ON":"OFF"),
                "Survey Coverage Mask: "+(AppSettings.sonarCoverageMask(this)?"ON":"OFF"),
                "Label Major Contour: "+(AppSettings.sonarContourLabels(this)?"ON":"OFF"),
                "Bangun Ulang Bathymetry",
                "Survey Center / Data Sonar",
                "Hapus Semua Sounding"
        };

        new AlertDialog.Builder(this)
                .setTitle("SONAR CHART VECTOR")
                .setMessage(stats)
                .setItems(items,(d,w)->{
                    if(w==0){
                        sonarEnabled=!sonarEnabled;
                        AppSettings.sonarEnabled(this,sonarEnabled);
                    }else if(w==1){
                        sonarShading=!sonarShading;
                    }else if(w==2){
                        sonarContours=!sonarContours;
                    }else if(w==3){
                        sonarSoundings=!sonarSoundings;
                    }else if(w==4){
                        AppSettings.sonarRelief(this,!AppSettings.sonarRelief(this));
                    }else if(w==5){
                        AppSettings.sonarCoverageMask(this,!AppSettings.sonarCoverageMask(this));
                    }else if(w==6){
                        AppSettings.sonarContourLabels(this,!AppSettings.sonarContourLabels(this));
                    }else if(w==7){
                        lastBathymetryProfile="";
                        rebuildSonarChart();
                    }else if(w==8){
                        startActivity(new Intent(this,SonarSurveyActivity.class));
                    }else if(w==9){
                        confirmClearSonar();
                    }
                    applySonarLayers();
                }).show();
    }

    private void confirmClearSonar(){
        new AlertDialog.Builder(this)
                .setTitle("Hapus Sounding?")
                .setMessage("Data mentah sounding akan dihapus dari perangkat.")
                .setNegativeButton("Batal",null)
                .setPositiveButton("Hapus",(d,w)->{
                    sonarStore.clear();
                    sonarSamples.clear();
                    sonarChart=null;
                    lastSoundingLat=null;
                    lastSoundingLon=null;
                    lastSoundingAt=0L;
                    newSoundings=0;
                    map.sonarChart(null);
                    updateTitle();
                    Toast.makeText(this,"Sounding dihapus",Toast.LENGTH_LONG).show();
                }).show();
    }

    private void applyWindowSettings(){
        if(AppSettings.keepScreenOn(this)){
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }else{
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        sonarEnabled=AppSettings.sonarEnabled(this);
        aisEnabled=AppSettings.aisEnabled(this);
    }

    private void loadMapOverlays(){
        if(map==null)return;
        List<WaypointStore.Waypoint> route=WaypointStore.load(this);
        map.waypoints(route,WaypointStore.activeIndex(this));
        List<AisTarget> targets=AisTargetStore.load(this,120000L);
        map.aisTargets(targets,aisEnabled);
        if(aisChip!=null){
            aisChip.setText(aisEnabled?"AIS "+targets.size():"AIS OFF");
            aisChip.setAlpha(aisEnabled?1f:.55f);
        }
    }

    private void applySonarLayers(){
        if(map!=null){
            map.sonarLayers(sonarEnabled,sonarShading,sonarContours,sonarSoundings);
            double renderInterval=sonarChart!=null&&sonarChart.stats!=null&&Double.isFinite(sonarChart.stats.contourIntervalMeters)
                    ?sonarChart.stats.contourIntervalMeters
                    :BathymetryStyle.contourInterval(
                            AppSettings.contourIntervalMeters(this),
                            AppSettings.sonarDensity(this),
                            map.zoomLevel());
            map.sonarRenderOptions(
                    AppSettings.sonarDensity(this),
                    AppSettings.sonarRelief(this),
                    AppSettings.sonarCoverageMask(this),
                    AppSettings.sonarContourLabels(this),
                    renderInterval);
        }
        if(bathyButton!=null)bathyButton.setText(map!=null&&map.globalBathymetry()?"BATHY":"B OFF");
        if(sonarButton!=null){
            sonarButton.setText(sonarEnabled?"SC ON":"SC OFF");
            sonarButton.setAlpha(sonarEnabled?1f:.55f);
        }
        updateTitle();
    }

    private void updateTitle(){
        if(title==null)return;
        int count=sonarChart!=null?sonarChart.stats.acceptedSoundings:sonarSamples.size();
        MarineServiceState.Snapshot ms=MarineServiceState.read(this);
        String density=AppSettings.sonarDensity(this);
        title.setText("SEMBULUNG MARINE • V20 • "+(ms.running?"SERVICE LIVE":"SERVICE OFF")+" • "+count+" SOUNDING • "+density);
    }

    private void maybeRecordSonar(){
        if(!sonarEnabled)return;
        if(nmeaSnapshot==null
                ||!nmeaSnapshot.positionFresh(FRESH_MS)
                ||!nmeaSnapshot.depthFresh(FRESH_MS)
                ||nmeaSnapshot.lat==null
                ||nmeaSnapshot.lon==null
                ||nmeaSnapshot.depth==null)return;

        if(nmeaSnapshot.depth<=0||nmeaSnapshot.depth>2000)return;

        long now=System.currentTimeMillis();
        double moved=lastSoundingLat==null
                ?Double.POSITIVE_INFINITY
                :haversineMeters(lastSoundingLat,lastSoundingLon,nmeaSnapshot.lat,nmeaSnapshot.lon);

        if(moved<4.0&&now-lastSoundingAt<4000L)return;

        long skew=Math.abs(nmeaSnapshot.positionTime-nmeaSnapshot.depthTime);
        DepthSample.Quality quality=skew>3000L
                ?DepthSample.Quality.QUESTIONABLE
                :DepthSample.Quality.GOOD;
        if(nmeaSnapshot.speed!=null&&nmeaSnapshot.speed>35){
            quality=DepthSample.Quality.QUESTIONABLE;
        }

        DepthSample sample=new DepthSample(
                nmeaSnapshot.lat,
                nmeaSnapshot.lon,
                nmeaSnapshot.depth,
                now,
                nmeaSnapshot.speed,
                nmeaSnapshot.headingFresh(FRESH_MS)?nmeaSnapshot.heading:null,
                quality,
                nmeaSnapshot.source);

        try{
            sonarStore.append(sample);
            if(sonarSessions!=null)sonarSessions.append(sample);
            sonarSamples.add(sample);
            if(sonarSamples.size()>5000)sonarSamples.remove(0);
            lastSoundingLat=nmeaSnapshot.lat;
            lastSoundingLon=nmeaSnapshot.lon;
            lastSoundingAt=now;
            newSoundings++;
            if(sonarSamples.size()>=3&&(sonarChart==null||newSoundings>=3)){
                rebuildSonarChart();
            }
            updateTitle();
        }catch(Exception e){
            Toast.makeText(this,"Gagal menyimpan sounding: "+e.getMessage(),Toast.LENGTH_SHORT).show();
        }
    }

    private void rebuildSonarChart(){
        if(map==null||!chartBuilding.compareAndSet(false,true))return;

        final String density=AppSettings.sonarDensity(this);
        final int zoom=map.zoomLevel();
        final double configured=AppSettings.contourIntervalMeters(this);
        final double cellMeters=BathymetryStyle.cellMeters(density,zoom);
        final double interval=BathymetryStyle.contourInterval(configured,density,zoom);
        final String profile=BathymetryStyle.profileKey(density,zoom,configured);
        lastBathymetryProfile=profile;

        final ArrayList<DepthSample> copy=new ArrayList<>(
                SonarSampleFilter.apply(sonarSamples,AppSettings.sonarQualityMode(this)));

        new Thread(()->{
            try{
                SonarChartEngine.Chart chart=SonarChartEngine.build(copy,cellMeters,interval);
                runOnUiThread(()->{
                    sonarChart=chart;
                    newSoundings=0;
                    map.sonarChart(chart);
                    map.sonarRenderOptions(
                            density,
                            AppSettings.sonarRelief(this),
                            AppSettings.sonarCoverageMask(this),
                            AppSettings.sonarContourLabels(this),
                            interval);
                    updateTitle();
                });
            }finally{
                chartBuilding.set(false);
            }
        },"Sembulung-Bathymetry-V20").start();
    }

    private void maybeRebuildBathymetryForZoom(){
        if(map==null||sonarSamples.size()<3||chartBuilding.get())return;
        String profile=BathymetryStyle.profileKey(
                AppSettings.sonarDensity(this),
                map.zoomLevel(),
                AppSettings.contourIntervalMeters(this));
        if(!profile.equals(lastBathymetryProfile))rebuildSonarChart();
    }

    private void startGps(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            },REQ_LOCATION);
            return;
        }

        if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
            if(gpsChip!=null)gpsChip.setText("GPS OFF");
            return;
        }

        locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0.5f,
                this);

        Location last=locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if(last!=null)onLocationChanged(last);
    }

    @Override public void onLocationChanged(Location location){
        phoneLocation=location;
        refresh();
    }

    @Override public void onProviderEnabled(String provider){}
    @Override public void onProviderDisabled(String provider){refresh();}

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode==REQ_LOCATION&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED){
            startGps();
        }
    }

    private boolean nmeaPositionFresh(){
        return nmeaSnapshot!=null&&nmeaSnapshot.positionFresh(FRESH_MS);
    }

    private boolean useNmea(){
        return sourceMode==2
                ?nmeaPositionFresh()
                :sourceMode==0&&nmeaPositionFresh();
    }

    private Double currentLat(){
        if(sourceMode==2)return nmeaPositionFresh()?nmeaSnapshot.lat:null;
        if(useNmea())return nmeaSnapshot.lat;
        return phoneLocation==null?null:phoneLocation.getLatitude();
    }

    private Double currentLon(){
        if(sourceMode==2)return nmeaPositionFresh()?nmeaSnapshot.lon:null;
        if(useNmea())return nmeaSnapshot.lon;
        return phoneLocation==null?null:phoneLocation.getLongitude();
    }

    private Double currentSpeed(){
        if(useNmea()&&nmeaSnapshot!=null&&nmeaSnapshot.speed!=null)return nmeaSnapshot.speed;
        if(sourceMode!=2&&phoneLocation!=null&&phoneLocation.hasSpeed())return (double)(phoneLocation.getSpeed()*1.94384449);
        return null;
    }

    private Double currentHeading(){
        if(useNmea()&&nmeaSnapshot.headingFresh(FRESH_MS))return nmeaSnapshot.heading;
        if(sourceMode!=2&&phoneLocation!=null&&phoneLocation.hasBearing()){
            return (double)phoneLocation.getBearing();
        }
        return null;
    }

    private void refresh(){
        nmeaSnapshot=NmeaDataStore.read(this);

        if(phoneLocation!=null){
            String accuracy=phoneLocation.hasAccuracy()
                    ?String.format(Locale.US,"%.0fm",phoneLocation.getAccuracy())
                    :"--";
            gpsChip.setText("GPS "+accuracy);
            gpsChip.setAlpha(sourceMode==2?.55f:1f);
        }else{
            gpsChip.setText("GPS --");
            gpsChip.setAlpha(.55f);
        }

        boolean packetFresh=nmeaSnapshot!=null&&nmeaSnapshot.packetFresh(FRESH_MS);
        nmeaChip.setText(packetFresh?"NMEA LIVE":"NMEA --");
        nmeaChip.setAlpha(packetFresh?1f:.55f);

        if(nmeaSnapshot!=null&&nmeaSnapshot.depthFresh(FRESH_MS)){
            String unit=AppSettings.depthUnit(this);
            double value=nmeaSnapshot.depth;
            if("ft".equals(unit))value*=3.280839895;
            depthChip.setText(String.format(Locale.US,"DEPTH %.1f%s",value,unit));
            depthChip.setAlpha(1f);
        }else{
            depthChip.setText("DEPTH --");
            depthChip.setAlpha(.55f);
        }

        sourceButton.setText(sourceMode==0?"AUTO":sourceMode==1?"GPS":"NMEA");
        sourceButton.setAlpha(sourceMode==2&&!nmeaPositionFresh()?.55f:1f);

        map.vessel(
                currentLat(),
                currentLon(),
                currentHeading(),
                useNmea(),
                AppSettings.autoCenter(this));
        map.aisOwnShip(currentSpeed(),currentHeading());

        loadMapOverlays();
        maybeRecordSonar();
        maybeRebuildBathymetryForZoom();
        updateGuidanceAndSafety();

        if(AppSettings.shallowWarning(this)
                &&nmeaSnapshot!=null
                &&nmeaSnapshot.depthFresh(FRESH_MS)
                &&nmeaSnapshot.depth!=null
                &&nmeaSnapshot.depth<AppSettings.shallowMeters(this)){
            depthChip.setTextColor(0xffff6b6b);
        }else{
            depthChip.setTextColor(Color.WHITE);
        }
    }

    private void updateGuidanceAndSafety(){
        if(routeGuidance==null||safetyGuidance==null)return;

        List<WaypointStore.Waypoint> route=WaypointStore.load(this);
        int active=WaypointStore.activeIndex(this);
        Double lat=currentLat(),lon=currentLon(),speed=currentSpeed(),course=currentHeading();

        boolean routeActive=active>=0&&active<route.size();
        guidancePanel.setVisibility(routeActive?android.view.View.VISIBLE:android.view.View.GONE);
        skipWaypointButton.setEnabled(routeActive);
        endRouteButton.setEnabled(routeActive);
        skipWaypointButton.setVisibility(routeActive?android.view.View.VISIBLE:android.view.View.GONE);
        endRouteButton.setVisibility(routeActive?android.view.View.VISIBLE:android.view.View.GONE);

        RouteGuidanceEngine.Guidance g=null;
        if(routeActive&&lat!=null&&lon!=null){
            g=RouteGuidanceEngine.assess(
                    lat,lon,speed,route,active,AppSettings.arrivalRadiusNm(this));
        }

        if(g==null){
            routeGuidance.setText(routeActive
                    ?"NAVIGASI • "+route.get(active).name+" • menunggu posisi"
                    :"NAVIGASI • belum ada tujuan aktif");
            routeGuidance.setTextColor(Color.WHITE);
        }else{
            if(g.arrived){
                long now=System.currentTimeMillis();
                if(lastArrivalIndex!=active||now-lastArrivalAt>15000L){
                    lastArrivalIndex=active;
                    lastArrivalAt=now;
                    Toast.makeText(this,"Tiba di "+g.targetName,Toast.LENGTH_SHORT).show();
                }
                if(AppSettings.autoAdvanceRoute(this)){
                    int next=RouteGuidanceEngine.nextIndex(active,route.size(),true,true);
                    WaypointStore.setActiveIndex(this,next);
                    if(next>=0){
                        Toast.makeText(this,"Auto lanjut ke "+route.get(next).name,Toast.LENGTH_SHORT).show();
                    }else{
                        Toast.makeText(this,"Rute selesai",Toast.LENGTH_LONG).show();
                    }
                    loadMapOverlays();
                    return;
                }
            }

            String xte=Double.isNaN(g.xteNm)?"--":String.format(Locale.US,"%.2f NM",Math.abs(g.xteNm));
            String eta=Double.isNaN(g.etaMinutes)?"--":formatMinutes(g.etaMinutes);
            routeGuidance.setText(String.format(Locale.US,
                    "▶ %s • DTW %.2f NM • BTW %.0f° • XTE %s • ETA %s%s",
                    g.targetName,g.distanceNm,g.bearingDeg,xte,eta,g.arrived?" • TIBA":""));
            routeGuidance.setTextColor(g.arrived?0xff8fffc0:Color.WHITE);
        }

        boolean offRoute=g!=null&&!Double.isNaN(g.xteNm)&&Math.abs(g.xteNm)>AppSettings.offRouteNm(this);
        SonarHazardEngine.Assessment hazard=SonarHazardEngine.assessAhead(
                sonarChart,
                lat==null?0.0:lat,
                lon==null?0.0:lon,
                course,
                speed,
                AppSettings.shallowMeters(this),
                AppSettings.lookAheadMinutes(this),
                60.0);

        StringBuilder safety=new StringBuilder("SAFETY");
        int severity=0;
        if(offRoute){
            severity=Math.max(severity,1);
            safety.append(String.format(Locale.US," • OFF ROUTE %.2f NM",Math.abs(g.xteNm)));
        }
        if(AppSettings.shallowAheadWarning(this)
                &&(hazard.risk==SonarHazardEngine.Risk.WARNING||hazard.risk==SonarHazardEngine.Risk.DANGER)){
            severity=Math.max(severity,hazard.risk==SonarHazardEngine.Risk.DANGER?2:1);
            safety.append(String.format(Locale.US," • SHALLOW AHEAD %.1fm @ %.0fm",
                    hazard.minimumDepthMeters,hazard.distanceAheadMeters));
        }
        if(severity==0)safety.append(" • normal");

        safetyGuidance.setText(safety.toString());
        safetyGuidance.setTextColor(severity==2?0xffff6575:severity==1?0xffffc04a:0xff8fffc0);

        String key=severity+":"+safety;
        if(severity>0&&!key.equals(lastSafetyKey)){
            Toast.makeText(this,safety.toString(),Toast.LENGTH_LONG).show();
        }
        lastSafetyKey=key;
    }

    private String formatMinutes(double minutes){
        if(!Double.isFinite(minutes)||minutes<0)return "--";
        long m=Math.round(minutes);
        return String.format(Locale.US,"%02d:%02d",m/60,m%60);
    }

    private void recenter(){
        if(currentLat()==null||currentLon()==null){
            Toast.makeText(this,"Posisi belum tersedia",Toast.LENGTH_SHORT).show();
            startGps();
            return;
        }
        map.recenter();
    }

    private static double haversineMeters(double lat1,double lon1,double lat2,double lon2){
        double r=6371000.0;
        double p1=Math.toRadians(lat1);
        double p2=Math.toRadians(lat2);
        double dp=Math.toRadians(lat2-lat1);
        double dl=Math.toRadians(lon2-lon1);
        double q=Math.sin(dp/2)*Math.sin(dp/2)
                +Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);
        return r*2*Math.atan2(Math.sqrt(q),Math.sqrt(1-q));
    }

    @Override protected void onResume(){
        super.onResume();
        applyWindowSettings();
        applySonarLayers();
        loadMapOverlays();
        if(locationManager!=null&&map!=null)startGps();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override protected void onPause(){
        handler.removeCallbacks(tick);
        try{
            if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){
                locationManager.removeUpdates(this);
            }
        }catch(Exception ignored){}
        super.onPause();
    }

    @Override protected void onDestroy(){
        handler.removeCallbacksAndMessages(null);
        if(tileLoader!=null)tileLoader.close();
        super.onDestroy();
    }

    private TextView chip(String text,int sp,boolean bold){
        TextView v=new TextView(this); v.setText(text); v.setTextColor(Color.WHITE); v.setTextSize(sp); v.setGravity(Gravity.CENTER); v.setPadding(dp(7),dp(2),dp(7),dp(2)); if(bold)v.setTypeface(Typeface.DEFAULT_BOLD); v.setBackground(bg()); return v;
    }

    private Button floating(String text,int sp){
        Button b=new Button(this); b.setText(text); b.setTextSize(sp); b.setTextColor(Color.WHITE); b.setAllCaps(false); b.setGravity(Gravity.CENTER); b.setPadding(0,0,0,0); b.setMinWidth(0); b.setMinHeight(0); b.setBackground(bg()); return b;
    }

    private Button navButton(String text){
        Button b=floating(text,10);
        return b;
    }

    private GradientDrawable bg(){
        GradientDrawable d=new GradientDrawable(); d.setColor(0xd10a2136); d.setCornerRadius(dp(15)); d.setStroke(dp(1),0x6657b9dd); return d;
    }

    private LinearLayout.LayoutParams chipLp(){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(34),1f);
        p.setMargins(dp(2),0,dp(2),0);
        return p;
    }

    private LinearLayout.LayoutParams square(){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(44),dp(42)); p.setMargins(0,dp(3),0,dp(3)); return p;
    }

    private LinearLayout.LayoutParams navLp(){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1f); p.setMargins(dp(2),0,dp(2),0); return p;
    }

    private int dp(int v){
        return Math.round(v*getResources().getDisplayMetrics().density);
    }
}
