package com.sembulung.navigator;

import android.content.Context;
import com.sembulung.navigator.sonar.SonarChartStore;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;

public final class SurveyBackupManager {
    private SurveyBackupManager(){}

    public static void exportZip(Context c,OutputStream out) throws IOException{
        ZipOutputStream z=new ZipOutputStream(out);
        SonarChartStore store=new SonarChartStore(c);
        putFile(z,"sonar/soundings.csv",store.file());

        String waypoint=c.getSharedPreferences("sembulung_waypoints",Context.MODE_PRIVATE).getString("waypoints_json","[]");
        putText(z,"data/waypoints.json",waypoint);

        AppSettingsSnapshot settings=AppSettingsSnapshot.from(c);
        putText(z,"data/settings.txt",settings.asText());

        MarineServiceState.Snapshot m=MarineServiceState.read(c);
        putText(z,"diagnostic/marine_service.txt",
                "running="+m.running+"\nport="+m.port+"\nreceived="+m.received+"\nvalid="+m.valid+"\nrejected="+m.rejected+"\naisDecoded="+m.aisDecoded+"\nstatus="+m.status+"\n");
        z.finish();z.flush();
    }

    private static void putFile(ZipOutputStream z,String name,File f) throws IOException{
        if(f==null||!f.exists())return;
        z.putNextEntry(new ZipEntry(name));
        try(InputStream in=new FileInputStream(f)){
            byte[] b=new byte[8192];int n;while((n=in.read(b))>0)z.write(b,0,n);
        }
        z.closeEntry();
    }
    private static void putText(ZipOutputStream z,String name,String s) throws IOException{
        z.putNextEntry(new ZipEntry(name));
        z.write((s==null?"":s).getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }

    private static final class AppSettingsSnapshot{
        final boolean keep,center,sonar,ais,shallow,autoAdvance,shallowAhead;
        final double shallowM,arrival,offRoute,lookAhead,contour;
        final String distance,depth,quality;
        AppSettingsSnapshot(boolean keep,boolean center,boolean sonar,boolean ais,boolean shallow,boolean autoAdvance,boolean shallowAhead,
                            double shallowM,double arrival,double offRoute,double lookAhead,double contour,String distance,String depth,String quality){
            this.keep=keep;this.center=center;this.sonar=sonar;this.ais=ais;this.shallow=shallow;this.autoAdvance=autoAdvance;this.shallowAhead=shallowAhead;
            this.shallowM=shallowM;this.arrival=arrival;this.offRoute=offRoute;this.lookAhead=lookAhead;this.contour=contour;
            this.distance=distance;this.depth=depth;this.quality=quality;
        }
        static AppSettingsSnapshot from(Context c){return new AppSettingsSnapshot(
                AppSettings.keepScreenOn(c),AppSettings.autoCenter(c),AppSettings.sonarEnabled(c),AppSettings.aisEnabled(c),
                AppSettings.shallowWarning(c),AppSettings.autoAdvanceRoute(c),AppSettings.shallowAheadWarning(c),
                AppSettings.shallowMeters(c),AppSettings.arrivalRadiusNm(c),AppSettings.offRouteNm(c),AppSettings.lookAheadMinutes(c),
                AppSettings.contourIntervalMeters(c),AppSettings.distanceUnit(c),AppSettings.depthUnit(c),AppSettings.sonarQualityMode(c));}
        String asText(){return "keepScreenOn="+keep+"\nautoCenter="+center+"\nsonar="+sonar+"\nais="+ais+"\nshallowWarning="+shallow+
                "\nshallowMeters="+shallowM+"\nautoAdvance="+autoAdvance+"\narrivalRadiusNm="+arrival+"\noffRouteNm="+offRoute+
                "\nshallowAhead="+shallowAhead+"\nlookAheadMinutes="+lookAhead+"\ncontourIntervalMeters="+contour+
                "\ndistanceUnit="+distance+"\ndepthUnit="+depth+"\nquality="+quality+"\n";}
    }
}
