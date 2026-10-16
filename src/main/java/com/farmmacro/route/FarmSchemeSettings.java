package com.farmmacro.route;

import java.util.List;

/** Opt-in per-route farming. Missing JSON fields retain ordinary route behaviour. */
public final class FarmSchemeSettings {
    public static final List<String> MODES=List.of("s_shape","sugarcane","mushroom","cocoa","ad","ws","custom");
    public static final List<String> KEYS=List.of("","w","s","a","d","wa","wd","sa","sd");
    public boolean enabled,holdW,reverse,fastSwitch,setspawnOnStart;
    public boolean lockCamera=true;
    public String mode="s_shape",firstKeys="a",laneKeys="w";
    public int rows=4,switchPauseTicks=4;
    public double rowLength=24,laneSpacing=1,reachRadius=.15,boundMargin=.8;
    public double yaw,pitch=3,yawRange,pitchRange,turnSpeed=120;

    public FarmSchemeSettings copy(){
        var s=new FarmSchemeSettings();
        s.enabled=enabled;s.holdW=holdW;s.reverse=reverse;s.fastSwitch=fastSwitch;
        s.setspawnOnStart=setspawnOnStart;s.lockCamera=lockCamera;s.mode=mode;
        s.firstKeys=firstKeys;s.laneKeys=laneKeys;s.rows=rows;s.switchPauseTicks=switchPauseTicks;
        s.rowLength=rowLength;s.laneSpacing=laneSpacing;s.reachRadius=reachRadius;s.boundMargin=boundMargin;
        s.yaw=yaw;s.pitch=pitch;s.yawRange=yawRange;s.pitchRange=pitchRange;s.turnSpeed=turnSpeed;return s;
    }
    public void preset(String key){
        if(key==null||!MODES.contains(key))return;
        mode=key;holdW=false;reverse=false;firstKeys="a";laneKeys="w";laneSpacing=1;
        switch(key){
            case "ad" -> laneSpacing=0;
            case "ws" -> {firstKeys="w";laneKeys="d";laneSpacing=0;}
            case "cocoa" -> {firstKeys="w";laneKeys="d";}
            case "sugarcane" -> {firstKeys="a";laneKeys="w";laneSpacing=0;}
            case "mushroom" -> {firstKeys="a";laneKeys="s";}
            default -> {}
        }
    }
    public void sanitize(){
        if(mode==null||!MODES.contains(mode))mode="s_shape";
        if(firstKeys==null||!KEYS.contains(firstKeys)||firstKeys.isEmpty())firstKeys="a";
        if(laneKeys==null||!KEYS.contains(laneKeys)||laneKeys.isEmpty())laneKeys="w";
        rows=Math.max(1,Math.min(128,rows));switchPauseTicks=Math.max(0,Math.min(200,switchPauseTicks));
        rowLength=clamp(rowLength,1,256,24);laneSpacing=clamp(laneSpacing,0,16,1);
        reachRadius=clamp(reachRadius,.05,.45,.15);boundMargin=clamp(boundMargin,.35,4,.8);
        yaw=clamp(yaw,-180,180,0);pitch=clamp(pitch,-89,89,3);
        yawRange=clamp(yawRange,0,2,0);pitchRange=clamp(pitchRange,0,10,0);
        turnSpeed=clamp(turnSpeed,20,360,120);
    }
    private static double clamp(double v,double min,double max,double fallback){return Double.isFinite(v)?Math.max(min,Math.min(max,v)):fallback;}
    public static String opposite(String k){return k.replace('w','W').replace('s','w').replace('W','s').replace('a','A').replace('d','a').replace('A','d');}
    public static String mirror(String k){return k.replace('a','A').replace('d','a').replace('A','d');}
    /** Minecraft yaw: forward (-sin,cos), LEFT (+cos,+sin); not snapped to block axes. */
    public static double[] vector(String k,double yaw){
        double f=(k.contains("w")?1:0)-(k.contains("s")?1:0),l=(k.contains("a")?1:0)-(k.contains("d")?1:0);
        double a=Math.toRadians(yaw),x=-Math.sin(a)*f+Math.cos(a)*l,z=Math.cos(a)*f+Math.sin(a)*l,n=Math.hypot(x,z);
        return n<1e-9?new double[]{0,0}:new double[]{x/n,z/n};
    }
}
