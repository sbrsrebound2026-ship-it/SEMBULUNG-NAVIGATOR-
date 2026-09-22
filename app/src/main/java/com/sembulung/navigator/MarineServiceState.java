package com.sembulung.navigator;

import android.content.Context;
import android.content.SharedPreferences;

public final class MarineServiceState {
    private static final String PREFS="sembulung_marine_service";
    private MarineServiceState(){}

    public static final class Snapshot {
        public boolean running;
        public int port;
        public long received,valid,rejected,aisDecoded,lastUpdate;
        public String status,lastLine,source;
    }

    private static SharedPreferences p(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}

    public static void started(Context c,int port){
        p(c).edit().putBoolean("running",true).putInt("port",port)
                .putString("status","Marine Data Service aktif").putLong("last_update",System.currentTimeMillis()).apply();
    }

    public static void stopped(Context c,String status){
        p(c).edit().putBoolean("running",false).putString("status",status==null?"Berhenti":status)
                .putLong("last_update",System.currentTimeMillis()).apply();
    }

    public static void update(Context c,long received,long valid,long rejected,long aisDecoded,String line,String source){
        p(c).edit()
                .putLong("received",received).putLong("valid",valid).putLong("rejected",rejected)
                .putLong("ais_decoded",aisDecoded).putString("last_line",line==null?"":line)
                .putString("source",source==null?"UDP":source).putLong("last_update",System.currentTimeMillis()).apply();
    }

    public static Snapshot read(Context c){
        SharedPreferences p=p(c);
        Snapshot s=new Snapshot();
        s.running=p.getBoolean("running",false);s.port=p.getInt("port",10110);
        s.received=p.getLong("received",0);s.valid=p.getLong("valid",0);s.rejected=p.getLong("rejected",0);
        s.aisDecoded=p.getLong("ais_decoded",0);s.lastUpdate=p.getLong("last_update",0);
        s.status=p.getString("status","Marine Data Service belum aktif");
        s.lastLine=p.getString("last_line","");s.source=p.getString("source","UDP");
        return s;
    }

    public static void resetCounters(Context c){
        p(c).edit().putLong("received",0).putLong("valid",0).putLong("rejected",0).putLong("ais_decoded",0).apply();
    }
}
