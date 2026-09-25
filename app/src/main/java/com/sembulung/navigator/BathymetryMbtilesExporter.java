package com.sembulung.navigator;

import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;

public final class BathymetryMbtilesExporter {
    private static final double EARTH_KM=6371.0088;

    private BathymetryMbtilesExporter(){}

    public static int export(File output, MarineTileLoader loader,
                             double lat0, double lon0, int radiusKm, int zoom) throws Exception {
        if(output==null)throw new IllegalArgumentException("Output file kosong");
        if(radiusKm<=0)throw new IllegalArgumentException("Radius harus lebih besar dari 0");
        if(zoom<1||zoom>18)throw new IllegalArgumentException("Zoom tidak valid");

        File parent=output.getParentFile();
        if(parent!=null&&!parent.exists()&&!parent.mkdirs())throw new IOException("Folder output tidak dapat dibuat");
        if(output.exists()&&!output.delete())throw new IOException("File output tidak dapat ditimpa");

        int n=1<<zoom;
        double latDelta=radiusKm/111.32;
        double lonScale=Math.max(0.1,Math.cos(Math.toRadians(lat0)));
        double lonDelta=radiusKm/(111.32*lonScale);
        double south=Math.max(-85.05112878,lat0-latDelta);
        double north=Math.min(85.05112878,lat0+latDelta);
        double west=norm(lon0-lonDelta),east=norm(lon0+lonDelta);

        int y0=(int)Math.floor(yForLatitude(north,n));
        int y1=(int)Math.floor(yForLatitude(south,n));
        int x0=(int)Math.floor(xForLongitude(west,n));
        int x1=(int)Math.floor(xForLongitude(east,n));

        SQLiteDatabase db=null;
        int inserted=0;
        try{
            db=SQLiteDatabase.openOrCreateDatabase(output,null);
            db.execSQL("CREATE TABLE metadata (name TEXT, value TEXT)");
            db.execSQL("CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB)");
            db.execSQL("CREATE UNIQUE INDEX tile_index ON tiles (zoom_level, tile_column, tile_row)");

            putMetadata(db,"name","SEMBULUNG NAVIGATOR Bathymetry");
            putMetadata(db,"format","png");
            putMetadata(db,"type","overlay");
            putMetadata(db,"version","1");
            putMetadata(db,"minzoom",Integer.toString(zoom));
            putMetadata(db,"maxzoom",Integer.toString(zoom));
            putMetadata(db,"bounds",String.format(Locale.US,"%.6f,%.6f,%.6f,%.6f",west,south,east,north));
            putMetadata(db,"center",String.format(Locale.US,"%.6f,%.6f,%d",lon0,lat0,zoom));
            putMetadata(db,"description","Offline GEBCO/OpenSeaMap bathymetry tiles downloaded by SEMBULUNG NAVIGATOR.");
            putMetadata(db,"attribution","Bathymetry rendered from OpenSeaMap/GEBCO. Not for sole use in navigation or safety decisions.");

            db.beginTransaction();
            for(int y=y0;y<=y1;y++){
                if(y<0||y>=n)continue;
                for(int x=x0;x<=x1;x++){
                    int xx=((x%n)+n)%n;
                    if(!tileIntersects(lat0,lon0,radiusKm,xx,y,n))continue;
                    File tile=loader.cachedTileFile(MarineTileLoader.LAYER_BATHY,zoom,xx,y);
                    if(!tile.exists()||tile.length()==0)continue;
                    byte[] data=read(tile);
                    if(data.length==0)continue;
                    android.content.ContentValues values=new android.content.ContentValues();
                    values.put("zoom_level",zoom);
                    values.put("tile_column",xx);
                    values.put("tile_row",n-1-y);
                    values.put("tile_data",data);
                    db.insertOrThrow("tiles",null,values);
                    inserted++;
                }
            }
            db.setTransactionSuccessful();
        }finally{
            if(db!=null){
                if(db.inTransaction())db.endTransaction();
                db.close();
            }
        }
        if(inserted==0){
            if(output.exists())output.delete();
            throw new IOException("Tidak ada tile bathymetry tersimpan untuk area ini.");
        }
        return inserted;
    }

    private static void putMetadata(SQLiteDatabase db,String name,String value){
        android.content.ContentValues v=new android.content.ContentValues();
        v.put("name",name);v.put("value",value);db.insertOrThrow("metadata",null,v);
    }

    private static byte[] read(File f)throws IOException{
        long len=f.length();
        if(len>Integer.MAX_VALUE)throw new IOException("Tile terlalu besar");
        byte[] data=new byte[(int)len];
        try(FileInputStream in=new FileInputStream(f)){
            int off=0,n;
            while(off<data.length&&(n=in.read(data,off,data.length-off))>0)off+=n;
            if(off!=data.length)throw new IOException("Tile tidak lengkap");
        }
        return data;
    }

    private static boolean tileIntersects(double lat0,double lon0,double radiusKm,int x,int y,int n){
        double west=x/(double)n*360.0-180.0;
        double east=(x+1)/(double)n*360.0-180.0;
        double north=latitudeAt((double)y/n);
        double south=latitudeAt((double)(y+1)/n);
        double testLon=nearestLongitude(lon0,west,east);
        double testLat=Math.max(south,Math.min(lat0,north));
        return haversineKm(lat0,lon0,testLat,testLon)<=radiusKm;
    }

    private static double nearestLongitude(double center,double west,double east){
        double c=center;
        double w=west,e=east;
        while(w-c>180)w-=360;
        while(w-c<-180)w+=360;
        while(e-c>180)e-=360;
        while(e-c<-180)e+=360;
        if(w>e){double t=w;w=e;e=t;}
        return Math.max(w,Math.min(c,e));
    }

    private static double haversineKm(double a1,double o1,double a2,double o2){
        double p1=Math.toRadians(a1),p2=Math.toRadians(a2);
        double dp=Math.toRadians(a2-a1),dl=Math.toRadians(o2-o1);
        double a=Math.sin(dp/2)*Math.sin(dp/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);
        return EARTH_KM*2*Math.atan2(Math.sqrt(a),Math.sqrt(Math.max(0,1-a)));
    }

    private static double xForLongitude(double lon,int n){return (norm(lon)+180.0)/360.0*n;}
    private static double yForLatitude(double lat,int n){
        double r=Math.toRadians(Math.max(-85.05112878,Math.min(85.05112878,lat)));
        return (1.0-Math.log(Math.tan(r)+1.0/Math.cos(r))/Math.PI)/2.0*n;
    }
    private static double latitudeAt(double yNorm){
        double m=Math.PI*(1.0-2.0*yNorm);
        return Math.toDegrees(Math.atan(Math.sinh(m)));
    }
    private static double norm(double lon){
        double v=lon%360.0;
        if(v>180)v-=360;
        if(v<-180)v+=360;
        return v;
    }
}
