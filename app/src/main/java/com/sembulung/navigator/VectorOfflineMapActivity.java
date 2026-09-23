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

import org.json.JSONObject;
import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.FillLayer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.layers.RasterLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.android.style.sources.RasterSource;
import org.maplibre.android.style.sources.TileSet;
import org.maplibre.android.style.sources.VectorSource;

import com.sembulung.navigator.ais.AisCollisionEngine;
import com.sembulung.navigator.ais.AisTarget;
import com.sembulung.navigator.ais.AisTargetStore;
import com.sembulung.navigator.sonar.DepthSample;
import com.sembulung.navigator.sonar.SonarChartEngine;
import com.sembulung.navigator.sonar.SonarChartStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private static final String SRC_OWN = "v25-own";
    private static final String SRC_HEADING = "v25-heading";
    private static final String SRC_ROUTE = "v25-route";
    private static final String SRC_WAYPOINTS = "v25-waypoints";
    private static final String SRC_TARGET = "v25-target";
    private static final String SRC_DIRECT = "v25-direct";
    private static final String SRC_AIS_DANGER = "v25-ais-danger";
    private static final String SRC_AIS_WARNING = "v25-ais-warning";
    private static final String SRC_AIS_OTHER = "v25-ais-other";
    private static final String SRC_SONAR_POINTS = "v25-sonar-points";
    private static final String SRC_SONAR_CONTOURS = "v25-sonar-contours";

    private MapView mapView;
    private MapLibreMap map;
    private TextView status;
    private TextView safety;
    private TextView navStatus;
    private TextView routeStatus;
    private TextView aisStatus;
    private TextView sonarStatus;

    private LocationManager locationManager;
    private Location phoneLocation;
    private NmeaDataStore.Snapshot nmeaSnapshot;

    private Button importButton;
    private Button marineButton;
    private Button depthButton;
    private Button sourceButton;
    private Button aisButton;
    private Button sonarButton;

    private boolean marineOverlayEnabled = true;
    private boolean depthOverlayEnabled = false;
    private boolean pmtilesLoaded = false;
    private boolean aisEnabled = true;
    private boolean sonarEnabled = true;
    private int sourceMode = 0; // 0 AUTO, 1 GPS HP, 2 NMEA

    private final Handler liveHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean sonarBuilding = new AtomicBoolean(false);
    private SonarChartEngine.Chart sonarChart;
    private int sonarSampleCount = -1;
    private long lastSonarCheck = 0L;

    private final Runnable liveRefresh = new Runnable() {
        @Override public void run() {
            refreshLiveState();
            liveHandler.postDelayed(this,1000L);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MapLibre.getInstance(this);
        locationManager = (LocationManager)getSystemService(LOCATION_SERVICE);
        aisEnabled = AppSettings.aisEnabled(this);
        sonarEnabled = AppSettings.sonarEnabled(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(4,24,43));

        mapView = new MapView(this);
        mapView.onCreate(savedInstanceState);
        root.addView(mapView,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(8),dp(7),dp(8),dp(7));
        top.setBackgroundColor(0xE904182B);

        TextView title = label("SEMBULUNG UNIFIED MARINE MAP • V25",15,true);
        top.addView(title);

        status = label("Menyiapkan MapLibre + marine data…",11,false);
        status.setPadding(0,dp(3),0,dp(2));
        top.addView(status);

        navStatus = label("NAV • menunggu GPS/NMEA",11,true);
        navStatus.setTextColor(0xff8fffc0);
        top.addView(navStatus);

        routeStatus = label("RUTE • belum ada tujuan aktif",10,true);
        routeStatus.setTextColor(0xffffe18a);
        top.addView(routeStatus);

        aisStatus = label("AIS • menunggu target",10,true);
        aisStatus.setTextColor(0xffa8e7ff);
        top.addView(aisStatus);

        sonarStatus = label("SONAR • menunggu sounding",10,true);
        sonarStatus.setTextColor(0xff8ffff0);
        sonarStatus.setPadding(0,0,0,dp(4));
        top.addView(sonarStatus);

        LinearLayout row1 = row();
        importButton = button("IMPOR PMT");
        importButton.setOnClickListener(v -> choosePmtiles());
        row1.addView(importButton,third());

        marineButton = button("MARINE ON");
        marineButton.setOnClickListener(v -> {
            marineOverlayEnabled=!marineOverlayEnabled;
            updateButtons();
            loadBaseStyle();
        });
        row1.addView(marineButton,third());

        depthButton = button("DEPTH OFF");
        depthButton.setOnClickListener(v -> {
            depthOverlayEnabled=!depthOverlayEnabled;
            updateButtons();
            loadBaseStyle();
        });
        row1.addView(depthButton,third());
        top.addView(row1);

        LinearLayout row2 = row();
        Button position = button("POSISI");
        position.setOnClickListener(v -> centerCurrentPosition());
        row2.addView(position,third());

        sourceButton = button("SUMBER AUTO");
        sourceButton.setOnClickListener(v -> {
            sourceMode=(sourceMode+1)%3;
            updateButtons();
            refreshLiveState();
        });
        row2.addView(sourceButton,third());

        Button route = button("RUTE / WP");
        route.setOnClickListener(v -> startActivity(new Intent(this,NavigationActivity.class)));
        row2.addView(route,third());
        top.addView(row2);

        LinearLayout row3 = row();
        aisButton = button("AIS ON");
        aisButton.setOnClickListener(v -> {
            aisEnabled=!aisEnabled;
            AppSettings.aisEnabled(this,aisEnabled);
            updateButtons();
            refreshLiveMap();
        });
        row3.addView(aisButton,third());

        sonarButton = button("SONAR ON");
        sonarButton.setOnClickListener(v -> {
            sonarEnabled=!sonarEnabled;
            AppSettings.sonarEnabled(this,sonarEnabled);
            updateButtons();
            refreshLiveMap();
        });
        sonarButton.setOnLongClickListener(v -> {
            startActivity(new Intent(this,SonarSurveyActivity.class));
            return true;
        });
        row3.addView(sonarButton,third());

        Button back = button("KEMBALI");
        back.setOnClickListener(v -> finish());
        row3.addView(back,third());
        top.addView(row3);

        safety = label("OPEN DATA • gunakan bersama peta laut resmi",9,true);
        safety.setTextColor(0xffffd764);
        safety.setPadding(0,dp(3),0,0);
        top.addView(safety);

        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        root.addView(top,topLp);

        TextView attribution = label(
                "© OSM • Geofabrik • OpenSeaMap • GEBCO • MapLibre",9,false);
        attribution.setPadding(dp(7),dp(3),dp(7),dp(3));
        attribution.setBackgroundColor(0xC003172A);
        FrameLayout.LayoutParams attrLp = new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.END);
        root.addView(attribution,attrLp);

        setContentView(root);
        updateButtons();

        mapView.getMapAsync(m -> {
            map=m;
            map.setMinZoomPreference(0.0);
            map.setMaxZoomPreference(18.0);
            loadBaseStyle();
        });

        startGps();
        rebuildSonarIfNeeded(true);
        refreshLiveState();
    }

    private void loadBaseStyle() {
        if(map==null)return;
        pmtilesLoaded=false;

        String styleJson="{"
                +"\"version\":8,"
                +"\"name\":\"SEMBULUNG UNIFIED V25\","
                +"\"sources\":{},"
                +"\"layers\":[{\"id\":\"background\",\"type\":\"background\","
                +"\"paint\":{\"background-color\":\"#0A3654\"}}]}";

        map.setStyle(new Style.Builder().fromJson(styleJson),style->{
            File f=localPmtiles();
            if(f.exists()&&f.length()>0)addPmtiles(style,f);
            addDepthOverlay(style);
            addMarineOverlay(style);
            addLiveSources(style);
            updateBaseStatus(f);
            refreshLiveMap();
            if(activeLat()==null)centerBanyuwangi();
        });
    }

    private void addPmtiles(Style style,File file) {
        try {
            style.addSource(new VectorSource("sembulung-vector","pmtiles://file://"+file.getAbsolutePath()));

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
            pmtilesLoaded=false;
            status.setText("PMTiles gagal dimuat • "+e.getClass().getSimpleName());
        }
    }

    private void addDepthOverlay(Style style) {
        if(!depthOverlayEnabled)return;
        try {
            TileSet ts=new TileSet("2.1.0",GEBCO_WMS);
            style.addSource(new RasterSource("openseamap-gebco",ts,256));
            RasterLayer layer=new RasterLayer("openseamap-gebco-layer","openseamap-gebco");
            layer.setProperties(
                    PropertyFactory.rasterOpacity(0.60f),
                    PropertyFactory.rasterFadeDuration(0f));
            style.addLayer(layer);
        } catch(Exception e) {
            safety.setText("GEBCO gagal • "+e.getClass().getSimpleName());
        }
    }

    private void addMarineOverlay(Style style) {
        if(!marineOverlayEnabled)return;
        try {
            TileSet ts=new TileSet("2.1.0",SEAMARK_TILES);
            style.addSource(new RasterSource("openseamap-seamarks",ts,256));
            RasterLayer layer=new RasterLayer("openseamap-seamarks-layer","openseamap-seamarks");
            layer.setProperties(
                    PropertyFactory.rasterOpacity(0.97f),
                    PropertyFactory.rasterFadeDuration(0f));
            style.addLayer(layer);
        } catch(Exception e) {
            safety.setText("Seamark gagal • "+e.getClass().getSimpleName());
        }
    }

    private void addLiveSources(Style style) {
        style.addSource(new GeoJsonSource(SRC_OWN,emptyFc()));
        style.addSource(new GeoJsonSource(SRC_HEADING,emptyFc()));
        style.addSource(new GeoJsonSource(SRC_ROUTE,emptyFc()));
        style.addSource(new GeoJsonSource(SRC_WAYPOINTS,emptyFc()));
        style.addSource(new GeoJsonSource(SRC_TARGET,emptyFc()));
        style.addSource(new GeoJsonSource(SRC_DIRECT,emptyFc()));
        style.addSource(new GeoJsonSource(SRC_AIS_DANGER,emptyFc()));
        style.addSource(new GeoJsonSource(SRC_AIS_WARNING,emptyFc()));
        style.addSource(new GeoJsonSource(SRC_AIS_OTHER,emptyFc()));
        style.addSource(new GeoJsonSource(SRC_SONAR_POINTS,emptyFc()));
        style.addSource(new GeoJsonSource(SRC_SONAR_CONTOURS,emptyFc()));

        CircleLayer own=new CircleLayer("v25-own-layer",SRC_OWN);
        own.setProperties(
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleColor(0xff15f3ff),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circleStrokeColor(Color.WHITE));
        style.addLayer(own);

        LineLayer hdg=new LineLayer("v25-heading-layer",SRC_HEADING);
        hdg.setProperties(PropertyFactory.lineColor(0xff15f3ff),PropertyFactory.lineWidth(3.0f));
        style.addLayer(hdg);

        LineLayer route=new LineLayer("v25-route-layer",SRC_ROUTE);
        route.setProperties(PropertyFactory.lineColor(0xffffd54f),PropertyFactory.lineWidth(3.0f));
        style.addLayer(route);

        CircleLayer wps=new CircleLayer("v25-waypoints-layer",SRC_WAYPOINTS);
        wps.setProperties(
                PropertyFactory.circleRadius(5.5f),
                PropertyFactory.circleColor(0xffffe082),
                PropertyFactory.circleStrokeWidth(1.5f),
                PropertyFactory.circleStrokeColor(0xff5d4a00));
        style.addLayer(wps);

        CircleLayer target=new CircleLayer("v25-target-layer",SRC_TARGET);
        target.setProperties(
                PropertyFactory.circleRadius(9f),
                PropertyFactory.circleColor(0xffff5722),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circleStrokeColor(Color.WHITE));
        style.addLayer(target);

        LineLayer direct=new LineLayer("v25-direct-layer",SRC_DIRECT);
        direct.setProperties(PropertyFactory.lineColor(0xffff7043),PropertyFactory.lineWidth(2.2f));
        style.addLayer(direct);

        CircleLayer aisOther=new CircleLayer("v25-ais-other-layer",SRC_AIS_OTHER);
        aisOther.setProperties(
                PropertyFactory.circleRadius(5f),
                PropertyFactory.circleColor(0xff57d4ff),
                PropertyFactory.circleStrokeWidth(1.5f),
                PropertyFactory.circleStrokeColor(0xff003b5a));
        style.addLayer(aisOther);

        CircleLayer aisWarning=new CircleLayer("v25-ais-warning-layer",SRC_AIS_WARNING);
        aisWarning.setProperties(
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleColor(0xffffc107),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor(Color.WHITE));
        style.addLayer(aisWarning);

        CircleLayer aisDanger=new CircleLayer("v25-ais-danger-layer",SRC_AIS_DANGER);
        aisDanger.setProperties(
                PropertyFactory.circleRadius(9f),
                PropertyFactory.circleColor(0xffff3344),
                PropertyFactory.circleStrokeWidth(2.5f),
                PropertyFactory.circleStrokeColor(Color.WHITE));
        style.addLayer(aisDanger);

        CircleLayer sounding=new CircleLayer("v25-sonar-points-layer",SRC_SONAR_POINTS);
        sounding.setProperties(
                PropertyFactory.circleRadius(3.0f),
                PropertyFactory.circleColor(0xff7df9ff),
                PropertyFactory.circleOpacity(0.82f),
                PropertyFactory.circleStrokeWidth(0.6f),
                PropertyFactory.circleStrokeColor(0xff003747));
        style.addLayer(sounding);

        LineLayer contours=new LineLayer("v25-sonar-contours-layer",SRC_SONAR_CONTOURS);
        contours.setProperties(
                PropertyFactory.lineColor(0xff7df9ff),
                PropertyFactory.lineWidth(1.5f),
                PropertyFactory.lineOpacity(0.82f));
        style.addLayer(contours);
    }

    private void refreshLiveState() {
        nmeaSnapshot=NmeaDataStore.read(this);
        updateNavigationHud();
        updateRouteGuidance();
        updateAisHud();
        rebuildSonarIfNeeded(false);
        refreshLiveMap();
    }

    private void updateNavigationHud() {
        Double lat=activeLat(),lon=activeLon(),speed=activeSpeedKnots(),course=activeCourseDeg();
        String src=activeSourceName();
        String pos=(lat==null||lon==null)?"--":String.format(Locale.US,"%.5f %.5f",lat,lon);
        String sog=speed==null?"--":String.format(Locale.US,"%.1f kn",speed);
        String cog=course==null?"--":String.format(Locale.US,"%.0f°",course);
        String dep=(nmeaSnapshot!=null&&nmeaSnapshot.depthFresh(NMEA_FRESH_MS)&&nmeaSnapshot.depth!=null)
                ?String.format(Locale.US,"%.1f m",nmeaSnapshot.depth):"--";
        navStatus.setText("NAV • "+src+" • "+pos+" • SOG "+sog+" • COG "+cog+" • D "+dep);
    }

    private void updateRouteGuidance() {
        List<WaypointStore.Waypoint> w=WaypointStore.load(this);
        int active=WaypointStore.activeIndex(this);
        Double lat=activeLat(),lon=activeLon();
        if(active<0||active>=w.size()) {
            routeStatus.setText("RUTE • belum ada tujuan aktif • WP "+w.size());
            return;
        }
        if(lat==null||lon==null) {
            routeStatus.setText("RUTE • "+w.get(active).name+" • menunggu posisi");
            return;
        }

        RouteGuidanceEngine.Guidance g=RouteGuidanceEngine.assess(
                lat,lon,activeSpeedKnots(),w,active,AppSettings.arrivalRadiusNm(this));
        if(g==null)return;

        if(g.arrived&&AppSettings.autoAdvanceRoute(this)) {
            int next=RouteGuidanceEngine.nextIndex(active,w.size(),true,true);
            WaypointStore.setActiveIndex(this,next);
            active=next;
            if(next<0) {
                routeStatus.setText("RUTE • selesai");
                return;
            }
            g=RouteGuidanceEngine.assess(
                    lat,lon,activeSpeedKnots(),w,next,AppSettings.arrivalRadiusNm(this));
            if(g==null)return;
        }

        String eta=Double.isFinite(g.etaMinutes)
                ?String.format(Locale.US,"ETA %.0f m",g.etaMinutes):"ETA --";
        String xte=Double.isFinite(g.xteNm)
                ?String.format(Locale.US," • XTE %.2f",Math.abs(g.xteNm)):"";
        routeStatus.setText(String.format(Locale.US,
                "RUTE • %s • %.2f NM • BRG %.0f° • %s%s",
                g.targetName,g.distanceNm,g.bearingDeg,eta,xte));
    }

    private void updateAisHud() {
        if(!aisEnabled) {
            aisStatus.setText("AIS • OFF");
            return;
        }
        List<AisTarget> targets=AisTargetStore.load(this,120000L);
        Double lat=activeLat(),lon=activeLon(),speed=activeSpeedKnots(),course=activeCourseDeg();
        int danger=0,warning=0,monitor=0;
        double nearest=Double.POSITIVE_INFINITY;

        for(AisTarget t:targets) {
            if(lat==null||lon==null)continue;
            AisCollisionEngine.Assessment a=AisCollisionEngine.assess(
                    lat,lon,
                    speed==null?Double.NaN:speed,
                    course==null?Double.NaN:course,
                    t);
            if(Double.isFinite(a.rangeNm))nearest=Math.min(nearest,a.rangeNm);
            if(a.risk==AisCollisionEngine.Risk.DANGER)danger++;
            else if(a.risk==AisCollisionEngine.Risk.WARNING)warning++;
            else if(a.risk==AisCollisionEngine.Risk.MONITOR)monitor++;
        }

        String risk=danger>0?"DANGER "+danger:warning>0?"WARNING "+warning:monitor>0?"MONITOR "+monitor:"NORMAL";
        String near=Double.isFinite(nearest)?String.format(Locale.US," • nearest %.2f NM",nearest):"";
        aisStatus.setText("AIS • "+targets.size()+" target • "+risk+near);
    }

    private void rebuildSonarIfNeeded(boolean force) {
        long now=System.currentTimeMillis();
        if(!force&&now-lastSonarCheck<5000L)return;
        lastSonarCheck=now;
        if(sonarBuilding.get())return;

        List<DepthSample> samples=new SonarChartStore(this).load(5000);
        if(!force&&samples.size()==sonarSampleCount)return;
        sonarSampleCount=samples.size();

        if(samples.isEmpty()) {
            sonarChart=null;
            sonarStatus.setText(sonarEnabled?"SONAR • belum ada sounding":"SONAR • OFF");
            return;
        }

        sonarBuilding.set(true);
        final ArrayList<DepthSample> copy=new ArrayList<>(samples);
        final double interval=AppSettings.contourIntervalMeters(this);
        new Thread(()->{
            SonarChartEngine.Chart chart=SonarChartEngine.build(copy,20.0,interval);
            runOnUiThread(()->{
                sonarChart=chart;
                sonarBuilding.set(false);
                updateSonarHud();
                refreshLiveMap();
            });
        },"v25-sonar-build").start();
    }

    private void updateSonarHud() {
        if(!sonarEnabled) {
            sonarStatus.setText("SONAR • OFF");
            return;
        }
        if(sonarChart==null||sonarChart.stats==null||sonarChart.stats.acceptedSoundings==0) {
            sonarStatus.setText("SONAR • belum ada sounding");
            return;
        }
        sonarStatus.setText(String.format(Locale.US,
                "SONAR • %d sounding • %.1f–%.1f m • contour %.1f m",
                sonarChart.stats.acceptedSoundings,
                sonarChart.stats.minDepth,
                sonarChart.stats.maxDepth,
                sonarChart.stats.contourIntervalMeters));
    }

    private void refreshLiveMap() {
        if(map==null)return;
        map.getStyle(style->{
            try {
                setGeo(style,SRC_OWN,ownGeoJson());
                setGeo(style,SRC_HEADING,headingGeoJson());

                List<WaypointStore.Waypoint> w=WaypointStore.load(this);
                int active=WaypointStore.activeIndex(this);
                setGeo(style,SRC_ROUTE,routeGeoJson(w));
                setGeo(style,SRC_WAYPOINTS,waypointsGeoJson(w));
                setGeo(style,SRC_TARGET,targetGeoJson(w,active));
                setGeo(style,SRC_DIRECT,directGeoJson(w,active));

                if(aisEnabled) {
                    AisGeo ais=aisGeoJson();
                    setGeo(style,SRC_AIS_DANGER,ais.danger);
                    setGeo(style,SRC_AIS_WARNING,ais.warning);
                    setGeo(style,SRC_AIS_OTHER,ais.other);
                } else {
                    setGeo(style,SRC_AIS_DANGER,emptyFc());
                    setGeo(style,SRC_AIS_WARNING,emptyFc());
                    setGeo(style,SRC_AIS_OTHER,emptyFc());
                }

                if(sonarEnabled&&sonarChart!=null) {
                    setGeo(style,SRC_SONAR_POINTS,sonarPointsGeoJson());
                    setGeo(style,SRC_SONAR_CONTOURS,sonarContoursGeoJson());
                } else {
                    setGeo(style,SRC_SONAR_POINTS,emptyFc());
                    setGeo(style,SRC_SONAR_CONTOURS,emptyFc());
                }
            } catch(Exception ignored) {}
        });
    }

    private void setGeo(Style style,String id,String json) {
        GeoJsonSource s=style.getSourceAs(id);
        if(s!=null)s.setGeoJson(json);
    }

    private String ownGeoJson() {
        Double lat=activeLat(),lon=activeLon();
        if(lat==null||lon==null)return emptyFc();
        return pointFc(lon,lat);
    }

    private String headingGeoJson() {
        Double lat=activeLat(),lon=activeLon(),course=activeCourseDeg();
        if(lat==null||lon==null||course==null)return emptyFc();
        double[] end=destination(lat,lon,course,0.18);
        return lineFc(lon,lat,end[1],end[0]);
    }

    private String routeGeoJson(List<WaypointStore.Waypoint> w) {
        if(w==null||w.size()<2)return emptyFc();
        StringBuilder b=new StringBuilder();
        b.append("{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[");
        for(int i=0;i<w.size();i++) {
            if(i>0)b.append(',');
            b.append('[').append(num(w.get(i).lon)).append(',').append(num(w.get(i).lat)).append(']');
        }
        b.append("]}}]}");
        return b.toString();
    }

    private String waypointsGeoJson(List<WaypointStore.Waypoint> w) {
        if(w==null||w.isEmpty())return emptyFc();
        StringBuilder b=new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[");
        for(int i=0;i<w.size();i++) {
            if(i>0)b.append(',');
            WaypointStore.Waypoint p=w.get(i);
            b.append("{\"type\":\"Feature\",\"properties\":{\"name\":")
                    .append(JSONObject.quote(p.name))
                    .append("},\"geometry\":{\"type\":\"Point\",\"coordinates\":[")
                    .append(num(p.lon)).append(',').append(num(p.lat)).append("]}}");
        }
        b.append("]}");
        return b.toString();
    }

    private String targetGeoJson(List<WaypointStore.Waypoint> w,int active) {
        if(w==null||active<0||active>=w.size())return emptyFc();
        WaypointStore.Waypoint p=w.get(active);
        return pointFc(p.lon,p.lat);
    }

    private String directGeoJson(List<WaypointStore.Waypoint> w,int active) {
        Double lat=activeLat(),lon=activeLon();
        if(lat==null||lon==null||w==null||active<0||active>=w.size())return emptyFc();
        WaypointStore.Waypoint p=w.get(active);
        return lineFc(lon,lat,p.lon,p.lat);
    }

    private static final class AisGeo {
        final String danger,warning,other;
        AisGeo(String d,String w,String o){danger=d;warning=w;other=o;}
    }

    private AisGeo aisGeoJson() {
        List<AisTarget> all=AisTargetStore.load(this,120000L);
        Double lat=activeLat(),lon=activeLon(),speed=activeSpeedKnots(),course=activeCourseDeg();
        ArrayList<AisTarget> d=new ArrayList<>(),w=new ArrayList<>(),o=new ArrayList<>();

        for(AisTarget t:all) {
            AisCollisionEngine.Risk risk=AisCollisionEngine.Risk.UNKNOWN;
            if(lat!=null&&lon!=null) {
                risk=AisCollisionEngine.assess(
                        lat,lon,
                        speed==null?Double.NaN:speed,
                        course==null?Double.NaN:course,t).risk;
            }
            if(risk==AisCollisionEngine.Risk.DANGER)d.add(t);
            else if(risk==AisCollisionEngine.Risk.WARNING)w.add(t);
            else o.add(t);
        }
        return new AisGeo(aisPoints(d),aisPoints(w),aisPoints(o));
    }

    private String aisPoints(List<AisTarget> list) {
        if(list==null||list.isEmpty())return emptyFc();
        StringBuilder b=new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[");
        for(int i=0;i<list.size();i++) {
            if(i>0)b.append(',');
            AisTarget t=list.get(i);
            b.append("{\"type\":\"Feature\",\"properties\":{\"mmsi\":")
                    .append(t.mmsi)
                    .append("},\"geometry\":{\"type\":\"Point\",\"coordinates\":[")
                    .append(num(t.longitude)).append(',').append(num(t.latitude)).append("]}}");
        }
        b.append("]}");
        return b.toString();
    }

    private String sonarPointsGeoJson() {
        if(sonarChart==null||sonarChart.soundings==null||sonarChart.soundings.isEmpty())return emptyFc();
        int start=Math.max(0,sonarChart.soundings.size()-1000);
        StringBuilder b=new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[");
        boolean first=true;
        for(int i=start;i<sonarChart.soundings.size();i++) {
            DepthSample s=sonarChart.soundings.get(i);
            if(s==null||!s.valid())continue;
            if(!first)b.append(',');
            first=false;
            b.append("{\"type\":\"Feature\",\"properties\":{\"depth\":")
                    .append(num(s.depthMeters))
                    .append("},\"geometry\":{\"type\":\"Point\",\"coordinates\":[")
                    .append(num(s.lon)).append(',').append(num(s.lat)).append("]}}");
        }
        b.append("]}");
        return b.toString();
    }

    private String sonarContoursGeoJson() {
        if(sonarChart==null||sonarChart.contours==null||sonarChart.contours.isEmpty())return emptyFc();
        int max=Math.min(2500,sonarChart.contours.size());
        StringBuilder b=new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[");
        for(int i=0;i<max;i++) {
            if(i>0)b.append(',');
            SonarChartEngine.ContourSegment s=sonarChart.contours.get(i);
            b.append("{\"type\":\"Feature\",\"properties\":{\"level\":")
                    .append(num(s.level))
                    .append("},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[")
                    .append(num(s.lon1)).append(',').append(num(s.lat1)).append("],[")
                    .append(num(s.lon2)).append(',').append(num(s.lat2)).append("]]}}");
        }
        b.append("]}");
        return b.toString();
    }

    private static String emptyFc() {
        return "{\"type\":\"FeatureCollection\",\"features\":[]}";
    }

    private static String pointFc(double lon,double lat) {
        return "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Point\",\"coordinates\":["
                +num(lon)+","+num(lat)+"]}}]}";
    }

    private static String lineFc(double lon1,double lat1,double lon2,double lat2) {
        return "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[["
                +num(lon1)+","+num(lat1)+"],["+num(lon2)+","+num(lat2)+"]]}}]}";
    }

    private static String num(double v) {
        return String.format(Locale.US,"%.7f",v);
    }

    private static double[] destination(double lat,double lon,double bearingDeg,double distanceNm) {
        double r=Math.toRadians(bearingDeg);
        double dLat=distanceNm*Math.cos(r)/60.0;
        double cos=Math.max(0.15,Math.cos(Math.toRadians(lat)));
        double dLon=distanceNm*Math.sin(r)/(60.0*cos);
        return new double[]{lat+dLat,lon+dLon};
    }

    private void updateBaseStatus(File f) {
        String base=pmtilesLoaded&&f.exists()
                ?String.format(Locale.US,"PMTiles OFFLINE %.1f MB",f.length()/1048576.0)
                :"Basemap kosong • impor PMTiles";
        String marine=marineOverlayEnabled?"seamarks ON":"seamarks OFF";
        String depth=depthOverlayEnabled?"GEBCO ON":"GEBCO OFF";
        status.setText(base+" • "+marine+" • "+depth);
        safety.setText("OPEN DATA + SENSOR LOKAL • bukan pengganti chart resmi");
    }

    private void updateButtons() {
        if(marineButton!=null) {
            marineButton.setText(marineOverlayEnabled?"MARINE ON":"MARINE OFF");
            marineButton.setAlpha(marineOverlayEnabled?1f:0.6f);
        }
        if(depthButton!=null) {
            depthButton.setText(depthOverlayEnabled?"DEPTH ON":"DEPTH OFF");
            depthButton.setAlpha(depthOverlayEnabled?1f:0.6f);
        }
        if(sourceButton!=null) {
            sourceButton.setText(sourceMode==0?"SUMBER AUTO":sourceMode==1?"SUMBER GPS":"SUMBER NMEA");
        }
        if(aisButton!=null) {
            aisButton.setText(aisEnabled?"AIS ON":"AIS OFF");
            aisButton.setAlpha(aisEnabled?1f:0.6f);
        }
        if(sonarButton!=null) {
            sonarButton.setText(sonarEnabled?"SONAR ON":"SONAR OFF");
            sonarButton.setAlpha(sonarEnabled?1f:0.6f);
        }
    }

    private boolean useNmeaPosition() {
        boolean fresh=nmeaSnapshot!=null&&nmeaSnapshot.positionFresh(NMEA_FRESH_MS);
        return sourceMode==2?fresh:(sourceMode==0&&fresh);
    }

    private String activeSourceName() {
        if(useNmeaPosition())return "NMEA";
        if(sourceMode==2)return "NMEA STALE";
        return phoneLocation!=null?"GPS HP":"NO FIX";
    }

    private Double activeLat() {
        if(useNmeaPosition())return nmeaSnapshot.lat;
        if(sourceMode==2)return null;
        return phoneLocation==null?null:phoneLocation.getLatitude();
    }

    private Double activeLon() {
        if(useNmeaPosition())return nmeaSnapshot.lon;
        if(sourceMode==2)return null;
        return phoneLocation==null?null:phoneLocation.getLongitude();
    }

    private Double activeSpeedKnots() {
        if(useNmeaPosition()&&nmeaSnapshot!=null&&nmeaSnapshot.speed!=null
                &&nmeaSnapshot.packetFresh(NMEA_FRESH_MS))return nmeaSnapshot.speed;
        if(sourceMode==2)return null;
        if(phoneLocation!=null&&phoneLocation.hasSpeed())return phoneLocation.getSpeed()*1.943844492;
        return null;
    }

    private Double activeCourseDeg() {
        if(useNmeaPosition()&&nmeaSnapshot!=null&&nmeaSnapshot.headingFresh(NMEA_FRESH_MS))return nmeaSnapshot.heading;
        if(sourceMode==2)return null;
        if(phoneLocation!=null&&phoneLocation.hasBearing())return (double)phoneLocation.getBearing();
        return null;
    }

    private void choosePmtiles() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i,REQ_PMTILES);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=REQ_PMTILES||resultCode!=RESULT_OK||data==null||data.getData()==null)return;

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
                    status.setText(String.format(Locale.US,"Import %.1f MB selesai • memuat…",size/1048576.0));
                    loadBaseStyle();
                });
            } catch(Exception e) {
                if(out.exists())out.delete();
                runOnUiThread(()->{
                    importButton.setEnabled(true);
                    status.setText("Import gagal • "+e.getMessage());
                });
            }
        },"v25-pmtiles-import").start();
    }

    private File localPmtiles() {
        return new File(getFilesDir(),FILE_NAME);
    }

    private void startGps() {
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);
            return;
        }
        try {
            if(locationManager!=null&&locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000L,0.5f,this);
                Location last=locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if(last!=null)phoneLocation=last;
            }
        } catch(Exception ignored) {}
    }

    private void centerCurrentPosition() {
        Double lat=activeLat(),lon=activeLon();
        if(lat==null||lon==null) {
            navStatus.setText("NAV • posisi belum tersedia • aktifkan GPS/NMEA");
            startGps();
            return;
        }
        if(map!=null) {
            map.setCameraPosition(new CameraPosition.Builder()
                    .target(new LatLng(lat,lon))
                    .zoom(Math.max(13.0,map.getCameraPosition().zoom))
                    .bearing(0).tilt(0).build());
        }
    }

    private void centerBanyuwangi() {
        if(map==null)return;
        map.setCameraPosition(new CameraPosition.Builder()
                .target(new LatLng(-8.2192,114.3691))
                .zoom(11.0).bearing(0).tilt(0).build());
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

    @Override public void onLocationChanged(Location location) {
        phoneLocation=location;
        refreshLiveState();
    }

    @Override public void onProviderEnabled(String provider) {}

    @Override public void onProviderDisabled(String provider) {
        refreshLiveState();
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==REQ_LOCATION&&grantResults.length>0
                &&grantResults[0]==PackageManager.PERMISSION_GRANTED)startGps();
    }

    private LinearLayout row() {
        LinearLayout r=new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0,dp(3),0,0);
        return r;
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
        if(mapView!=null)mapView.onStart();
    }

    @Override protected void onResume() {
        super.onResume();
        if(mapView!=null)mapView.onResume();
        liveHandler.removeCallbacks(liveRefresh);
        liveHandler.post(liveRefresh);
        startGps();
        rebuildSonarIfNeeded(true);
    }

    @Override protected void onPause() {
        liveHandler.removeCallbacks(liveRefresh);
        if(locationManager!=null&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                ==PackageManager.PERMISSION_GRANTED) {
            try{locationManager.removeUpdates(this);}catch(Exception ignored){}
        }
        if(mapView!=null)mapView.onPause();
        super.onPause();
    }

    @Override protected void onStop() {
        if(mapView!=null)mapView.onStop();
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if(mapView!=null)mapView.onSaveInstanceState(outState);
    }

    @Override public void onLowMemory() {
        super.onLowMemory();
        if(mapView!=null)mapView.onLowMemory();
    }

    @Override protected void onDestroy() {
        liveHandler.removeCallbacks(liveRefresh);
        if(mapView!=null)mapView.onDestroy();
        super.onDestroy();
    }
}
