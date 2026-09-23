package com.sembulung.navigator;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.sembulung.navigator.ais.AisCollisionEngine;
import com.sembulung.navigator.ais.AisTarget;
import com.sembulung.navigator.ais.AisTargetStore;
import com.sembulung.navigator.sonar.DepthSample;
import com.sembulung.navigator.sonar.SonarChartEngine;
import com.sembulung.navigator.sonar.SonarChartStore;
import com.sembulung.navigator.sonar.SonarHazardEngine;

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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VectorOfflineMapActivity extends Activity implements LocationListener {
    private static final int REQ_PMTILES = 940;
    private static final int REQ_LOCATION = 941;
    private static final int REQ_EXPORT_GPX = 942;
    private static final int REQ_EXPORT_KML = 943;
    private static final String FILE_NAME = "sembulung_jatim_bali.pmtiles";
    private static final long LIVE_AGE_MS = 15000L;

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
    private UnifiedOverlayView overlay;
    private TextView status;
    private TextView navStatus;
    private TextView safety;
    private Button importButton;
    private Button marineButton;
    private Button depthButton;
    private Button routeTapButton;
    private Button trackButton;

    private boolean marineOverlayEnabled = true;
    private boolean depthOverlayEnabled = false;
    private boolean pmtilesLoaded = false;
    private boolean followBoat = true;
    private boolean routeTapMode = false;
    private boolean trackRecording = false;

    private LocationManager locationManager;
    private Location gpsLocation;
    private final List<LatLng> trackPoints = new ArrayList<>();
    private LatLng lastTrackPoint;
    private long lastTrackTime = 0L;
    private long lastAlarmAt = 0L;
    private long lastAisAlarmAt = 0L;
    private long lastHazardBuildAt = 0L;
    private SonarHazardEngine.Assessment cachedHazard;
    private ToneGenerator alarmTone;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable liveTick = new Runnable() {
        @Override public void run() {
            refreshLiveData();
            handler.postDelayed(this,1000L);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MapLibre.getInstance(this);
        locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);
        alarmTone=new ToneGenerator(AudioManager.STREAM_ALARM,80);
        loadTrack();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(4,24,43));

        mapView = new MapView(this);
        mapView.onCreate(savedInstanceState);
        root.addView(mapView,new FrameLayout.LayoutParams(-1,-1));

        overlay = new UnifiedOverlayView();
        root.addView(overlay,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(8),dp(7),dp(8),dp(7));
        top.setBackgroundColor(0xE904182B);

        TextView title=label("SEMBULUNG UNIFIED MARINE MAP • V26",15,true);
        top.addView(title);

        status=label("Menyiapkan MapLibre…",11,false);
        status.setPadding(0,dp(2),0,dp(2));
        top.addView(status);

        navStatus=label("GPS/NMEA • AIS • WAYPOINT • SONAR",11,true);
        navStatus.setTextColor(Color.rgb(79,224,247));
        navStatus.setPadding(0,0,0,dp(4));
        top.addView(navStatus);

        safety=label("OPEN DATA • gunakan bersama peta laut resmi",9,true);
        safety.setTextColor(0xffffd764);
        safety.setPadding(0,0,0,dp(5));
        top.addView(safety);

        LinearLayout row1=new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);

        importButton=button("IMPOR MAP");
        importButton.setOnClickListener(v->choosePmtiles());
        row1.addView(importButton,quarter());

        marineButton=button("MARINE ON");
        marineButton.setOnClickListener(v->{
            marineOverlayEnabled=!marineOverlayEnabled;
            updateButtons();
            loadBaseStyle();
        });
        row1.addView(marineButton,quarter());

        depthButton=button("DEPTH OFF");
        depthButton.setOnClickListener(v->{
            depthOverlayEnabled=!depthOverlayEnabled;
            updateButtons();
            loadBaseStyle();
        });
        row1.addView(depthButton,quarter());

        Button follow=button("FOLLOW ON");
        follow.setOnClickListener(v->{
            followBoat=!followBoat;
            follow.setText(followBoat?"FOLLOW ON":"FOLLOW OFF");
            follow.setAlpha(followBoat?1f:0.6f);
            if(followBoat) centerOnBoat();
        });
        row1.addView(follow,quarter());
        top.addView(row1);

        LinearLayout row2=new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0,dp(3),0,0);

        routeTapButton=button("ROUTE TAP OFF");
        routeTapButton.setOnClickListener(v->{
            routeTapMode=!routeTapMode;
            routeTapButton.setText(routeTapMode?"ROUTE TAP ON":"ROUTE TAP OFF");
            routeTapButton.setAlpha(routeTapMode?1f:0.6f);
            safety.setText(routeTapMode?"Ketuk peta untuk menambah titik rute":"LIVE OVERLAY: VESSEL • ROUTE • AIS • SOUNDINGS");
        });
        row2.addView(routeTapButton,quarter());

        trackButton=button("TRACK REC OFF");
        trackButton.setOnClickListener(v->{
            trackRecording=!trackRecording;
            trackButton.setText(trackRecording?"TRACK REC ON":"TRACK REC OFF");
            trackButton.setAlpha(trackRecording?1f:0.6f);
            if(trackRecording)recordTrackPoint();
        });
        row2.addView(trackButton,quarter());

        Button ais=button("AIS");
        ais.setOnClickListener(v->startActivity(new Intent(this,AisActivity.class)));
        row2.addView(ais,quarter());

        Button sonar=button("SONAR");
        sonar.setOnClickListener(v->startActivity(new Intent(this,SonarSurveyActivity.class)));
        row2.addView(sonar,quarter());
        top.addView(row2);

        LinearLayout row3=new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.setPadding(0,dp(3),0,0);

        Button wp=button("WAYPOINT");
        wp.setOnClickListener(v->startActivity(new Intent(this,NavigationActivity.class)));
        row3.addView(wp,quarter());

        Button clearRoute=button("CLEAR ROUTE");
        clearRoute.setOnClickListener(v->{
            WaypointStore.save(this,new ArrayList<>());
            WaypointStore.clearActive(this);
            refreshLiveData();
        });
        row3.addView(clearRoute,quarter());

        Button clearTrack=button("CLEAR TRACK");
        clearTrack.setOnClickListener(v->clearTrack());
        row3.addView(clearTrack,quarter());

        Button export=button("EXPORT TRACK");
        export.setOnClickListener(v->showTrackExportDialog());
        row3.addView(export,quarter());
        top.addView(row3);

        LinearLayout row4=new LinearLayout(this);
        row4.setOrientation(LinearLayout.HORIZONTAL);
        row4.setPadding(0,dp(3),0,0);

        Button settings=button("SETTINGS");
        settings.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));
        row4.addView(settings,quarter());

        Button center=button("CENTER");
        center.setOnClickListener(v->centerOnBoat());
        row4.addView(center,quarter());

        Button nav=button("NAV DATA");
        nav.setOnClickListener(v->startActivity(new Intent(this,NavigationActivity.class)));
        row4.addView(nav,quarter());

        Button back=button("KEMBALI");
        back.setOnClickListener(v->finish());
        row4.addView(back,quarter());
        top.addView(row4);

        FrameLayout.LayoutParams topLp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        root.addView(top,topLp);

        TextView attribution=label("© OSM • Geofabrik • OpenSeaMap • GEBCO • MapLibre",9,false);
        attribution.setPadding(dp(6),dp(3),dp(6),dp(3));
        attribution.setBackgroundColor(0xC003172A);
        FrameLayout.LayoutParams attrLp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.END);
        root.addView(attribution,attrLp);

        setContentView(root);

        mapView.getMapAsync(m->{
            map=m;
            map.setMinZoomPreference(0.0);
            map.setMaxZoomPreference(18.0);
            map.addOnMapClickListener(point->{
                if(!routeTapMode)return false;
                addRoutePoint(point);
                return true;
            });
            loadBaseStyle();
            refreshLiveData();
        });

        requestGps();
        handler.post(liveTick);
    }

    private void loadBaseStyle() {
        if(map==null)return;
        pmtilesLoaded=false;
        String styleJson="{"
                +"\"version\":8,"
                +"\"name\":\"SEMBULUNG UNIFIED V26\","
                +"\"sources\":{},"
                +"\"layers\":[{"
                +"\"id\":\"background\","
                +"\"type\":\"background\","
                +"\"paint\":{\"background-color\":\"#0A3654\"}"
                +"}]}";

        map.setStyle(new Style.Builder().fromJson(styleJson),style->{
            File f=localPmtiles();
            if(f.exists()&&f.length()>0)addPmtiles(style,f);
            addDepthOverlay(style);
            addMarineOverlay(style);
            updateBaseStatus(f);
            if(currentLatLng()!=null)centerOnBoat();
            else centerBanyuwangi();
            overlay.invalidate();
        });
    }

    private void addPmtiles(Style style,File file) {
        try {
            VectorSource source=new VectorSource("sembulung-vector","pmtiles://file://"+file.getAbsolutePath());
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
            pmtilesLoaded=true;
        } catch(Exception e) {
            status.setText("PMTiles gagal: "+e.getMessage());
        }
    }

    private void addMarineOverlay(Style style) {
        if(!marineOverlayEnabled)return;
        try {
            TileSet tileSet=new TileSet("2.1.0",SEAMARK_TILES);
            RasterSource source=new RasterSource("openseamap-seamarks",tileSet,256);
            style.addSource(source);
            RasterLayer layer=new RasterLayer("openseamap-seamarks-layer","openseamap-seamarks");
            layer.setProperties(PropertyFactory.rasterOpacity(0.97f),PropertyFactory.rasterFadeDuration(0f));
            style.addLayer(layer);
        } catch(Exception e) {
            safety.setText("Seamark overlay gagal • "+e.getClass().getSimpleName());
        }
    }

    private void addDepthOverlay(Style style) {
        if(!depthOverlayEnabled)return;
        try {
            TileSet tileSet=new TileSet("2.1.0",GEBCO_WMS);
            RasterSource source=new RasterSource("openseamap-gebco",tileSet,256);
            style.addSource(source);
            RasterLayer layer=new RasterLayer("openseamap-gebco-layer","openseamap-gebco");
            layer.setProperties(PropertyFactory.rasterOpacity(0.60f),PropertyFactory.rasterFadeDuration(0f));
            style.addLayer(layer);
        } catch(Exception e) {
            safety.setText("Depth overlay gagal • "+e.getClass().getSimpleName());
        }
    }

    private void refreshLiveData() {
        if(overlay==null)return;

        NmeaDataStore.Snapshot n=NmeaDataStore.read(this);
        List<WaypointStore.Waypoint> wps=WaypointStore.load(this);
        int active=WaypointStore.activeIndex(this);
        List<AisTarget> ais=AisTargetStore.load(this,120000L);
        List<DepthSample> sonar=new SonarChartStore(this).load(350);

        overlay.nmea=n;
        overlay.waypoints=wps;
        overlay.activeIndex=active;
        overlay.aisTargets=ais;
        overlay.soundings=sonar;
        if(trackRecording)recordTrackPoint();
        evaluateShallowAlarm(n,sonar);
        overlay.invalidate();

        LatLng pos=currentLatLng();
        double sog=currentSog();
        double cog=currentCog();
        Double depth=n.depthFresh(15000L)?n.depth:null;

        RouteGuidanceEngine.Guidance guidance=null;
        if(pos!=null&&active>=0&&active<wps.size()) {
            guidance=RouteGuidanceEngine.assess(
                    pos.getLatitude(),pos.getLongitude(),sog,
                    wps,active,AppSettings.arrivalRadiusNm(this));
            if(guidance!=null&&guidance.arrived) {
                int next=RouteGuidanceEngine.nextIndex(
                        active,wps.size(),true,AppSettings.autoAdvanceRoute(this));
                WaypointStore.setActiveIndex(this,next);
                active=next;
                overlay.activeIndex=next;
                if(next>=0&&next<wps.size()) {
                    guidance=RouteGuidanceEngine.assess(
                            pos.getLatitude(),pos.getLongitude(),sog,
                            wps,next,AppSettings.arrivalRadiusNm(this));
                } else guidance=null;
            }
        }

        evaluateAisAlarm(pos,sog,cog,ais);

        String source=gpsLocation!=null?"GPS":(n.positionFresh(LIVE_AGE_MS)?"NMEA":"NO FIX");
        String routeText="";
        if(guidance!=null) {
            routeText=String.format(Locale.US,
                    " • %s %.2fNM BRG %.0f° XTE %s ETA %s",
                    guidance.targetName,guidance.distanceNm,guidance.bearingDeg,
                    Double.isFinite(guidance.xteNm)?String.format(Locale.US,"%.2fNM",guidance.xteNm):"--",
                    Double.isFinite(guidance.etaMinutes)?formatEtaMinutes(guidance.etaMinutes):"--");
            if(Double.isFinite(guidance.xteNm)&&Math.abs(guidance.xteNm)>AppSettings.offRouteNm(this)) {
                safety.setText(String.format(Locale.US,"OFF ROUTE • XTE %.2fNM > %.2fNM",
                        Math.abs(guidance.xteNm),AppSettings.offRouteNm(this)));
                safety.setTextColor(Color.rgb(255,145,45));
            }
        }

        navStatus.setText(String.format(Locale.US,
                "%s • SOG %.1fkn • COG %s • DEPTH %s%s\nAIS %d • WP %d • TRACK %d • SONAR %d",
                source,sog,
                Double.isFinite(cog)?String.format(Locale.US,"%.0f°",cog):"--",
                depth!=null?String.format(Locale.US,"%.1fm",depth):"--",
                routeText,ais.size(),wps.size(),trackPoints.size(),sonar.size()));

        if(followBoat&&pos!=null&&map!=null) {
            CameraPosition cp=map.getCameraPosition();
            map.setCameraPosition(new CameraPosition.Builder(cp).target(pos).build());
        }
    }

    private void addRoutePoint(LatLng point) {
        List<WaypointStore.Waypoint> all=WaypointStore.load(this);
        all.add(new WaypointStore.Waypoint("RUTE "+(all.size()+1),point.getLatitude(),point.getLongitude()));
        WaypointStore.save(this,all);
        if(WaypointStore.activeIndex(this)<0)WaypointStore.setActiveIndex(this,0);
        refreshLiveData();
    }

    private File trackFile() {
        File dir=new File(getFilesDir(),"track");
        if(!dir.exists())dir.mkdirs();
        return new File(dir,"track.csv");
    }

    private void loadTrack() {
        trackPoints.clear();
        File f=trackFile();
        if(!f.exists())return;
        try(BufferedReader r=new BufferedReader(new FileReader(f))) {
            String line;
            while((line=r.readLine())!=null) {
                String[] p=line.split(",");
                if(p.length<2)continue;
                try {
                    double lat=Double.parseDouble(p[0]);
                    double lon=Double.parseDouble(p[1]);
                    trackPoints.add(new LatLng(lat,lon));
                } catch(Exception ignored) {}
            }
            if(trackPoints.size()>5000) {
                List<LatLng> tail=new ArrayList<>(trackPoints.subList(trackPoints.size()-5000,trackPoints.size()));
                trackPoints.clear();trackPoints.addAll(tail);
            }
            if(!trackPoints.isEmpty())lastTrackPoint=trackPoints.get(trackPoints.size()-1);
        } catch(Exception ignored) {}
    }

    private void recordTrackPoint() {
        LatLng p=currentLatLng();
        if(p==null)return;
        long now=System.currentTimeMillis();
        if(lastTrackPoint!=null) {
            float[] d=new float[1];
            Location.distanceBetween(lastTrackPoint.getLatitude(),lastTrackPoint.getLongitude(),
                    p.getLatitude(),p.getLongitude(),d);
            if(d[0]<5.0f && now-lastTrackTime<5000L)return;
        }
        trackPoints.add(p);
        if(trackPoints.size()>5000)trackPoints.remove(0);
        lastTrackPoint=p;lastTrackTime=now;
        try(BufferedWriter w=new BufferedWriter(new FileWriter(trackFile(),true))) {
            w.write(String.format(Locale.US,"%.7f,%.7f,%d\n",p.getLatitude(),p.getLongitude(),now));
        } catch(Exception ignored) {}
    }

    private void clearTrack() {
        trackPoints.clear();
        lastTrackPoint=null;
        lastTrackTime=0L;
        File f=trackFile();
        if(f.exists())f.delete();
        if(overlay!=null)overlay.invalidate();
    }

    private void evaluateShallowAlarm(NmeaDataStore.Snapshot n,List<DepthSample> sonar) {
        double threshold=AppSettings.shallowMeters(this);
        boolean danger=false;
        String message=null;

        if(AppSettings.shallowWarning(this)&&n.depthFresh(15000L)&&n.depth!=null&&n.depth<threshold) {
            danger=true;
            message=String.format(Locale.US,"SHALLOW WATER %.1fm < %.1fm",n.depth,threshold);
        }

        LatLng pos=currentLatLng();
        double cog=currentCog();
        long now=System.currentTimeMillis();
        if(AppSettings.shallowAheadWarning(this)&&pos!=null&&Double.isFinite(cog)&&sonar!=null&&!sonar.isEmpty()) {
            if(now-lastHazardBuildAt>5000L) {
                SonarChartEngine.Chart chart=SonarChartEngine.build(
                        sonar,20.0,AppSettings.contourIntervalMeters(this));
                cachedHazard=SonarHazardEngine.assessAhead(
                        chart,pos.getLatitude(),pos.getLongitude(),
                        cog,currentSog(),threshold,AppSettings.lookAheadMinutes(this),75.0);
                lastHazardBuildAt=now;
            }
            if(cachedHazard!=null&&(cachedHazard.risk==SonarHazardEngine.Risk.DANGER||
                    cachedHazard.risk==SonarHazardEngine.Risk.WARNING)) {
                danger=true;
                message=String.format(Locale.US,"SHALLOW AHEAD %.1fm • %.0fm",
                        cachedHazard.minimumDepthMeters,cachedHazard.distanceAheadMeters);
            }
        }

        if(danger) {
            safety.setText(message);
            safety.setTextColor(Color.rgb(255,90,70));
            if(now-lastAlarmAt>10000L&&alarmTone!=null) {
                alarmTone.startTone(ToneGenerator.TONE_PROP_BEEP,550);
                lastAlarmAt=now;
            }
        } else if(!routeTapMode) {
            safety.setText("LIVE OVERLAY: VESSEL • ROUTE • AIS CPA/TCPA • TRACK • SOUNDINGS");
            safety.setTextColor(0xffffd764);
        }
    }

    private String formatEtaMinutes(double minutes) {
        if(!Double.isFinite(minutes)||minutes<0)return "--";
        long total=Math.round(minutes);
        return String.format(Locale.US,"%02d:%02d",total/60,total%60);
    }

    private void evaluateAisAlarm(LatLng own,double sog,double cog,List<AisTarget> targets) {
        if(!AppSettings.aisEnabled(this)||own==null||targets==null)return;
        AisCollisionEngine.Assessment worst=null;
        AisTarget worstTarget=null;
        for(AisTarget t:targets) {
            AisCollisionEngine.Assessment a=AisCollisionEngine.assess(
                    own.getLatitude(),own.getLongitude(),sog,cog,t);
            if(a.risk==AisCollisionEngine.Risk.DANGER||
                    a.risk==AisCollisionEngine.Risk.WARNING) {
                if(worst==null||riskRank(a.risk)>riskRank(worst.risk)) {
                    worst=a;worstTarget=t;
                }
            }
        }
        if(worst!=null&&worstTarget!=null) {
            safety.setText(String.format(Locale.US,
                    "AIS %s • MMSI %d • CPA %.2fNM • TCPA %.0fmin",
                    worst.risk.name(),worstTarget.mmsi,worst.cpaNm,worst.tcpaMinutes));
            safety.setTextColor(worst.risk==AisCollisionEngine.Risk.DANGER?
                    Color.rgb(255,65,65):Color.rgb(255,145,45));
            long now=System.currentTimeMillis();
            if(now-lastAisAlarmAt>12000L&&alarmTone!=null) {
                alarmTone.startTone(
                        worst.risk==AisCollisionEngine.Risk.DANGER?
                                ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD:
                                ToneGenerator.TONE_PROP_BEEP2,
                        700);
                lastAisAlarmAt=now;
            }
        }
    }

    private int riskRank(AisCollisionEngine.Risk risk) {
        if(risk==AisCollisionEngine.Risk.DANGER)return 4;
        if(risk==AisCollisionEngine.Risk.WARNING)return 3;
        if(risk==AisCollisionEngine.Risk.MONITOR)return 2;
        if(risk==AisCollisionEngine.Risk.SAFE)return 1;
        return 0;
    }

    private void showTrackExportDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Export track")
                .setItems(new String[]{"GPX","KML"},(d,which)->{
                    Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    if(which==0) {
                        i.setType("application/gpx+xml");
                        i.putExtra(Intent.EXTRA_TITLE,"SEMBULUNG_TRACK.gpx");
                        startActivityForResult(i,REQ_EXPORT_GPX);
                    } else {
                        i.setType("application/vnd.google-earth.kml+xml");
                        i.putExtra(Intent.EXTRA_TITLE,"SEMBULUNG_TRACK.kml");
                        startActivityForResult(i,REQ_EXPORT_KML);
                    }
                }).show();
    }

    private void writeTrackExport(Uri uri,boolean gpx) {
        if(uri==null)return;
        try(OutputStream out=getContentResolver().openOutputStream(uri)) {
            if(out==null)throw new IllegalStateException("Output tidak dapat dibuka");
            StringBuilder s=new StringBuilder();
            if(gpx) {
                s.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                s.append("<gpx version=\"1.1\" creator=\"SEMBULUNG NAVIGATOR\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>SEMBULUNG Track</name><trkseg>\n");
                for(LatLng p:trackPoints) {
                    s.append(String.format(Locale.US,"<trkpt lat=\"%.7f\" lon=\"%.7f\"/>\n",
                            p.getLatitude(),p.getLongitude()));
                }
                s.append("</trkseg></trk></gpx>\n");
            } else {
                s.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                s.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document><Placemark><name>SEMBULUNG Track</name><LineString><coordinates>\n");
                for(LatLng p:trackPoints) {
                    s.append(String.format(Locale.US,"%.7f,%.7f,0 ",p.getLongitude(),p.getLatitude()));
                }
                s.append("\n</coordinates></LineString></Placemark></Document></kml>\n");
            }
            out.write(s.toString().getBytes("UTF-8"));
            out.flush();
            safety.setText("Track berhasil diexport • "+(gpx?"GPX":"KML"));
            safety.setTextColor(Color.rgb(100,230,130));
        } catch(Exception e) {
            safety.setText("Export gagal: "+e.getMessage());
            safety.setTextColor(Color.rgb(255,90,70));
        }
    }

    private LatLng currentLatLng() {
        if(gpsLocation!=null) return new LatLng(gpsLocation.getLatitude(),gpsLocation.getLongitude());
        NmeaDataStore.Snapshot n=NmeaDataStore.read(this);
        if(n.positionFresh(LIVE_AGE_MS))return new LatLng(n.lat,n.lon);
        return null;
    }

    private double currentSog() {
        if(gpsLocation!=null&&gpsLocation.hasSpeed())return gpsLocation.getSpeed()*1.943844492;
        NmeaDataStore.Snapshot n=NmeaDataStore.read(this);
        return n.speed!=null?n.speed:0.0;
    }

    private double currentCog() {
        if(gpsLocation!=null&&gpsLocation.hasBearing())return gpsLocation.getBearing();
        NmeaDataStore.Snapshot n=NmeaDataStore.read(this);
        return n.heading!=null?n.heading:Double.NaN;
    }

    private double currentHeading() {
        NmeaDataStore.Snapshot n=NmeaDataStore.read(this);
        if(n.headingFresh(LIVE_AGE_MS))return n.heading;
        return currentCog();
    }

    private void requestGps() {
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);
            return;
        }
        try {
            if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000L,0.5f,this);
                Location last=locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if(last!=null)gpsLocation=last;
            }
        } catch(Exception ignored) {}
    }

    @Override public void onLocationChanged(Location location) {
        gpsLocation=location;
        refreshLiveData();
    }

    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) {
        if(LocationManager.GPS_PROVIDER.equals(provider))gpsLocation=null;
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==REQ_LOCATION&&grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED)requestGps();
    }

    private void centerOnBoat() {
        if(map==null)return;
        LatLng p=currentLatLng();
        if(p==null)return;
        double bearing=Double.isFinite(currentCog())?currentCog():0.0;
        map.setCameraPosition(new CameraPosition.Builder()
                .target(p).zoom(Math.max(12.0,map.getCameraPosition().zoom))
                .bearing(bearing).tilt(0).build());
    }

    private void updateBaseStatus(File f) {
        String base=pmtilesLoaded&&f.exists()
                ?String.format(Locale.US,"PMTiles OFFLINE %.1f MB",f.length()/1048576.0)
                :"Basemap kosong • impor PMTiles";
        status.setText(base+" • "+(marineOverlayEnabled?"SEAMARK ON":"SEAMARK OFF")
                +" • "+(depthOverlayEnabled?"GEBCO ON":"GEBCO OFF"));
        safety.setText("LIVE OVERLAY: VESSEL • ROUTE • AIS • SOUNDINGS • OPEN DATA");
    }

    private void updateButtons() {
        marineButton.setText(marineOverlayEnabled?"MARINE ON":"MARINE OFF");
        marineButton.setAlpha(marineOverlayEnabled?1f:0.6f);
        depthButton.setText(depthOverlayEnabled?"DEPTH ON":"DEPTH OFF");
        depthButton.setAlpha(depthOverlayEnabled?1f:0.6f);
    }

    private void choosePmtiles() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i,REQ_PMTILES);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        if(requestCode==REQ_EXPORT_GPX){writeTrackExport(data.getData(),true);return;}
        if(requestCode==REQ_EXPORT_KML){writeTrackExport(data.getData(),false);return;}
        if(requestCode!=REQ_PMTILES)return;
        Uri uri=data.getData();
        importButton.setEnabled(false);
        status.setText("Menyalin PMTiles…");

        new Thread(()->{
            File out=localPmtiles();
            long total=0;
            try(InputStream in=getContentResolver().openInputStream(uri);
                FileOutputStream fos=new FileOutputStream(out,false)) {
                if(in==null)throw new IllegalStateException("File tidak dapat dibuka");
                byte[] buffer=new byte[1024*1024];
                int n;
                while((n=in.read(buffer))>0){fos.write(buffer,0,n);total+=n;}
                fos.flush();
                final long size=total;
                runOnUiThread(()->{
                    importButton.setEnabled(true);
                    status.setText(String.format(Locale.US,"Import %.1f MB selesai",size/1048576.0));
                    loadBaseStyle();
                });
            } catch(Exception e) {
                if(out.exists())out.delete();
                runOnUiThread(()->{
                    importButton.setEnabled(true);
                    status.setText("Import gagal: "+e.getMessage());
                });
            }
        }).start();
    }

    private File localPmtiles(){return new File(getFilesDir(),FILE_NAME);}

    private void centerBanyuwangi() {
        if(map==null)return;
        map.setCameraPosition(new CameraPosition.Builder()
                .target(new LatLng(-8.2192,114.3691)).zoom(11.0).bearing(0).tilt(0).build());
    }

    private void addFill(Style style,String id,String sourceLayer,int color,float opacity) {
        FillLayer layer=new FillLayer(id,"sembulung-vector");
        layer.setSourceLayer(sourceLayer);
        layer.setProperties(PropertyFactory.fillColor(color),PropertyFactory.fillOpacity(opacity));
        style.addLayer(layer);
    }

    private void addLine(Style style,String id,String sourceLayer,int color,float width) {
        LineLayer layer=new LineLayer(id,"sembulung-vector");
        layer.setSourceLayer(sourceLayer);
        layer.setProperties(PropertyFactory.lineColor(color),PropertyFactory.lineWidth(width));
        style.addLayer(layer);
    }

    private TextView label(String text,int sp,boolean bold) {
        TextView v=new TextView(this);
        v.setText(text);v.setTextColor(Color.WHITE);v.setTextSize(sp);v.setGravity(Gravity.CENTER);
        if(bold)v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return v;
    }

    private Button button(String text){Button b=new Button(this);b.setText(text);b.setAllCaps(false);return b;}
    private LinearLayout.LayoutParams quarter(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1f);p.setMargins(dp(1),0,dp(1),0);return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}

    private final class UnifiedOverlayView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);
        private NmeaDataStore.Snapshot nmea;
        private List<WaypointStore.Waypoint> waypoints;
        private int activeIndex=-1;
        private List<AisTarget> aisTargets;
        private List<DepthSample> soundings;

        UnifiedOverlayView() {
            super(VectorOfflineMapActivity.this);
            setWillNotDraw(false);
            text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            text.setTextSize(dp(10));
            text.setShadowLayer(3,1,1,Color.BLACK);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            if(map==null)return;

            drawRangeRings(c);
            drawTrack(c);
            drawSoundings(c);
            drawRouteAndWaypoints(c);
            drawAis(c);
            drawVessel(c);
        }

        private PointF screen(double lat,double lon) {
            try{return map.getProjection().toScreenLocation(new LatLng(lat,lon));}
            catch(Exception e){return null;}
        }

        private void drawRangeRings(Canvas c) {
            LatLng v=currentLatLng();
            if(v==null)return;
            PointF center=screen(v.getLatitude(),v.getLongitude());
            if(center==null)return;
            double[] rings={0.5,1.0,2.0};
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(0x99B8F5FF);p.setAlpha(150);
            text.setColor(Color.rgb(190,245,255));text.setAlpha(220);
            for(double nm:rings) {
                PointF north=screen(v.getLatitude()+nm/60.0,v.getLongitude());
                if(north==null)continue;
                float radius=Math.abs(center.y-north.y);
                if(radius<dp(8)||radius>Math.max(getWidth(),getHeight())*1.5f)continue;
                c.drawCircle(center.x,center.y,radius,p);
                c.drawText(String.format(Locale.US,"%.1f NM",nm),center.x+radius+dp(3),center.y,text);
            }
            c.drawLine(center.x-dp(18),center.y,center.x+dp(18),center.y,p);
            c.drawLine(center.x,center.y-dp(18),center.x,center.y+dp(18),p);
        }

        private void drawTrack(Canvas c) {
            if(trackPoints.size()<2)return;
            Path path=new Path();
            boolean started=false;
            int start=Math.max(0,trackPoints.size()-2000);
            for(int i=start;i<trackPoints.size();i++) {
                LatLng ll=trackPoints.get(i);
                PointF pt=screen(ll.getLatitude(),ll.getLongitude());
                if(pt==null)continue;
                if(!started){path.moveTo(pt.x,pt.y);started=true;}
                else path.lineTo(pt.x,pt.y);
            }
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(3));
            p.setColor(Color.rgb(255,79,210));p.setAlpha(215);
            c.drawPath(path,p);
        }

        private void drawSoundings(Canvas c) {
            if(soundings==null)return;
            int start=Math.max(0,soundings.size()-250);
            for(int i=start;i<soundings.size();i++) {
                DepthSample s=soundings.get(i);
                if(s==null||!s.valid())continue;
                PointF pt=screen(s.lat,s.lon);
                if(pt==null||offscreen(pt))continue;
                int color=depthColor(s.depthMeters);
                p.setColor(color);p.setStyle(Paint.Style.FILL);p.setAlpha(185);
                c.drawCircle(pt.x,pt.y,dp(3),p);
                if(map.getCameraPosition().zoom>=14.0) {
                    text.setColor(Color.WHITE);text.setAlpha(230);
                    c.drawText(String.format(Locale.US,"%.1f",s.depthMeters),pt.x+dp(4),pt.y-dp(3),text);
                }
            }
        }

        private void drawRouteAndWaypoints(Canvas c) {
            if(waypoints==null)return;
            LatLng vessel=currentLatLng();

            if(waypoints.size()>1) {
                Path route=new Path();
                boolean started=false;
                for(WaypointStore.Waypoint w:waypoints) {
                    PointF pt=screen(w.lat,w.lon);
                    if(pt==null)continue;
                    if(!started){route.moveTo(pt.x,pt.y);started=true;}
                    else route.lineTo(pt.x,pt.y);
                }
                p.setColor(Color.rgb(80,245,255));p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(2));p.setAlpha(170);
                c.drawPath(route,p);
            }

            if(activeIndex>=0&&activeIndex<waypoints.size()&&vessel!=null) {
                WaypointStore.Waypoint w=waypoints.get(activeIndex);
                PointF a=screen(vessel.getLatitude(),vessel.getLongitude());
                PointF b=screen(w.lat,w.lon);
                if(a!=null&&b!=null) {
                    p.setColor(Color.rgb(55,235,255));p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(dp(3));p.setAlpha(230);
                    c.drawLine(a.x,a.y,b.x,b.y,p);
                }
            }

            for(int i=0;i<waypoints.size();i++) {
                WaypointStore.Waypoint w=waypoints.get(i);
                PointF pt=screen(w.lat,w.lon);
                if(pt==null||offscreen(pt))continue;
                boolean active=i==activeIndex;
                p.setColor(active?Color.rgb(255,214,70):Color.WHITE);
                p.setStyle(Paint.Style.FILL);p.setAlpha(245);
                c.drawCircle(pt.x,pt.y,active?dp(7):dp(5),p);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(Color.BLACK);
                c.drawCircle(pt.x,pt.y,active?dp(7):dp(5),p);
                text.setColor(active?Color.rgb(255,230,100):Color.WHITE);text.setAlpha(255);
                c.drawText(w.name,pt.x+dp(8),pt.y-dp(6),text);
            }
        }

        private void drawAis(Canvas c) {
            if(aisTargets==null)return;
            LatLng own=currentLatLng();
            double ownSog=currentSog();
            double ownCog=currentCog();

            for(AisTarget t:aisTargets) {
                if(t==null||!t.hasValidPosition())continue;
                PointF pt=screen(t.latitude,t.longitude);
                if(pt==null||offscreen(pt))continue;

                AisCollisionEngine.Assessment a=own==null?null:
                        AisCollisionEngine.assess(
                                own.getLatitude(),own.getLongitude(),ownSog,ownCog,t);
                int color=riskColor(a==null?AisCollisionEngine.Risk.UNKNOWN:a.risk);
                double course=t.hasMotionVector()?t.courseDeg:(t.headingDeg!=null?t.headingDeg:0.0);
                drawTriangle(c,pt.x,pt.y,course,dp(9),color);

                if(t.hasMotionVector()&&t.speedKnots>0.3) {
                    double r=Math.toRadians(t.courseDeg);
                    float len=dp((int)Math.min(32,8+t.speedKnots));
                    p.setColor(color);p.setStrokeWidth(dp(2));p.setAlpha(220);
                    c.drawLine(pt.x,pt.y,pt.x+(float)Math.sin(r)*len,pt.y-(float)Math.cos(r)*len,p);
                }

                if(map.getCameraPosition().zoom>=10.5) {
                    text.setColor(color);text.setAlpha(255);
                    String label=Long.toString(t.mmsi);
                    if(a!=null&&Double.isFinite(a.cpaNm)&&Double.isFinite(a.tcpaMinutes)) {
                        label+=String.format(Locale.US," CPA %.2fNM %.0fm",a.cpaNm,a.tcpaMinutes);
                    }
                    c.drawText(label,pt.x+dp(9),pt.y+dp(4),text);
                }
            }
        }

        private int riskColor(AisCollisionEngine.Risk risk) {
            if(risk==AisCollisionEngine.Risk.DANGER)return Color.rgb(255,65,65);
            if(risk==AisCollisionEngine.Risk.WARNING)return Color.rgb(255,145,45);
            if(risk==AisCollisionEngine.Risk.MONITOR)return Color.rgb(255,220,70);
            if(risk==AisCollisionEngine.Risk.SAFE)return Color.rgb(100,230,130);
            return Color.rgb(210,210,210);
        }

        private void drawVessel(Canvas c) {
            LatLng pos=currentLatLng();
            if(pos==null)return;
            PointF pt=screen(pos.getLatitude(),pos.getLongitude());
            if(pt==null)return;
            double hdg=currentHeading();
            if(!Double.isFinite(hdg))hdg=0.0;

            p.setColor(0x5500E8FF);p.setStyle(Paint.Style.FILL);p.setAlpha(90);
            c.drawCircle(pt.x,pt.y,dp(18),p);

            drawTriangle(c,pt.x,pt.y,hdg,dp(13),Color.rgb(31,238,255));

            double cog=currentCog();
            if(Double.isFinite(cog)) {
                double r=Math.toRadians(cog);
                float len=dp(38);
                p.setColor(Color.rgb(31,238,255));p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(2));p.setAlpha(230);
                c.drawLine(pt.x,pt.y,pt.x+(float)Math.sin(r)*len,pt.y-(float)Math.cos(r)*len,p);
            }

            text.setColor(Color.WHITE);text.setAlpha(255);
            c.drawText(String.format(Locale.US,"%.1f kn",currentSog()),pt.x+dp(14),pt.y+dp(18),text);
        }

        private void drawTriangle(Canvas c,float x,float y,double bearing,float size,int color) {
            double r=Math.toRadians(bearing);
            float fx=(float)Math.sin(r),fy=(float)-Math.cos(r);
            float rx=(float)Math.cos(r),ry=(float)Math.sin(r);
            Path path=new Path();
            path.moveTo(x+fx*size,y+fy*size);
            path.lineTo(x-fx*size*0.65f+rx*size*0.65f,y-fy*size*0.65f+ry*size*0.65f);
            path.lineTo(x-fx*size*0.65f-rx*size*0.65f,y-fy*size*0.65f-ry*size*0.65f);
            path.close();
            p.setColor(color);p.setStyle(Paint.Style.FILL);p.setAlpha(245);
            c.drawPath(path,p);
            p.setColor(Color.BLACK);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setAlpha(230);
            c.drawPath(path,p);
        }

        private int depthColor(double d) {
            if(d<5)return Color.rgb(255,105,65);
            if(d<10)return Color.rgb(255,190,70);
            if(d<20)return Color.rgb(85,220,190);
            if(d<50)return Color.rgb(60,170,230);
            return Color.rgb(80,105,220);
        }

        private boolean offscreen(PointF pt) {
            return pt.x<-50||pt.y<-50||pt.x>getWidth()+50||pt.y>getHeight()+50;
        }
    }

    @Override protected void onStart(){super.onStart();if(mapView!=null)mapView.onStart();}
    @Override protected void onResume(){super.onResume();if(mapView!=null)mapView.onResume();requestGps();refreshLiveData();}
    @Override protected void onPause(){if(mapView!=null)mapView.onPause();super.onPause();}
    @Override protected void onStop(){if(mapView!=null)mapView.onStop();super.onStop();}
    @Override protected void onSaveInstanceState(Bundle outState){super.onSaveInstanceState(outState);if(mapView!=null)mapView.onSaveInstanceState(outState);}
    @Override public void onLowMemory(){super.onLowMemory();if(mapView!=null)mapView.onLowMemory();}
    @Override protected void onDestroy(){
        handler.removeCallbacks(liveTick);
        try{if(locationManager!=null)locationManager.removeUpdates(this);}catch(Exception ignored){}
        try{if(alarmTone!=null)alarmTone.release();}catch(Exception ignored){}
        if(mapView!=null)mapView.onDestroy();
        super.onDestroy();
    }
}
