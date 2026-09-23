package com.sembulung.navigator;

import android.Manifest;
import android.app.Activity;
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

import com.sembulung.navigator.ais.AisTarget;
import com.sembulung.navigator.ais.AisTargetStore;
import com.sembulung.navigator.sonar.DepthSample;
import com.sembulung.navigator.sonar.SonarChartStore;

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
import java.util.List;
import java.util.Locale;

public class VectorOfflineMapActivity extends Activity implements LocationListener {
    private static final int REQ_PMTILES = 940;
    private static final int REQ_LOCATION = 941;
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

    private boolean marineOverlayEnabled = true;
    private boolean depthOverlayEnabled = false;
    private boolean pmtilesLoaded = false;
    private boolean followBoat = true;

    private LocationManager locationManager;
    private Location gpsLocation;

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

        TextView title=label("SEMBULUNG UNIFIED MARINE MAP • V25",15,true);
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

        Button wp=button("WAYPOINT");
        wp.setOnClickListener(v->startActivity(new Intent(this,NavigationActivity.class)));
        row2.addView(wp,quarter());

        Button ais=button("AIS");
        ais.setOnClickListener(v->startActivity(new Intent(this,AisActivity.class)));
        row2.addView(ais,quarter());

        Button sonar=button("SONAR");
        sonar.setOnClickListener(v->startActivity(new Intent(this,SonarSurveyActivity.class)));
        row2.addView(sonar,quarter());

        Button back=button("KEMBALI");
        back.setOnClickListener(v->finish());
        row2.addView(back,quarter());
        top.addView(row2);

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
                +"\"name\":\"SEMBULUNG UNIFIED V25\","
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
        overlay.invalidate();

        LatLng pos=currentLatLng();
        double sog=currentSog();
        double cog=currentCog();
        Double depth=n.depthFresh(15000L)?n.depth:null;

        String source=gpsLocation!=null?"GPS":(n.positionFresh(LIVE_AGE_MS)?"NMEA":"NO FIX");
        navStatus.setText(String.format(Locale.US,
                "%s • SOG %.1f kn • COG %s • DEPTH %s • AIS %d • WP %d • SONAR %d",
                source,sog,
                Double.isFinite(cog)?String.format(Locale.US,"%.0f°",cog):"--",
                depth!=null?String.format(Locale.US,"%.1fm",depth):"--",
                ais.size(),wps.size(),sonar.size()));

        if(followBoat&&pos!=null&&map!=null) {
            CameraPosition cp=map.getCameraPosition();
            map.setCameraPosition(new CameraPosition.Builder(cp).target(pos).build());
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

            drawSoundings(c);
            drawRouteAndWaypoints(c);
            drawAis(c);
            drawVessel(c);
        }

        private PointF screen(double lat,double lon) {
            try{return map.getProjection().toScreenLocation(new LatLng(lat,lon));}
            catch(Exception e){return null;}
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
            for(AisTarget t:aisTargets) {
                if(t==null||!t.hasValidPosition())continue;
                PointF pt=screen(t.latitude,t.longitude);
                if(pt==null||offscreen(pt))continue;
                double course=t.hasMotionVector()?t.courseDeg:(t.headingDeg!=null?t.headingDeg:0.0);
                drawTriangle(c,pt.x,pt.y,course,dp(9),Color.rgb(255,151,55));

                if(t.hasMotionVector()&&t.speedKnots>0.3) {
                    double r=Math.toRadians(t.courseDeg);
                    float len=dp((int)Math.min(28,8+t.speedKnots));
                    p.setColor(Color.rgb(255,180,80));p.setStrokeWidth(dp(2));p.setAlpha(220);
                    c.drawLine(pt.x,pt.y,pt.x+(float)Math.sin(r)*len,pt.y-(float)Math.cos(r)*len,p);
                }

                if(map.getCameraPosition().zoom>=11.0) {
                    text.setColor(Color.rgb(255,210,135));text.setAlpha(255);
                    c.drawText(Long.toString(t.mmsi),pt.x+dp(9),pt.y+dp(4),text);
                }
            }
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
        if(mapView!=null)mapView.onDestroy();
        super.onDestroy();
    }
}
