package com.sembulung.navigator.sonar;

import java.util.*;

public final class SonarReplayEngine {
    private final List<DepthSample> samples;
    private int index;

    public SonarReplayEngine(List<DepthSample> samples){
        this.samples=samples==null?Collections.emptyList():new ArrayList<>(samples);
    }

    public boolean hasNext(){return index<samples.size();}
    public DepthSample next(){return hasNext()?samples.get(index++):null;}
    public void reset(){index=0;}
    public int position(){return index;}
    public int size(){return samples.size();}
    public double progress(){return samples.isEmpty()?0.0:(double)index/samples.size();}
}
