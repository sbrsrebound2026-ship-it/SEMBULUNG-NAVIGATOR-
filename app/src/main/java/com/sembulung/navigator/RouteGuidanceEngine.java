package com.sembulung.navigator;

import java.util.List;

public final class RouteGuidanceEngine {
    private static final double EARTH_NM=3440.065;
    private RouteGuidanceEngine(){}

    public static final class Guidance {
        public final int activeIndex;
        public final String targetName;
        public final double distanceNm;
        public final double bearingDeg;
        public final double xteNm;
        public final double etaMinutes;
        public final boolean arrived;

        public Guidance(int activeIndex,String targetName,double distanceNm,double bearingDeg,
                        double xteNm,double etaMinutes,boolean arrived){
            this.activeIndex=activeIndex;
            this.targetName=targetName;
            this.distanceNm=distanceNm;
            this.bearingDeg=bearingDeg;
            this.xteNm=xteNm;
            this.etaMinutes=etaMinutes;
            this.arrived=arrived;
        }
    }

    public static Guidance assess(
            double ownLat,double ownLon,Double speedKnots,
            List<WaypointStore.Waypoint> waypoints,int activeIndex,double arrivalRadiusNm){

        if(waypoints==null||activeIndex<0||activeIndex>=waypoints.size())return null;
        WaypointStore.Waypoint target=waypoints.get(activeIndex);
        double distance=haversineNm(ownLat,ownLon,target.lat,target.lon);
        double bearing=initialBearing(ownLat,ownLon,target.lat,target.lon);
        double eta=(speedKnots!=null&&speedKnots>=0.5)?distance/speedKnots*60.0:Double.NaN;
        double xte=Double.NaN;
        if(activeIndex>0){
            WaypointStore.Waypoint start=waypoints.get(activeIndex-1);
            xte=signedCrossTrackNm(start.lat,start.lon,target.lat,target.lon,ownLat,ownLon);
        }
        return new Guidance(activeIndex,target.name,distance,bearing,xte,eta,distance<=Math.max(0.005,arrivalRadiusNm));
    }

    public static int nextIndex(int activeIndex,int size,boolean arrived,boolean autoAdvance){
        if(!arrived||!autoAdvance)return activeIndex;
        int next=activeIndex+1;
        return next<size?next:-1;
    }

    public static double haversineNm(double lat1,double lon1,double lat2,double lon2){
        double p1=Math.toRadians(lat1),p2=Math.toRadians(lat2);
        double dp=Math.toRadians(lat2-lat1),dl=Math.toRadians(lon2-lon1);
        double a=Math.sin(dp/2)*Math.sin(dp/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);
        return EARTH_NM*2.0*Math.atan2(Math.sqrt(a),Math.sqrt(1.0-a));
    }

    public static double initialBearing(double lat1,double lon1,double lat2,double lon2){
        double p1=Math.toRadians(lat1),p2=Math.toRadians(lat2),dl=Math.toRadians(lon2-lon1);
        double y=Math.sin(dl)*Math.cos(p2);
        double x=Math.cos(p1)*Math.sin(p2)-Math.sin(p1)*Math.cos(p2)*Math.cos(dl);
        return normalize(Math.toDegrees(Math.atan2(y,x)));
    }

    static double signedCrossTrackNm(double aLat,double aLon,double bLat,double bLon,double pLat,double pLon){
        double refLat=Math.toRadians((aLat+bLat+pLat)/3.0);
        double bx=(bLon-aLon)*60.0*Math.cos(refLat);
        double by=(bLat-aLat)*60.0;
        double px=(pLon-aLon)*60.0*Math.cos(refLat);
        double py=(pLat-aLat)*60.0;
        double leg=Math.hypot(bx,by);
        if(leg<1e-9)return 0.0;
        return (bx*py-by*px)/leg;
    }

    private static double normalize(double x){
        double r=x%360.0;
        return r<0?r+360.0:r;
    }
}
