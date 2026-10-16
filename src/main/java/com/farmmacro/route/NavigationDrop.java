package com.farmmacro.route;

/** Shared planned drop envelope; no unsupported air strafe, no Shift edge lock or physics edits. */
public final class NavigationDrop {
    private int ticks,phase=-1;
    public String reason="";
    public void reset(){ticks=0;phase=-1;reason="";}
    public boolean tick(PathFinder.World w,double x,double y,double z,RoutePoint landing,float yaw,int stage,WalkCore.Out out){
        if(stage==WalkCore.D_NONE){reset();return true;}
        if(stage!=phase){phase=stage;ticks=0;}
        if(++ticks>80){reason="спуск: фаза не завершилась за 4 секунды";return false;}
        out.sneak=out.sprint=out.jump=false;
        if(stage==WalkCore.D_EDGE){
            if(!clear(w,x,y,z,landing.x,landing.y,landing.z)){reason="спуск: нет проверенной опоры/свободной шахты";return false;}
            WalkCore.keysFor(landing.x-x,landing.z-z,yaw,out);
        }else if(stage==WalkCore.D_AIR){
            double d=Math.hypot(landing.x-x,landing.z-z);
            if(d>.35&&NavigationMotion.airClear(w,x,y,z,landing.x,y,landing.z,.30))WalkCore.keysFor(landing.x-x,landing.z-z,yaw,out);
            else out.forward=out.back=out.left=out.right=false;
        }
        return true;
    }
    public static boolean clear(PathFinder.World w,double x,double y,double z,double tx,double ty,double tz){
        if(w==null||y-ty>PathFinder.DROP_MAX+.01||ty>=y+.01||Math.hypot(tx-x,tz-z)>2.2)return false;
        double support=w.supportY(tx,tz,ty-.06,ty+.06);
        if(!Double.isFinite(support)||Math.abs(support-ty)>.06)return false;
        int hz=w.hazard((int)Math.floor(tx),(int)Math.floor(ty-.01),(int)Math.floor(tz));
        if(hz!=PathFinder.H_NONE)return false;
        return NavigationMotion.airClear(w,x,y,z,tx,y,tz,.30)&&NavigationMotion.airClear(w,tx,y,tz,tx,ty,tz,.30);
    }
}
