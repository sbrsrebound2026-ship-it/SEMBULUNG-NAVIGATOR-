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
}
