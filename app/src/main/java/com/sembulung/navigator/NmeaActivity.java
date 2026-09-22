package com.sembulung.navigator;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class NmeaActivity extends Activity {
    private static final int DEFAULT_PORT = 10110;

    private EditText portInput;
    private EditText manualInput;
    private TextView connection;
    private TextView position;
    private TextView depth;
    private TextView heading;
    private TextView speed;
    private TextView satellites;
    private TextView waterTemp;
    private TextView lastSentence;
    private TextView stats;

    private volatile boolean listening = false;
    private DatagramSocket socket;
    private Thread listenerThread;

    private long receivedCount = 0;
    private long validCount = 0;
    private long invalidCount = 0;

    private Double latitude = null;
    private Double longitude = null;
    private Double depthMeters = null;
    private Double headingDeg = null;
    private Double speedKnots = null;
    private Integer satelliteCount = null;
    private Double waterTempC = null;

    private long positionUpdatedAt = 0L;
    private long headingUpdatedAt = 0L;
    private long depthUpdatedAt = 0L;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(3,27,61));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(24),dp(20),dp(30));
        scroll.addView(root,new ScrollView.LayoutParams(-1,-2));

        TextView title = label("SONAR / NMEA 0183",24,true);
        root.addView(title);

        TextView sub = label("UDP Wi-Fi listener + parser data navigasi laut",14,false);
        sub.setPadding(0,dp(6),0,dp(18));
        root.addView(sub);

        connection = panel("STATUS\nListener belum aktif");
        position = panel("POSISI\n---");
        depth = panel("DEPTH / SONAR\n--- m");
        heading = panel("HEADING\n---°");
        speed = panel("SPEED\n--- kn");
        satellites = panel("SATELIT\n---");
        waterTemp = panel("SUHU AIR\n--- °C");
        stats = panel("DATA\n0 diterima • 0 valid • 0 ditolak");
        lastSentence = panel("NMEA TERAKHIR\n---");

        root.addView(connection,lp());
        root.addView(position,lp());
        root.addView(depth,lp());
        root.addView(heading,lp());
        root.addView(speed,lp());
        root.addView(satellites,lp());
        root.addView(waterTemp,lp());
        root.addView(stats,lp());
        root.addView(lastSentence,lp());

        TextView portLabel = label("PORT UDP",13,true);
        portLabel.setPadding(0,dp(18),0,dp(4));
        root.addView(portLabel);

        portInput = new EditText(this);
        portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        portInput.setText(String.valueOf(DEFAULT_PORT));
        portInput.setTextColor(Color.WHITE);
        portInput.setHintTextColor(0xFF94A3B8);
        portInput.setGravity(Gravity.CENTER);
        root.addView(portInput,lp());

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);

        Button start = button("MULAI LISTENER");
        start.setOnClickListener(v -> startListener());
        controls.addView(start,half());

        Button stop = button("STOP");
        stop.setOnClickListener(v -> stopListener());
        controls.addView(stop,half());

        root.addView(controls,lp());

        TextView manualLabel = label("UJI NMEA MANUAL",13,true);
        manualLabel.setPadding(0,dp(20),0,dp(4));
        root.addView(manualLabel);

        manualInput = new EditText(this);
        manualInput.setTextColor(Color.WHITE);
        manualInput.setHintTextColor(0xFF94A3B8);
        manualInput.setHint("$GPRMC,... atau $SDDPT,...");
        manualInput.setSingleLine(false);
        manualInput.setMinLines(2);
        root.addView(manualInput,lp());

        Button parse = button("PROSES KALIMAT NMEA");
        parse.setOnClickListener(v -> {
            String raw = manualInput.getText().toString().trim();
            if(raw.isEmpty()) {
                Toast.makeText(this,"Masukkan kalimat NMEA",Toast.LENGTH_SHORT).show();
                return;
            }
            String[] lines = raw.split("[\\r\\n]+");
            for(String line : lines) {
                if(!line.trim().isEmpty()) processIncoming(line.trim(),"manual");
            }
        });
        root.addView(parse,lp());

        Button demo = button("ISI CONTOH DATA");
        demo.setOnClickListener(v -> {
            String sample =
                    "$GPRMC,123519,A,0830.000,S,11420.000,E,7.5,84.4,230394,,\n" +
                    "$GPGGA,123520,0830.000,S,11420.000,E,1,10,0.8,5.2,M,0.0,M,,\n" +
                    "$SDDPT,18.7,0.0,\n" +
                    "$HCHDT,92.5,T\n" +
                    "$WIMTW,28.6,C";
            manualInput.setText(sample);
        });
        root.addView(demo,lp());

        Button openMap = button("BUKA PETA TERINTEGRASI");
        openMap.setOnClickListener(v -> startActivity(new Intent(this, MarineMapActivity.class)));
        root.addView(openMap,lp());

        Button clear = button("RESET DATA");
        clear.setOnClickListener(v -> resetValues());
        root.addView(clear,lp());

        Button back = button("KEMBALI");
        back.setOnClickListener(v -> finish());
        root.addView(back,top(18));

        setContentView(scroll);
    }

    private void startListener() {
        if(listening) {
            Toast.makeText(this,"Listener sudah aktif",Toast.LENGTH_SHORT).show();
            return;
        }

        final int port;
        try {
            port = Integer.parseInt(portInput.getText().toString().trim());
            if(port < 1 || port > 65535) throw new IllegalArgumentException();
        } catch(Exception e) {
            Toast.makeText(this,"Port UDP tidak valid",Toast.LENGTH_LONG).show();
            return;
        }

        listening = true;
        connection.setText("STATUS\nMembuka UDP port " + port + "...");

        listenerThread = new Thread(() -> {
            try {
                socket = new DatagramSocket(port);
                socket.setSoTimeout(1000);
                runOnUiThread(() -> connection.setText("STATUS\nUDP aktif • port " + port));

                byte[] buffer = new byte[8192];

                while(listening) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer,buffer.length);
                        socket.receive(packet);

                        String raw = new String(packet.getData(),packet.getOffset(),packet.getLength(),StandardCharsets.US_ASCII);
                        String source = packet.getAddress().getHostAddress() + ":" + packet.getPort();

                        String[] lines = raw.split("[\\r\\n]+");
                        for(String line : lines) {
                            if(!line.trim().isEmpty()) processIncoming(line.trim(),source);
                        }
                    } catch(SocketTimeoutException ignored) {
                    }
                }
            } catch(Exception e) {
                if(listening) {
                    runOnUiThread(() -> connection.setText("STATUS\nListener gagal: " + e.getMessage()));
                }
            } finally {
                if(socket != null) {
                    try { socket.close(); } catch(Exception ignored) {}
                    socket = null;
                }
                listening = false;
            }
        },"Sembulung-NMEA-UDP");

        listenerThread.start();
    }

    private void stopListener() {
        listening = false;
        if(socket != null) {
            try { socket.close(); } catch(Exception ignored) {}
            socket = null;
        }
        connection.setText("STATUS\nListener berhenti");
    }

    private void processIncoming(String raw,String source) {
        final String sentence = raw.trim();
        receivedCount++;

        boolean checksumPresent = sentence.contains("*");
        boolean checksumOk = !checksumPresent || checksumValid(sentence);

        if(!checksumOk) {
            invalidCount++;
            runOnUiThread(() -> {
                lastSentence.setText("NMEA DITOLAK • checksum salah\n" + sentence);
                updateStats();
            });
            return;
        }

        boolean parsed = parseSentence(sentence);
        if(parsed) {
            validCount++;
            NmeaDataStore.write(
                    this,
                    latitude,
                    longitude,
                    headingDeg,
                    depthMeters,
                    speedKnots,
                    positionUpdatedAt,
                    headingUpdatedAt,
                    depthUpdatedAt,
                    source);
        } else {
            invalidCount++;
        }

        runOnUiThread(() -> {
            lastSentence.setText("NMEA TERAKHIR • " + source + "\n" + sentence);
            updateDisplays();
            updateStats();
        });
    }

    private boolean parseSentence(String sentence) {
        try {
            String s = sentence;
            int star = s.indexOf('*');
            if(star >= 0) s = s.substring(0,star);
            if(s.startsWith("$") || s.startsWith("!")) s = s.substring(1);

            String[] f = s.split(",",-1);
            if(f.length == 0 || f[0].length() < 3) return false;

            String type = f[0].substring(f[0].length()-3).toUpperCase(Locale.US);

            switch(type) {
                case "RMC":
                    if(f.length > 8 && ("A".equalsIgnoreCase(f[2]) || f[2].isEmpty())) {
                        Double lat = parseLatLon(f[3],f[4],true);
                        Double lon = parseLatLon(f[5],f[6],false);
                        if(lat != null && lon != null) {
                            latitude = lat;
                            longitude = lon;
                            positionUpdatedAt = System.currentTimeMillis();
                        }
                        Double sp = num(f[7]);
                        if(sp != null) speedKnots = sp;
                        Double crs = num(f[8]);
                        if(crs != null) {
                            headingDeg = normalizeHeading(crs);
                            headingUpdatedAt = System.currentTimeMillis();
                        }
                    }
                    return true;

                case "GGA":
                    if(f.length > 9) {
                        Double lat = parseLatLon(f[2],f[3],true);
                        Double lon = parseLatLon(f[4],f[5],false);
                        if(lat != null && lon != null) {
                            latitude = lat;
                            longitude = lon;
                            positionUpdatedAt = System.currentTimeMillis();
                        }
                        Integer sat = integer(f[7]);
                        if(sat != null) satelliteCount = sat;
                    }
                    return true;

                case "GLL":
                    if(f.length > 4) {
                        Double lat = parseLatLon(f[1],f[2],true);
                        Double lon = parseLatLon(f[3],f[4],false);
                        if(lat != null && lon != null) {
                            latitude = lat;
                            longitude = lon;
                            positionUpdatedAt = System.currentTimeMillis();
                        }
                    }
                    return true;

                case "VTG":
                    if(f.length > 5) {
                        Double h = num(f[1]);
                        if(h != null) {
                            headingDeg = normalizeHeading(h);
                            headingUpdatedAt = System.currentTimeMillis();
                        }
                        Double sp = num(f[5]);
                        if(sp != null) speedKnots = sp;
                    }
                    return true;

                case "VHW":
                    if(f.length > 5) {
                        Double h = num(f[1]);
                        if(h != null) {
                            headingDeg = normalizeHeading(h);
                            headingUpdatedAt = System.currentTimeMillis();
                        }
                        Double sp = num(f[5]);
                        if(sp != null) speedKnots = sp;
                    }
                    return true;

                case "HDG":
                case "HDT":
                    if(f.length > 1) {
                        Double h = num(f[1]);
                        if(h != null) {
                            headingDeg = normalizeHeading(h);
                            headingUpdatedAt = System.currentTimeMillis();
                        }
                    }
                    return true;

                case "DPT":
                    if(f.length > 1) {
                        Double d = num(f[1]);
                        if(d != null && d >= 0) {
                            depthMeters = d;
                            depthUpdatedAt = System.currentTimeMillis();
                        }
                    }
                    return true;

                case "DBT":
                    if(f.length > 3) {
                        Double d = num(f[3]);
                        if(d != null && d >= 0) {
                            depthMeters = d;
                            depthUpdatedAt = System.currentTimeMillis();
                        }
                    }
                    return true;

                case "MTW":
                    if(f.length > 1) {
                        Double t = num(f[1]);
                        if(t != null) waterTempC = t;
                    }
                    return true;

                default:
                    return false;
            }
        } catch(Exception e) {
            return false;
        }
    }

    private boolean checksumValid(String sentence) {
        try {
            int start = (sentence.startsWith("$") || sentence.startsWith("!")) ? 1 : 0;
            int star = sentence.indexOf('*');
            if(star < 0 || star + 2 >= sentence.length()) return true;

            int checksum = 0;
            for(int i=start;i<star;i++) checksum ^= sentence.charAt(i);

            String hex = sentence.substring(star+1,Math.min(star+3,sentence.length()));
            int expected = Integer.parseInt(hex,16);
            return checksum == expected;
        } catch(Exception e) {
            return false;
        }
    }

    private Double parseLatLon(String raw,String hemi,boolean latitudeMode) {
        if(raw == null || raw.isEmpty()) return null;
        try {
            int degDigits = latitudeMode ? 2 : 3;
            if(raw.length() <= degDigits) return null;
            double deg = Double.parseDouble(raw.substring(0,degDigits));
            double min = Double.parseDouble(raw.substring(degDigits));
            double value = deg + min/60.0;

            if("S".equalsIgnoreCase(hemi) || "W".equalsIgnoreCase(hemi)) value = -value;
            return value;
        } catch(Exception e) {
            return null;
        }
    }

    private Double num(String s) {
        try {
            if(s == null || s.trim().isEmpty()) return null;
            return Double.parseDouble(s.trim());
        } catch(Exception e) {
            return null;
        }
    }

    private Integer integer(String s) {
        try {
            if(s == null || s.trim().isEmpty()) return null;
            return Integer.parseInt(s.trim());
        } catch(Exception e) {
            return null;
        }
    }

    private double normalizeHeading(double h) {
        double r = h % 360.0;
        if(r < 0) r += 360.0;
        return r;
    }

    private void updateDisplays() {
        position.setText(latitude != null && longitude != null
                ? String.format(Locale.US,"POSISI\n%.6f, %.6f",latitude,longitude)
                : "POSISI\n---");

        depth.setText(depthMeters != null
                ? String.format(Locale.US,"DEPTH / SONAR\n%.2f m",depthMeters)
                : "DEPTH / SONAR\n--- m");

        heading.setText(headingDeg != null
                ? String.format(Locale.US,"HEADING\n%.1f° %s",headingDeg,cardinal(headingDeg))
                : "HEADING\n---°");

        speed.setText(speedKnots != null
                ? String.format(Locale.US,"SPEED\n%.1f kn",speedKnots)
                : "SPEED\n--- kn");

        satellites.setText(satelliteCount != null
                ? "SATELIT\n" + satelliteCount
                : "SATELIT\n---");

        waterTemp.setText(waterTempC != null
                ? String.format(Locale.US,"SUHU AIR\n%.1f °C",waterTempC)
                : "SUHU AIR\n--- °C");
    }

    private void updateStats() {
        stats.setText(String.format(Locale.US,
                "DATA\n%d diterima • %d valid • %d ditolak",
                receivedCount,validCount,invalidCount));
    }

    private String cardinal(double bearing) {
        String[] dirs = {"N","NE","E","SE","S","SW","W","NW"};
        return dirs[((int)Math.round(bearing/45.0)) % 8];
    }

    private void resetValues() {
        latitude = null;
        longitude = null;
        depthMeters = null;
        headingDeg = null;
        speedKnots = null;
        satelliteCount = null;
        waterTempC = null;
        positionUpdatedAt = 0L;
        headingUpdatedAt = 0L;
        depthUpdatedAt = 0L;
        receivedCount = 0;
        validCount = 0;
        invalidCount = 0;
        NmeaDataStore.clear(this);
        lastSentence.setText("NMEA TERAKHIR\n---");
        updateDisplays();
        updateStats();
    }

    @Override protected void onDestroy() {
        stopListener();
        super.onDestroy();
    }

    private TextView panel(String s) {
        TextView t = label(s,17,true);
        t.setPadding(dp(12),dp(14),dp(12),dp(14));
        t.setBackgroundColor(Color.rgb(7,57,94));
        return t;
    }

    private TextView label(String s,int sp,boolean bold) {
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
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,dp(margin),0,0);
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
}
