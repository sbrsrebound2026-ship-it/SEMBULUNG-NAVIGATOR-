package com.sembulung.navigator;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.view.Gravity;
import android.widget.*;

import com.sembulung.navigator.sonar.*;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class SonarSurveyActivity extends Activity {
    private static final int REQ_EXPORT=1901;
    private static final int REQ_IMPORT=1902;
    private static final int REQ_BACKUP=1903;

    private SonarChartStore store;
    private SonarSessionStore sessions;
    private TextView stats,sessionStatus,replayStatus;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private SonarReplayEngine replay;
    private final Runnable replayTick=new Runnable(){
        @Override public void run(){
            if(replay==null||!replay.hasNext()){
                if(replayStatus!=null)replayStatus.setText("REPLAY\nSelesai");
                return;
            }
            DepthSample s=replay.next();
            replayStatus.setText(String.format(Locale.US,
                    "REPLAY %d/%d • %.0f%%\n%.6f, %.6f • %.2f m • %s",
                    replay.position(),replay.size(),replay.progress()*100.0,s.lat,s.lon,s.depthMeters,s.quality));
            handler.postDelayed(this,250L);
        }
    };

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        store=new SonarChartStore(this);
        sessions=new SonarSessionStore(this);

        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.rgb(3,27,61));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(24),dp(20),dp(30));scroll.addView(root,new ScrollView.LayoutParams(-1,-2));

        root.addView(text("SONAR SURVEY CENTER",24,true));
        TextView sub=text("Sounding, session, contour, import/export, replay dan backup",13,false);
        sub.setPadding(0,dp(5),0,dp(14));root.addView(sub);

        stats=panel("STATISTIK\nMemuat...");
        sessionStatus=panel("SESSION\n---");
        replayStatus=panel("REPLAY\nBelum aktif");
        root.addView(stats,lp());root.addView(sessionStatus,lp());root.addView(replayStatus,lp());

        Button start=button("MULAI SESSION SURVEY");
        start.setOnClickListener(v->startSession());
        root.addView(start,top(14));

        Button stop=button("STOP SESSION");
        stop.setOnClickListener(v->{sessions.stop();refresh();Toast.makeText(this,"Session dihentikan",Toast.LENGTH_SHORT).show();});
        root.addView(stop,lp());

        Button rebuild=button("BANGUN ULANG SONAR CHART");
        rebuild.setOnClickListener(v->{
            startActivity(new Intent(this,MarineMapActivity.class));
            Toast.makeText(this,"Marine Map akan rebuild menggunakan interval/filter aktif",Toast.LENGTH_LONG).show();
        });
        root.addView(rebuild,top(14));

        Button export=button("EKSPOR SOUNDING CSV");
        export.setOnClickListener(v->createDocument("text/csv","SEMBULUNG_SOUNDINGS_"+stamp()+".csv",REQ_EXPORT));
        root.addView(export,lp());

        Button importCsv=button("IMPOR SOUNDING CSV");
        importCsv.setOnClickListener(v->openCsv());
        root.addView(importCsv,lp());

        Button replayBtn=button("REPLAY SESSION TERBARU");
        replayBtn.setOnClickListener(v->replayLatest());
        root.addView(replayBtn,lp());

        Button backup=button("BACKUP DATA ZIP");
        backup.setOnClickListener(v->createDocument("application/zip","SEMBULUNG_BACKUP_"+stamp()+".zip",REQ_BACKUP));
        root.addView(backup,top(14));

        Button diag=button("BUKA DIAGNOSTIC");
        diag.setOnClickListener(v->startActivity(new Intent(this,DiagnosticActivity.class)));
        root.addView(diag,lp());

        Button map=button("BUKA MARINE MAP");
        map.setOnClickListener(v->startActivity(new Intent(this,MarineMapActivity.class)));
        root.addView(map,lp());

        Button back=button("KEMBALI");back.setOnClickListener(v->finish());root.addView(back,top(18));
        setContentView(scroll);
        refresh();
    }

    private void refresh(){
        List<DepthSample> all=store.load(0);
        int good=0,q=0,rejected=0;
        for(DepthSample s:all){
            if(s.quality==DepthSample.Quality.GOOD)good++;
            else if(s.quality==DepthSample.Quality.QUESTIONABLE)q++;
            else rejected++;
        }
        List<DepthSample> filtered=SonarSampleFilter.apply(all,AppSettings.sonarQualityMode(this));
        String density=AppSettings.sonarDensity(this);
        double cell=BathymetryStyle.cellMeters(density,14);
        double interval=BathymetryStyle.contourInterval(AppSettings.contourIntervalMeters(this),density,14);
        SonarChartEngine.Chart chart=SonarChartEngine.build(filtered,cell,interval);
        stats.setText(String.format(Locale.US,
                "STATISTIK\nTotal %d • GOOD %d • QUESTIONABLE %d • REJECTED %d\nDipakai %d • Depth %s–%s m • Mean %s m\nCoverage %.2f ha • Kontur %.1f m • Density %s • Confidence %.0f%% • %s",
                all.size(),good,q,rejected,filtered.size(),
                fmt(chart.stats.minDepth),fmt(chart.stats.maxDepth),fmt(chart.stats.meanDepth),
                chart.stats.coverageSquareMeters/10000.0,interval,density,chart.stats.meanConfidence*100.0,
                "GOOD_ONLY".equals(AppSettings.sonarQualityMode(this))?"Hanya GOOD":"GOOD + QUESTIONABLE"));

        File active=sessions.activeFile();
        List<File> list=sessions.list();
        sessionStatus.setText("SESSION\n"+(active==null?"Tidak aktif":"AKTIF • "+active.getName())+
                "\nTersimpan: "+list.size()+" session");
    }

    private void startSession(){
        final EditText name=new EditText(this);name.setHint("Nama session");name.setText("survey");
        new AlertDialog.Builder(this).setTitle("Mulai Session Survey").setView(name)
                .setNegativeButton("Batal",null)
                .setPositiveButton("Mulai",(d,w)->{
                    try{
                        File f=sessions.start(name.getText().toString());
                        Toast.makeText(this,"Session aktif: "+f.getName(),Toast.LENGTH_LONG).show();
                        refresh();
                    }catch(Exception e){Toast.makeText(this,"Gagal mulai session: "+e.getMessage(),Toast.LENGTH_LONG).show();}
                }).show();
    }

    private void replayLatest(){
        List<File> list=sessions.list();
        if(list.isEmpty()){Toast.makeText(this,"Belum ada session survey",Toast.LENGTH_SHORT).show();return;}
        List<DepthSample> samples=sessions.load(list.get(0),3000);
        if(samples.isEmpty()){Toast.makeText(this,"Session kosong",Toast.LENGTH_SHORT).show();return;}
        replay=new SonarReplayEngine(samples);
        handler.removeCallbacks(replayTick);handler.post(replayTick);
    }

    private void openCsv(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/*");
        startActivityForResult(i,REQ_IMPORT);
    }

    private void createDocument(String type,String name,int req){
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(type);i.putExtra(Intent.EXTRA_TITLE,name);
        startActivityForResult(i,req);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        try{
            if(requestCode==REQ_EXPORT){
                try(OutputStream out=getContentResolver().openOutputStream(uri)){store.exportStream(out);}
                Toast.makeText(this,"CSV berhasil diekspor",Toast.LENGTH_LONG).show();
            }else if(requestCode==REQ_BACKUP){
                try(OutputStream out=getContentResolver().openOutputStream(uri)){SurveyBackupManager.exportZip(this,out);}
                Toast.makeText(this,"Backup ZIP berhasil dibuat",Toast.LENGTH_LONG).show();
            }else if(requestCode==REQ_IMPORT){
                confirmImport(uri);
            }
        }catch(Exception e){Toast.makeText(this,"Operasi gagal: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    private void confirmImport(Uri uri){
        new AlertDialog.Builder(this).setTitle("Impor Sounding")
                .setMessage("Pilih cara memasukkan sounding CSV.")
                .setNegativeButton("Batal",null)
                .setNeutralButton("Ganti Semua",(d,w)->doImport(uri,false))
                .setPositiveButton("Tambahkan",(d,w)->doImport(uri,true)).show();
    }

    private void doImport(Uri uri,boolean append){
        try(InputStream in=getContentResolver().openInputStream(uri)){
            int n=store.importStream(in,append);
            Toast.makeText(this,n+" sounding diimpor",Toast.LENGTH_LONG).show();
            refresh();
        }catch(Exception e){Toast.makeText(this,"Impor gagal: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    private String fmt(double d){return Double.isFinite(d)?String.format(Locale.US,"%.1f",d):"--";}
    private String stamp(){return new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());}
    @Override protected void onResume(){super.onResume();refresh();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}

    private TextView panel(String s){TextView t=text(s,14,true);t.setGravity(Gravity.LEFT);t.setPadding(dp(12),dp(12),dp(12),dp(12));t.setBackgroundColor(Color.rgb(7,57,94));return t;}
    private TextView text(String s,int sp,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(sp);t.setGravity(Gravity.CENTER);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private LinearLayout.LayoutParams lp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(8),0,0);return p;}
    private LinearLayout.LayoutParams top(int m){LinearLayout.LayoutParams p=lp();p.topMargin=dp(m);return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
