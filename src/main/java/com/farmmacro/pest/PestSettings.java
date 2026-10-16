package com.farmmacro.pest;
import java.util.*;
/** Additional opt-ins; legacy pestHuntEnabled/pestPlotSearch remain the canonical migrated toggles. */
public final class PestSettings {
    public PestAssistSettings assist=new PestAssistSettings();
    public boolean autoStart,resumeFarm,tracker,drawArc,esp,timeWindow,plotWarp,aotv,etherwarp,shredder,breakLeaves;
    public boolean skipFever=true,afterRewarp=true;
    public int threshold=4,startMinTicks=30,startMaxTicks=70,startMinute=360,endMinute=1080;
    public int targetSeconds=30,searchSeconds=60,sessionSeconds=360,noDamageSeconds=12,acquireTicks=4,maxTargets=32,trackerAttempts=3;
    public double fov=240,aimSpeed=150,leadTicks=1,rangeMargin=.5;
    public String leavePlots="",shredderPlots="";
    public int shredderWarps=2,shredderHoldTicks=40;
    public List<Landing> landings=new ArrayList<>();
    public static final class Landing {
        public String world,dimension;
        public int plot;public double x,y,z;public float yaw,pitch;
        public boolean valid(){return plot>=0&&plot<=24&&plot==PestPlots.plotAt(x,z)&&world!=null&&dimension!=null&&Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)&&Math.abs(x)<1024&&Math.abs(z)<1024&&y>-64&&y<320&&Float.isFinite(yaw)&&Float.isFinite(pitch)&&Math.abs(pitch)<=90;}
    }
    public void sanitize(){
        if(assist==null)assist=new PestAssistSettings();assist.sanitize();
        threshold=cap(threshold,1,80);startMinTicks=cap(startMinTicks,0,1200);startMaxTicks=cap(startMaxTicks,startMinTicks,1200);
        startMinute=cap(startMinute,0,1439);endMinute=cap(endMinute,0,1439);
        targetSeconds=cap(targetSeconds,5,120);searchSeconds=cap(searchSeconds,10,180);sessionSeconds=cap(sessionSeconds,30,900);
        noDamageSeconds=cap(noDamageSeconds,3,60);acquireTicks=cap(acquireTicks,0,40);maxTargets=cap(maxTargets,1,128);trackerAttempts=cap(trackerAttempts,0,8);
        fov=finite(fov,30,360,240);aimSpeed=finite(aimSpeed,20,360,150);leadTicks=finite(leadTicks,0,3,1);rangeMargin=finite(rangeMargin,.15,2,.5);
        leavePlots=cleanPlots(leavePlots);shredderPlots=cleanPlots(shredderPlots);shredderWarps=cap(shredderWarps,1,4);shredderHoldTicks=cap(shredderHoldTicks,5,100);
        if(landings==null)landings=new ArrayList<>();landings.removeIf(l->l==null||!l.valid());
        var seen=new HashSet<Integer>();landings.removeIf(l->!seen.add(l.plot));if(landings.size()>25)landings=new ArrayList<>(landings.subList(0,25));
    }
    public static Set<Integer> plots(String text){
        Set<Integer> ids=new TreeSet<>();if(text!=null)for(String s:text.split("[,; ]+"))try{int n=Integer.parseInt(s);if(n>=0&&n<=24)ids.add(n);}catch(NumberFormatException ignored){}return ids;
    }
    private static String cleanPlots(String s){return plots(s).stream().map(Object::toString).collect(java.util.stream.Collectors.joining(","));}
    private static int cap(int v,int a,int b){return Math.max(a,Math.min(b,v));}
    private static double finite(double v,double a,double b,double d){return Double.isFinite(v)?Math.max(a,Math.min(b,v)):d;}
    public Landing landing(int id){return landings.stream().filter(l->l.plot==id).findFirst().orElse(null);}
}
