package com.sembulung.navigator.sonar;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SonarChartStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "sembulung_sonar_chart.db";
    private static final int DB_VERSION = 1;

    public SonarChartStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sounding (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "lat REAL NOT NULL," +
                "lon REAL NOT NULL," +
                "depth REAL NOT NULL," +
                "ts INTEGER NOT NULL," +
                "speed REAL," +
                "course REAL," +
                "quality TEXT NOT NULL," +
                "source TEXT NOT NULL)");
        db.execSQL("CREATE INDEX idx_sounding_ts ON sounding(ts)");
        db.execSQL("CREATE INDEX idx_sounding_pos ON sounding(lat, lon)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public synchronized long insert(DepthSample sample) {
        if (sample == null || !sample.isUsable()) return -1L;
        ContentValues v = new ContentValues();
        v.put("lat", sample.latitude);
        v.put("lon", sample.longitude);
        v.put("depth", sample.depthMeters);
        v.put("ts", sample.timestampMs);
        if (sample.speedKnots != null) v.put("speed", sample.speedKnots);
        if (sample.courseDegrees != null) v.put("course", sample.courseDegrees);
        v.put("quality", sample.quality.name());
        v.put("source", sample.source);
        return getWritableDatabase().insert("sounding", null, v);
    }

    public synchronized List<DepthSample> recent(int limit) {
        int safeLimit = Math.max(1, Math.min(10000, limit));
        List<DepthSample> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query(
                "sounding",
                new String[]{"lat","lon","depth","ts","speed","course","quality","source"},
                null, null, null, null, "ts DESC",
                Integer.toString(safeLimit));
        try {
            while (c.moveToNext()) {
                Double speed = c.isNull(4) ? null : c.getDouble(4);
                Double course = c.isNull(5) ? null : c.getDouble(5);
                DepthSample.Quality q;
                try { q = DepthSample.Quality.valueOf(c.getString(6)); }
                catch (Exception e) { q = DepthSample.Quality.QUESTIONABLE; }
                out.add(new DepthSample(
                        c.getDouble(0),
                        c.getDouble(1),
                        c.getDouble(2),
                        c.getLong(3),
                        speed,
                        course,
                        q,
                        c.getString(7)));
            }
        } finally {
            c.close();
        }
        Collections.reverse(out);
        return out;
    }

    public synchronized long count() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM sounding", null);
        try {
            return c.moveToFirst() ? c.getLong(0) : 0L;
        } finally {
            c.close();
        }
    }

    public synchronized void clearAll() {
        getWritableDatabase().delete("sounding", null, null);
    }
}
