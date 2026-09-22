package com.sembulung.navigator;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import com.sembulung.navigator.sonar.DepthSample;
import com.sembulung.navigator.sonar.SonarChartEngine;

public class MarineMapView extends View {
    private static final int T=256;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final MarineTileLoader l;
    private final ScaleGestureDetector s;

    private int z=5;
    private double clat=-2.5,clon=118;
    private Double lat,lon,hdg;
    private boolean nmea,seamarks=true,moved=false;
    private float lx,ly;
    private boolean drag;

    private SonarChartEngine.Chart sonarChart;
    private boolean sonarEnabled=true;
    private boolean sonarShading=true;
    private boolean sonarContours=true;
    private boolean sonarSoundings=false;

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
    public boolean seamarks(){return seamarks;}

    public void sonarChart(SonarChartEngine.Chart chart){sonarChart=chart;invalidate();}
    public void sonarLayers(boolean enabled,boolean shading,boolean contours,boolean soundings){
        sonarEnabled=enabled;
        sonarShading=shading;
        sonarContours=contours;
        sonarSoundings=soundings;
        invalidate();
    }

    protected void onDraw(Canvas c){
        super.onDraw(c);
        layer(c,MarineTileLoader.LAYER_OSM);
        if(sonarEnabled&&sonarChart!=null&&sonarShading)drawSonarShading(c);
        if(seamarks)layer(c,MarineTileLoader.LAYER_SEAMARK);
        if(sonarEnabled&&sonarChart!=null&&sonarContours)drawSonarContours(c);
        if(sonarEnabled&&sonarChart!=null&&sonarSoundings)drawSonarSoundings(c);
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

    private void drawSonarShading(Canvas c){
        p.setStyle(Paint.Style.FILL);
        for(SonarChartEngine.Cell cell:sonarChart.cells){
            float[] sw=pt(cell.south,cell.west);
            float[] se=pt(cell.south,cell.east);
            float[] ne=pt(cell.north,cell.east);
            float[] nw=pt(cell.north,cell.west);
            if(offscreen(sw,se,ne,nw))continue;
            Path path=new Path();
            path.moveTo(sw[0],sw[1]);path.lineTo(se[0],se[1]);path.lineTo(ne[0],ne[1]);path.lineTo(nw[0],nw[1]);path.close();
            p.setColor(depthColor(cell.depth));
            c.drawPath(path,p);
        }
    }

    private void drawSonarContours(Canvas c){
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        for(SonarChartEngine.ContourSegment seg:sonarChart.contours){
            float[] a=pt(seg.lat1,seg.lon1),b=pt(seg.lat2,seg.lon2);
            if((a[0]<-50&&b[0]<-50)||(a[0]>getWidth()+50&&b[0]>getWidth()+50)
                    ||(a[1]<-50&&b[1]<-50)||(a[1]>getHeight()+50&&b[1]>getHeight()+50))continue;
            boolean major=Math.abs(seg.level/5.0-Math.rint(seg.level/5.0))<1e-6;
            p.setStrokeWidth(dp(major?2:1));
            p.setColor(major?0xdd9af4ff:0xaa42d8ff);
            c.drawLine(a[0],a[1],b[0],b[1],p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    private void drawSonarSoundings(Canvas c){
        if(z<13)return;
        int step=Math.max(1,sonarChart.soundings.size()/90);
        p.setTextSize(dp(9));
        p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER);
        for(int i=0;i<sonarChart.soundings.size();i+=step){
            DepthSample s=sonarChart.soundings.get(i);
            float[] q=pt(s.lat,s.lon);
            if(q[0]<0||q[0]>getWidth()||q[1]<0||q[1]>getHeight())continue;
            p.setColor(0xcc00151f);c.drawCircle(q[0],q[1],dp(8),p);
            p.setColor(Color.WHITE);c.drawText(String.format(java.util.Locale.US,"%.1f",s.depthMeters),q[0],q[1]+dp(3),p);
        }
        p.setTextAlign(Paint.Align.LEFT);
    }

    private int depthColor(double d){
        if(d<3)return Color.argb(105,255,74,74);
        if(d<5)return Color.argb(95,255,154,56);
        if(d<10)return Color.argb(85,255,222,71);
        if(d<20)return Color.argb(78,61,226,214);
        if(d<30)return Color.argb(72,45,155,255);
        return Color.argb(68,45,80,220);
    }

    private boolean offscreen(float[]... q){
        boolean left=true,right=true,top=true,bottom=true;
        for(float[] v:q){
            if(v[0]>=-40)left=false;if(v[0]<=getWidth()+40)right=false;
            if(v[1]>=-40)top=false;if(v[1]<=getHeight()+40)bottom=false;
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
        c.save();c.rotate(h,q[0],q[1]);c.drawPath(b,p);c.restore();
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(Color.WHITE);
        c.drawCircle(q[0],q[1],dp(17),p);
        p.setStyle(Paint.Style.FILL);
    }

    private void cross(Canvas c){
        float x=getWidth()/2f,y=getHeight()/2f;
        p.setColor(0x55ffffff);p.setStrokeWidth(dp(1));
        c.drawLine(x-dp(7),y,x+dp(7),y,p);
        c.drawLine(x,y-dp(7),x,y+dp(7),p);
    }

    private float[] pt(double a,double o){
        double cx=wx(clon),cy=wy(clat),x=wx(o),y=wy(a),w=T*(double)(1<<z),d=x-cx;
        if(d>w/2)d-=w;if(d<-w/2)d+=w;
        return new float[]{(float)(getWidth()/2d+d),(float)(getHeight()/2d+y-cy)};
    }

    public boolean onTouchEvent(MotionEvent e){
        s.onTouchEvent(e);
        if(s.isInProgress())return true;
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:lx=e.getX();ly=e.getY();drag=true;return true;
            case MotionEvent.ACTION_MOVE:
                if(!drag)return true;
                float dx=e.getX()-lx,dy=e.getY()-ly;lx=e.getX();ly=e.getY();
                clon=lon(wx(clon)-dx);clat=lat(wy(clat)-dy);moved=true;invalidate();return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:drag=false;return true;
        }
        return true;
    }

    private double wx(double o){double w=T*(double)(1<<z);return(norm(o)+180)/360*w;}
    private double wy(double a){a=cap(a);double s=Math.sin(Math.toRadians(a)),w=T*(double)(1<<z);return(.5-Math.log((1+s)/(1-s))/(4*Math.PI))*w;}
    private double lon(double x){double w=T*(double)(1<<z);x=((x%w)+w)%w;return x/w*360-180;}
    private double lat(double y){double w=T*(double)(1<<z);y=Math.max(0,Math.min(w,y));return Math.toDegrees(Math.atan(Math.sinh(Math.PI-2*Math.PI*y/w)));}
    private double cap(double a){return Math.max(-85.05112878,Math.min(85.05112878,a));}
    private double norm(double o){double r=o%360;if(r>180)r-=360;if(r<-180)r+=360;return r;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
