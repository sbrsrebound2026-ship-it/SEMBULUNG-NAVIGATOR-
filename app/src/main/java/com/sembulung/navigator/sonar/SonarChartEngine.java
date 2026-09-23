package com.sembulung.navigator.sonar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SonarChartEngine {
    private static final double METERS_PER_DEG_LAT=110540.0;
    private static final double METERS_PER_DEG_LON=111320.0;
    private static final int MAX_AXIS=120;

    private SonarChartEngine(){}

    public static final class Cell {
        public final double south,west,north,east,depth;
        public final double confidence;
        public final double relief;

        Cell(double south,double west,double north,double east,double depth){
            this(south,west,north,east,depth,1.0,0.0);
        }

        Cell(double south,double west,double north,double east,double depth,double confidence,double relief){
            this.south=south;this.west=west;this.north=north;this.east=east;this.depth=depth;
            this.confidence=confidence;this.relief=relief;
        }
    }

    public static final class ContourSegment {
        public final double lat1,lon1,lat2,lon2,level;
        ContourSegment(double lat1,double lon1,double lat2,double lon2,double level){
            this.lat1=lat1;this.lon1=lon1;this.lat2=lat2;this.lon2=lon2;this.level=level;
        }
    }

    public static final class Stats {
        public final int acceptedSoundings;
        public final double minDepth,maxDepth,meanDepth,coverageSquareMeters,cellMeters;
        public final double contourIntervalMeters;
        public final double meanConfidence;

        Stats(int acceptedSoundings,double minDepth,double maxDepth,double meanDepth,
              double coverageSquareMeters,double cellMeters){
            this(acceptedSoundings,minDepth,maxDepth,meanDepth,coverageSquareMeters,cellMeters,Double.NaN,1.0);
        }

        Stats(int acceptedSoundings,double minDepth,double maxDepth,double meanDepth,
              double coverageSquareMeters,double cellMeters,double contourIntervalMeters,double meanConfidence){
            this.acceptedSoundings=acceptedSoundings;this.minDepth=minDepth;this.maxDepth=maxDepth;
            this.meanDepth=meanDepth;this.coverageSquareMeters=coverageSquareMeters;this.cellMeters=cellMeters;
            this.contourIntervalMeters=contourIntervalMeters;this.meanConfidence=meanConfidence;
        }
    }

    public static final class Chart {
        public final List<Cell> cells;
        public final List<ContourSegment> contours;
        public final List<DepthSample> soundings;
        public final Stats stats;

        Chart(List<Cell> cells,List<ContourSegment> contours,List<DepthSample> soundings,Stats stats){
            this.cells=Collections.unmodifiableList(cells);
            this.contours=Collections.unmodifiableList(contours);
            this.soundings=Collections.unmodifiableList(soundings);
            this.stats=stats;
        }

        public boolean isEmpty(){return cells.isEmpty()&&soundings.isEmpty();}
    }

    public static Chart build(List<DepthSample> input,double requestedCellMeters,double contourIntervalMeters){
        List<DepthSample> samples=new ArrayList<>();
        if(input!=null){
            int start=Math.max(0,input.size()-5000);
            for(int i=start;i<input.size();i++){
                DepthSample s=input.get(i);
                if(s!=null&&s.valid())samples.add(s);
            }
        }

        double requestedCell=Math.max(5.0,requestedCellMeters);
        double interval=Math.max(0.5,contourIntervalMeters);

        if(samples.isEmpty()){
            return new Chart(new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),
                    new Stats(0,Double.NaN,Double.NaN,Double.NaN,0,requestedCell,interval,0));
        }

        double lat0=0,lon0=0,minDepth=Double.POSITIVE_INFINITY,maxDepth=0,sumDepth=0;
        for(DepthSample s:samples){
            lat0+=s.lat;lon0+=s.lon;
            minDepth=Math.min(minDepth,s.depthMeters);
            maxDepth=Math.max(maxDepth,s.depthMeters);
            sumDepth+=s.depthMeters;
        }
        lat0/=samples.size();lon0/=samples.size();

        double cos=Math.max(.15,Math.cos(Math.toRadians(lat0)));
        double[] xs=new double[samples.size()],ys=new double[samples.size()];
        double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY;
        double minY=Double.POSITIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;

        for(int i=0;i<samples.size();i++){
            DepthSample s=samples.get(i);
            xs[i]=(s.lon-lon0)*METERS_PER_DEG_LON*cos;
            ys[i]=(s.lat-lat0)*METERS_PER_DEG_LAT;
            minX=Math.min(minX,xs[i]);maxX=Math.max(maxX,xs[i]);
            minY=Math.min(minY,ys[i]);maxY=Math.max(maxY,ys[i]);
        }

        double cell=requestedCell;
        double spanX=Math.max(cell,maxX-minX),spanY=Math.max(cell,maxY-minY);
        cell=Math.max(cell,Math.max(spanX/MAX_AXIS,spanY/MAX_AXIS));
        minX-=cell;maxX+=cell;minY-=cell;maxY+=cell;

        int cols=Math.max(2,(int)Math.ceil((maxX-minX)/cell));
        int rows=Math.max(2,(int)Math.ceil((maxY-minY)/cell));
        cols=Math.min(MAX_AXIS,cols);rows=Math.min(MAX_AXIS,rows);

        double[][] grid=new double[rows+1][cols+1];
        double[][] confidence=new double[rows+1][cols+1];
        double searchRadius=Math.max(cell*4.5,30.0);

        for(int r=0;r<=rows;r++){
            double y=minY+r*cell;
            for(int c=0;c<=cols;c++){
                double x=minX+c*cell;
                Estimate e=idw(x,y,xs,ys,samples,searchRadius);
                grid[r][c]=e.depth;
                confidence[r][c]=e.confidence;
            }
        }

        grid=smooth(grid,confidence,rows,cols);

        List<Cell> cells=new ArrayList<>();
        double coverage=0,confidenceSum=0;
        int confidenceCount=0;

        for(int r=0;r<rows;r++){
            for(int c=0;c<cols;c++){
                double d=averageFinite(grid[r][c],grid[r][c+1],grid[r+1][c],grid[r+1][c+1]);
                double conf=averageAll(confidence[r][c],confidence[r][c+1],confidence[r+1][c],confidence[r+1][c+1]);
                if(Double.isNaN(d)||conf<0.08)continue;

                double south=lat0+(minY+r*cell)/METERS_PER_DEG_LAT;
                double north=lat0+(minY+(r+1)*cell)/METERS_PER_DEG_LAT;
                double west=lon0+(minX+c*cell)/(METERS_PER_DEG_LON*cos);
                double east=lon0+(minX+(c+1)*cell)/(METERS_PER_DEG_LON*cos);
                double relief=relief(grid,r,c,rows,cols,cell);

                cells.add(new Cell(south,west,north,east,d,conf,relief));
                confidenceSum+=conf;confidenceCount++;
                if(conf>=.25)coverage+=cell*cell;
            }
        }

        double[][] contourGrid=new double[rows+1][cols+1];
        for(int r=0;r<=rows;r++){
            for(int c=0;c<=cols;c++){
                contourGrid[r][c]=confidence[r][c]>=.14?grid[r][c]:Double.NaN;
            }
        }

        List<ContourSegment> contours=new ArrayList<>();
        double first=Math.ceil(minDepth/interval)*interval;
        for(double level=first;level<=maxDepth+1e-9;level+=interval){
            march(contourGrid,rows,cols,minX,minY,cell,lat0,lon0,cos,level,contours);
        }

        Stats stats=new Stats(
                samples.size(),minDepth,maxDepth,sumDepth/samples.size(),
                coverage,cell,interval,
                confidenceCount==0?0:confidenceSum/confidenceCount);

        return new Chart(cells,contours,new ArrayList<>(samples),stats);
    }

    private static Estimate idw(double x,double y,double[] xs,double[] ys,List<DepthSample> samples,double radius){
        double radius2=radius*radius,weightSum=0,depthSum=0,support=0;
        for(int i=0;i<samples.size();i++){
            double dx=xs[i]-x,dy=ys[i]-y,d2=dx*dx+dy*dy;
            if(d2>radius2)continue;
            DepthSample sample=samples.get(i);
            double quality=sample.quality==DepthSample.Quality.GOOD?1.0:
                    sample.quality==DepthSample.Quality.QUESTIONABLE?.35:0.0;
            if(quality<=0)continue;
            if(d2<1.0)return new Estimate(sample.depthMeters,Math.min(1.0,.45+.25*quality));
            double w=quality/(d2+4.0);
            weightSum+=w;
            depthSum+=w*sample.depthMeters;
            support+=quality*Math.max(.15,1.0-Math.sqrt(d2)/radius);
        }
        if(weightSum==0)return new Estimate(Double.NaN,0);
        double conf=1.0-Math.exp(-support/2.5);
        return new Estimate(depthSum/weightSum,Math.max(0,Math.min(1,conf)));
    }

    private static double[][] smooth(double[][] input,double[][] confidence,int rows,int cols){
        double[][] out=new double[rows+1][cols+1];
        for(int r=0;r<=rows;r++){
            for(int c=0;c<=cols;c++){
                if(Double.isNaN(input[r][c])){out[r][c]=Double.NaN;continue;}
                double sum=input[r][c]*2.0,ws=2.0;
                for(int dr=-1;dr<=1;dr++){
                    for(int dc=-1;dc<=1;dc++){
                        if(dr==0&&dc==0)continue;
                        int rr=r+dr,cc=c+dc;
                        if(rr<0||rr>rows||cc<0||cc>cols||Double.isNaN(input[rr][cc]))continue;
                        double w=.35+.65*confidence[rr][cc];
                        sum+=input[rr][cc]*w;ws+=w;
                    }
                }
                out[r][c]=sum/ws;
            }
        }
        return out;
    }

    private static double relief(double[][] g,int r,int c,int rows,int cols,double cell){
        double center=averageFinite(g[r][c],g[r][c+1],g[r+1][c],g[r+1][c+1]);
        if(Double.isNaN(center))return 0;
        double left=c>0?averageFinite(g[r][c-1],g[r+1][c-1]):center;
        double right=c+2<=cols?averageFinite(g[r][c+2],g[r+1][c+2]):center;
        double down=r>0?averageFinite(g[r-1][c],g[r-1][c+1]):center;
        double up=r+2<=rows?averageFinite(g[r+2][c],g[r+2][c+1]):center;
        if(Double.isNaN(left))left=center;if(Double.isNaN(right))right=center;
        if(Double.isNaN(down))down=center;if(Double.isNaN(up))up=center;
        double gx=(right-left)/(2.0*cell);
        double gy=(up-down)/(2.0*cell);
        double shade=(gy*.75-gx*.45)*9.0;
        return Math.max(-1.0,Math.min(1.0,shade));
    }

    private static double averageFinite(double... v){
        double sum=0;int n=0;
        for(double x:v)if(!Double.isNaN(x)&&Double.isFinite(x)){sum+=x;n++;}
        return n==0?Double.NaN:sum/n;
    }

    private static double averageAll(double... v){
        double sum=0;for(double x:v)sum+=x;return sum/v.length;
    }

    private static void march(double[][] g,int rows,int cols,double minX,double minY,double cell,
                              double lat0,double lon0,double cos,double level,List<ContourSegment> out){
        for(int r=0;r<rows;r++){
            for(int c=0;c<cols;c++){
                double bl=g[r][c],br=g[r][c+1],tr=g[r+1][c+1],tl=g[r+1][c];
                if(anyNaN(bl,br,tr,tl))continue;
                int code=(bl>=level?1:0)|(br>=level?2:0)|(tr>=level?4:0)|(tl>=level?8:0);
                if(code==0||code==15)continue;
                Point bottom=interp(c,r,bl,c+1,r,br,level,minX,minY,cell);
                Point right=interp(c+1,r,br,c+1,r+1,tr,level,minX,minY,cell);
                Point top=interp(c,r+1,tl,c+1,r+1,tr,level,minX,minY,cell);
                Point left=interp(c,r,bl,c,r+1,tl,level,minX,minY,cell);
                switch(code){
                    case 1:case 14:add(left,bottom);break;
                    case 2:case 13:add(bottom,right);break;
                    case 3:case 12:add(left,right);break;
                    case 4:case 11:add(right,top);break;
                    case 5:add(left,top);add(bottom,right);break;
                    case 6:case 9:add(bottom,top);break;
                    case 7:case 8:add(left,top);break;
                    case 10:add(left,bottom);add(right,top);break;
                }
            }
        }
        voidConvert(out,lat0,lon0,cos,level);
    }

    private static final ThreadLocal<List<RawSegment>> RAW=new ThreadLocal<>();

    private static void add(Point a,Point b){
        List<RawSegment> list=RAW.get();
        if(list==null){list=new ArrayList<>();RAW.set(list);}
        list.add(new RawSegment(a,b));
    }

    private static void voidConvert(List<ContourSegment> out,double lat0,double lon0,double cos,double level){
        List<RawSegment> list=RAW.get();
        if(list==null||list.isEmpty())return;
        for(RawSegment s:list){
            out.add(new ContourSegment(
                    lat0+s.a.y/METERS_PER_DEG_LAT,
                    lon0+s.a.x/(METERS_PER_DEG_LON*cos),
                    lat0+s.b.y/METERS_PER_DEG_LAT,
                    lon0+s.b.x/(METERS_PER_DEG_LON*cos),
                    level));
        }
        list.clear();
    }

    private static Point interp(double c1,double r1,double v1,double c2,double r2,double v2,double level,
                                double minX,double minY,double cell){
        double t=Math.abs(v2-v1)<1e-9?.5:(level-v1)/(v2-v1);
        t=Math.max(0,Math.min(1,t));
        return new Point(minX+(c1+(c2-c1)*t)*cell,minY+(r1+(r2-r1)*t)*cell);
    }

    private static boolean anyNaN(double... v){
        for(double x:v)if(Double.isNaN(x))return true;
        return false;
    }

    private static final class Estimate{
        final double depth,confidence;
        Estimate(double depth,double confidence){this.depth=depth;this.confidence=confidence;}
    }
    private static final class Point{final double x,y;Point(double x,double y){this.x=x;this.y=y;}}
    private static final class RawSegment{final Point a,b;RawSegment(Point a,Point b){this.a=a;this.b=b;}}
}
