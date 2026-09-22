package com.sembulung.navigator.sonar;

import android.content.Context;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SonarChartStore {
    private final File file;

    public SonarChartStore(Context context){
        File dir=new File(context.getFilesDir(),"sonar_chart");
        if(!dir.exists()) dir.mkdirs();
        file=new File(dir,"soundings.csv");
    }

    public synchronized void append(DepthSample s) throws IOException{
        boolean header=!file.exists()||file.length()==0;
        try(BufferedWriter w=new BufferedWriter(new FileWriter(file,true))){
            if(header) w.write("timestamp,lat,lon,depth_m,speed_kn,course_deg,quality,source\n");
            w.write(String.format(Locale.US,"%d,%.8f,%.8f,%.3f,%s,%s,%s,%s\n",
                    s.timestamp,s.lat,s.lon,s.depthMeters,
                    s.speedKnots==null?"":String.format(Locale.US,"%.3f",s.speedKnots),
                    s.courseDeg==null?"":String.format(Locale.US,"%.3f",s.courseDeg),
                    s.quality.name(),sanitize(s.source)));
        }
    }

    public synchronized List<DepthSample> load(int max) {
        ArrayList<DepthSample> out=new ArrayList<>();
        if(!file.exists()) return out;
        try(BufferedReader r=new BufferedReader(new FileReader(file))){
            String line; boolean first=true;
            while((line=r.readLine())!=null){
                if(first){first=false;continue;}
                String[] f=line.split(",",-1);
                if(f.length<8) continue;
                try{
                    out.add(new DepthSample(
                            Double.parseDouble(f[1]),Double.parseDouble(f[2]),Double.parseDouble(f[3]),
                            Long.parseLong(f[0]),nullable(f[4]),nullable(f[5]),
                            DepthSample.Quality.valueOf(f[6]),f[7]));
                }catch(Exception ignored){}
            }
        }catch(Exception ignored){}
        if(max>0&&out.size()>max) return new ArrayList<>(out.subList(out.size()-max,out.size()));
        return out;
    }

    public synchronized boolean clear(){ return !file.exists()||file.delete(); }
    public File file(){ return file; }

    private static Double nullable(String s){ try{return s==null||s.isEmpty()?null:Double.parseDouble(s);}catch(Exception e){return null;} }
    private static String sanitize(String s){ return (s==null?"NMEA":s).replace(',',';').replace('\n',' '); }
}
