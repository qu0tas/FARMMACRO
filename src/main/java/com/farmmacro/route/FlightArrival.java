package com.farmmacro.route;

/** Arrival is observed ground+disabled flight, or stable hover; a key press alone never completes a task. */
public final class FlightArrival {
    public enum Result { WAIT,DONE,FAIL }
    public String reason="";
    private int ticks,stable;
    private boolean complete;
    private final FlightGuide guide=new FlightGuide();
    public void reset(){ticks=stable=0;complete=false;reason="";guide.reset();}
    public Result tick(PathFinder.World w,boolean land,boolean grounded,boolean flying,double x,double y,double z,
                       double vx,double vy,double vz,float yaw,RoutePoint goal,WalkCore.Out out){
        if(complete){out.forward=out.back=out.left=out.right=out.jump=out.sneak=out.sprint=false;return Result.DONE;}
        if(++ticks>160){reason=land?"посадка/выключение полёта не подтверждены за 8 секунд":"зависание не стабилизировалось за 8 секунд";return Result.FAIL;}
        double d=Math.hypot(goal.x-x,goal.z-z);
        guide.tick(x,y,z,vx,vy,vz,yaw,goal.x,goal.y,goal.z,out);
        if(land){
            double support=w.supportY(goal.x,goal.z,goal.y-.21,goal.y+.21);
            if(!Double.isFinite(support)){reason="у конечной точки нет подтверждённой опоры для посадки";return Result.FAIL;}
            for(int ix=-1;ix<=1;ix++)for(int iz=-1;iz<=1;iz++){
                double sx=goal.x+ix*.3,sz=goal.z+iz*.3,f=w.supportY(sx,sz,goal.y-.21,goal.y+.21);
                if(!Double.isFinite(f)){reason="неполная опора конечной посадки";return Result.FAIL;}
                int hazard=w.hazard((int)Math.floor(sx),(int)Math.floor(f-.001),(int)Math.floor(sz));
                if(hazard!=PathFinder.H_NONE){reason="опасная/специальная опора посадки";return Result.FAIL;}
            }
            boolean aligned=d<.4;
            out.jump=false;out.sneak=aligned&&!grounded;
            if(grounded){out.sneak=false;out.forward=out.back=out.left=out.right=false;}
            if(aligned&&grounded&&!flying&&Math.abs(y-support)<.21&&Math.hypot(vx,vz)<.055)stable++;else stable=0;
        }else{
            if(!flying){reason="полёт потерян перед зависанием";return Result.FAIL;}
            if(d<.35&&Math.abs(goal.y-y)<.22&&Math.hypot(vx,vz)<.045&&Math.abs(vy)<.04)stable++;else stable=0;
        }
        if(stable>=3){complete=true;out.forward=out.back=out.left=out.right=out.jump=out.sneak=false;return Result.DONE;}
        if(land&&d<.4&&!grounded){
            // A legal descent ends at the supporting shape, not half a block inside it.
            double support=w.supportY(goal.x,goal.z,goal.y-.21,goal.y+.21);
            if(y<support-.21||!NavigationMotion.airClear(w,x,y,z,x,Math.max(support,y-.5),z,.32)
                ||!NavigationMotion.airClear(w,x,y,z,x+vx*4,y,z+vz*4,.32)){
                reason="объём посадки/инерционный коридор заблокирован";return Result.FAIL;
            }
            out.jump=false;out.sneak=true;return Result.WAIT;
        }
        if(!grounded&&!guide.constrain(w,x,y,z,vx,vy,vz,yaw,goal.x,goal.y,goal.z,out)){
            reason="проход/инерция заблокировали завершение полёта";return Result.FAIL;
        }
        return Result.WAIT;
    }
}
