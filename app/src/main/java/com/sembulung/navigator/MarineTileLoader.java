package com.sembulung.navigator;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MarineTileLoader {
    public static final String LAYER_OSM = "osm";
    public static final String LAYER_SEAMARK = "seamark";
    public static final String LAYER_BATHY = "bathymetry";

    private static final long CACHE_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final String OSM_URL = "https://tile.openstreetmap.org/%d/%d/%d.png";
    private static final String SEAMARK_URL = "https://tiles.openseamap.org/seamark/%d/%d/%d.png";
    // OpenSeaMap Marine Profile: GEBCO-derived depth shading/contours.
    private static final String BATHY_WMS = "https://geoserver.openseamap.org/geoserver/wms";
    private static final int TILE_SIZE = 256;
    private static final String USER_AGENT = "SEMBULUNG-NAVIGATOR/1.0-v13 (Android)";

    private final File cacheDir;
    private final Runnable invalidate;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Set<String> inFlight = Collections.synchronizedSet(new HashSet<>());

    private final LruCache<String, Bitmap> memory = new LruCache<String, Bitmap>(24 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) {
            return value == null ? 0 : value.getByteCount() / 1024;
        }
    };

    public MarineTileLoader(Context context, Runnable invalidate) {
        this.invalidate = invalidate;
        cacheDir = new File(context.getFilesDir(), "marine_tiles_v14");
        if (!cacheDir.exists()) cacheDir.mkdirs();
    }

    public Bitmap get(String layer, int z, int x, int y) {
        String key = layer + "_" + z + "_" + x + "_" + y;

        Bitmap mem = memory.get(key);
        if (mem != null && !mem.isRecycled()) return mem;

        File disk = new File(cacheDir, key + ".png");

        if (disk.exists() && disk.length() > 0) {
            Bitmap cached = BitmapFactory.decodeFile(disk.getAbsolutePath());
            if (cached != null) {
                memory.put(key, cached);
                if (System.currentTimeMillis() - disk.lastModified() > CACHE_MS) {
                    schedule(layer, z, x, y, key, disk);
                }
                return cached;
            }
        }

        schedule(layer, z, x, y, key, disk);
        return null;
    }

    private void schedule(String layer, int z, int x, int y, String key, File disk) {
        if (!inFlight.add(key)) return;

        executor.execute(() -> {
            File tmp = new File(cacheDir, key + ".tmp");

            try {
                String urlText;
                if (LAYER_BATHY.equals(layer)) {
                    urlText = bathyUrl(z, x, y);
                } else {
                    String template = LAYER_SEAMARK.equals(layer) ? SEAMARK_URL : OSM_URL;
                    urlText = String.format(Locale.US, template, z, x, y);
                }

                HttpURLConnection c = (HttpURLConnection) new URL(urlText).openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(7000);
                c.setUseCaches(true);
                c.setRequestProperty("User-Agent", USER_AGENT);

                if (c.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    try (InputStream in = c.getInputStream();
                         FileOutputStream out = new FileOutputStream(tmp, false)) {
                        byte[] buffer = new byte[32768];
                        int n;
                        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                        out.flush();
                    }

                    if (tmp.length() > 0) {
                        if (disk.exists()) disk.delete();
                        if (!tmp.renameTo(disk)) {
                            try (InputStream in = new java.io.FileInputStream(tmp);
                                 FileOutputStream out = new FileOutputStream(disk, false)) {
                                byte[] buffer = new byte[32768];
                                int n;
                                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                                out.flush();
                            }
                            tmp.delete();
                        }

                        Bitmap b = BitmapFactory.decodeFile(disk.getAbsolutePath());
                        if (b != null) {
                            memory.put(key, b);
                            if (invalidate != null) invalidate.run();
                        }
                    }
                }

                c.disconnect();
            } catch (Exception ignored) {
            } finally {
                if (tmp.exists()) tmp.delete();
                inFlight.remove(key);
            }
        });
    }

    private String bathyUrl(int z,int x,int y) {
        int n=1<<z;
        double west=x/(double)n*360.0-180.0;
        double east=(x+1)/(double)n*360.0-180.0;
        double north=Math.toDegrees(Math.atan(Math.sinh(Math.PI-2*Math.PI*y/(double)n)));
        double south=Math.toDegrees(Math.atan(Math.sinh(Math.PI-2*Math.PI*(y+1)/(double)n)));
        double minX=west*20037508.34/180.0, maxX=east*20037508.34/180.0;
        double minY=Math.log(Math.tan(Math.toRadians(south))+1/Math.cos(Math.toRadians(south)))*20037508.34/Math.PI;
        double maxY=Math.log(Math.tan(Math.toRadians(north))+1/Math.cos(Math.toRadians(north)))*20037508.34/Math.PI;
        return BATHY_WMS+"?SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap"
                +"&LAYERS=gebco%3Adeeps_gwc&STYLES=&SRS=EPSG%3A900913"
                +"&FORMAT=image%2Fpng&TRANSPARENT=true"
                +"&BBOX="+String.format(Locale.US,"%.3f,%.3f,%.3f,%.3f",minX,minY,maxX,maxY)
                +"&WIDTH="+TILE_SIZE+"&HEIGHT="+TILE_SIZE;
    }

    public File cachedTileFile(String layer,int z,int x,int y) {
        String key=layer+"_"+z+"_"+x+"_"+y;
        return new File(cacheDir,key+".png");
    }

    public Bitmap getCached(String layer,int z,int x,int y) {
        if(z<1||z>18)return null;
        int n=1<<z;
        x=((x%n)+n)%n;
        if(y<0||y>=n)return null;
        String key=layer+"_"+z+"_"+x+"_"+y;
        Bitmap mem=memory.get(key);
        if(mem!=null&&!mem.isRecycled())return mem;
        File disk=new File(cacheDir,key+".png");
        if(!disk.exists()||disk.length()<=0)return null;
        Bitmap b=BitmapFactory.decodeFile(disk.getAbsolutePath());
        if(b!=null)memory.put(key,b);
        return b;
    }

    public boolean hasCached(String layer,int z,int x,int y) {
        String key=layer+"_"+z+"_"+x+"_"+y;
        File disk=new File(cacheDir,key+".png");
        return disk.exists()&&disk.length()>0;
    }

    public void prefetch(String layer,int z,int x,int y) {
        if(z<1||z>18)return;
        int n=1<<z;
        x=((x%n)+n)%n;
        if(y<0||y>=n)return;
        String key=layer+"_"+z+"_"+x+"_"+y;
        File disk=new File(cacheDir,key+".png");
        if(disk.exists()&&disk.length()>0)return;
        schedule(layer,z,x,y,key,disk);
    }

    public void close() {
        executor.shutdownNow();
        memory.evictAll();
        inFlight.clear();
    }
}
