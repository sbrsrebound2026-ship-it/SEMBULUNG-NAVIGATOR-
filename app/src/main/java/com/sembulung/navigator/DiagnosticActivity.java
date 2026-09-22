package com.sembulung.navigator;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import com.sembulung.navigator.ais.AisTargetStore;
import com.sembulung.navigator.sonar.SonarChartStore;
import java.io.File;
import java.util.Locale;

public class DiagnosticActivity extends Activity {
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.rgb(3,27,61));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(24),dp(20),dp(28));
        scroll.addView(root,new ScrollView.LayoutParams(-1,-2));
        root.addView(text("DIAGNOSTIC",24,true));
        root.addView(panel(buildReport()),top(14));
        Button back=button("KEMBALI");back.setOnClickListener(v->finish());root.addView(back,top(18));
        setContentView(scroll);
    }

    private String buildReport(){
        MarineServiceState.Snapshot m=MarineServiceState.read(this);
        NmeaDataStore.Snapshot n=NmeaDataStore.read(this);
        SonarChartStore sonar=new SonarChartStore(this);
        int soundings=sonar.load(0).size();
        int ais=AisTargetStore.load(this,120000L).size();
        int wp=WaypointStore.load(this).size();
        File f=sonar.file();
        return String.format(Locale.US,
                "APP\nVersion %s (%d)\n\nMARINE SERVICE\nRunning: %s\nPort: %d\nReceived: %d\nValid: %d\nRejected: %d\nAIS decoded: %d\nStatus: %s\n\nNMEA\nPosition fresh: %s\nDepth fresh: %s\nSource: %s\n\nDATA\nSoundings: %d\nSounding file: %.1f KB\nAIS fresh: %d\nWaypoint: %d\nActive WP: %d\n\nSONAR SETTINGS\nContour: %.1f m\nQuality: %s\nSafety depth: %.1f m",
                BuildConfig.VERSION_NAME,BuildConfig.VERSION_CODE,m.running,m.port,m.received,m.valid,m.rejected,m.aisDecoded,m.status,
                n!=null&&n.positionFresh(5000),n!=null&&n.depthFresh(5000),n==null?"---":n.source,
                soundings,f.exists()?f.length()/1024.0:0.0,ais,wp,WaypointStore.activeIndex(this),
                AppSettings.contourIntervalMeters(this),AppSettings.sonarQualityMode(this),AppSettings.shallowMeters(this));
    }

    private TextView panel(String s){TextView t=text(s,14,false);t.setGravity(Gravity.LEFT);t.setPadding(dp(14),dp(14),dp(14),dp(14));t.setBackgroundColor(Color.rgb(7,57,94));return t;}
    private TextView text(String s,int sp,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(sp);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);t.setGravity(Gravity.CENTER);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private LinearLayout.LayoutParams top(int m){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(m),0,0);return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
