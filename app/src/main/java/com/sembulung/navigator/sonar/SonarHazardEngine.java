package com.sembulung.navigator.sonar;

public final class SonarHazardEngine {
    private static final double M_PER_DEG_LAT=110540.0;
    private static final double M_PER_DEG_LON=111320.0;
    private SonarHazardEngine(){}

    public enum Risk { DANGER, WARNING, CLEAR, UNKNOWN }

    public static final class Assessment {
        public final Risk risk;
        public final double minimumDepthMeters;
        public final double distanceAheadMeters;
        public final int inspectedCells;

        Assessment(Risk risk,double minimumDepthMeters,double distanceAheadMeters,int inspectedCells){
            this.risk=risk;
            this.minimumDepthMeters=minimumDepthMeters;
            this.distanceAheadMeters=distanceAheadMeters;
            this.inspectedCells=inspectedCells;
        }
    }

    public static Assessment assessAhead(
            SonarChartEngine.Chart chart,double ownLat,double ownLon,
            Double courseDeg,Double speedKnots,double safetyDepthMeters,
            double lookAheadMinutes,double corridorMeters){

        if(chart==null||chart.cells==null||chart.cells.isEmpty()||courseDeg==null||!Double.isFinite(courseDeg)){
            return new Assessment(Risk.UNKNOWN,Double.NaN,Double.NaN,0);
        }

        double speed=speedKnots==null||!Double.isFinite(speedKnots)?0.0:Math.max(0.0,speedKnots);
        double lookAhead=Math.max(250.0,speed*1852.0*Math.max(1.0,lookAheadMinutes)/60.0);
        double corridor=Math.max(25.0,corridorMeters);
        double course=Math.toRadians(courseDeg);
        double forwardEast=Math.sin(course),forwardNorth=Math.cos(course);
        double rightEast=Math.cos(course),rightNorth=-Math.sin(course);
        double cos=Math.max(0.15,Math.cos(Math.toRadians(ownLat)));

        double minDepth=Double.POSITIVE_INFINITY;
        double minAhead=Double.NaN;
        int inspected=0;

        for(SonarChartEngine.Cell cell:chart.cells){
            double lat=(cell.south+cell.north)*0.5;
            double lon=(cell.west+cell.east)*0.5;
            double east=(lon-ownLon)*M_PER_DEG_LON*cos;
            double north=(lat-ownLat)*M_PER_DEG_LAT;
            double along=east*forwardEast+north*forwardNorth;
            if(along<0||along>lookAhead)continue;
            double cross=Math.abs(east*rightEast+north*rightNorth);
            double allowance=chart.stats==null?0.0:chart.stats.cellMeters*0.75;
            if(cross>corridor+allowance)continue;
            inspected++;
            if(cell.depth<minDepth){
                minDepth=cell.depth;
                minAhead=along;
            }
        }

        if(inspected==0||!Double.isFinite(minDepth)){
            return new Assessment(Risk.UNKNOWN,Double.NaN,Double.NaN,0);
        }

        double safe=Math.max(0.1,safetyDepthMeters);
        Risk risk=minDepth<safe*0.7?Risk.DANGER:minDepth<safe?Risk.WARNING:Risk.CLEAR;
        return new Assessment(risk,minDepth,minAhead,inspected);
    }
}
