package com.sembulung.navigator;

import java.util.Locale;

public final class NmeaSentenceParser {
    private NmeaSentenceParser(){}

    public static final class State {
        public Double lat,lon,heading,depth,speed,waterTemp;
        public Integer satellites;
        public long positionTime,headingTime,depthTime;

        public State copy(){
            State s=new State();
            s.lat=lat;s.lon=lon;s.heading=heading;s.depth=depth;s.speed=speed;
            s.waterTemp=waterTemp;s.satellites=satellites;
            s.positionTime=positionTime;s.headingTime=headingTime;s.depthTime=depthTime;
            return s;
        }
    }

    public static boolean apply(String raw,State state,long now){
        if(raw==null||state==null)return false;
        String sentence=raw.trim();
        if(sentence.isEmpty()||!checksumValidIfPresent(sentence))return false;
        try{
            int star=sentence.indexOf('*');
            String body=star>=0?sentence.substring(0,star):sentence;
            if(body.startsWith("$")||body.startsWith("!"))body=body.substring(1);
            String[] f=body.split(",",-1);
            if(f.length==0||f[0].length()<3)return false;
            String type=f[0].substring(f[0].length()-3).toUpperCase(Locale.US);

            switch(type){
                case "RMC":
                    if(f.length>8&&("A".equalsIgnoreCase(f[2])||f[2].isEmpty())){
                        Double lat=parseLatLon(f[3],f[4],true);
                        Double lon=parseLatLon(f[5],f[6],false);
                        if(lat!=null&&lon!=null){state.lat=lat;state.lon=lon;state.positionTime=now;}
                        Double sp=num(f[7]);if(sp!=null)state.speed=sp;
                        Double crs=num(f[8]);if(crs!=null){state.heading=normalizeHeading(crs);state.headingTime=now;}
                    }
                    return true;
                case "GGA":
                    if(f.length>9){
                        Double lat=parseLatLon(f[2],f[3],true);
                        Double lon=parseLatLon(f[4],f[5],false);
                        if(lat!=null&&lon!=null){state.lat=lat;state.lon=lon;state.positionTime=now;}
                        Integer sat=integer(f[7]);if(sat!=null)state.satellites=sat;
                    }
                    return true;
                case "GLL":
                    if(f.length>4){
                        Double lat=parseLatLon(f[1],f[2],true);
                        Double lon=parseLatLon(f[3],f[4],false);
                        if(lat!=null&&lon!=null){state.lat=lat;state.lon=lon;state.positionTime=now;}
                    }
                    return true;
                case "VTG":
                case "VHW":
                    if(f.length>5){
                        Double h=num(f[1]);if(h!=null){state.heading=normalizeHeading(h);state.headingTime=now;}
                        Double sp=num(f[5]);if(sp!=null)state.speed=sp;
                    }
                    return true;
                case "HDG":
                case "HDT":
                    if(f.length>1){
                        Double h=num(f[1]);if(h!=null){state.heading=normalizeHeading(h);state.headingTime=now;}
                    }
                    return true;
                case "DPT":
                    if(f.length>1){
                        Double d=num(f[1]);if(d!=null&&d>=0){state.depth=d;state.depthTime=now;}
                    }
                    return true;
                case "DBT":
                    if(f.length>3){
                        Double d=num(f[3]);if(d!=null&&d>=0){state.depth=d;state.depthTime=now;}
                    }
                    return true;
                case "MTW":
                    if(f.length>1){Double t=num(f[1]);if(t!=null)state.waterTemp=t;}
                    return true;
                default:
                    return false;
            }
        }catch(Exception e){return false;}
    }

    public static boolean checksumValidIfPresent(String sentence){
        int star=sentence.indexOf('*');
        if(star<0)return true;
        if(star+2>=sentence.length())return false;
        int start=(sentence.startsWith("$")||sentence.startsWith("!"))?1:0;
        int checksum=0;
        for(int i=start;i<star;i++)checksum^=sentence.charAt(i);
        try{return checksum==Integer.parseInt(sentence.substring(star+1,star+3),16);}
        catch(Exception e){return false;}
    }

    static Double parseLatLon(String raw,String hemi,boolean latitudeMode){
        if(raw==null||raw.isEmpty())return null;
        try{
            int degDigits=latitudeMode?2:3;
            if(raw.length()<=degDigits)return null;
            double deg=Double.parseDouble(raw.substring(0,degDigits));
            double min=Double.parseDouble(raw.substring(degDigits));
            double value=deg+min/60.0;
            if("S".equalsIgnoreCase(hemi)||"W".equalsIgnoreCase(hemi))value=-value;
            double max=latitudeMode?90.0:180.0;
            return Math.abs(value)<=max?value:null;
        }catch(Exception e){return null;}
    }

    private static Double num(String s){
        try{return s==null||s.trim().isEmpty()?null:Double.parseDouble(s.trim());}
        catch(Exception e){return null;}
    }
    private static Integer integer(String s){
        try{return s==null||s.trim().isEmpty()?null:Integer.parseInt(s.trim());}
        catch(Exception e){return null;}
    }
    private static double normalizeHeading(double h){
        double r=h%360.0;return r<0?r+360.0:r;
    }
}
