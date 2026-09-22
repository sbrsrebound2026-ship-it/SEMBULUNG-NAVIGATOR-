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
import com.sembulung.navigator.sonar.SonarChartSnapshot;
import com.sembulung.navigator.sonar.SonarChartStore;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MarineMapActivity extends Activity implements LocationListener {
    private static final int R = 1301;
    private static final long F = 5000;

    private LocationManager lm;
    private Location phone;
    private NmeaDataStore.Snapshot n;
    private MarineTileLoader loader;
    private MarineMapView map;
    private TextView gps, nmea, depth, sonarInfo;
    private Button source, sonarButton;
    private int mode = 0;

    private SonarChartStore sonarStore;
    private final SonarChartEngine sonarEngine = new SonarChartEngine();
    private final ExecutorService sonarExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean sonarBuildRunning = false;
    private boolean liveMapping = true;
    private double sonarCellMeters = 15.0;
    private double contourIntervalMeters = 2.0;
    private long lastSoundingAt = 0L;
    private long lastChartBuildAt = 0L;
    private Double lastSoundingLat, lastSoundingLon, lastSoundingDepth;

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        public void run() {
            refresh();
            h.postDelayed(this, 500);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(3,27,61));
        getWindow().setNavigationBarColor(Color.rgb(3,27,61));

        lm = (LocationManager)getSystemService(LOCATION_SERVICE);
        sonarStore = new SonarChartStore(this);

        FrameLayout root = new FrameLayout(this);
        loader = new MarineTileLoader(this, () -> {
            if (map != null) map.postInvalidate();
        });
        map = new MarineMapView(this, loader);
        root.addView(map, new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(10),dp(10),dp(10),0);

        TextView title = chip("SEMBULUNG MARINE • LIVE SONAR CHART",15,true);
        title.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        top.addView(title,new LinearLayout.LayoutParams(-2,dp(38)));

        LinearLayout row = new LinearLayout(this);
        gps = chip("GPS --",10,true);
        nmea = chip("NMEA --",10,true);
        depth = chip("DEPTH --",10,true);
        row.addView(gps,chipLp());
        row.addView(nmea,chipLp());
        row.addView(depth,chipLp());
        top.addView(row,new LinearLayout.LayoutParams(-1,dp(38)));

        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-1,-2);
        tlp.gravity = Gravity.TOP;
        root.addView(top,tlp);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);

        Button plus = floating("+",22);
        plus.setOnClickListener(v -> map.zoom(1));
        right.addView(plus,square());

        Button center = floating("◎",20);
        center.setOnClickListener(v -> recenter());
        right.addView(center,square());

        Button minus = floating("−",22);
        minus.setOnClickListener(v -> map.zoom(-1));
        right.addView(minus,square());

        sonarButton = floating("SC",12);
        sonarButton.setOnClickListener(v -> {
            map.sonarEnabled(!map.sonarEnabled());
            updateSonarButton();
        });
        sonarButton.setOnLongClickListener(v -> {
            sonarMenu();
            return true;
        });
        right.addView(sonarButton,square());

        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(-2,-2);
        rlp.gravity = Gravity.END|Gravity.CENTER_VERTICAL;
        rlp.setMargins(0,0,dp(10),0);
        root.addView(right,rlp);

        source = floating("AUTO",12);
        source.setOnClickListener(v -> {
            mode = (mode+1)%3;
            refresh();
        });
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(dp(88),dp(44));
        slp.gravity = Gravity.START|Gravity.BOTTOM;
        slp.setMargins(dp(12),0,0,dp(30));
        root.addView(source,slp);

        Button menu = floating("☰",18);
        menu.setOnClickListener(v -> menu());
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(dp(50),dp(50));
        mlp.gravity = Gravity.END|Gravity.BOTTOM;
        mlp.setMargins(0,0,dp(12),dp(28));
        root.addView(menu,mlp);

        sonarInfo = chip("SONAR CHART • memuat...",9,true);
        FrameLayout.LayoutParams silp = new FrameLayout.LayoutParams(dp(250),dp(42));
        silp.gravity = Gravity.CENTER_HORIZONTAL|Gravity.BOTTOM;
        silp.setMargins(0,0,0,dp(28));
        root.addView(sonarInfo,silp);

        TextView att = chip("© OpenStreetMap contributors • Seamarks © OpenSeaMap",8,false);
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(-2,dp(22));
        alp.gravity = Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL;
        alp.setMargins(0,0,0,dp(4));
        root.addView(att,alp);

        setContentView(root);
        startGps();
        updateSonarButton();
        refresh();
        scheduleChartRebuild(true);
    }

    private void menu() {
        String seamark = map.seamarks() ? "Sembunyikan seamarks" : "Tampilkan seamarks";
        String sonar = map.sonarEnabled() ? "Sonar Chart: ON" : "Sonar Chart: OFF";
        String[] a = {seamark, sonar, "Atur Sonar Chart", "Peta MBTiles lokal",
                "SONAR / NMEA", "AIS • CPA / TCPA", "Dashboard"};

        new AlertDialog.Builder(this)
                .setTitle("Marine Map")
                .setItems(a,(d,w) -> {
                    if(w==0) map.seamarks(!map.seamarks());
                    else if(w==1) {
                        map.sonarEnabled(!map.sonarEnabled());
                        updateSonarButton();
                    }
                    else if(w==2) sonarMenu();
                    else if(w==3) startActivity(new Intent(this,OfflineMapActivity.class));
                    else if(w==4) startActivity(new Intent(this,NmeaActivity.class));
                    else if(w==5) startActivity(new Intent(this,AisActivity.class));
                    else finish();
                }).show();
    }

    private void sonarMenu() {
        String[] a = {
                "Live Mapping: " + (liveMapping ? "ON" : "OFF"),
                "Depth Shading: " + (map.sonarShading() ? "ON" : "OFF"),
                "Contour Vector: " + (map.sonarContours() ? "ON" : "OFF"),
                "Sounding Labels: " + (map.sonarSoundings() ? "ON" : "OFF"),
                "Interval kontur: " + formatInterval(contourIntervalMeters),
                "Bangun ulang kontur",
                "Hapus semua data Sonar Chart"
        };

        new AlertDialog.Builder(this)
                .setTitle("Live Sonar Chart")
                .setItems(a,(d,w) -> {
                    if(w==0) liveMapping = !liveMapping;
                    else if(w==1) map.sonarShading(!map.sonarShading());
                    else if(w==2) map.sonarContours(!map.sonarContours());
                    else if(w==3) map.sonarSoundings(!map.sonarSoundings());
                    else if(w==4) contourDialog();
                    else if(w==5) scheduleChartRebuild(true);
                    else if(w==6) confirmClearSonar();
                    updateSonarButton();
                }).show();
    }

    private void contourDialog() {
        final double[] values = {1.0, 2.0, 5.0, 10.0};
        String[] labels = {"1 m","2 m","5 m","10 m"};
        int checked = 1;
        for(int i=0;i<values.length;i++) {
            if(Math.abs(values[i]-contourIntervalMeters)<0.001) checked=i;
        }
        new AlertDialog.Builder(this)
                .setTitle("Interval Kontur")
                .setSingleChoiceItems(labels, checked, (d,w) -> {
                    contourIntervalMeters = values[w];
                    d.dismiss();
                    scheduleChartRebuild(true);
                }).show();
    }

    private void confirmClearSonar() {
        new AlertDialog.Builder(this)
                .setTitle("Hapus data Sonar Chart?")
                .setMessage("Semua raw sounding lokal akan dihapus. Tindakan ini tidak dapat dibatalkan.")
                .setNegativeButton("Batal",null)
                .setPositiveButton("Hapus",(d,w) -> {
                    sonarExecutor.execute(() -> {
                        sonarStore.clearAll();
                        SonarChartSnapshot empty = SonarChartSnapshot.empty(
                                sonarCellMeters, contourIntervalMeters);
                        runOnUiThread(() -> {
                            map.sonarChart(empty);
                            updateSonarInfo(empty);
                        });
                    });
                }).show();
    }

    private String formatInterval(double v) {
        return Math.abs(v-Math.rint(v))<0.001
                ? String.format(Locale.US,"%.0f m",v)
                : String.format(Locale.US,"%.1f m",v);
    }

    private void startGps() {
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION},R);
            return;
        }
        if(!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return;
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,.5f,this);
        Location x=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if(x!=null) onLocationChanged(x);
    }

    @Override public void onLocationChanged(Location x) {
        phone=x;
        refresh();
    }
    @Override public void onProviderEnabled(String p) {}
    @Override public void onProviderDisabled(String p) { refresh(); }

    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g) {
        super.onRequestPermissionsResult(r,p,g);
        if(r==R&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED) startGps();
    }

    private boolean nf() { return n!=null&&n.positionFresh(F); }
    private boolean useN() { return mode==2 ? nf() : mode==0&&nf(); }

    private Double lat() {
        if(mode==2) return nf()?n.lat:null;
        if(useN()) return n.lat;
        return phone==null?null:phone.getLatitude();
    }

    private Double lon() {
        if(mode==2) return nf()?n.lon:null;
        if(useN()) return n.lon;
        return phone==null?null:phone.getLongitude();
    }

    private Double hdg() {
        if(useN()&&n.headingFresh(F)) return n.heading;
        if(mode!=2&&phone!=null&&phone.hasBearing()) return (double)phone.getBearing();
        return null;
    }

    private void refresh() {
        n=NmeaDataStore.read(this);

        if(phone!=null) {
            String a=phone.hasAccuracy()?String.format(Locale.US,"%.0fm",phone.getAccuracy()):"--";
            gps.setText("GPS "+a);
            gps.setAlpha(mode==2?.55f:1);
        } else {
            gps.setText("GPS --");
            gps.setAlpha(.55f);
        }

        boolean live=n!=null&&n.packetFresh(F);
        nmea.setText(live?"NMEA LIVE":"NMEA --");
        nmea.setAlpha(live?1:.55f);

        if(n!=null&&n.depthFresh(F)) {
            depth.setText(String.format(Locale.US,"DEPTH %.1fm",n.depth));
            depth.setAlpha(1);
        } else {
            depth.setText("DEPTH --");
            depth.setAlpha(.55f);
        }

        source.setText(mode==0?"AUTO":mode==1?"GPS":"NMEA");
        source.setAlpha(mode==2&&!nf()?.55f:1);

        map.vessel(lat(),lon(),hdg(),useN(),true);
        maybeRecordSounding();
    }

    private void maybeRecordSounding() {
        if(!liveMapping || n==null || !n.depthFresh(F)) return;

        Double la = lat();
        Double lo = lon();
        if(la==null || lo==null || n.depth==null || n.depth<0.0 || n.depth>2000.0) return;

        long now = System.currentTimeMillis();
        if(now-lastSoundingAt<900) return;

        boolean movedEnough = lastSoundingLat==null
                || distanceMeters(lastSoundingLat,lastSoundingLon,la,lo)>=2.0;
        boolean depthChanged = lastSoundingDepth==null
                || Math.abs(lastSoundingDepth-n.depth)>=0.12;

        if(!movedEnough && !depthChanged && now-lastSoundingAt<3000) return;

        DepthSample.Quality quality = sampleQuality();
        String src = n.source == null ? "NMEA" : n.source;
        if(!useN()) src += "+ANDROID_GPS";

        DepthSample sample = new DepthSample(
                la,lo,n.depth,
                n.depthTime>0?n.depthTime:now,
                n.speed,hdg(),quality,src);

        if(sonarStore.insert(sample)>=0) {
            lastSoundingAt=now;
            lastSoundingLat=la;
            lastSoundingLon=lo;
            lastSoundingDepth=n.depth;
            scheduleChartRebuild(false);
        }
    }

    private DepthSample.Quality sampleQuality() {
        if(n!=null&&n.positionFresh(F)&&n.depthFresh(F)
                && Math.abs(n.positionTime-n.depthTime)<=3000) {
            return DepthSample.Quality.GOOD;
        }
        if(phone!=null&&phone.hasAccuracy()) {
            if(phone.getAccuracy()<=25f) return DepthSample.Quality.GOOD;
            if(phone.getAccuracy()<=75f) return DepthSample.Quality.QUESTIONABLE;
        }
        return DepthSample.Quality.QUESTIONABLE;
    }

    private void scheduleChartRebuild(boolean force) {
        long now=System.currentTimeMillis();
        if(sonarBuildRunning) return;
        if(!force&&now-lastChartBuildAt<2000) return;

        sonarBuildRunning=true;
        lastChartBuildAt=now;
        final double interval=contourIntervalMeters;
        final double cell=sonarCellMeters;

        sonarExecutor.execute(() -> {
            try {
                List<DepthSample> samples=sonarStore.recent(3000);
                SonarChartSnapshot snapshot=sonarEngine.build(samples,cell,interval);
                runOnUiThread(() -> {
                    map.sonarChart(snapshot);
                    updateSonarInfo(snapshot);
                });
            } finally {
                sonarBuildRunning=false;
            }
        });
    }

    private void updateSonarInfo(SonarChartSnapshot s) {
        if(s==null||s.sampleCount==0) {
            sonarInfo.setText("SONAR CHART • 0 sounding");
            sonarInfo.setAlpha(.72f);
            return;
        }
        double areaKm2=s.surveyedAreaSquareMeters/1_000_000.0;
        sonarInfo.setText(String.format(Locale.US,
                "SC • %d pts • %.3f km² • %.1f–%.1fm",
                s.sampleCount,areaKm2,s.minDepth,s.maxDepth));
        sonarInfo.setAlpha(1f);
    }

    private void updateSonarButton() {
        sonarButton.setText(map.sonarEnabled()?"SC ON":"SC OFF");
        sonarButton.setAlpha(map.sonarEnabled()?1f:.55f);
    }

    private double distanceMeters(double lat1,double lon1,double lat2,double lon2) {
        double r=6371000.0;
        double p1=Math.toRadians(lat1),p2=Math.toRadians(lat2);
        double dp=Math.toRadians(lat2-lat1),dl=Math.toRadians(lon2-lon1);
        double a=Math.sin(dp/2)*Math.sin(dp/2)
                +Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);
        return 2*r*Math.atan2(Math.sqrt(a),Math.sqrt(1-a));
    }

    private void recenter() {
        if(lat()==null||lon()==null) {
            Toast.makeText(this,"Posisi belum tersedia",Toast.LENGTH_SHORT).show();
            startGps();
            return;
        }
        map.recenter();
    }

    @Override protected void onResume() {
        super.onResume();
        if(lm!=null&&map!=null) startGps();
        h.removeCallbacks(tick);
        h.post(tick);
        scheduleChartRebuild(true);
    }

    @Override protected void onPause() {
        super.onPause();
        h.removeCallbacks(tick);
        try {
            if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)
                lm.removeUpdates(this);
        } catch(Exception ignored) {}
    }

    @Override protected void onDestroy() {
        h.removeCallbacks(tick);
        if(loader!=null) loader.close();
        sonarExecutor.shutdownNow();
        if(sonarStore!=null) sonarStore.close();
        super.onDestroy();
    }

    private TextView chip(String t,int s,boolean bold) {
        TextView v=new TextView(this);
        v.setText(t);
        v.setTextColor(Color.WHITE);
        v.setTextSize(s);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(8),dp(3),dp(8),dp(3));
        if(bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        v.setBackground(bg());
        return v;
    }

    private Button floating(String t,int s) {
        Button b=new Button(this);
        b.setText(t);
        b.setTextSize(s);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setPadding(0,0,0,0);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setBackground(bg());
        return b;
    }

    private GradientDrawable bg() {
        GradientDrawable d=new GradientDrawable();
        d.setColor(0xb809213b);
        d.setCornerRadius(dp(14));
        d.setStroke(dp(1),0x4478a9d1);
        return d;
    }

    private LinearLayout.LayoutParams chipLp() {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(34),1);
        p.setMargins(dp(2),0,dp(2),0);
        return p;
    }

    private LinearLayout.LayoutParams square() {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(58),dp(46));
        p.setMargins(0,dp(3),0,dp(3));
        return p;
    }

    private int dp(int v) {
        return Math.round(v*getResources().getDisplayMetrics().density);
    }
}
