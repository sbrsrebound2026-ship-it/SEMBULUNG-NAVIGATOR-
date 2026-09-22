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

import java.util.Locale;

public class NmeaActivity extends Activity {
    private EditText portInput,manualInput;
    private TextView connection,position,depth,heading,speed,stats,lastSentence;
    private final Handler handler=new Handler(Looper.getMainLooper());

    private final Runnable refresh=new Runnable(){
        @Override public void run(){render();handler.postDelayed(this,1000L);}
    };

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.rgb(3,27,61));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(24),dp(20),dp(30));scroll.addView(root,new ScrollView.LayoutParams(-1,-2));

        root.addView(label("SONAR / NMEA • BACKGROUND SERVICE",23,true));
        TextView sub=label("Satu listener UDP bersama untuk GPS/NMEA, depth/sonar dan AIS",13,false);
        sub.setPadding(0,dp(6),0,dp(16));root.addView(sub);

        connection=panel("STATUS\nMarine Data Service belum aktif");
        position=panel("POSISI\n---");depth=panel("DEPTH / SONAR\n---");
        heading=panel("HEADING\n---");speed=panel("SPEED\n---");
        stats=panel("DATA\n---");lastSentence=panel("KALIMAT TERAKHIR\n---");
        root.addView(connection,lp());root.addView(position,lp());root.addView(depth,lp());
        root.addView(heading,lp());root.addView(speed,lp());root.addView(stats,lp());root.addView(lastSentence,lp());

        root.addView(label("PORT UDP MARINE DATA",13,true),top(16));
        portInput=new EditText(this);portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        portInput.setText(String.valueOf(MarineDataService.DEFAULT_PORT));portInput.setTextColor(Color.WHITE);
        portInput.setGravity(Gravity.CENTER);root.addView(portInput,lp());

        LinearLayout controls=new LinearLayout(this);
        Button start=button("MULAI SERVICE");start.setOnClickListener(v->startServiceListener());controls.addView(start,half());
        Button stop=button("STOP");stop.setOnClickListener(v->MarineDataService.stop(this));controls.addView(stop,half());
        root.addView(controls,lp());

        root.addView(label("UJI NMEA MANUAL",13,true),top(18));
        manualInput=new EditText(this);manualInput.setTextColor(Color.WHITE);manualInput.setHintTextColor(0xFF94A3B8);
        manualInput.setHint("$GPRMC,... atau $SDDPT,...");manualInput.setMinLines(2);root.addView(manualInput,lp());

        Button parse=button("PROSES NMEA MANUAL");parse.setOnClickListener(v->processManual());root.addView(parse,lp());
        Button demo=button("ISI CONTOH DATA");demo.setOnClickListener(v->manualInput.setText(
                "$GPRMC,123519,A,0830.000,S,11420.000,E,7.5,84.4,230394,,\n$SDDPT,18.7,0.0,\n$HCHDT,92.5,T"));
        root.addView(demo,lp());

        Button map=button("BUKA MARINE MAP");map.setOnClickListener(v->startActivity(new Intent(this,MarineMapActivity.class)));root.addView(map,top(14));
        Button clear=button("RESET DATA NMEA");clear.setOnClickListener(v->{NmeaDataStore.clear(this);render();});root.addView(clear,lp());
        Button back=button("KEMBALI");back.setOnClickListener(v->finish());root.addView(back,top(18));
        setContentView(scroll);
        requestNotificationPermission();
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
        if(raw.isEmpty()){Toast.makeText(this,"Masukkan kalimat NMEA",Toast.LENGTH_SHORT).show();return;}
        NmeaDataStore.Snapshot old=NmeaDataStore.read(this);
        NmeaSentenceParser.State s=new NmeaSentenceParser.State();
        if(old!=null){
            s.lat=old.lat;s.lon=old.lon;s.heading=old.heading;s.depth=old.depth;s.speed=old.speed;
            s.positionTime=old.positionTime;s.headingTime=old.headingTime;s.depthTime=old.depthTime;
        }
        int ok=0;
        for(String line:raw.split("[\\r\\n]+")){
            long now=System.currentTimeMillis();
            if(NmeaSentenceParser.apply(line.trim(),s,now)){ok++;}
        }
        if(ok>0)NmeaDataStore.write(this,s.lat,s.lon,s.heading,s.depth,s.speed,s.positionTime,s.headingTime,s.depthTime,"manual");
        Toast.makeText(this,ok+" kalimat NMEA diproses",Toast.LENGTH_SHORT).show();
        render();
    }

    private void render(){
        MarineServiceState.Snapshot ms=MarineServiceState.read(this);
        connection.setText("STATUS\n"+ms.status+" • UDP "+ms.port+(ms.running?" • AKTIF":""));
        stats.setText(String.format(Locale.US,"DATA\n%d diterima • %d valid • %d ditolak • %d AIS",ms.received,ms.valid,ms.rejected,ms.aisDecoded));
        lastSentence.setText("KALIMAT TERAKHIR\n"+(ms.lastLine==null||ms.lastLine.isEmpty()?"---":ms.lastLine));

        NmeaDataStore.Snapshot n=NmeaDataStore.read(this);
        position.setText(n.lat!=null&&n.lon!=null?String.format(Locale.US,"POSISI\n%.6f, %.6f",n.lat,n.lon):"POSISI\n---");
        depth.setText(n.depth!=null?String.format(Locale.US,"DEPTH / SONAR\n%.2f m",n.depth):"DEPTH / SONAR\n---");
        heading.setText(n.heading!=null?String.format(Locale.US,"HEADING\n%.1f°",n.heading):"HEADING\n---");
        speed.setText(n.speed!=null?String.format(Locale.US,"SPEED\n%.1f kn",n.speed):"SPEED\n---");
    }

    private void requestNotificationPermission(){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1701);
    }

    @Override protected void onResume(){super.onResume();handler.removeCallbacks(refresh);handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}

    private TextView panel(String s){TextView t=label(s,16,true);t.setPadding(dp(12),dp(13),dp(12),dp(13));t.setBackgroundColor(Color.rgb(7,57,94));return t;}
    private TextView label(String s,int sp,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(sp);t.setGravity(Gravity.CENTER);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private LinearLayout.LayoutParams lp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(8),0,0);return p;}
    private LinearLayout.LayoutParams top(int m){LinearLayout.LayoutParams p=lp();p.topMargin=dp(m);return p;}
    private LinearLayout.LayoutParams half(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1f);p.setMargins(dp(3),0,dp(3),0);return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
