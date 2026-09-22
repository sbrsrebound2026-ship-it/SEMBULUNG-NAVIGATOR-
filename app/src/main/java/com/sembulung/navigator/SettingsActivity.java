package com.sembulung.navigator;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;

public class SettingsActivity extends Activity {
    private LinearLayout root;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(3,27,61));
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(24),dp(20),dp(28));
        scroll.addView(root,new ScrollView.LayoutParams(-1,-2));
        build();
        setContentView(scroll);
    }

    private void build(){
        root.removeAllViews();
        root.addView(text("PENGATURAN",24,true));
        TextView sub=text("Navigasi, peta, sonar, AIS dan tampilan perangkat",13,false);
        sub.setPadding(0,dp(5),0,dp(14));root.addView(sub);

        root.addView(section("TAMPILAN"));
        addSwitch("Layar tetap hidup","Mencegah layar mati selama aplikasi aktif",AppSettings.keepScreenOn(this),
                v->AppSettings.keepScreenOn(this,v));
        addSwitch("Pusatkan otomatis ke kapal","Peta mengikuti posisi kapal sampai digeser manual",AppSettings.autoCenter(this),
                v->AppSettings.autoCenter(this,v));

        root.addView(section("PETA & NAVIGASI"));
        addChoice("Satuan jarak",AppSettings.distanceUnit(this),new String[]{"NM","km","mi"},
                v->AppSettings.distanceUnit(this,v));
        addChoice("Satuan kedalaman",AppSettings.depthUnit(this),new String[]{"m","ft"},
                v->AppSettings.depthUnit(this,v));

        root.addView(section("SONAR"));
        addSwitch("Aktifkan Sonar Chart","Tampilkan bathymetry hasil sounding pada Marine Map",AppSettings.sonarEnabled(this),
                v->AppSettings.sonarEnabled(this,v));
        addSwitch("Peringatan kedalaman dangkal","Peringatan saat depth di bawah batas aman",AppSettings.shallowWarning(this),
                v->AppSettings.shallowWarning(this,v));
        addChoice("Batas dangkal",String.format(java.util.Locale.US,"%.1f m",AppSettings.shallowMeters(this)),
                new String[]{"2 m","3 m","5 m","10 m","15 m"},
                v->{try{AppSettings.shallowMeters(this,Double.parseDouble(v.split(" ")[0]));}catch(Exception ignored){}});

        root.addView(section("AIS"));
        addSwitch("Aktifkan AIS","Tampilkan target AIS tersimpan/fresh pada peta",AppSettings.aisEnabled(this),
                v->AppSettings.aisEnabled(this,v));

        root.addView(section("DATA"));
        Button maps=button("KELOLA PETA OFFLINE");
        maps.setOnClickListener(v->startActivity(new android.content.Intent(this,OfflineMapActivity.class)));
        root.addView(maps,lp());
        Button nmea=button("KONEKSI SONAR / NMEA");
        nmea.setOnClickListener(v->startActivity(new android.content.Intent(this,NmeaActivity.class)));
        root.addView(nmea,lp());
        Button ais=button("KONEKSI / TARGET AIS");
        ais.setOnClickListener(v->startActivity(new android.content.Intent(this,AisActivity.class)));
        root.addView(ais,lp());

        Button back=button("KEMBALI");
        back.setOnClickListener(v->finish());
        root.addView(back,top(18));
    }

    private void addSwitch(String title,String sub,boolean checked,ToggleHandler h){
        LinearLayout row=card();
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);
        TextView a=text(title,15,true);a.setGravity(Gravity.LEFT);labels.addView(a);
        TextView b=text(sub,11,false);b.setGravity(Gravity.LEFT);b.setTextColor(0xFFB8C9DB);labels.addView(b);
        Switch sw=new Switch(this);sw.setChecked(checked);sw.setOnCheckedChangeListener((x,v)->h.set(v));
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        row.addView(sw,new LinearLayout.LayoutParams(-2,-2));
        root.addView(row,lp());
    }

    private void addChoice(String title,String current,String[] values,ChoiceHandler h){
        LinearLayout row=card();
        TextView t=text(title+"\n"+current,14,true);t.setGravity(Gravity.LEFT);
        row.addView(t,new LinearLayout.LayoutParams(0,-2,1));
        Button b=button("UBAH");
        b.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(title).setItems(values,(d,w)->{h.set(values[w]);build();}).show());
        row.addView(b,new LinearLayout.LayoutParams(-2,-2));
        root.addView(row,lp());
    }

    private TextView section(String s){TextView t=text(s,15,true);t.setTextColor(Color.rgb(37,217,248));t.setGravity(Gravity.LEFT);t.setPadding(0,dp(18),0,dp(2));return t;}
    private LinearLayout card(){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(12),dp(11),dp(12),dp(11));r.setBackgroundColor(Color.rgb(7,57,94));return r;}
    private TextView text(String s,int sp,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(sp);t.setGravity(Gravity.CENTER);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private LinearLayout.LayoutParams lp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(8),0,0);return p;}
    private LinearLayout.LayoutParams top(int v){LinearLayout.LayoutParams p=lp();p.topMargin=dp(v);return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private interface ToggleHandler{void set(boolean v);}
    private interface ChoiceHandler{void set(String v);}
}
