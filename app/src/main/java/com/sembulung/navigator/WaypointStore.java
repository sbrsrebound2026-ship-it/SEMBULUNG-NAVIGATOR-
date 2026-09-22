package com.sembulung.navigator;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class WaypointStore {
    private static final String PREFS="sembulung_waypoints";
    private static final String KEY="waypoints_json";
    private static final String ACTIVE="active_index";
    private WaypointStore(){}

    public static final class Waypoint {
        public final String name;
        public final double lat,lon;
        public Waypoint(String name,double lat,double lon){this.name=name;this.lat=lat;this.lon=lon;}
    }

    public static List<Waypoint> load(Context c){
        ArrayList<Waypoint> out=new ArrayList<>();
        String raw=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY,"[]");
        try{
            JSONArray a=new JSONArray(raw);
            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i);
                out.add(new Waypoint(o.optString("name","Waypoint "+(i+1)),o.getDouble("lat"),o.getDouble("lon")));
            }
        }catch(Exception ignored){}
        return out;
    }

    public static void add(Context c,Waypoint wp){
        List<Waypoint> all=load(c);all.add(wp);save(c,all);
    }

    public static void save(Context c,List<Waypoint> all){
        JSONArray a=new JSONArray();
        try{
            for(Waypoint wp:all){
                JSONObject o=new JSONObject();
                o.put("name",wp.name);o.put("lat",wp.lat);o.put("lon",wp.lon);a.put(o);
            }
        }catch(Exception ignored){}
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY,a.toString()).apply();
    }

    public static int activeIndex(Context c){
        int i=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getInt(ACTIVE,-1);
        int size=load(c).size();
        return i>=0&&i<size?i:-1;
    }

    public static void setActiveIndex(Context c,int index){
        int size=load(c).size();
        int safe=index>=0&&index<size?index:-1;
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putInt(ACTIVE,safe).apply();
    }

    public static void clearActive(Context c){
        setActiveIndex(c,-1);
    }
}
