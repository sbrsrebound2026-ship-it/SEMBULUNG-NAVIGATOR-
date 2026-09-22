package com.sembulung.navigator.sonar;

import android.content.Context;
import java.io.*;
import java.util.*;

public final class SonarChartStore {
    private final File file;

    public SonarChartStore(Context context){
        File dir=new File(context.getFilesDir(),"sonar_chart");
        if(!dir.exists())dir.mkdirs();
        file=new File(dir,"soundings.csv");
    }

    public synchronized void append(DepthSample s) throws IOException{
        boolean header=!file.exists()||file.length()==0;
        try(BufferedWriter w=new BufferedWriter(new FileWriter(file,true))){
            if(header){w.write(SonarCsvCodec.HEADER);w.write("\n");}
            w.write(SonarCsvCodec.encode(s));w.write("\n");
        }
    }

    public synchronized List<DepthSample> load(int max){
        if(!file.exists())return new ArrayList<>();
        try(FileReader r=new FileReader(file)){return SonarCsvCodec.readAll(r,max);}
        catch(Exception e){return new ArrayList<>();}
    }

    public synchronized int importStream(InputStream in,boolean append) throws IOException{
        List<DepthSample> incoming=SonarCsvCodec.readAll(new InputStreamReader(in,"UTF-8"),0);
        List<DepthSample> all=append?load(0):new ArrayList<>();
        all.addAll(incoming);
        replaceAll(all);
        return incoming.size();
    }

    public synchronized void exportStream(OutputStream out) throws IOException{
        SonarCsvCodec.writeAll(new OutputStreamWriter(out,"UTF-8"),load(0));
    }

    public synchronized void replaceAll(List<DepthSample> samples) throws IOException{
        File tmp=new File(file.getParentFile(),"soundings.tmp");
        try(FileWriter w=new FileWriter(tmp)){SonarCsvCodec.writeAll(w,samples);}
        if(file.exists()&&!file.delete())throw new IOException("Tidak dapat mengganti sounding lama");
        if(!tmp.renameTo(file))throw new IOException("Tidak dapat menyimpan sounding baru");
    }

    public synchronized boolean clear(){return !file.exists()||file.delete();}
    public File file(){return file;}
}
