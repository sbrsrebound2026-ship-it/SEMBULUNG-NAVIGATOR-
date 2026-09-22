package com.sembulung.navigator.sonar;

import java.io.*;
import java.util.*;
import java.util.Locale;

public final class SonarCsvCodec {
    public static final String HEADER="timestamp,lat,lon,depth_m,speed_kn,course_deg,quality,source";
    private SonarCsvCodec(){}

    public static String encode(DepthSample s){
        return String.format(Locale.US,"%d,%.8f,%.8f,%.3f,%s,%s,%s,%s",
                s.timestamp,s.lat,s.lon,s.depthMeters,
                s.speedKnots==null?"":String.format(Locale.US,"%.3f",s.speedKnots),
                s.courseDeg==null?"":String.format(Locale.US,"%.3f",s.courseDeg),
                s.quality.name(),sanitize(s.source));
    }

    public static DepthSample decode(String line){
        if(line==null)return null;
        String t=line.trim();
        if(t.isEmpty()||t.startsWith("timestamp,"))return null;
        String[] f=t.split(",",-1);
        if(f.length<8)return null;
        try{
            return new DepthSample(
                    Double.parseDouble(f[1]),Double.parseDouble(f[2]),Double.parseDouble(f[3]),
                    Long.parseLong(f[0]),nullable(f[4]),nullable(f[5]),
                    DepthSample.Quality.valueOf(f[6].trim().toUpperCase(Locale.US)),f[7]);
        }catch(Exception e){return null;}
    }

    public static List<DepthSample> readAll(Reader reader,int max) throws IOException{
        ArrayList<DepthSample> out=new ArrayList<>();
        BufferedReader r=reader instanceof BufferedReader?(BufferedReader)reader:new BufferedReader(reader);
        String line;
        while((line=r.readLine())!=null){
            DepthSample s=decode(line);
            if(s!=null)out.add(s);
        }
        if(max>0&&out.size()>max)return new ArrayList<>(out.subList(out.size()-max,out.size()));
        return out;
    }

    public static void writeAll(Writer writer,List<DepthSample> samples) throws IOException{
        BufferedWriter w=writer instanceof BufferedWriter?(BufferedWriter)writer:new BufferedWriter(writer);
        w.write(HEADER);w.write("\n");
        if(samples!=null)for(DepthSample s:samples){if(s!=null){w.write(encode(s));w.write("\n");}}
        w.flush();
    }

    private static Double nullable(String s){
        try{return s==null||s.trim().isEmpty()?null:Double.parseDouble(s.trim());}
        catch(Exception e){return null;}
    }
    private static String sanitize(String s){
        return (s==null?"NMEA":s).replace(',',';').replace('\n',' ').replace('\r',' ');
    }
}
