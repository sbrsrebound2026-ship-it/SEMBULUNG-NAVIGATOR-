package com.sembulung.navigator;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.*;
import android.os.*;
import android.view.Gravity;
import android.widget.*;

import com.sembulung.navigator.sonar.DepthSample;
import com.sembulung.navigator.sonar.SonarChartEngine;
import com.sembulung.navigator.sonar.SonarChartStore;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class MarineMapActivity extends Activity implements LocationListener {
    private static final int R=1301;
    private static final long F=5000;

    private LocationManager lm;
    private Location phone;
    private NmeaDataStore.Snapshot n;
    private MarineTileLoader loader;
    private MarineMapView map;
    private TextView title,gps,nmea,depth,ais;
    private Button source,sonarButton;
    private int mode=0;

    private final Handler h=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){public void run(){refresh();h.postDelayed(this,500);}};

    private SonarChartStore sonarStore;
    private final ArrayList<DepthSample> sonarSamples=new ArrayList<>();
    private SonarChartEngine.Chart sonarChart;
    private final AtomicBoolean chartBuilding=new AtomicBoolean(false);
    private long lastSoundingAt=0;
    private Double lastSoundingLat,lastSoundingLon;
    private int newSoundings=0;

    private boolean sonarEnabled=true;\n    private boolean aisEnabled=true;
    private boolean sonarShading=true;
    private boolean sonarContours=true;
    private boolean sonarSoundings=false;

    protected void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(3,27,61));
        getWindow().setNavigationBarColor(Color.rgb(3,27,61));
        lm=(LocationManager)getSystemService(LOCATION_SERVICE);
        applyWindowSettings();

        FrameLayout root=new FrameLayout(this);
        loader=new MarineTileLoader(this,()->{if(map!=null)map.postInvalidate();});
        map=new MarineMapView(this,loader);
        root.addView(map,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top=new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(10),dp(10),dp(10),0);
        title=chip("SEMBULUNG MARINE • SONAR CHART",15,true);
        title.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        top.addView(title,new LinearLayout.LayoutParams(-2,dp(38)));

        LinearLayout row=new LinearLayout(this);
        gps=chip("GPS --",10,true);
        nmea=chip("NMEA --",10,true);
        depth=chip("DEPTH --",10,true);
        row.addView(gps,chipLp());row.addView(nmea,chipLp());row.addView(depth,chipLp());
        top.addView(row,new LinearLayout.LayoutParams(-1,dp(38)));
        FrameLayout.LayoutParams tlp=new FrameLayout.LayoutParams(-1,-2);
        tlp.gravity=Gravity.TOP;
        root.addView(top,tlp);

        LinearLayout right=new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);

        Button plus=floating("+",22);plus.setOnClickListener(v->map.zoom(1));right.addView(plus,square());
        Button center=floating("◎",20);center.setOnClickListener(v->recenter());right.addView(center,square());
        Button minus=floating("−",22);minus.setOnClickListener(v->map.zoom(-1));right.addView(minus,square());

        sonarButton=floating("SC",12);
        sonarButton.setOnClickListener(v->{sonarEnabled=!sonarEnabled;applySonarLayers();});
        sonarButton.setOnLongClickListener(v->{sonarMenu();return true;});
        right.addView(sonarButton,square());

        FrameLayout.LayoutParams rlp=new FrameLayout.LayoutParams(-2,-2);
        rlp.gravity=Gravity.END|Gravity.CENTER_VERTICAL;
        rlp.setMargins(0,0,dp(10),0);
        root.addView(right,rlp);

        source=floating("AUTO",12);
        source.setOnClickListener(v->{mode=(mode+1)%3;refresh();});
        FrameLayout.LayoutParams slp=new FrameLayout.LayoutParams(dp(88),dp(44));
        slp.gravity=Gravity.START|Gravity.BOTTOM;slp.setMargins(dp(12),0,0,dp(30));
        root.addView(source,slp);

        Button menu=floating("☰",18);
        menu.setOnClickListener(v->menu());
        FrameLayout.LayoutParams mlp=new FrameLayout.LayoutParams(dp(50),dp(50));
        mlp.gravity=Gravity.END|Gravity.BOTTOM;mlp.setMargins(0,0,dp(12),dp(28));
        root.addView(menu,mlp);

        TextView att=chip("© OpenStreetMap contributors • Seamarks © OpenSeaMap",8,false);
        FrameLayout.LayoutParams alp=new FrameLayout.LayoutParams(-2,dp(22));
        alp.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL;alp.setMargins(0,0,0,dp(4));
        root.addView(att,alp);

        setContentView(root);

        Intent intent=getIntent();
        if(intent!=null&&intent.hasExtra("focus_lat")&&intent.hasExtra("focus_lon")){
            map.focus(intent.getDoubleExtra("focus_lat",0),intent.getDoubleExtra("focus_lon",0));
        }

        sonarStore=new SonarChartStore(this);
        sonarSamples.addAll(sonarStore.load(5000));
        applySonarLayers();
        if(sonarSamples.size()>=3)rebuildSonarChart();

        startGps();
        refresh();
    }

    private void menu(){
        String s=map.seamarks()?"Sembunyikan seamarks":"Tampilkan seamarks";
        String[] a={s,"Layer Peta","Cari Koordinat","Peta Offline / Download","Sonar / NMEA","AIS • CPA / TCPA","Pengaturan","Dashboard"};
        new AlertDialog.Builder(this).setTitle("Marine Map")
                .setItems(a,(d,w)->{
                    if(w==0)map.seamarks(!map.seamarks());
                    else if(w==1)sonarMenu();
                    else if(w==2)startActivity(new Intent(this,OfflineMapActivity.class));
                    else if(w==3)startActivity(new Intent(this,NmeaActivity.class));
                    else if(w==4)startActivity(new Intent(this,AisActivity.class));
                    else finish();
                }).show();
    }

    private void layerMenu(){
        String[] items={
                "Sonar Chart: "+(sonarEnabled?"ON":"OFF"),
                "Depth Shading: "+(sonarShading?"ON":"OFF"),
                "Contour Vector: "+(sonarContours?"ON":"OFF"),
                "Angka Sounding: "+(sonarSoundings?"ON":"OFF"),
                "Seamarks: "+(map.seamarks()?"ON":"OFF"),
                "AIS Kapal: "+(aisEnabled?"ON":"OFF"),
                "Waypoint / Rute: ON"
        };
        new AlertDialog.Builder(this).setTitle("LAYER PETA").setItems(items,(d,w)->{
            if(w==0){sonarEnabled=!sonarEnabled;AppSettings.sonarEnabled(this,sonarEnabled);}
            else if(w==1)sonarShading=!sonarShading;
            else if(w==2)sonarContours=!sonarContours;
            else if(w==3)sonarSoundings=!sonarSoundings;
            else if(w==4)map.seamarks(!map.seamarks());
            else if(w==5){aisEnabled=!aisEnabled;AppSettings.aisEnabled(this,aisEnabled);}
            applySonarLayers();
            loadMapOverlays();
        }).show();
    }

    private void sonarMenu(){
        String stats=sonarChart==null?"Belum ada chart":
                String.format(Locale.US,"%d sounding • %.1f–%.1f m • %.2f ha",
                        sonarChart.stats.acceptedSoundings,sonarChart.stats.minDepth,sonarChart.stats.maxDepth,
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
                .setMessage(stats+"\n\nTekan lama tombol SC untuk membuka panel ini.")
                .setItems(items,(d,w)->{
                    if(w==0)sonarEnabled=!sonarEnabled;
                    else if(w==1)sonarShading=!sonarShading;
                    else if(w==2)sonarContours=!sonarContours;
                    else if(w==3)sonarSoundings=!sonarSoundings;
                    else if(w==4)rebuildSonarChart();
                    else if(w==5)confirmClearSonar();
                    applySonarLayers();
                }).show();
    }

    private void confirmClearSonar(){
        new AlertDialog.Builder(this)
                .setTitle("Hapus Sounding?")
                .setMessage("Data mentah sounding akan dihapus dari perangkat. Tindakan ini tidak dapat dibatalkan.")
                .setNegativeButton("Batal",null)
                .setPositiveButton("Hapus",(d,w)->{
                    sonarStore.clear();
                    sonarSamples.clear();
                    sonarChart=null;
                    lastSoundingLat=null;lastSoundingLon=null;lastSoundingAt=0;newSoundings=0;
                    map.sonarChart(null);
                    updateSonarTitle();
                    Toast.makeText(this,"Sounding Sonar Chart dihapus",Toast.LENGTH_LONG).show();
                }).show();
    }

    private void applyWindowSettings(){
        if(AppSettings.keepScreenOn(this))getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sonarEnabled=AppSettings.sonarEnabled(this);
        aisEnabled=AppSettings.aisEnabled(this);
    }

    private void loadMapOverlays(){
        if(map==null)return;
        map.waypoints(WaypointStore.load(this));
        java.util.List<com.sembulung.navigator.ais.AisTarget> targets=
                com.sembulung.navigator.ais.AisTargetStore.load(this,120000L);
        map.aisTargets(targets,aisEnabled);
        if(ais!=null){
            ais.setText(aisEnabled?"AIS "+targets.size():"AIS OFF");
            ais.setAlpha(aisEnabled?1f:.55f);
        }
    }

    private void applySonarLayers(){
        if(map!=null)map.sonarLayers(sonarEnabled,sonarShading,sonarContours,sonarSoundings);
        if(sonarButton!=null){
            sonarButton.setText(sonarEnabled?"SC ON":"SC OFF");
            sonarButton.setAlpha(sonarEnabled?1f:.55f);
        }
        updateSonarTitle();
    }

    private void updateSonarTitle(){
        if(title==null)return;
        int count=sonarChart!=null?sonarChart.stats.acceptedSoundings:sonarSamples.size();
        title.setText("SEMBULUNG MARINE • SONAR CHART "+(sonarEnabled?"LIVE":"OFF")+" • "+count+" SOUNDING");
    }

    private void maybeRecordSonar(){
        if(n==null||!n.positionFresh(F)||!n.depthFresh(F)||n.lat==null||n.lon==null||n.depth==null)return;
        if(n.depth<=0||n.depth>2000)return;

        long now=System.currentTimeMillis();
        double moved=lastSoundingLat==null?Double.POSITIVE_INFINITY:haversineMeters(lastSoundingLat,lastSoundingLon,n.lat,n.lon);
        if(moved<4.0&&now-lastSoundingAt<4000)return;

        long skew=Math.abs(n.positionTime-n.depthTime);
        DepthSample.Quality quality=skew>3000?DepthSample.Quality.QUESTIONABLE:DepthSample.Quality.GOOD;
        if(n.speed!=null&&n.speed>35)quality=DepthSample.Quality.QUESTIONABLE;

        DepthSample sample=new DepthSample(n.lat,n.lon,n.depth,now,n.speed,
                n.headingFresh(F)?n.heading:null,quality,n.source);
        try{
            sonarStore.append(sample);
            sonarSamples.add(sample);
            if(sonarSamples.size()>5000)sonarSamples.remove(0);
            lastSoundingLat=n.lat;lastSoundingLon=n.lon;lastSoundingAt=now;
            newSoundings++;
            if(sonarSamples.size()>=3&&(sonarChart==null||newSoundings>=3))rebuildSonarChart();
            updateSonarTitle();
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
                    updateSonarTitle();
                });
            }finally{
                chartBuilding.set(false);
            }
        },"Sembulung-SonarChart").start();
    }

    private void startGps(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},R);
            return;
        }
        if(!lm.isProviderEnabled(LocationManager.GPS_PROVIDER))return;
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,.5f,this);
        Location x=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if(x!=null)onLocationChanged(x);
    }

    public void onLocationChanged(Location x){phone=x;refresh();}
    public void onProviderEnabled(String p){}
    public void onProviderDisabled(String p){refresh();}
    public void onRequestPermissionsResult(int r,String[] p,int[] g){
        super.onRequestPermissionsResult(r,p,g);
        if(r==R&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)startGps();
    }

    private boolean nf(){return n!=null&&n.positionFresh(F);}
    private boolean useN(){return mode==2?nf():mode==0&&nf();}
    private Double lat(){if(mode==2)return nf()?n.lat:null;if(useN())return n.lat;return phone==null?null:phone.getLatitude();}
    private Double lon(){if(mode==2)return nf()?n.lon:null;if(useN())return n.lon;return phone==null?null:phone.getLongitude();}
    private Double hdg(){if(useN()&&n.headingFresh(F))return n.heading;if(mode!=2&&phone!=null&&phone.hasBearing())return(double)phone.getBearing();return null;}

    private void refresh(){
        n=NmeaDataStore.read(this);
        if(phone!=null){
            String a=phone.hasAccuracy()?String.format(Locale.US,"%.0fm",phone.getAccuracy()):"--";
            gps.setText("GPS "+a);gps.setAlpha(mode==2?.55f:1);
        }else{gps.setText("GPS --");gps.setAlpha(.55f);}

        boolean live=n!=null&&n.packetFresh(F);
        nmea.setText(live?"NMEA LIVE":"NMEA --");nmea.setAlpha(live?1:.55f);

        if(n!=null&&n.depthFresh(F)){
            depth.setText(String.format(Locale.US,"DEPTH %.1fm",n.depth));depth.setAlpha(1);
        }else{depth.setText("DEPTH --");depth.setAlpha(.55f);}

        source.setText(mode==0?"AUTO":mode==1?"GPS":"NMEA");
        source.setAlpha(mode==2&&!nf()?.55f:1);

        map.vessel(lat(),lon(),hdg(),useN(),true);
        maybeRecordSonar();
    }

    private void recenter(){
        if(lat()==null||lon()==null){
            Toast.makeText(this,"Posisi belum tersedia",Toast.LENGTH_SHORT).show();
            startGps();return;
        }
        map.recenter();
    }

    private static double haversineMeters(double a1,double o1,double a2,double o2){
        double R=6371000.0;
        double p1=Math.toRadians(a1),p2=Math.toRadians(a2);
        double dp=Math.toRadians(a2-a1),dl=Math.toRadians(o2-o1);
        double q=Math.sin(dp/2)*Math.sin(dp/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);
        return R*2*Math.atan2(Math.sqrt(q),Math.sqrt(1-q));
    }

    protected void onResume(){
        super.onResume();
        if(lm!=null&&map!=null)startGps();
        h.removeCallbacks(tick);h.post(tick);
    }

    protected void onPause(){
        super.onPause();h.removeCallbacks(tick);
        try{if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)lm.removeUpdates(this);}
        catch(Exception ignored){}
    }

    protected void onDestroy(){
        h.removeCallbacks(tick);
        if(loader!=null)loader.close();
        super.onDestroy();
    }

    private TextView chip(String t,int s,boolean bold){
        TextView v=new TextView(this);v.setText(t);v.setTextColor(Color.WHITE);v.setTextSize(s);
        v.setGravity(Gravity.CENTER);v.setPadding(dp(8),dp(3),dp(8),dp(3));
        if(bold)v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);v.setBackground(bg());return v;
    }
    private Button floating(String t,int s){
        Button b=new Button(this);b.setText(t);b.setTextSize(s);b.setTextColor(Color.WHITE);b.setAllCaps(false);
        b.setPadding(0,0,0,0);b.setMinWidth(0);b.setMinHeight(0);b.setBackground(bg());return b;
    }
    private GradientDrawable bg(){
        GradientDrawable d=new GradientDrawable();d.setColor(0xb809213b);d.setCornerRadius(dp(14));
        d.setStroke(dp(1),0x4478a9d1);return d;
    }
    private LinearLayout.LayoutParams chipLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(34),1);p.setMargins(dp(2),0,dp(2),0);return p;}
    private LinearLayout.LayoutParams square(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(58),dp(46));p.setMargins(0,dp(3),0,dp(3));return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
