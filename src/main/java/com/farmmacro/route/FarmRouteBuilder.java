package com.farmmacro.route;

/** Deterministic editor geometry, never mutates a loaded/saved route or world. */
public final class FarmRouteBuilder {
    private FarmRouteBuilder(){}
    public static PointRoute generate(FarmSchemeSettings template,double x,double y,double z,String world,String dimension){
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException("Некорректные координаты старта");
        var s=template.copy();s.sanitize();
        if("custom".equals(s.mode))throw new IllegalArgumentException("Custom: используй точки редактора");
        PointRoute r=new PointRoute();r.name="farm_"+s.mode;r.world=world;r.dimension=dimension;r.createdAt=System.currentTimeMillis();
        r.settings.farm=s;s.enabled=true;
        // Independent from global new-route defaults: no whole-pass attack on lane transitions.
        r.settings.human.enabled=false;
        r.points.add(new RoutePoint(x,y,z));
        for(int row=0;row<s.rows;row++){
            String k;
            if("sugarcane".equals(s.mode))k=row%2==0?"a":"s";
            else if("mushroom".equals(s.mode))k=row%2==0?"a":"sd";
            else k=row%2==0?s.firstKeys:FarmSchemeSettings.opposite(s.firstKeys);
            if(s.reverse)k=FarmSchemeSettings.mirror(k);
            if(s.holdW&&(k.equals("a")||k.equals("d")))k="w"+k;
            add(r,k,s.rowLength,true);
            if(row+1<s.rows){
                if(s.laneSpacing>0&&(!"mushroom".equals(s.mode)||row%2==0))add(r,s.reverse?FarmSchemeSettings.mirror(s.laneKeys):s.laneKeys,s.laneSpacing,false);
                else r.points.getLast().farmTurn=true;
            }
        }
        r.sanitize();return r;
    }
    private static void add(PointRoute r,String keys,double length,boolean attack){
        RoutePoint a=r.points.getLast();a.farmKeys=keys;a.action=attack?RoutePoint.ATTACK:RoutePoint.NONE;if(!attack)a.farmTurn=true;
        double[] d=FarmSchemeSettings.vector(keys,r.settings.farm.yaw);
        r.points.add(new RoutePoint(a.x+d[0]*length,a.y,a.z+d[1]*length));
    }
}
