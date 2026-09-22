package com.sembulung.navigator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.*;

import java.util.Locale;

public class SearchCoordinateActivity extends Activity {
    private EditText query;
    private TextView result;
    private CoordinateParser.Point point;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);

        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(3,27,61));
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(24),dp(20),dp(28));
        scroll.addView(root,new ScrollView.LayoutParams(-1,-2));

        root.addView(text("CARI KOORDINAT",24,true));
        TextView sub=text("Masukkan format DD atau DMS. Contoh: -8.123456, 114.123456",13,false);
        sub.setPadding(0,dp(5),0,dp(14));
        root.addView(sub);

        query=new EditText(this);
        query.setTextColor(Color.WHITE);
        query.setHintTextColor(0xFF94A3B8);
        query.setHint("-8.123456, 114.123456  atau  8°07'24.4\"S 114°07'24.4\"E");
        query.setSingleLine(false);
        query.setMinLines(2);
        query.setInputType(InputType.TYPE_CLASS_TEXT);
        root.addView(query,lp());

        Button paste=button("TEMPEL DARI CLIPBOARD");
        paste.setOnClickListener(v->paste());
        root.addView(paste,lp());

        Button search=button("CARI KOORDINAT");
        search.setOnClickListener(v->search());
        root.addView(search,lp());

        result=panel("HASIL\nBelum ada koordinat dipilih");
        root.addView(result,top(16));

        Button map=button("BUKA DI PETA");
        map.setOnClickListener(v->openMap());
        root.addView(map,lp());

        Button save=button("SIMPAN SEBAGAI WAYPOINT");
        save.setOnClickListener(v->saveWaypoint());
        root.addView(save,lp());

        Button back=button("KEMBALI");
        back.setOnClickListener(v->finish());
        root.addView(back,top(18));

        setContentView(scroll);
    }

    private void paste(){
        try{
            android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(cm!=null&&cm.hasPrimaryClip()&&cm.getPrimaryClip().getItemCount()>0){
                query.setText(cm.getPrimaryClip().getItemAt(0).coerceToText(this));
            }
        }catch(Exception ignored){}
    }

    private void search(){
        point=CoordinateParser.parsePair(query.getText().toString());
        if(point==null){
            result.setText("HASIL\nKoordinat tidak valid. Gunakan DD atau DMS.");
            return;
        }
        result.setText(String.format(Locale.US,
                "HASIL\nLatitude %.6f\nLongitude %.6f\n\nTekan BUKA DI PETA untuk memusatkan chart.",
                point.lat,point.lon));
    }

    private void openMap(){
        if(point==null){search();if(point==null)return;}
        Intent i=new Intent(this,MarineMapActivity.class);
        i.putExtra("focus_lat",point.lat);
        i.putExtra("focus_lon",point.lon);
        i.putExtra("focus_label","Lokasi dicari");
        startActivity(i);
    }

    private void saveWaypoint(){
        if(point==null){search();if(point==null)return;}
        EditText name=new EditText(this);
        name.setHint("Nama waypoint");
        name.setText("Koordinat "+String.format(Locale.US,"%.4f, %.4f",point.lat,point.lon));
        new AlertDialog.Builder(this)
                .setTitle("Simpan Waypoint")
                .setView(name)
                .setNegativeButton("Batal",null)
                .setPositiveButton("Simpan",(d,w)->{
                    String n=name.getText().toString().trim();
                    if(n.isEmpty())n="Waypoint";
                    WaypointStore.add(this,new WaypointStore.Waypoint(n,point.lat,point.lon));
                    Toast.makeText(this,"Waypoint disimpan",Toast.LENGTH_SHORT).show();
                }).show();
    }

    private TextView panel(String s){
        TextView t=text(s,16,true);
        t.setGravity(Gravity.LEFT);
        t.setPadding(dp(14),dp(14),dp(14),dp(14));
        t.setBackgroundColor(Color.rgb(7,57,94));
        return t;
    }
    private TextView text(String s,int sp,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(sp);t.setGravity(Gravity.CENTER);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private LinearLayout.LayoutParams lp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(8),0,0);return p;}
    private LinearLayout.LayoutParams top(int v){LinearLayout.LayoutParams p=lp();p.topMargin=dp(v);return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
