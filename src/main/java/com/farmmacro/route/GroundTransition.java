package com.farmmacro.route;

/** Navigation-only planned ascent. Bounded attempts; never changes motion/position or held input directly. */
public final class GroundTransition {
    public enum State { IDLE, APPROACH, ALIGN, STEP, JUMP, AIR, WAIT, BLOCKED }
    public State state=State.IDLE;
    public String reason="";
    private int target=-1,attempts,pulse,cooldown;
    public void reset(){target=-1;attempts=pulse=cooldown=0;state=State.IDLE;reason="";}
    public void target(int index){if(index!=target){reset();target=index;}else state=State.IDLE;}
    public boolean rising(){return state!=State.IDLE;}
    public boolean rise(PathFinder.World world,double x,double y,double z,RoutePoint to,float yaw,
                        boolean grounded,WalkCore.Out out){
        out.jump=out.sprint=out.sneak=false;
        double d=Math.hypot(to.x-x,to.z-z),dy=to.y-y;
        float heading=WalkCore.yawTo(x,z,to.x,to.z);
        if(!grounded){state=State.AIR;pulse=0;return true;}
        if(dy>PathFinder.JUMP_MAX+.05&&d<1.5)return blocked(out,"ступень выше доступного прыжка");
        if(d>1.35){state=State.APPROACH;return true;}
        if(Math.abs(WalkCore.wrap(heading-yaw))>35){
            stop(out);state=State.ALIGN;return true;
        }
        // Stairs/slabs often form a real walking staircase despite the target column being 1 block higher.
        if(world!=null&&new GroundProbe(world).clear(x,y,z,to.x,to.z,.30)){
            state=State.STEP;return true;
        }
        if(pulse>0){out.jump=true;pulse--;state=State.JUMP;return true;}
        if(cooldown>0){cooldown--;state=State.WAIT;return true;}
        if(attempts>=3)return blocked(out,"подъём не подтверждён после 3 попыток");
        if(world==null||!jumpClear(world,x,y,z,to.x,to.y,to.z))
            return blocked(out,"нет свободного объёма для прыжка");
        attempts++;pulse=1;cooldown=8;out.jump=true;state=State.JUMP;return true;
    }
    private boolean blocked(WalkCore.Out out,String why){stop(out);state=State.BLOCKED;reason=why;return false;}
    private static void stop(WalkCore.Out out){out.forward=out.back=out.left=out.right=out.jump=out.sprint=out.sneak=false;}
    /** Conservative up/across/down body envelope, not a ballistic feasibility guarantee. */
    public static boolean jumpClear(PathFinder.World w,double x,double y,double z,double tx,double ty,double tz){
        double apex=y+PathFinder.JUMP_MAX;
        return NavigationMotion.airClear(w,x,y,z,x,apex,z,.30)
            &&NavigationMotion.airClear(w,x,apex,z,tx,apex,tz,.30)
            &&NavigationMotion.airClear(w,tx,apex,tz,tx,ty,tz,.30);
    }
}