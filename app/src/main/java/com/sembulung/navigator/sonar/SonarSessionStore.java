package com.sembulung.navigator.sonar;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class SonarSessionStore {
    private static final String PREFS="sonar_session_state";
    private static final String ACTIVE="active_file";
    private final Context context;
    private final File dir;

    public SonarSessionStore(Context context){
        this.context=context.getApplicationContext();
        dir=new File(context.getFilesDir(),"sonar_sessions");
        if(!dir.exists())dir.mkdirs();
    }

    public synchronized File start(String label) throws IOException{
        String ts=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());
        String safe=(label==null||label.trim().isEmpty())?"survey":label.trim().replaceAll("[^A-Za-z0-9_-]+","_");
        File f=new File(dir,ts+"_"+safe+".csv");
        try(BufferedWriter w=new BufferedWriter(new FileWriter(f))){w.write(SonarCsvCodec.HEADER);w.write("\n");}
        prefs().edit().putString(ACTIVE,f.getName()).apply();
        return f;
    }

    public synchronized void stop(){prefs().edit().remove(ACTIVE).apply();}
    public synchronized boolean isActive(){return activeFile()!=null;}
    public synchronized File activeFile(){
        String name=prefs().getString(ACTIVE,null);
        if(name==null)return null;
        File f=new File(dir,name);
        return f.exists()?f:null;
    }

    public synchronized void append(DepthSample s) throws IOException{
        File f=activeFile();
        if(f==null)return;
        try(BufferedWriter w=new BufferedWriter(new FileWriter(f,true))){
            w.write(SonarCsvCodec.encode(s));w.write("\n");
        }
    }

    public synchronized List<File> list(){
        File[] fs=dir.listFiles((d,n)->n.endsWith(".csv"));
        if(fs==null)return new ArrayList<>();
        Arrays.sort(fs,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        return new ArrayList<>(Arrays.asList(fs));
    }

    public synchronized List<DepthSample> load(File f,int max){
        if(f==null||!f.exists())return new ArrayList<>();
        try(FileReader r=new FileReader(f)){return SonarCsvCodec.readAll(r,max);}
        catch(Exception e){return new ArrayList<>();}
    }

    private SharedPreferences prefs(){return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
}
