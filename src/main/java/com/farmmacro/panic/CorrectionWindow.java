package com.farmmacro.panic;

import java.util.ArrayDeque;

/** Main-thread sliding budget. Counts path length, never cancelling opposite corrections. */
final class CorrectionWindow {
    private record Sample(long at,double distance) {}
    private final ArrayDeque<Sample> samples=new ArrayDeque<>();
    void reset(){samples.clear();}
    boolean accept(long now,int seconds,int maxPackets,double budget,double distance){
        while(!samples.isEmpty()&&now-samples.peekFirst().at()>seconds*1_000_000_000L)samples.removeFirst();
        if(!Double.isFinite(distance)||distance<0||!Double.isFinite(budget)||budget<0||seconds<1||maxPackets<1)return false;
        double total=distance;for(var s:samples)total+=s.distance();
        if(samples.size()>=maxPackets||total>budget)return false;
        // Hard resource bound independent of malformed unsanitized configuration.
        if(samples.size()>=12)return false;
        samples.addLast(new Sample(now,distance));return true;
    }
}
