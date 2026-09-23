package com.sembulung.navigator.sonar;

public final class BathymetryStyle {
    public static final String LOW="LOW";
    public static final String NORMAL="NORMAL";
    public static final String HIGH="HIGH";
    public static final String ULTRA="ULTRA";

    private BathymetryStyle(){}

    public static int zoomBucket(int zoom){
        if(zoom>=16)return 3;
        if(zoom>=14)return 2;
        if(zoom>=12)return 1;
        return 0;
    }

    public static double cellMeters(String density,int zoom){
        double base;
        if(ULTRA.equals(density))base=7.0;
        else if(HIGH.equals(density))base=10.0;
        else if(LOW.equals(density))base=28.0;
        else base=18.0;

        switch(zoomBucket(zoom)){
            case 3:return Math.max(5.0,base*.70);
            case 2:return Math.max(6.0,base*.85);
            case 1:return base;
            default:return base*1.6;
        }
    }

    public static double contourInterval(double configured,String density,int zoom){
        double v=Math.max(.5,configured);
        int b=zoomBucket(zoom);
        if(ULTRA.equals(density)){
            if(b>=3)return Math.min(v,.5);
            if(b>=2)return Math.min(v,1.0);
            if(b>=1)return Math.min(v,2.0);
        }else if(HIGH.equals(density)){
            if(b>=3)return Math.min(v,1.0);
            if(b>=2)return Math.min(v,2.0);
        }else if(LOW.equals(density)){
            return Math.max(v,b>=2?5.0:10.0);
        }
        if(b==0)return Math.max(v,5.0);
        return v;
    }

    public static double majorInterval(double minor){
        if(minor<=1.0)return 5.0;
        if(minor<=2.0)return 10.0;
        if(minor<=5.0)return 20.0;
        return Math.max(20.0,minor*5.0);
    }

    public static double minConfidence(String density){
        if(ULTRA.equals(density))return .18;
        if(HIGH.equals(density))return .22;
        if(LOW.equals(density))return .34;
        return .28;
    }

    public static String profileKey(String density,int zoom,double configured){
        return (density==null?NORMAL:density)+":"+zoomBucket(zoom)+":"+String.format(java.util.Locale.US,"%.2f",configured);
    }
}
