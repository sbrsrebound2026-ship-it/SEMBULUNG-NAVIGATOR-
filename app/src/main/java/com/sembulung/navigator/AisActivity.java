package com.sembulung.navigator;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.widget.*;

import com.sembulung.navigator.ais.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class AisActivity extends Activity {
    private static final long OWN_FRESH=15000L;
    private EditText portInput,manualInput;
    private TextView connection,ownVessel,statistics;
    private LinearLayout targetList;
    private final Handler handler=new Handler(Looper.getMainLooper());

    private final Runnable refresh=new Runnable(){
        @Override public void run(){render();handler.postDelayed(this,1000L);}
    };

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.rgb(3,27,61));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(24),dp(20),dp(30));scroll.addView(root,new ScrollView.LayoutParams(-1,-2));

        root.addView(text("AIS • CPA / TCPA",24,true));
        TextView sub=text("Target AIS realtime dari Marine Data Service",13,false);sub.setPadding(0,dp(5),0,dp(14));root.addView(sub);

        connection=panel("STATUS\nMarine Data Service belum aktif");
        ownVessel=panel("KAPAL SENDIRI\nMenunggu NMEA");
        statistics=panel("DATA AIS\n---");
        root.addView(connection,lp());root.addView(ownVessel,lp());root.addView(statistics,lp());

        root.addView(text("PORT UDP MARINE DATA",13,true),top(16));
        portInput=new EditText(this);portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        portInput.setText(String.valueOf(MarineDataService.DEFAULT_PORT));portInput.setTextColor(Color.WHITE);portInput.setGravity(Gravity.CENTER);
        root.addView(portInput,lp());

        LinearLayout controls=new LinearLayout(this);
        Button start=button("MULAI SERVICE");start.setOnClickListener(v->startServiceListener());controls.addView(start,half());
        Button stop=button("STOP");stop.setOnClickListener(v->MarineDataService.stop(this));controls.addView(stop,half());
        root.addView(controls,lp());

        root.addView(text("UJI !AIVDM / !AIVDO MANUAL",13,true),top(18));
        manualInput=new EditText(this);manualInput.setTextColor(Color.WHITE);manualInput.setHintTextColor(0xFF94A3B8);
        manualInput.setHint("Tempel kalimat AIS NMEA 0183");manualInput.setMinLines(2);root.addView(manualInput,lp());
        Button parse=button("PROSES AIS MANUAL");parse.setOnClickListener(v->processManual());root.addView(parse,lp());

        TextView title=text("TARGET AIS AKTIF",16,true);title.setPadding(0,dp(22),0,dp(5));root.addView(title);
        targetList=new LinearLayout(this);targetList.setOrientation(LinearLayout.VERTICAL);root.addView(targetList,new LinearLayout.LayoutParams(-1,-2));

        Button clear=button("HAPUS TARGET AIS");clear.setOnClickListener(v->{AisTargetStore.clear(this);render();});root.addView(clear,top(16));
        Button map=button("BUKA MARINE MAP");map.setOnClickListener(v->startActivity(new Intent(this,MarineMapActivity.class)));root.addView(map,lp());
        Button back=button("KEMBALI");back.setOnClickListener(v->finish());root.addView(back,lp());

        setContentView(scroll);requestNotificationPermission();
    }

    private void startServiceListener(){
        try{
            int port=Integer.parseInt(portInput.getText().toString().trim());
            if(port<1||port>65535)throw new IllegalArgumentException();
            MarineDataService.start(this,port);
            Toast.makeText(this,"Marine Data Service dimulai",Toast.LENGTH_SHORT).show();
        }catch(Exception e){Toast.makeText(this,"Port tidak valid",Toast.LENGTH_LONG).show();}
    }

    private void processManual(){
        String raw=manualInput.getText().toString().trim();
        if(raw.isEmpty()){Toast.makeText(this,"Masukkan !AIVDM atau !AIVDO",Toast.LENGTH_SHORT).show();return;}
        int ok=0;
        for(String line:raw.split("[\\r\\n]+")){
            AisTarget t=AisParser.parse(line.trim());
            if(t!=null&&t.hasValidPosition()){AisTargetStore.upsert(this,t);ok++;}
        }
        Toast.makeText(this,ok+" target AIS diproses",Toast.LENGTH_SHORT).show();render();
    }

    private void render(){
        MarineServiceState.Snapshot ms=MarineServiceState.read(this);
        connection.setText("STATUS\n"+ms.status+" • UDP "+ms.port+(ms.running?" • AKTIF":""));

        NmeaDataStore.Snapshot own=NmeaDataStore.read(this);
        boolean ownReady=own!=null&&own.positionFresh(OWN_FRESH);
        if(ownReady){
            ownVessel.setText(String.format(Locale.US,"KAPAL SENDIRI\n%.6f, %.6f • SOG %s • COG %s",
                    own.lat,own.lon,own.speed==null?"---":String.format(Locale.US,"%.1f kn",own.speed),
                    own.heading==null?"---":String.format(Locale.US,"%.0f°",own.heading)));
        }else ownVessel.setText("KAPAL SENDIRI\nMenunggu posisi NMEA fresh");

        List<AisTarget> targets=AisTargetStore.load(this,120000L);
        statistics.setText(String.format(Locale.US,"DATA AIS\n%d target fresh • %d decoded service",targets.size(),ms.aisDecoded));
        targetList.removeAllViews();
        if(targets.isEmpty()){targetList.addView(text("Belum ada target AIS aktif.",14,false));return;}

        double sog=ownReady&&own.speed!=null?own.speed:Double.NaN;
        double cog=ownReady&&own.heading!=null?own.heading:Double.NaN;
        List<Row> rows=new ArrayList<>();
        for(AisTarget t:targets){
            AisCollisionEngine.Assessment a=ownReady
                    ?AisCollisionEngine.assess(own.lat,own.lon,sog,cog,t)
                    :new AisCollisionEngine.Assessment(Double.NaN,Double.NaN,Double.NaN,Double.NaN,AisCollisionEngine.Risk.UNKNOWN);
            rows.add(new Row(t,a));
        }
        rows.sort(Comparator.comparingInt((Row r)->rank(r.a.risk)).thenComparingDouble(r->Double.isNaN(r.a.rangeNm)?99999:r.a.rangeNm));
        for(Row r:rows)addCard(r.t,r.a);
    }

    private void addCard(AisTarget t,AisCollisionEngine.Assessment a){
        TextView card=text(String.format(Locale.US,
                "MMSI %09d • %s\nRange %s • Bearing %s\nSOG %s • COG %s\nCPA %s • TCPA %s",
                t.mmsi,a.risk.name(),fmt(a.rangeNm,"%.2f NM"),fmt(a.bearingDeg,"%.0f°"),
                fmt(t.speedKnots,"%.1f kn"),fmt(t.courseDeg,"%.0f°"),
                fmt(a.cpaNm,"%.2f NM"),Double.isInfinite(a.tcpaMinutes)?"∞":fmt(a.tcpaMinutes,"%.0f min")),14,true);
        card.setGravity(Gravity.LEFT);card.setPadding(dp(12),dp(10),dp(12),dp(10));card.setBackgroundColor(riskColor(a.risk));
        targetList.addView(card,lp());
    }

    private String fmt(double v,String f){return Double.isNaN(v)?"---":String.format(Locale.US,f,v);}
    private int rank(AisCollisionEngine.Risk r){switch(r){case DANGER:return 0;case WARNING:return 1;case MONITOR:return 2;case SAFE:return 3;default:return 4;}}
    private int riskColor(AisCollisionEngine.Risk r){switch(r){case DANGER:return Color.rgb(120,20,25);case WARNING:return Color.rgb(110,72,10);case MONITOR:return Color.rgb(10,70,105);case SAFE:return Color.rgb(7,57,94);default:return Color.rgb(45,55,70);}}

    private void requestNotificationPermission(){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1702);
    }

    @Override protected void onResume(){super.onResume();handler.removeCallbacks(refresh);handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}

    private TextView panel(String s){TextView t=text(s,15,true);t.setPadding(dp(12),dp(12),dp(12),dp(12));t.setBackgroundColor(Color.rgb(7,57,94));return t;}
    private TextView text(String s,int sp,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(sp);t.setGravity(Gravity.CENTER);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private LinearLayout.LayoutParams lp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(8),0,0);return p;}
    private LinearLayout.LayoutParams top(int m){LinearLayout.LayoutParams p=lp();p.topMargin=dp(m);return p;}
    private LinearLayout.LayoutParams half(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1f);p.setMargins(dp(3),0,dp(3),0);return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private static final class Row{final AisTarget t;final AisCollisionEngine.Assessment a;Row(AisTarget t,AisCollisionEngine.Assessment a){this.t=t;this.a=a;}}
}
