package com.sembulung.navigator;

import android.content.Context;
import android.graphics.*;
import android.view.*;

import com.sembulung.navigator.ais.AisCollisionEngine;
import com.sembulung.navigator.ais.AisTarget;
import com.sembulung.navigator.sonar.BathymetryStyle;
import com.sembulung.navigator.sonar.DepthSample;
import com.sembulung.navigator.sonar.SonarChartEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MarineMapView extends View {
    private static final int T=256;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final MarineTileLoader l;
    private final ScaleGestureDetector s;

    private int z=5;
    private double clat=-2.5,clon=118;
    private Double lat,lon,hdg;
    private boolean nmea,seamarks=true,globalBathymetry=true,moved=false;
    private float lx,ly;
    private boolean drag;
    private int previewRadiusKm=0;

    private SonarChartEngine.Chart sonarChart;
    private boolean sonarEnabled=true;
    private boolean sonarShading=true;
    private boolean sonarContours=true;
    private boolean sonarSoundings=false;

    private String sonarDensity=BathymetryStyle.HIGH;
    private boolean sonarRelief=true;
    private boolean sonarCoverageMask=true;
    private boolean sonarContourLabels=true;
    private double sonarMinorInterval=5.0;

    private List<WaypointStore.Waypoint> waypoints=new ArrayList<>();
    private int activeWaypointIndex=-1;
    private List<AisTarget> aisTargets=new ArrayList<>();
    private boolean aisEnabled=true;
    private Double ownSpeedKnots,ownCourseDeg;

    public MarineMapView(Context c,MarineTileLoader l){
        super(c);
        this.l=l;
        setBackgroundColor(Color.rgb(16,52,70));
        s=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            float a;
            public boolean onScaleBegin(ScaleGestureDetector d){a=d.getCurrentSpan();return true;}
            public boolean onScale(ScaleGestureDetector d){
                float r=d.getCurrentSpan()/Math.max(1,a);
                if(r>1.25f){zoom(1);a=d.getCurrentSpan();}
                else if(r<.8f){zoom(-1);a=d.getCurrentSpan();}
                moved=true;
                return true;
            }
        });
    }

    public int zoomLevel(){return z;}

    public void vessel(Double a,Double o,Double h,boolean n,boolean center){
        lat=a;lon=o;hdg=h;nmea=n;
        if(center&&!moved&&a!=null&&o!=null){
            if(z<11)z=12;
            clat=cap(a);clon=norm(o);
        }
        invalidate();
    }

    public void recenter(){
        if(lat!=null&&lon!=null){
            moved=false;clat=cap(lat);clon=norm(lon);invalidate();
        }
    }

    public void zoom(int d){z=Math.max(3,Math.min(18,z+d));invalidate();}
    public void seamarks(boolean v){seamarks=v;invalidate();}
    public void globalBathymetry(boolean v){globalBathymetry=v;invalidate();}
    public boolean globalBathymetry(){return globalBathymetry;}
    public int visibleBathymetryTileCount(){
        double cx=wx(clon),cy=wy(clat),L=cx-getWidth()/2d,U=cy-getHeight()/2d;
        int n=1<<z,x0=(int)Math.floor(L/T),x1=(int)Math.floor((L+getWidth())/T),
                y0=(int)Math.floor(U/T),y1=(int)Math.floor((U+getHeight())/T);
        return Math.max(0,x1-x0+1)*Math.max(0,y1-y0+1);
    }
    public int downloadVisibleBathymetry(){
        return downloadBathymetryArea(0);
    }

    /**
     * Downloads bathymetry inside a geographic radius around the current map center.
     * Radius is measured in kilometers, not tile counts.
     */
    public int downloadBathymetryRadiusKm(int radiusKm){
        if(radiusKm<=0)return downloadVisibleBathymetry();
        return downloadBathymetryRadiusKmInternal(radiusKm,false);
    }

    private int downloadBathymetryRadiusKmInternal(int radiusKm,boolean countOnly){
        double lat0=clat;
        double lon0=clon;
        double latDelta=radiusKm/111.32;
        double lonScale=Math.max(0.1,Math.cos(Math.toRadians(lat0)));
        double lonDelta=radiusKm/(111.32*lonScale);
        double south=cap(lat0-latDelta), north=cap(lat0+latDelta);
        double west=norm(lon0-lonDelta), east=norm(lon0+lonDelta);
        int n=1<<z;
        int y0=(int)Math.floor(wy(north)/T);
        int y1=(int)Math.floor(wy(south)/T);
        int x0=(int)Math.floor(wx(west)/T);
        int x1=(int)Math.floor(wx(east)/T);
        int count=0;
        for(int y=y0;y<=y1;y++){
            if(y<0||y>=n)continue;
            for(int x=x0;x<=x1;x++){
                int xx=((x%n)+n)%n;
                if(!tileIntersectsRadiusKm(xx,y,z,lat0,lon0,radiusKm))continue;
                if(!countOnly)l.prefetch(MarineTileLoader.LAYER_BATHY,z,xx,y);
                count++;
            }
        }
        return count;
    }

    public int cachedBathymetryRadiusKmCount(int radiusKm){
        if(radiusKm<=0)return cachedBathymetryAreaCount(0);
        double lat0=clat;
        double lon0=clon;
        double latDelta=radiusKm/111.32;
        double lonScale=Math.max(0.1,Math.cos(Math.toRadians(lat0)));
        double lonDelta=radiusKm/(111.32*lonScale);
        double south=cap(lat0-latDelta), north=cap(lat0+latDelta);
        double west=norm(lon0-lonDelta), east=norm(lon0+lonDelta);
        int n=1<<z;
        int y0=(int)Math.floor(wy(north)/T);
        int y1=(int)Math.floor(wy(south)/T);
        int x0=(int)Math.floor(wx(west)/T);
        int x1=(int)Math.floor(wx(east)/T);
        int count=0;
        for(int y=y0;y<=y1;y++){
            if(y<0||y>=n)continue;
            for(int x=x0;x<=x1;x++){
                int xx=((x%n)+n)%n;
                if(!tileIntersectsRadiusKm(xx,y,z,lat0,lon0,radiusKm))continue;
                if(l.hasCached(MarineTileLoader.LAYER_BATHY,z,xx,y))count++;
            }
        }
        return count;
    }

    public double centerLatitude(){return clat;}
    public double centerLongitude(){return clon;}
    public void downloadPreviewRadiusKm(int radiusKm){previewRadiusKm=Math.max(0,radiusKm);invalidate();}
    public int previewRadiusKm(){return previewRadiusKm;}
    /**
     * Downloads bathymetry around the current viewport.
     * radiusTiles=0 means viewport only; 2 and 5 add a 5x5 or 11x11 tile ring.
     */
    public int downloadBathymetryArea(int radiusTiles){
        double cx=wx(clon),cy=wy(clat),L=cx-getWidth()/2d,U=cy-getHeight()/2d;
        int n=1<<z;
        int x0=(int)Math.floor(L/T)-radiusTiles;
        int x1=(int)Math.floor((L+getWidth())/T)+radiusTiles;
        int y0=(int)Math.floor(U/T)-radiusTiles;
        int y1=(int)Math.floor((U+getHeight())/T)+radiusTiles;
        int count=0;
        for(int y=y0;y<=y1;y++){
            if(y<0||y>=n)continue;
            for(int x=x0;x<=x1;x++){
                l.prefetch(MarineTileLoader.LAYER_BATHY,z,x,y);
                count++;
            }
        }
        return count;
    }
    public int cachedBathymetryAreaCount(int radiusTiles){
        double cx=wx(clon),cy=wy(clat),L=cx-getWidth()/2d,U=cy-getHeight()/2d;
        int n=1<<z;
        int x0=(int)Math.floor(L/T)-radiusTiles;
        int x1=(int)Math.floor((L+getWidth())/T)+radiusTiles;
        int y0=(int)Math.floor(U/T)-radiusTiles;
        int y1=(int)Math.floor((U+getHeight())/T)+radiusTiles;
        int count=0;
        for(int y=y0;y<=y1;y++){
            if(y<0||y>=n)continue;
            for(int x=x0;x<=x1;x++){
                int xx=((x%n)+n)%n;
                if(l.hasCached(MarineTileLoader.LAYER_BATHY,z,xx,y))count++;
            }
        }
        return count;
    }
    public boolean seamarks(){return seamarks;}

    public void sonarChart(SonarChartEngine.Chart chart){sonarChart=chart;invalidate();}

    public void sonarRenderOptions(String density,boolean relief,boolean coverageMask,boolean contourLabels,double minorInterval){
        sonarDensity=density==null?BathymetryStyle.HIGH:density;
        sonarRelief=relief;
        sonarCoverageMask=coverageMask;
        sonarContourLabels=contourLabels;
        sonarMinorInterval=Math.max(.5,minorInterval);
        invalidate();
    }

    public void waypoints(List<WaypointStore.Waypoint> v){waypoints(v,-1);}
    public void waypoints(List<WaypointStore.Waypoint> v,int activeIndex){
        waypoints=v==null?new ArrayList<>():new ArrayList<>(v);
        activeWaypointIndex=activeIndex>=0&&activeIndex<waypoints.size()?activeIndex:-1;
        invalidate();
    }

    public void aisTargets(List<AisTarget> v,boolean enabled){
        aisTargets=v==null?new ArrayList<>():new ArrayList<>(v);
        aisEnabled=enabled;
        invalidate();
    }

    public void aisOwnShip(Double speedKnots,Double courseDeg){
        ownSpeedKnots=speedKnots;ownCourseDeg=courseDeg;invalidate();
    }

    public void focus(double a,double o){
        clat=cap(a);clon=norm(o);moved=true;
        if(z<11)z=12;
        invalidate();
    }

    public void sonarLayers(boolean enabled,boolean shading,boolean contours,boolean soundings){
        sonarEnabled=enabled;
        sonarShading=shading;
        sonarContours=contours;
        sonarSoundings=soundings;
        invalidate();
    }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        layer(c,MarineTileLoader.LAYER_OSM);
        if(globalBathymetry)layer(c,MarineTileLoader.LAYER_BATHY);
        if(sonarEnabled&&sonarChart!=null&&sonarShading)drawSonarShading(c);
        if(seamarks)layer(c,MarineTileLoader.LAYER_SEAMARK);
        if(sonarEnabled&&sonarChart!=null&&sonarContours)drawSonarContours(c);
        if(sonarEnabled&&sonarChart!=null&&sonarSoundings)drawSonarSoundings(c);
        if(previewRadiusKm>0)drawBathymetryPreview(c);
        drawRouteAndWaypoints(c);
        if(aisEnabled)drawAis(c);
        boat(c);
        cross(c);
    }

    private void layer(Canvas c,String q){
        double cx=wx(clon),cy=wy(clat),L=cx-getWidth()/2d,U=cy-getHeight()/2d;
        int n=1<<z,x0=(int)Math.floor(L/T),x1=(int)Math.floor((L+getWidth())/T),
                y0=(int)Math.floor(U/T),y1=(int)Math.floor((U+getHeight())/T);

        for(int y=y0;y<=y1;y++){
            if(y<0||y>=n)continue;
            for(int x=x0;x<=x1;x++){
                int xx=((x%n)+n)%n;
                Bitmap b=l.get(q,z,xx,y);
                float dx=(float)(x*T-L),dy=(float)(y*T-U);
                if(b!=null)c.drawBitmap(b,null,new RectF(dx,dy,dx+T,dy+T),p);
                else if(q.equals(MarineTileLoader.LAYER_OSM)){
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(((x+y)&1)==0?Color.rgb(20,57,72):Color.rgb(24,65,80));
                    c.drawRect(dx,dy,dx+T,dy+T,p);
                }
            }
        }
    }

    private void drawBathymetryPreview(Canvas c){
        double metersPerPixel=156543.03392*Math.max(0.01,Math.cos(Math.toRadians(clat)))/(1<<z);
        float r=(float)((previewRadiusKm*1000.0)/metersPerPixel);
        float cx=getWidth()/2f,cy=getHeight()/2f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(2));
        p.setColor(0x99ffd54f);
        c.drawCircle(cx,cy,r,p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x18ffd54f);
        c.drawCircle(cx,cy,r,p);
        p.setColor(0xffffd54f);
        c.drawCircle(cx,cy,dp(4),p);
    }

    private void drawSonarShading(Canvas c){
        double minConfidence=BathymetryStyle.minConfidence(sonarDensity);
        p.setStyle(Paint.Style.FILL);

        for(SonarChartEngine.Cell cell:sonarChart.cells){
            if(sonarCoverageMask&&cell.confidence<minConfidence)continue;

            float[] sw=pt(cell.south,cell.west);
            float[] se=pt(cell.south,cell.east);
            float[] ne=pt(cell.north,cell.east);
            float[] nw=pt(cell.north,cell.west);
            if(offscreen(sw,se,ne,nw))continue;

            Path path=new Path();
            path.moveTo(sw[0],sw[1]);
            path.lineTo(se[0],se[1]);
            path.lineTo(ne[0],ne[1]);
            path.lineTo(nw[0],nw[1]);
            path.close();

            p.setColor(depthColor(cell.depth,cell.confidence));
            c.drawPath(path,p);

            if(sonarRelief&&Math.abs(cell.relief)>.03){
                int alpha=(int)Math.min(42,8+Math.abs(cell.relief)*36);
                p.setColor(cell.relief>0?Color.argb(alpha,255,255,255):Color.argb(alpha,0,12,28));
                c.drawPath(path,p);
            }

            if(sonarCoverageMask&&cell.confidence<minConfidence+.10&&z>=14){
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(1));
                p.setColor(0x335be9ff);
                c.drawPath(path,p);
                p.setStyle(Paint.Style.FILL);
            }
        }
    }

    private void drawSonarContours(Canvas c){
        double minor=sonarChart.stats!=null&&Double.isFinite(sonarChart.stats.contourIntervalMeters)
                ?sonarChart.stats.contourIntervalMeters:sonarMinorInterval;
        double majorInterval=BathymetryStyle.majorInterval(minor);
        List<RectF> labelRects=new ArrayList<>();

        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);

        int index=0;
        for(SonarChartEngine.ContourSegment seg:sonarChart.contours){
            float[] a=pt(seg.lat1,seg.lon1),b=pt(seg.lat2,seg.lon2);
            if((a[0]<-50&&b[0]<-50)||(a[0]>getWidth()+50&&b[0]>getWidth()+50)
                    ||(a[1]<-50&&b[1]<-50)||(a[1]>getHeight()+50&&b[1]>getHeight()+50))continue;

            boolean major=isMultiple(seg.level,majorInterval);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(major?2:1));
            p.setColor(major?0xeea8efff:0x9950cfff);
            c.drawLine(a[0],a[1],b[0],b[1],p);

            if(major&&sonarContourLabels&&z>=12&&index%3==0){
                drawContourLabel(c,seg,a,b,labelRects);
            }
            index++;
        }
        p.setStyle(Paint.Style.FILL);
    }

    private void drawContourLabel(Canvas c,SonarChartEngine.ContourSegment seg,float[] a,float[] b,List<RectF> occupied){
        float dx=b[0]-a[0],dy=b[1]-a[1];
        float length=(float)Math.hypot(dx,dy);
        if(length<dp(24))return;

        float x=(a[0]+b[0])*.5f,y=(a[1]+b[1])*.5f;
        String text=formatDepth(seg.level);
        p.setTextSize(dp(9));
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD));
        float w=p.measureText(text)+dp(8),h=dp(15);
        RectF box=new RectF(x-w/2,y-h/2,x+w/2,y+h/2);

        for(RectF r:occupied)if(RectF.intersects(r,box))return;
        occupied.add(box);

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xc8002036);
        c.drawRoundRect(box,dp(4),dp(4),p);
        p.setColor(0xffd9f8ff);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText(text,x,y+dp(3),p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawSonarSoundings(Canvas c){
        if(z<12)return;

        int gridSize=dp(z>=16?24:z>=14?32:42);
        int cols=Math.max(1,getWidth()/gridSize+2);
        int rows=Math.max(1,getHeight()/gridSize+2);
        boolean[][] used=new boolean[rows][cols];

        p.setTypeface(Typeface.create(Typeface.SERIF,Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(dp(z>=16?10:9));

        for(DepthSample sample:sonarChart.soundings){
            float[] q=pt(sample.lat,sample.lon);
            if(q[0]<0||q[0]>getWidth()||q[1]<0||q[1]>getHeight())continue;

            int cx=Math.max(0,Math.min(cols-1,(int)(q[0]/gridSize)));
            int cy=Math.max(0,Math.min(rows-1,(int)(q[1]/gridSize)));
            if(used[cy][cx])continue;
            used[cy][cx]=true;

            String text=formatSounding(sample.depthMeters);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setColor(0xcc002238);
            c.drawText(text,q[0],q[1]+dp(3),p);

            p.setStyle(Paint.Style.FILL);
            p.setColor(sample.quality==DepthSample.Quality.GOOD?0xffe8fbff:0xbbc5dce5);
            c.drawText(text,q[0],q[1]+dp(3),p);
        }

        p.setTextAlign(Paint.Align.LEFT);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawRouteAndWaypoints(Canvas c){
        if(waypoints==null||waypoints.isEmpty())return;

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(2));
        p.setColor(0xaaffffff);
        Path path=new Path();
        boolean first=true;
        for(WaypointStore.Waypoint w:waypoints){
            float[] q=pt(w.lat,w.lon);
            if(first){path.moveTo(q[0],q[1]);first=false;}else path.lineTo(q[0],q[1]);
        }
        c.drawPath(path,p);

        if(activeWaypointIndex>=0&&activeWaypointIndex<waypoints.size()){
            WaypointStore.Waypoint target=waypoints.get(activeWaypointIndex);
            float[] b=pt(target.lat,target.lon);
            float[] a;
            if(activeWaypointIndex>0){
                WaypointStore.Waypoint start=waypoints.get(activeWaypointIndex-1);
                a=pt(start.lat,start.lon);
            }else if(lat!=null&&lon!=null){
                a=pt(lat,lon);
            }else a=null;

            if(a!=null){
                p.setColor(0xff23d7ff);
                p.setStrokeWidth(dp(4));
                c.drawLine(a[0],a[1],b[0],b[1],p);
            }
        }

        p.setStyle(Paint.Style.FILL);
        p.setTextSize(dp(10));
        p.setTypeface(Typeface.DEFAULT_BOLD);

        for(int i=0;i<waypoints.size();i++){
            WaypointStore.Waypoint w=waypoints.get(i);
            float[] q=pt(w.lat,w.lon);
            boolean active=i==activeWaypointIndex;
            p.setColor(active?0xffffb020:0xffffffff);
            c.drawCircle(q[0],q[1],dp(active?8:5),p);
            p.setColor(Color.WHITE);
            c.drawText((active?"▶ ":"")+w.name,q[0]+dp(9),q[1]-dp(8),p);
        }
    }

    private void drawAis(Canvas c){
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(dp(9));
        p.setStyle(Paint.Style.FILL);
        long cutoff=System.currentTimeMillis()-120000L;

        for(AisTarget t:aisTargets){
            if(t==null||!t.hasValidPosition()||t.receivedAtMillis<cutoff)continue;

            float[] q=pt(t.latitude,t.longitude);
            if(q[0]<-30||q[0]>getWidth()+30||q[1]<-30||q[1]>getHeight()+30)continue;

            AisCollisionEngine.Assessment assessment=(lat!=null&&lon!=null&&ownSpeedKnots!=null&&ownCourseDeg!=null)
                    ?AisCollisionEngine.assess(lat,lon,ownSpeedKnots,ownCourseDeg,t)
                    :new AisCollisionEngine.Assessment(Double.NaN,Double.NaN,Double.NaN,Double.NaN,AisCollisionEngine.Risk.UNKNOWN);

            float course=Double.isNaN(t.courseDeg)?0:(float)t.courseDeg;
            Path a=new Path();
            a.moveTo(q[0],q[1]-dp(9));
            a.lineTo(q[0]-dp(6),q[1]+dp(7));
            a.lineTo(q[0]+dp(6),q[1]+dp(7));
            a.close();

            p.setColor(aisRiskColor(assessment.risk));
            c.save();c.rotate(course,q[0],q[1]);c.drawPath(a,p);c.restore();

            p.setColor(Color.WHITE);
            c.drawText(String.format(Locale.US,"%09d",t.mmsi),q[0]+dp(8),q[1],p);

            if(assessment.risk==AisCollisionEngine.Risk.DANGER||assessment.risk==AisCollisionEngine.Risk.WARNING){
                String cpa=Double.isNaN(assessment.cpaNm)?"---":String.format(Locale.US,"%.2fNM",assessment.cpaNm);
                String tcpa=Double.isNaN(assessment.tcpaMinutes)?"---":String.format(Locale.US,"%.0fmin",assessment.tcpaMinutes);
                p.setTextSize(dp(8));
                c.drawText("CPA "+cpa+" / "+tcpa,q[0]+dp(8),q[1]+dp(11),p);
                p.setTextSize(dp(9));
            }
        }
    }

    private int aisRiskColor(AisCollisionEngine.Risk risk){
        switch(risk){
            case DANGER:return 0xffff4058;
            case WARNING:return 0xffffa62b;
            case MONITOR:return 0xff38c9ff;
            case SAFE:return 0xff47f59a;
            default:return 0xffc7d4df;
        }
    }

    private int depthColor(double d,double confidence){
        int alpha=(int)Math.max(58,Math.min(132,72+confidence*55));
        if(d<3)return Color.argb(alpha,188,246,250);
        if(d<5)return Color.argb(alpha,133,228,244);
        if(d<10)return Color.argb(alpha,82,202,236);
        if(d<20)return Color.argb(alpha,38,165,218);
        if(d<50)return Color.argb(alpha,27,119,190);
        if(d<100)return Color.argb(alpha,24,88,164);
        if(d<200)return Color.argb(alpha,20,62,132);
        return Color.argb(alpha,14,42,104);
    }

    private static boolean isMultiple(double value,double interval){
        if(interval<=0)return false;
        double q=value/interval;
        return Math.abs(q-Math.rint(q))<1e-5;
    }

    private String formatDepth(double d){
        if(Math.abs(d-Math.rint(d))<.05)return String.format(Locale.US,"%.0f",d);
        return String.format(Locale.US,"%.1f",d);
    }

    private String formatSounding(double d){
        if(d>=30)return String.format(Locale.US,"%.0f",d);
        if(d>=10)return String.format(Locale.US,"%.1f",d);
        return String.format(Locale.US,"%.1f",d);
    }

    private boolean offscreen(float[]... q){
        boolean left=true,right=true,top=true,bottom=true;
        for(float[] v:q){
            if(v[0]>=-40)left=false;
            if(v[0]<=getWidth()+40)right=false;
            if(v[1]>=-40)top=false;
            if(v[1]<=getHeight()+40)bottom=false;
        }
        return left||right||top||bottom;
    }

    private void boat(Canvas c){
        if(lat==null||lon==null)return;
        float[] q=pt(lat,lon);
        float h=hdg==null?0:hdg.floatValue();

        Path b=new Path();
        b.moveTo(q[0],q[1]-dp(15));
        b.lineTo(q[0]-dp(9),q[1]+dp(11));
        b.lineTo(q[0],q[1]+dp(6));
        b.lineTo(q[0]+dp(9),q[1]+dp(11));
        b.close();

        p.setStyle(Paint.Style.FILL);
        p.setColor(nmea?0xffff4bc1:0xff24d8ff);
        c.save();
        c.rotate(h,q[0],q[1]);
        c.drawPath(b,p);
        c.restore();

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(2));
        p.setColor(Color.WHITE);
        c.drawCircle(q[0],q[1],dp(17),p);
        p.setStyle(Paint.Style.FILL);
    }

    private void cross(Canvas c){
        float x=getWidth()/2f,y=getHeight()/2f;
        p.setColor(0x55ffffff);
        p.setStrokeWidth(dp(1));
        c.drawLine(x-dp(7),y,x+dp(7),y,p);
        c.drawLine(x,y-dp(7),x,y+dp(7),p);
    }

    private float[] pt(double a,double o){
        double cx=wx(clon),cy=wy(clat),x=wx(o),y=wy(a),w=T*(double)(1<<z),d=x-cx;
        if(d>w/2)d-=w;
        if(d<-w/2)d+=w;
        return new float[]{(float)(getWidth()/2d+d),(float)(getHeight()/2d+y-cy)};
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        s.onTouchEvent(e);
        if(s.isInProgress())return true;

        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                lx=e.getX();ly=e.getY();drag=true;return true;
            case MotionEvent.ACTION_MOVE:
                if(!drag)return true;
                float dx=e.getX()-lx,dy=e.getY()-ly;
                lx=e.getX();ly=e.getY();
                clon=lon(wx(clon)-dx);
                clat=lat(wy(clat)-dy);
                moved=true;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                drag=false;
                return true;
        }
        return true;
    }

    private boolean tileIntersectsRadiusKm(int x,int y,int zoom,double centerLat,double centerLon,double radiusKm){
        int n=1<<zoom;
        double west=x/(double)n*360.0-180.0;
        double east=(x+1)/(double)n*360.0-180.0;
        double north=tileCenterLatitude(y-0.5,zoom);
        double south=tileCenterLatitude(y+0.5,zoom);
        double scale=111.32;
        double cos=Math.max(0.1,Math.cos(Math.toRadians(centerLat)));
        double px=norm(centerLon)*cos*scale;
        double py=centerLat*scale;
        double x1=norm(west)*cos*scale;
        double x2=norm(east)*cos*scale;
        double y1=south*scale;
        double y2=north*scale;
        double minX=Math.min(x1,x2),maxX=Math.max(x1,x2);
        double minY=Math.min(y1,y2),maxY=Math.max(y1,y2);
        double nearestX=Math.max(minX,Math.min(px,maxX));
        double nearestY=Math.max(minY,Math.min(py,maxY));
        double dx=px-nearestX,dy=py-nearestY;
        return Math.hypot(dx,dy)<=radiusKm;
    }

    private double tileCenterLongitude(int x,int zoom){
        int n=1<<zoom;
        return ((x+0.5)/n)*360.0-180.0;
    }

    private double tileCenterLatitude(double y,int zoom){
        int n=1<<zoom;
        double mercator=Math.PI*(1.0-2.0*(y+0.5)/n);
        return Math.toDegrees(Math.atan(Math.sinh(mercator)));
    }

    private double distanceKm(double lat1,double lon1,double lat2,double lon2){
        double r=6371.0088;
        double p1=Math.toRadians(lat1),p2=Math.toRadians(lat2);
        double dp=Math.toRadians(lat2-lat1),dl=Math.toRadians(norm(lon2-lon1));
        double a=Math.sin(dp/2)*Math.sin(dp/2)
                +Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);
        return r*2.0*Math.atan2(Math.sqrt(a),Math.sqrt(Math.max(0.0,1.0-a)));
    }

    private double wx(double o){double w=T*(double)(1<<z);return(norm(o)+180)/360*w;}
    private double wy(double a){a=cap(a);double s=Math.sin(Math.toRadians(a)),w=T*(double)(1<<z);return(.5-Math.log((1+s)/(1-s))/(4*Math.PI))*w;}
    private double lon(double x){double w=T*(double)(1<<z);x=((x%w)+w)%w;return x/w*360-180;}
    private double lat(double y){double w=T*(double)(1<<z);y=Math.max(0,Math.min(w,y));return Math.toDegrees(Math.atan(Math.sinh(Math.PI-2*Math.PI*y/w)));}
    private double cap(double a){return Math.max(-85.05112878,Math.min(85.05112878,a));}
    private double norm(double o){double r=o%360;if(r>180)r-=360;if(r<-180)r+=360;return r;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
