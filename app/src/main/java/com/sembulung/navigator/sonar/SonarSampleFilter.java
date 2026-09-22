package com.sembulung.navigator.sonar;

import java.util.*;

public final class SonarSampleFilter {
    public static final String GOOD_ONLY="GOOD_ONLY";
    public static final String GOOD_AND_QUESTIONABLE="GOOD_AND_QUESTIONABLE";
    private SonarSampleFilter(){}

    public static List<DepthSample> apply(List<DepthSample> input,String mode){
        ArrayList<DepthSample> out=new ArrayList<>();
        boolean goodOnly=GOOD_ONLY.equals(mode);
        if(input==null)return out;
        for(DepthSample s:input){
            if(s==null||!s.valid())continue;
            if(goodOnly&&s.quality!=DepthSample.Quality.GOOD)continue;
            out.add(s);
        }
        return out;
    }
}
