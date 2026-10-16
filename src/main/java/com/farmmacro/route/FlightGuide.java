package com.farmmacro.route;

/** Keyboard-only flight. Cruise, braking and alignment are distinct; no ability/velocity writes.
 * Design review: Aether's separated cruise/arrival and coast-aware control (not a source port). */
public final class FlightGuide {
    public enum Mode { CRUISE,ALIGN,CLIMB,DESCEND,BRAKE,AVOID,BLOCKED }
    public Mode mode=Mode.CRUISE;
    public boolean sprintAllowed;
    public double normalSpeed=.6;
    private boolean sprint,sprintEnded;
    private double targetX=Double.NaN,targetY,targetZ;
    private int blocked;
    public void reset(){blocked=0;sprint=sprintEnded=false;targetX=Double.NaN;mode=Mode.CRUISE;}
    public int blockedTicks(){return blocked;}
    private static int axis(double desired,double actual){
        double error=desired-actual;
        // A cruise impulse must be allowed to coast, not immediately provoke its opposite.
        double band=desired*error<0?.13:Math.min(.045,Math.max(.004,Math.abs(desired)*.35));
        // Nonzero low-speed arrival intent must exceed its own deadband. The old .055
        // band swallowed it at ~.7 blocks, outside ReturnCore.REACH (.35), forever.
        if(Math.abs(desired)<.0001)band=.055;
        return error>band?1:error<-band?-1:0;
    }
    public void tick(double x,double y,double z,double vx,double vy,double vz,float yaw,
                     double tx,double ty,double tz,WalkCore.Out out){
        double dx=tx-x,dz=tz-z,d=Math.hypot(dx,dz),dy=ty-y,a=Math.toRadians(yaw);
        double angle=d>.25?Math.abs(WalkCore.wrap(WalkCore.yawTo(x,z,tx,tz)-yaw)):0;
        double speed=Math.hypot(vx,vz),brake=Math.max(3,speed*12+1.5);
        if(tx!=targetX||ty!=targetY||tz!=targetZ){sprint=sprintEnded=false;targetX=tx;targetY=ty;targetZ=tz;}
        boolean wasSprint=sprint;
        sprint=!sprintEnded&&sprintAllowed&&Math.abs(dy)<2.5&&(sprint?d>Math.max(5,brake)&&angle<35:d>Math.max(9,brake+2)&&angle<18);
        if(wasSprint&&!sprint)sprintEnded=true; // Do not re-enable after slowing on the same approach.
        double limit=Math.clamp(normalSpeed,.15,1.5)*(sprint?1.65:1);
        boolean cruise=d>brake+1&&angle<35;
        double wanted=cruise?limit:Math.min(limit,Math.max(0,d-.18)/12);
        double wx=d>.001?dx/d*wanted:0,wz=d>.001?dz/d*wanted:0;
        int f=axis(-Math.sin(a)*wx+Math.cos(a)*wz,-Math.sin(a)*vx+Math.cos(a)*vz);
        int l=axis(Math.cos(a)*wx+Math.sin(a)*wz,Math.cos(a)*vx+Math.sin(a)*vz);
        // On a long aligned leg hold W, rather than repeatedly tapping W and triggering vanilla sprint.
        if(cruise&&angle<18&&speed<limit*1.3)f=1;
        mode=cruise?Mode.CRUISE:Mode.BRAKE;
        if(angle>65){sprint=false;f=axis(0,-Math.sin(a)*vx+Math.cos(a)*vz);l=axis(0,Math.cos(a)*vx+Math.sin(a)*vz);mode=Mode.ALIGN;}
        double projected=dy-vy*2.5;
        int v=dy>.20&&projected>.16?1:dy<-.20&&projected<-.16?-1:0;
        if(mode!=Mode.ALIGN&&Math.abs(dy)>.6)mode=dy>0?Mode.CLIMB:Mode.DESCEND;
        out.forward=f>0;out.back=f<0;out.left=l>0;out.right=l<0;
        out.jump=v>0;out.sneak=v<0;out.sprint=sprint&&!out.back;
    }
    public boolean constrain(PathFinder.World w,double x,double y,double z,double vx,double vy,double vz,float yaw,
                             double tx,double ty,double tz,WalkCore.Out out){
        if(out.sprint){
            double dx=tx-x,dz=tz-z,n=Math.hypot(dx,dz),lead=Math.min(n,Math.max(8,Math.hypot(vx,vz)*13));
            if(n<.001||!NavigationMotion.airClear(w,x,y,z,x+dx/n*lead,y,z+dz/n*lead,.36)){sprint=out.sprint=false;sprintEnded=true;}
        }
        if(clear(w,x,y,z,vx,vy,vz,yaw,out)){blocked=0;return true;}
        sprint=out.sprint=false;sprintEnded=true;
        // Brake against measured drift before attempting a small checked lateral alternative.
        double a=Math.toRadians(yaw);int f=axis(0,-Math.sin(a)*vx+Math.cos(a)*vz),l=axis(0,Math.cos(a)*vx+Math.sin(a)*vz);
        out.forward=f>0;out.back=f<0;out.left=l>0;out.right=l<0;out.jump=out.sneak=false;mode=Mode.BRAKE;
        if(clear(w,x,y,z,vx,vy,vz,yaw,out)&&++blocked<12)return true;
        double dx=tx-x,dz=tz-z,n=Math.hypot(dx,dz);
        if(n>.2)for(int side:new int[]{1,-1}){
            double ux=-dz/n*side,uz=dx/n*side;
            if(!NavigationMotion.airClear(w,x,y,z,x+ux*1.5,y,z+uz*1.5,.35))continue;
            WalkCore.Out candidate=new WalkCore.Out();WalkCore.keysFor(ux,uz,yaw,candidate);
            if(clear(w,x,y,z,vx,vy,vz,yaw,candidate)){copy(candidate,out);mode=Mode.AVOID;return ++blocked<24;}
        }
        out.forward=out.back=out.left=out.right=out.jump=out.sneak=out.sprint=false;mode=Mode.BLOCKED;
        return ++blocked<14;
    }
    private boolean clear(PathFinder.World w,double x,double y,double z,double vx,double vy,double vz,float yaw,WalkCore.Out out){
        double[] dir=WalkCore.moveDir(out,yaw);
        double impulse=out.sprint?.85:.5;
        double px=x+vx*10+dir[0]*impulse,pz=z+vz*10+dir[1]*impulse;
        double py=y+vy*2.5+(out.jump?.35:out.sneak?-.35:0);
        return NavigationMotion.airClear(w,x,y,z,px,py,pz,.33);
    }
    private static void copy(WalkCore.Out a,WalkCore.Out b){b.forward=a.forward;b.back=a.back;b.left=a.left;b.right=a.right;b.jump=a.jump;b.sneak=a.sneak;b.sprint=false;}
}
