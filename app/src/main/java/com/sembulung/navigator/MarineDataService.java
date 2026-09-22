package com.sembulung.navigator;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import com.sembulung.navigator.ais.AisCollisionEngine;
import com.sembulung.navigator.ais.AisParser;
import com.sembulung.navigator.ais.AisTarget;
import com.sembulung.navigator.ais.AisTargetStore;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MarineDataService extends Service {
    public static final String ACTION_START="com.sembulung.navigator.START_MARINE_DATA";
    public static final String ACTION_STOP="com.sembulung.navigator.STOP_MARINE_DATA";
    public static final String EXTRA_PORT="port";
    public static final int DEFAULT_PORT=10110;

    private static final String CHANNEL_SERVICE="marine_data_service";
    private static final String CHANNEL_ALERT="ais_collision_alert";
    private static final int NOTIF_SERVICE=1710;
    private static final int NOTIF_ALERT_BASE=1800;

    private volatile boolean running;
    private DatagramSocket socket;
    private Thread receiverThread;
    private int activePort=DEFAULT_PORT;
    private long received,valid,rejected,aisDecoded;
    private final NmeaSentenceParser.State nmeaState=new NmeaSentenceParser.State();
    private final Map<Long,Long> alertTimes=new ConcurrentHashMap<>();

    public static void start(Context c,int port){
        Intent i=new Intent(c,MarineDataService.class).setAction(ACTION_START).putExtra(EXTRA_PORT,port);
        if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);
    }

    public static void stop(Context c){
        c.startService(new Intent(c,MarineDataService.class).setAction(ACTION_STOP));
    }

    @Override public void onCreate(){
        super.onCreate();
        createChannels();
        seedNmeaState();
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?ACTION_START:intent.getAction();
        if(ACTION_STOP.equals(action)){
            stopReceiver("Marine Data Service dihentikan");
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        int port=intent==null?DEFAULT_PORT:intent.getIntExtra(EXTRA_PORT,DEFAULT_PORT);
        if(port<1||port>65535)port=DEFAULT_PORT;
        startForeground(NOTIF_SERVICE,serviceNotification(port,"Menyiapkan listener UDP..."));

        if(running&&activePort==port)return START_STICKY;
        stopReceiver(null);
        activePort=port;
        running=true;
        MarineServiceState.started(this,port);
        startReceiver(port);
        return START_STICKY;
    }

    private void startReceiver(final int port){
        receiverThread=new Thread(()->{
            try{
                DatagramSocket s=new DatagramSocket(null);
                s.setReuseAddress(true);
                s.bind(new InetSocketAddress(port));
                s.setSoTimeout(1000);
                socket=s;
                updateServiceNotification("UDP "+port+" • NMEA/AIS aktif");

                byte[] buffer=new byte[8192];
                while(running){
                    try{
                        DatagramPacket packet=new DatagramPacket(buffer,buffer.length);
                        s.receive(packet);
                        String raw=new String(packet.getData(),packet.getOffset(),packet.getLength(), StandardCharsets.US_ASCII);
                        String source=packet.getAddress().getHostAddress()+":"+packet.getPort();
                        for(String line:raw.split("[\\r\\n]+")){
                            String sentence=line.trim();
                            if(!sentence.isEmpty())processLine(sentence,source);
                        }
                    }catch(SocketTimeoutException ignored){}
                }
            }catch(Exception e){
                if(running){
                    MarineServiceState.stopped(this,"Listener gagal: "+e.getMessage());
                    updateServiceNotification("Listener gagal • periksa port/jaringan");
                }
            }finally{
                closeSocket();
                running=false;
            }
        },"Sembulung-Marine-UDP");
        receiverThread.start();
    }

    private void processLine(String sentence,String source){
        received++;
        boolean ok=false;

        if(sentence.startsWith("!AIVDM")||sentence.startsWith("!AIVDO")){
            AisTarget target=AisParser.parse(sentence);
            if(target!=null&&target.hasValidPosition()){
                AisTargetStore.upsert(this,target);
                aisDecoded++;
                valid++;
                ok=true;
                assessCollision(target);
            }
        }else{
            long now=System.currentTimeMillis();
            if(NmeaSentenceParser.apply(sentence,nmeaState,now)){
                valid++;
                ok=true;
                NmeaDataStore.write(this,nmeaState.lat,nmeaState.lon,nmeaState.heading,nmeaState.depth,nmeaState.speed,
                        nmeaState.positionTime,nmeaState.headingTime,nmeaState.depthTime,source);
            }
        }

        if(!ok)rejected++;
        MarineServiceState.update(this,received,valid,rejected,aisDecoded,sentence,source);
    }

    private void assessCollision(AisTarget target){
        NmeaDataStore.Snapshot own=NmeaDataStore.read(this);
        if(own==null||!own.positionFresh(15000L)||own.speed==null||own.heading==null)return;
        AisCollisionEngine.Assessment a=AisCollisionEngine.assess(
                own.lat,own.lon,own.speed,own.heading,target);
        if(a.risk!=AisCollisionEngine.Risk.DANGER&&a.risk!=AisCollisionEngine.Risk.WARNING)return;

        long now=System.currentTimeMillis();
        long cooldown=a.risk==AisCollisionEngine.Risk.DANGER?60000L:120000L;
        Long last=alertTimes.get(target.mmsi);
        if(last!=null&&now-last<cooldown)return;
        alertTimes.put(target.mmsi,now);

        String cpa=Double.isNaN(a.cpaNm)?"---":String.format(Locale.US,"%.2f NM",a.cpaNm);
        String tcpa=Double.isNaN(a.tcpaMinutes)?"---":String.format(Locale.US,"%.0f min",a.tcpaMinutes);
        Notification n=new Notification.Builder(this,CHANNEL_ALERT)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("AIS "+a.risk.name()+" • MMSI "+String.format(Locale.US,"%09d",target.mmsi))
                .setContentText("CPA "+cpa+" • TCPA "+tcpa)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .build();
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
                .notify(NOTIF_ALERT_BASE+(int)(target.mmsi%700),n);
    }

    private void seedNmeaState(){
        NmeaDataStore.Snapshot s=NmeaDataStore.read(this);
        if(s==null)return;
        nmeaState.lat=s.lat;nmeaState.lon=s.lon;nmeaState.heading=s.heading;nmeaState.depth=s.depth;nmeaState.speed=s.speed;
        nmeaState.positionTime=s.positionTime;nmeaState.headingTime=s.headingTime;nmeaState.depthTime=s.depthTime;
    }

    private Notification serviceNotification(int port,String text){
        Intent open=new Intent(this,MarineMapActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL_SERVICE)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentTitle("SEMBULUNG NAVIGATOR • Marine Data")
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(pi)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void updateServiceNotification(String text){
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
                .notify(NOTIF_SERVICE,serviceNotification(activePort,text));
    }

    private void createChannels(){
        if(Build.VERSION.SDK_INT<26)return;
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel service=new NotificationChannel(CHANNEL_SERVICE,"Marine Data Service",NotificationManager.IMPORTANCE_LOW);
        service.setDescription("Koneksi NMEA, sonar dan AIS latar belakang");
        nm.createNotificationChannel(service);
        NotificationChannel alert=new NotificationChannel(CHANNEL_ALERT,"AIS Collision Alerts",NotificationManager.IMPORTANCE_HIGH);
        alert.setDescription("Peringatan CPA/TCPA AIS");
        nm.createNotificationChannel(alert);
    }

    private void stopReceiver(String status){
        running=false;
        closeSocket();
        if(receiverThread!=null)receiverThread.interrupt();
        receiverThread=null;
        if(status!=null)MarineServiceState.stopped(this,status);
    }

    private void closeSocket(){
        DatagramSocket s=socket;socket=null;
        if(s!=null)try{s.close();}catch(Exception ignored){}
    }

    @Override public void onDestroy(){
        stopReceiver("Marine Data Service berhenti");
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent){return null;}
}
