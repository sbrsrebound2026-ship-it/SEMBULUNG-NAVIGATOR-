package com.sembulung.navigator;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CoordinateParser {
    private CoordinateParser(){}

    public static final class Point {
        public final double lat,lon;
        public Point(double lat,double lon){this.lat=lat;this.lon=lon;}
        public String dd(){return String.format(Locale.US,"%.6f, %.6f",lat,lon);}
    }

    public static Point parsePair(String raw){
        if(raw==null) return null;
        String s=raw.trim();
        Point d=parseDecimalPair(s);
        if(d!=null)return d;
        return parseDmsPair(s);
    }

    public static Point parseDecimalPair(String s){
        Matcher m=Pattern.compile("(-?\\d{1,2}(?:\\.\\d+)?)\\s*[,; ]+\\s*(-?\\d{1,3}(?:\\.\\d+)?)").matcher(s);
        if(!m.find())return null;
        try{
            double lat=Double.parseDouble(m.group(1)),lon=Double.parseDouble(m.group(2));
            return valid(lat,lon)?new Point(lat,lon):null;
        }catch(Exception e){return null;}
    }

    public static Point parseDmsPair(String s){
        Pattern p=Pattern.compile(
                "(\\d{1,2})[^0-9]+(\\d{1,2})[^0-9]+(\\d{1,2}(?:\\.\\d+)?)\\s*([NS])[^0-9]+"+
                "(\\d{1,3})[^0-9]+(\\d{1,2})[^0-9]+(\\d{1,2}(?:\\.\\d+)?)\\s*([EW])",
                Pattern.CASE_INSENSITIVE);
        Matcher m=p.matcher(s);
        if(!m.find())return null;
        try{
            double lat=dms(m.group(1),m.group(2),m.group(3),m.group(4));
            double lon=dms(m.group(5),m.group(6),m.group(7),m.group(8));
            return valid(lat,lon)?new Point(lat,lon):null;
        }catch(Exception e){return null;}
    }

    private static double dms(String d,String m,String s,String h){
        double v=Double.parseDouble(d)+Double.parseDouble(m)/60.0+Double.parseDouble(s)/3600.0;
        if("S".equalsIgnoreCase(h)||"W".equalsIgnoreCase(h))v=-v;
        return v;
    }

    public static boolean valid(double lat,double lon){
        return Double.isFinite(lat)&&Double.isFinite(lon)&&lat>=-90&&lat<=90&&lon>=-180&&lon<=180;
    }
}
