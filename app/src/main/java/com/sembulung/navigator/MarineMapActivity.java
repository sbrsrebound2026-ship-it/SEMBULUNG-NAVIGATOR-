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
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.sembulung.navigator.ais.AisTarget;
import com.sembulung.navigator.ais.AisTargetStore;
import com.sembulung.navigator.sonar.DepthSample;
import com.sembulung.navigator.sonar.SonarChartEngine;
import com.sembulung.navigator.sonar.SonarChartStore;

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

    private int sourceMode=0; // 0 auto, 1 device GPS, 2 NMEA

    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){
        @Override public void run(){
            refresh();
            handler.postDelayed(this,500L);
        }
    };

    private SonarChartStore sonarStore;
    private final ArrayList<DepthSample> sonarSamples=new ArrayList<>();
    private SonarChartEngine.Chart sonarChart;
    private final AtomicBoolean chartBuilding=new AtomicBoolean(false);
    private long lastSoundingAt;
    private Double lastSoundingLat;
    private Double lastSoundingLon;
    private int newSoundings;

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
        buildBottomNavigation(root);

        setContentView(root);

        Intent intent=getIntent();
        if(intent!=null&&intent.hasExtra("focus_lat")&&intent.hasExtra("focus_lon")){
            map.focus(
                    intent.getDoubleExtra("focus_lat",0.0),
                    intent.getDoubleExtra("focus_lon",0.0));
        }

        sonarStore=new SonarChartStore(this);
        sonarSamples.addAll(sonarStore.load(5000));
        applySonarLayers();
        loadMapOverlays();
        if(sonarSamples.size()>=3)rebuildSonarChart();

        startGps();
        refresh();
    }

    private void buildHeader(FrameLayout root){
        LinearLayout top=new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(10),dp(10),dp(10),0);

        title=chip("SEMBULUNG MARINE • UNIFIED MAP",14,true);
        title.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        top.addView(title,new LinearLayout.LayoutParams(-1,dp(38)));

        LinearLayout row=new LinearLayout(this);
        gpsChip=chip("GPS --",9,true);
        nmeaChip=chip("NMEA --",9,true);
        depthChip=chip("DEPTH --",9,true);
        aisChip=chip("AIS --",9,true);
        row.addView(gpsChip,chipLp());
        row.addView(nmeaChip,chipLp());
        row.addView(depthChip,chipLp());
        row.addView(aisChip,chipLp());
        top.addView(row,new LinearLayout.LayoutParams(-1,dp(36)));

        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-2);
        p.gravity=Gravity.TOP;
        root.addView(top,p);
    }

    private void buildRightToolbar(FrameLayout root){
        LinearLayout right=new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);

        Button layers=floating("LAYER",9);
        layers.setOnClickListener(v->layerMenu());
        right.addView(layers,square());

        Button search=floating("CARI",9);
        search.setOnClickListener(v->startActivity(new Intent(this,SearchCoordinateActivity.class)));
        right.addView(search,square());

        Button download=floating("UNDUH",9);
        download.setOnClickListener(v->startActivity(new Intent(this,OfflineMapActivity.class)));
        right.addView(download,square());

        Button plus=floating("+",22);
        plus.setOnClickListener(v->map.zoom(1));
        right.addView(plus,square());

        Button center=floating("◎",20);
        center.setOnClickListener(v->recenter());
        right.addView(center,square());

        Button minus=floating("−",22);
        minus.setOnClickListener(v->map.zoom(-1));
        right.addView(minus,square());

        sonarButton=floating("SC",10);
        sonarButton.setOnClickListener(v->{
            sonarEnabled=!sonarEnabled;
            AppSettings.sonarEnabled(this,sonarEnabled);
            applySonarLayers();
        });
        sonarButton.setOnLongClickListener(v->{
            sonarMenu();
            return true;
        });
        right.addView(sonarButton,square());

        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-2,-2);
        p.gravity=Gravity.END|Gravity.CENTER_VERTICAL;
        p.setMargins(0,dp(52),dp(8),dp(70));
        root.addView(right,p);
    }

    private void buildBottomNavigation(FrameLayout root){
        LinearLayout bottom=new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setPadding(dp(6),dp(3),dp(6),dp(3));
        bottom.setBackground(bg());

        Button peta=navButton("PETA");
        peta.setEnabled(false);
        bottom.addView(peta,navLp());

        Button route=navButton("RUTE");
        route.setOnClickListener(v->startActivity(new Intent(this,NavigationActivity.class)));
        bottom.addView(route,navLp());

        Button sonar=navButton("SONAR");
        sonar.setOnClickListener(v->startActivity(new Intent(this,NmeaActivity.class)));
        bottom.addView(sonar,navLp());

        Button ais=navButton("AIS");
        ais.setOnClickListener(v->startActivity(new Intent(this,AisActivity.class)));
        bottom.addView(ais,navLp());

        Button settings=navButton("SET");
        settings.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));
        bottom.addView(settings,navLp());

        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,dp(58));
        p.gravity=Gravity.BOTTOM;
        p.setMargins(dp(8),0,dp(8),dp(8));
        root.addView(bottom,p);

        sourceButton=floating("AUTO",11);
        sourceButton.setOnClickListener(v->{
            sourceMode=(sourceMode+1)%3;
            refresh();
        });
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(dp(86),dp(40));
        sp.gravity=Gravity.START|Gravity.BOTTOM;
        sp.setMargins(dp(12),0,0,dp(72));
        root.addView(sourceButton,sp);
    }

    private void menu(){layerMenu();}

    private void layerMenu(){
        String[] items={
                "Sonar Chart: "+(sonarEnabled?"ON":"OFF"),
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
                        sonarEnabled=!sonarEnabled;
                        AppSettings.sonarEnabled(this,sonarEnabled);
                    }else if(w==1){
                        sonarShading=!sonarShading;
                    }else if(w==2){
                        sonarContours=!sonarContours;
                    }else if(w==3){
                        sonarSoundings=!sonarSoundings;
                    }else if(w==4){
                        map.seamarks(!map.seamarks());
                    }else if(w==5){
                        aisEnabled=!aisEnabled;
                        AppSettings.aisEnabled(this,aisEnabled);
                    }else if(w==6){
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
                "Bangun Ulang Kontur",
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
                        rebuildSonarChart();
                    }else if(w==5){
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
        map.waypoints(WaypointStore.load(this));
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
        }
        if(sonarButton!=null){
            sonarButton.setText(sonarEnabled?"SC ON":"SC OFF");
            sonarButton.setAlpha(sonarEnabled?1f:.55f);
        }
        updateTitle();
    }

    private void updateTitle(){
        if(title==null)return;
        int count=sonarChart!=null?sonarChart.stats.acceptedSoundings:sonarSamples.size();
        title.setText("SEMBULUNG MARINE • V16 • "+count+" SOUNDING");
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
        if(!chartBuilding.compareAndSet(false,true))return;
        final ArrayList<DepthSample> copy=new ArrayList<>(sonarSamples);

        new Thread(()->{
            try{
                SonarChartEngine.Chart chart=SonarChartEngine.build(copy,20.0,5.0);
                runOnUiThread(()->{
                    sonarChart=chart;
                    newSoundings=0;
                    map.sonarChart(chart);
                    updateTitle();
                });
            }finally{
                chartBuilding.set(false);
            }
        },"Sembulung-SonarChart").start();
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

        loadMapOverlays();
        maybeRecordSonar();

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
        TextView v=new TextView(this);
        v.setText(text);
        v.setTextColor(Color.WHITE);
        v.setTextSize(sp);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(7),dp(3),dp(7),dp(3));
        if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setBackground(bg());
        return v;
    }

    private Button floating(String text,int sp){
        Button b=new Button(this);
        b.setText(text);
        b.setTextSize(sp);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setPadding(0,0,0,0);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setBackground(bg());
        return b;
    }

    private Button navButton(String text){
        Button b=floating(text,10);
        return b;
    }

    private GradientDrawable bg(){
        GradientDrawable d=new GradientDrawable();
        d.setColor(0xd809213b);
        d.setCornerRadius(dp(13));
        d.setStroke(dp(1),0x6659c9f2);
        return d;
    }

    private LinearLayout.LayoutParams chipLp(){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(34),1f);
        p.setMargins(dp(2),0,dp(2),0);
        return p;
    }

    private LinearLayout.LayoutParams square(){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(58),dp(42));
        p.setMargins(0,dp(2),0,dp(2));
        return p;
    }

    private LinearLayout.LayoutParams navLp(){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1f);
        p.setMargins(dp(2),0,dp(2),0);
        return p;
    }

    private int dp(int v){
        return Math.round(v*getResources().getDisplayMetrics().density);
    }
}
