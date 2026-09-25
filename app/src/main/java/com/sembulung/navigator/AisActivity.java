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
        ScrollView scroll=new ScrollView(this); scroll.setBackgroundColor(Color.rgb(2,18,33));
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12),dp(12),dp(12),dp(20)); scroll.addView(root,new ScrollView.LayoutParams(-1,-2));

        TextView title=text("AIS TARGETS",20,true); title.setGravity(Gravity.LEFT); root.addView(title);
        TextView sub=text("Target kapal • CPA / TCPA • Marine Data Service",10,false); sub.setGravity(Gravity.LEFT); sub.setTextColor(0xff9fb4c5); root.addView(sub,top(2));

        connection=panel("SERVICE\nMenunggu koneksi"); root.addView(connection,top(12));
        ownVessel=panel("KAPAL SENDIRI\nMenunggu NMEA"); root.addView(ownVessel,lp());
        statistics=panel("TARGET\n---"); root.addView(statistics,lp());

        LinearLayout service=new LinearLayout(this);
        portInput=new EditText(this); portInput.setInputType(InputType.TYPE_CLASS_NUMBER); portInput.setText(String.valueOf(MarineDataService.DEFAULT_PORT)); portInput.setTextColor(Color.WHITE); portInput.setGravity(Gravity.CENTER); portInput.setBackground(panelBg());
        service.addView(portInput,new LinearLayout.LayoutParams(0,dp(48),1f));
        Button start=button("START"); start.setOnClickListener(v->startServiceListener()); service.addView(start,half());
        Button stop=button("STOP"); stop.setOnClickListener(v->MarineDataService.stop(this)); service.addView(stop,half());
        root.addView(service,top(8));

        TextView titleTargets=text("TARGET AIS AKTIF",12,true); titleTargets.setGravity(Gravity.LEFT); root.addView(titleTargets,top(14));
        targetList=new LinearLayout(this); targetList.setOrientation(LinearLayout.VERTICAL); root.addView(targetList,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout tools=new LinearLayout(this);
        Button manual=button("INPUT NMEA AIS"); manual.setOnClickListener(v->showManualInput()); tools.addView(manual,half());
        Button clear=button("CLEAR"); clear.setOnClickListener(v->{AisTargetStore.clear(this);render();}); tools.addView(clear,half());
        root.addView(tools,top(10));

        Button map=button("BUKA CHART"); map.setOnClickListener(v->startActivity(new Intent(this,MarineMapActivity.class))); root.addView(map,lp());
        Button back=button("KEMBALI"); back.setOnClickListener(v->finish()); root.addView(back,top(12));

        setContentView(scroll); requestNotificationPermission();
    }

    private void showManualInput(){
        final EditText input=new EditText(this);
        input.setTextColor(Color.WHITE); input.setHintTextColor(0xff94A3B8); input.setHint("!AIVDM / !AIVDO");
        input.setMinLines(3);
        new android.app.AlertDialog.Builder(this).setTitle("INPUT AIS NMEA").setView(input)
                .setNegativeButton("BATAL",null).setPositiveButton("PROSES",(d,w)->{
                    manualInput=input; processManual();
                }).show();
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

    private TextView panel(String s){TextView t=text(s,12,true);t.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);t.setPadding(dp(12),dp(9),dp(12),dp(9));t.setBackgroundColor(Color.rgb(7,43,70));return t;}
    private TextView text(String s,int sp,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(sp);t.setGravity(Gravity.CENTER);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(11);b.setTextColor(Color.WHITE);b.setMinHeight(0);b.setMinWidth(0);return b;}
    private android.graphics.drawable.GradientDrawable panelBg(){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(0xd10a2136);d.setCornerRadius(dp(12));d.setStroke(dp(1),0x4457b9dd);return d;}
    private LinearLayout.LayoutParams lp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(8),0,0);return p;}
    private LinearLayout.LayoutParams top(int m){LinearLayout.LayoutParams p=lp();p.topMargin=dp(m);return p;}
    private LinearLayout.LayoutParams half(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1f);p.setMargins(dp(3),0,dp(3),0);return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private static final class Row{final AisTarget t;final AisCollisionEngine.Assessment a;Row(AisTarget t,AisCollisionEngine.Assessment a){this.t=t;this.a=a;}}
}
