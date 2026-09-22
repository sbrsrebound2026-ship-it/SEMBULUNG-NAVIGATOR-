package com.sembulung.navigator.ais;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class AisTargetStore {
    private static final String PREFS="sembulung_ais_targets";
    private static final String KEY="targets";
    private AisTargetStore(){}

    public static synchronized void upsert(Context c,AisTarget t){
        List<AisTarget> all=load(c,120000L);
        boolean replaced=false;
        for(int i=0;i<all.size();i++){
            if(all.get(i).mmsi==t.mmsi){all.set(i,t);replaced=true;break;}
        }
        if(!replaced)all.add(t);
        if(all.size()>100)all=new ArrayList<>(all.subList(all.size()-100,all.size()));
        save(c,all);
    }

    public static synchronized List<AisTarget> load(Context c,long maxAgeMs){
        ArrayList<AisTarget> out=new ArrayList<>();
        String raw=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY,"[]");
        long cutoff=System.currentTimeMillis()-maxAgeMs;
        try{
            JSONArray a=new JSONArray(raw);
            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i);
                long at=o.optLong("at",0);
                if(at<cutoff)continue;
                double sog=o.isNull("sog")?Double.NaN:o.optDouble("sog",Double.NaN);
                double cog=o.isNull("cog")?Double.NaN:o.optDouble("cog",Double.NaN);
                AisTarget t=new AisTarget(
                        o.optInt("type"),o.optLong("mmsi"),
                        o.optDouble("lat",Double.NaN),o.optDouble("lon",Double.NaN),
                        sog,cog,
                        o.has("hdg")&&!o.isNull("hdg")?o.optInt("hdg"):null,
                        o.has("nav")&&!o.isNull("nav")?o.optInt("nav"):null,at);
                if(t.hasValidPosition())out.add(t);
            }
        }catch(Exception ignored){}
        return out;
    }

    public static synchronized void clear(Context c){
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().remove(KEY).apply();
    }

    private static void save(Context c,List<AisTarget> all){
        JSONArray a=new JSONArray();
        try{
            for(AisTarget t:all){
                JSONObject o=new JSONObject();
                o.put("type",t.messageType);
                o.put("mmsi",t.mmsi);
                o.put("lat",t.latitude);
                o.put("lon",t.longitude);
                if(Double.isFinite(t.speedKnots))o.put("sog",t.speedKnots);else o.put("sog",JSONObject.NULL);
                if(Double.isFinite(t.courseDeg))o.put("cog",t.courseDeg);else o.put("cog",JSONObject.NULL);
                o.put("hdg",t.headingDeg==null?JSONObject.NULL:t.headingDeg);
                o.put("nav",t.navigationStatus==null?JSONObject.NULL:t.navigationStatus);
                o.put("at",t.receivedAtMillis);
                a.put(o);
            }
        }catch(Exception ignored){}
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY,a.toString()).apply();
    }
}
