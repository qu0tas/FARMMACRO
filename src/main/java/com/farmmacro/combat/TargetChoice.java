package com.farmmacro.combat;
/** Relative acquisition cost, not a path length or weapon range. Lower is better.
 * Camera direction dominates tiny distance differences; zealots receive a bounded preference.
 * Existing living target locks are deliberately outside this scoring function. */
public final class TargetChoice {
    public static double score(double distance,double yawError,double pitchError,boolean preferred){
        if(!Double.isFinite(distance)||!Double.isFinite(yawError)||!Double.isFinite(pitchError)||distance<0)return Double.POSITIVE_INFINITY;
        double yaw=Math.min(180,Math.abs(yawError)),pitch=Math.min(180,Math.abs(pitchError));
        // Smooth angular cost, with an extra penalty beyond the forward hemisphere.
        double cost=distance+yaw*.12+40*Math.pow(yaw/120,2)+Math.max(0,yaw-100)*.45+pitch*.12;
        // No absolute class rank: a distant/rear zealot cannot always override a convenient target.
        return cost*(preferred?.85:1);
    }
    /** Lower bands win before score: exhaust a local forward group before a big turn. */
    public static int chainBand(double fromPrevious,double fromPreviousYaw,double currentYaw,double localRadius){
        if(fromPrevious<=localRadius&&fromPreviousYaw<=55&&currentYaw<=80)return 0;
        if(fromPrevious<=localRadius&&fromPreviousYaw<=85&&currentYaw<=110)return 1;
        if(fromPreviousYaw<=100&&currentYaw<=110)return 2;
        return currentYaw<=110?3:4;
    }
    public static double chainScore(double fromPrevious,double baseCost,double fromPreviousYaw){
        return 4*fromPrevious+.25*baseCost+.15*Math.abs(fromPreviousYaw);
    }
    /** Stable final tie-break, independent of the world's entity iteration order. */
    public static boolean better(int band,double score,double distance,int id,
                                 int bestBand,double bestScore,double bestDistance,int bestId){
        if(!Double.isFinite(score)||!Double.isFinite(distance))return false;
        if(band!=bestBand)return band<bestBand;
        // Compare fixed buckets, not pairwise epsilon: the latter is non-transitive for triples.
        int cost=Double.compare(Math.rint(score*1e6),Math.rint(bestScore*1e6));
        if(cost!=0)return cost<0;
        int length=Double.compare(Math.rint(distance*1e6),Math.rint(bestDistance*1e6));
        if(length!=0)return length<0;
        return id<bestId;
    }
    /** A short local pending window may beat an otherwise immediate far/rear fallback. */
    public static boolean deferFallback(boolean chained,int bestBand,int pendingBand,long now,long until){
        return chained&&now<until&&pendingBand<=1&&bestBand>=3;
    }
    private TargetChoice(){}
}
