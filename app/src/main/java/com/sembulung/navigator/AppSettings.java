package com.sembulung.navigator;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppSettings {
    private static final String PREFS="sembulung_settings";
    private AppSettings(){}

    private static SharedPreferences p(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}

    public static boolean keepScreenOn(Context c){return p(c).getBoolean("keep_screen_on",true);}
    public static void keepScreenOn(Context c,boolean v){p(c).edit().putBoolean("keep_screen_on",v).apply();}

    public static boolean autoCenter(Context c){return p(c).getBoolean("auto_center",true);}
    public static void autoCenter(Context c,boolean v){p(c).edit().putBoolean("auto_center",v).apply();}

    public static boolean sonarEnabled(Context c){return p(c).getBoolean("sonar_enabled",true);}
    public static void sonarEnabled(Context c,boolean v){p(c).edit().putBoolean("sonar_enabled",v).apply();}

    public static boolean aisEnabled(Context c){return p(c).getBoolean("ais_enabled",true);}
    public static void aisEnabled(Context c,boolean v){p(c).edit().putBoolean("ais_enabled",v).apply();}

    public static boolean shallowWarning(Context c){return p(c).getBoolean("shallow_warning",true);}
    public static void shallowWarning(Context c,boolean v){p(c).edit().putBoolean("shallow_warning",v).apply();}

    public static double shallowMeters(Context c){
        try{return Double.parseDouble(p(c).getString("shallow_m","5.0"));}catch(Exception e){return 5.0;}
    }
    public static void shallowMeters(Context c,double v){p(c).edit().putString("shallow_m",String.valueOf(v)).apply();}

    public static String distanceUnit(Context c){return p(c).getString("distance_unit","NM");}
    public static void distanceUnit(Context c,String v){p(c).edit().putString("distance_unit",v).apply();}

    public static String depthUnit(Context c){return p(c).getString("depth_unit","m");}
    public static void depthUnit(Context c,String v){p(c).edit().putString("depth_unit",v).apply();}

    public static boolean autoAdvanceRoute(Context c){return p(c).getBoolean("route_auto_advance",true);}
    public static void autoAdvanceRoute(Context c,boolean v){p(c).edit().putBoolean("route_auto_advance",v).apply();}

    public static double arrivalRadiusNm(Context c){
        try{return Double.parseDouble(p(c).getString("arrival_radius_nm","0.05"));}catch(Exception e){return 0.05;}
    }
    public static void arrivalRadiusNm(Context c,double v){p(c).edit().putString("arrival_radius_nm",String.valueOf(v)).apply();}

    public static double offRouteNm(Context c){
        try{return Double.parseDouble(p(c).getString("off_route_nm","0.15"));}catch(Exception e){return 0.15;}
    }
    public static void offRouteNm(Context c,double v){p(c).edit().putString("off_route_nm",String.valueOf(v)).apply();}

    public static boolean shallowAheadWarning(Context c){return p(c).getBoolean("shallow_ahead_warning",true);}
    public static void shallowAheadWarning(Context c,boolean v){p(c).edit().putBoolean("shallow_ahead_warning",v).apply();}

    public static double lookAheadMinutes(Context c){
        try{return Double.parseDouble(p(c).getString("look_ahead_minutes","5"));}catch(Exception e){return 5.0;}
    }
    public static void lookAheadMinutes(Context c,double v){p(c).edit().putString("look_ahead_minutes",String.valueOf(v)).apply();}

    public static double contourIntervalMeters(Context c){
        try{return Double.parseDouble(p(c).getString("contour_interval_m","5"));}catch(Exception e){return 5.0;}
    }
    public static void contourIntervalMeters(Context c,double v){p(c).edit().putString("contour_interval_m",String.valueOf(v)).apply();}

    public static String sonarQualityMode(Context c){return p(c).getString("sonar_quality_mode","GOOD_AND_QUESTIONABLE");}
    public static void sonarQualityMode(Context c,String v){p(c).edit().putString("sonar_quality_mode",v).apply();}

    public static String sonarDensity(Context c){return p(c).getString("sonar_density","HIGH");}
    public static void sonarDensity(Context c,String v){p(c).edit().putString("sonar_density",v).apply();}

    public static boolean sonarRelief(Context c){return p(c).getBoolean("sonar_relief",true);}
    public static void sonarRelief(Context c,boolean v){p(c).edit().putBoolean("sonar_relief",v).apply();}

    public static boolean sonarCoverageMask(Context c){return p(c).getBoolean("sonar_coverage_mask",true);}
    public static void sonarCoverageMask(Context c,boolean v){p(c).edit().putBoolean("sonar_coverage_mask",v).apply();}

    public static boolean sonarContourLabels(Context c){return p(c).getBoolean("sonar_contour_labels",true);}
    public static void sonarContourLabels(Context c,boolean v){p(c).edit().putBoolean("sonar_contour_labels",v).apply();}
}
