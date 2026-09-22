package com.sembulung.navigator;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.sembulung.navigator.ais.AisCollisionEngine;
import com.sembulung.navigator.ais.AisParser;
import com.sembulung.navigator.ais.AisTarget;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AisActivity extends Activity {
    private static final int DEFAULT_PORT = 10110;
    private static final long OWN_DATA_MAX_AGE_MS = 15000L;
    private static final long TARGET_MAX_AGE_MS = 120000L;

    private EditText portInput;
    private EditText manualInput;
    private TextView connection;
    private TextView ownVessel;
    private TextView statistics;
    private LinearLayout targetList;

    private final Map<Long, AisTarget> targets = new ConcurrentHashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private volatile boolean listening;
    private DatagramSocket socket;
    private Thread receiverThread;
    private long received;
    private long decoded;
    private long rejected;

    private final Runnable refreshUi = new Runnable() {
        @Override public void run() {
            purgeStaleTargets();
            renderOwnVessel();
            renderTargets();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(3,27,61));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(24),dp(20),dp(30));
        scroll.addView(root,new ScrollView.LayoutParams(-1,-2));

        root.addView(text("AIS • CPA / TCPA",24,true));

        TextView subtitle = text("Target kapal AIS Class A/B • perhitungan relative-motion",14,false);
        subtitle.setPadding(0,dp(5),0,dp(14));
        root.addView(subtitle);

        TextView advisory = text(
                "ADVISORY: CPA/TCPA membantu kewaspadaan situasional dan bukan pengganti radar, pengamatan visual, atau keputusan nakhoda.",
                12,false);
        advisory.setTextColor(Color.rgb(255,210,80));
        advisory.setPadding(dp(10),dp(10),dp(10),dp(10));
        advisory.setBackgroundColor(Color.rgb(68,48,8));
        root.addView(advisory,lp());

        connection = panel("STATUS\nListener AIS belum aktif");
        ownVessel = panel("KAPAL SENDIRI\nMenunggu posisi/COG/SOG NMEA");
        statistics = panel("DATA AIS\n0 diterima • 0 decoded • 0 ditolak");
        root.addView(connection,lp());
        root.addView(ownVessel,lp());
        root.addView(statistics,lp());

        TextView portLabel = text("PORT UDP AIS",13,true);
        portLabel.setPadding(0,dp(16),0,dp(4));
        root.addView(portLabel);

        portInput = new EditText(this);
        portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        portInput.setText(String.valueOf(DEFAULT_PORT));
        portInput.setTextColor(Color.WHITE);
        portInput.setGravity(Gravity.CENTER);
        root.addView(portInput,lp());

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);

        Button start = button("MULAI AIS");
        start.setOnClickListener(v -> startListener());
        controls.addView(start,half());

        Button stop = button("STOP");
        stop.setOnClickListener(v -> stopListener());
        controls.addView(stop,half());
        root.addView(controls,lp());

        TextView manualLabel = text("UJI KALIMAT !AIVDM / !AIVDO",13,true);
        manualLabel.setPadding(0,dp(18),0,dp(4));
        root.addView(manualLabel);

        manualInput = new EditText(this);
        manualInput.setTextColor(Color.WHITE);
        manualInput.setHintTextColor(0xFF94A3B8);
        manualInput.setHint("Tempel kalimat AIS NMEA 0183 di sini");
        manualInput.setMinLines(2);
        root.addView(manualInput,lp());

        Button parse = button("PROSES AIS MANUAL");
        parse.setOnClickListener(v -> {
            String raw = manualInput.getText().toString().trim();
            if(raw.isEmpty()) {
                Toast.makeText(this,"Masukkan !AIVDM atau !AIVDO",Toast.LENGTH_SHORT).show();
                return;
            }
            for(String line : raw.split("[\\r\\n]+")) processLine(line.trim(),"manual");
        });
        root.addView(parse,lp());

        TextView targetTitle = text("TARGET AIS AKTIF",16,true);
        targetTitle.setPadding(0,dp(22),0,dp(5));
        root.addView(targetTitle);

        targetList = new LinearLayout(this);
        targetList.setOrientation(LinearLayout.VERTICAL);
        root.addView(targetList,new LinearLayout.LayoutParams(-1,-2));

        Button clear = button("HAPUS TARGET AIS");
        clear.setOnClickListener(v -> {
            targets.clear();
            renderTargets();
        });
        root.addView(clear,top(16));

        Button back = button("KEMBALI");
        back.setOnClickListener(v -> finish());
        root.addView(back,lp());

        setContentView(scroll);
    }

    private void startListener() {
        if(listening) {
            Toast.makeText(this,"Listener AIS sudah aktif",Toast.LENGTH_SHORT).show();
            return;
        }

        final int port;
        try {
            port = Integer.parseInt(portInput.getText().toString().trim());
            if(port < 1 || port > 65535) throw new IllegalArgumentException();
        } catch(Exception e) {
            Toast.makeText(this,"Port AIS tidak valid",Toast.LENGTH_LONG).show();
            return;
        }

        listening = true;
        connection.setText("STATUS\nMembuka UDP port " + port + "...");

        receiverThread = new Thread(() -> {
            try {
                socket = new DatagramSocket(port);
                socket.setSoTimeout(1000);
                runOnUiThread(() -> connection.setText("STATUS\nAIS UDP aktif • port " + port));

                byte[] buffer = new byte[8192];
                while(listening) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer,buffer.length);
                        socket.receive(packet);
                        String raw = new String(
                                packet.getData(),packet.getOffset(),packet.getLength(),StandardCharsets.US_ASCII);
                        String source = packet.getAddress().getHostAddress() + ":" + packet.getPort();
                        for(String line : raw.split("[\\r\\n]+")) processLine(line.trim(),source);
                    } catch(SocketTimeoutException ignored) {
                    }
                }
            } catch(Exception e) {
                if(listening) runOnUiThread(() ->
                        connection.setText("STATUS\nAIS listener gagal: " + e.getMessage()));
            } finally {
                closeSocket();
                listening = false;
            }
        },"Sembulung-AIS-UDP");

        receiverThread.start();
    }

    private void stopListener() {
        listening = false;
        closeSocket();
        connection.setText("STATUS\nAIS listener berhenti");
    }

    private void closeSocket() {
        if(socket != null) {
            try { socket.close(); } catch(Exception ignored) {}
            socket = null;
        }
    }

    private void processLine(String sentence,String source) {
        if(sentence == null || sentence.isEmpty()) return;
        if(!(sentence.startsWith("!AIVDM") || sentence.startsWith("!AIVDO"))) return;

        received++;
        AisTarget target = AisParser.parse(sentence);
        if(target != null && target.hasValidPosition()) {
            decoded++;
            targets.put(target.mmsi,target);
        } else {
            rejected++;
        }

        runOnUiThread(() -> {
            statistics.setText(String.format(Locale.US,
                    "DATA AIS\n%d diterima • %d decoded • %d ditolak • %d target",
                    received,decoded,rejected,targets.size()));
            if("manual".equals(source) && target == null) {
                Toast.makeText(this,
                        "AIS belum dapat didecode. V12 mendukung single-fragment type 1/2/3/18.",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void renderOwnVessel() {
        NmeaDataStore.Snapshot own = NmeaDataStore.read(this);
        if(own == null || !own.positionFresh(OWN_DATA_MAX_AGE_MS)) {
            ownVessel.setText("KAPAL SENDIRI\nPosisi NMEA belum fresh • buka SONAR/NMEA dan kirim RMC/GGA");
            return;
        }
        String sog = own.speed == null ? "---" : String.format(Locale.US,"%.1f kn",own.speed);
        String cog = own.heading == null ? "---" : String.format(Locale.US,"%.0f°",own.heading);
        ownVessel.setText(String.format(Locale.US,
                "KAPAL SENDIRI\n%.6f, %.6f • SOG %s • COG %s",
                own.lat,own.lon,sog,cog));
    }

    private void renderTargets() {
        if(targetList == null) return;
        targetList.removeAllViews();

        if(targets.isEmpty()) {
            TextView empty = text("Belum ada target AIS aktif.",14,false);
            empty.setPadding(0,dp(8),0,dp(12));
            targetList.addView(empty);
            return;
        }

        NmeaDataStore.Snapshot own = NmeaDataStore.read(this);
        boolean ownReady = own != null && own.positionFresh(OWN_DATA_MAX_AGE_MS);
        double ownSog = ownReady && own.speed != null ? own.speed : Double.NaN;
        double ownCog = ownReady && own.heading != null ? own.heading : Double.NaN;

        List<TargetRow> rows = new ArrayList<>();
        for(AisTarget t : targets.values()) {
            AisCollisionEngine.Assessment a = ownReady
                    ? AisCollisionEngine.assess(own.lat,own.lon,ownSog,ownCog,t)
                    : new AisCollisionEngine.Assessment(
                            Double.NaN,Double.NaN,Double.NaN,Double.NaN,AisCollisionEngine.Risk.UNKNOWN);
            rows.add(new TargetRow(t,a));
        }

        rows.sort(Comparator
                .comparingInt((TargetRow r) -> riskRank(r.assessment.risk))
                .thenComparingDouble(r -> Double.isNaN(r.assessment.rangeNm)
                        ? Double.POSITIVE_INFINITY : r.assessment.rangeNm));

        for(TargetRow row : rows) addTargetCard(row.target,row.assessment);
    }

    private void addTargetCard(AisTarget target,AisCollisionEngine.Assessment a) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12),dp(10),dp(12),dp(10));
        card.setBackgroundColor(riskColor(a.risk));

        String range = Double.isNaN(a.rangeNm) ? "---" : String.format(Locale.US,"%.2f NM",a.rangeNm);
        String bearing = Double.isNaN(a.bearingDeg) ? "---" : String.format(Locale.US,"%.0f°",a.bearingDeg);
        String cpa = Double.isNaN(a.cpaNm) ? "---" : String.format(Locale.US,"%.2f NM",a.cpaNm);
        String tcpa;
        if(Double.isInfinite(a.tcpaMinutes)) tcpa = "∞";
        else if(Double.isNaN(a.tcpaMinutes)) tcpa = "---";
        else tcpa = String.format(Locale.US,"%.0f min",a.tcpaMinutes);

        String sog = Double.isNaN(target.speedKnots) ? "---" : String.format(Locale.US,"%.1f kn",target.speedKnots);
        String cog = Double.isNaN(target.courseDeg) ? "---" : String.format(Locale.US,"%.0f°",target.courseDeg);

        TextView info = text(String.format(Locale.US,
                "MMSI %09d • %s\nRange %s • Bearing %s\nSOG %s • COG %s\nCPA %s • TCPA %s",
                target.mmsi,a.risk.name(),range,bearing,sog,cog,cpa,tcpa),14,true);
        info.setGravity(Gravity.LEFT);
        card.addView(info);

        targetList.addView(card,lp());
    }

    private int riskRank(AisCollisionEngine.Risk r) {
        switch(r) {
            case DANGER: return 0;
            case WARNING: return 1;
            case MONITOR: return 2;
            case SAFE: return 3;
            default: return 4;
        }
    }

    private int riskColor(AisCollisionEngine.Risk r) {
        switch(r) {
            case DANGER: return Color.rgb(120,20,25);
            case WARNING: return Color.rgb(110,72,10);
            case MONITOR: return Color.rgb(10,70,105);
            case SAFE: return Color.rgb(7,57,94);
            default: return Color.rgb(45,55,70);
        }
    }

    private void purgeStaleTargets() {
        long cutoff = System.currentTimeMillis() - TARGET_MAX_AGE_MS;
        targets.entrySet().removeIf(e -> e.getValue().receivedAtMillis < cutoff);
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refreshUi);
        handler.post(refreshUi);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refreshUi);
        super.onPause();
    }

    @Override protected void onDestroy() {
        stopListener();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private TextView panel(String s) {
        TextView t = text(s,15,true);
        t.setPadding(dp(12),dp(12),dp(12),dp(12));
        t.setBackgroundColor(Color.rgb(7,57,94));
        return t;
    }

    private TextView text(String s,int sp,boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(sp);
        t.setGravity(Gravity.CENTER);
        if(bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams lp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,dp(8),0,0);
        return p;
    }

    private LinearLayout.LayoutParams top(int margin) {
        LinearLayout.LayoutParams p = lp();
        p.topMargin = dp(margin);
        return p;
    }

    private LinearLayout.LayoutParams half() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,-2,1f);
        p.setMargins(dp(3),0,dp(3),0);
        return p;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static final class TargetRow {
        final AisTarget target;
        final AisCollisionEngine.Assessment assessment;
        TargetRow(AisTarget target,AisCollisionEngine.Assessment assessment) {
            this.target = target;
            this.assessment = assessment;
        }
    }
}
