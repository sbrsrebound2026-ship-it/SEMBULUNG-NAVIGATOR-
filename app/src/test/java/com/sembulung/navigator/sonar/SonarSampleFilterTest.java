package com.sembulung.navigator.sonar;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class SonarSampleFilterTest {
    @Test public void goodOnlyDropsQuestionableAndRejected(){
        List<DepthSample> all=Arrays.asList(
                s(DepthSample.Quality.GOOD),s(DepthSample.Quality.QUESTIONABLE),s(DepthSample.Quality.REJECTED));
        assertEquals(1,SonarSampleFilter.apply(all,SonarSampleFilter.GOOD_ONLY).size());
    }

    @Test public void defaultKeepsGoodAndQuestionable(){
        List<DepthSample> all=Arrays.asList(
                s(DepthSample.Quality.GOOD),s(DepthSample.Quality.QUESTIONABLE),s(DepthSample.Quality.REJECTED));
        assertEquals(2,SonarSampleFilter.apply(all,SonarSampleFilter.GOOD_AND_QUESTIONABLE).size());
    }

    private DepthSample s(DepthSample.Quality q){
        return new DepthSample(-8,114,10,System.currentTimeMillis(),null,null,q,"test");
    }
}
