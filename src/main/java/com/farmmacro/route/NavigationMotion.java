package com.farmmacro.route;

import java.util.List;
import java.util.SplittableRandom;

/** Shared navigation policy, not farming playback. Produces intent, never modifies physics.
 * Session-seeded slow gait variation; geometry always has priority over variation. */
public final class NavigationMotion {
    public enum Mode { WALK, SPRINT, JUMP, FALL, FLY, CLIMB, ALIGN, BRAKE }
    public Mode mode = Mode.WALK;
    public boolean sprintAllowed, jumpsAllowed, variation, heightLook;
    private SplittableRandom random = new SplittableRandom(0);
    private int ticks, gaitUntil, jumpAfter;
    private boolean brisk;
    private double tempo=1, drift, driftTarget;
    private float gazePitch;

    public void reset(long seed) {
        random=new SplittableRandom(seed); ticks=gaitUntil=0; jumpAfter=35;
        brisk=false; tempo=1; drift=driftTarget=0; gazePitch=0; mode=Mode.WALK;
    }
    public void tick() {
        ticks++;
        if(!variation){brisk=true;tempo=1;drift=driftTarget=0;return;}
        if(ticks>=gaitUntil) {
            brisk=random.nextDouble()<.72; tempo=random.nextDouble(.94,1.02);
            driftTarget=random.nextDouble(-.10,.10); gaitUntil=ticks+random.nextInt(45,131);
        }
        drift+=(driftTarget-drift)*.025;
    }
    public double speed(boolean open) { return (open&&sprintAllowed&&brisk?.28:.215)*tempo; }
    public double[] aim(GroundProbe probe,double x,double y,double z,double tx,double tz,boolean open) {
        double dx=tx-x,dz=tz-z,n=Math.hypot(dx,dz);
        if(!open||n<2)return new double[]{tx,tz};
        double ax=tx+dz/n*drift,az=tz-dx/n*drift;
        return probe.clear(x,y,z,ax,az,.36)?new double[]{ax,az}:new double[]{tx,tz};
    }
    public void ground(PathFinder.World world,double x,double y,double z,float yaw,
                       boolean grounded,boolean open,GroundGuide guide,WalkCore.Out out) {
        mode=switch(guide.state) {case ALIGN -> Mode.ALIGN; case BRAKE,BLOCKED -> Mode.BRAKE; default -> Mode.WALK;};
        out.sprint=sprintAllowed&&brisk&&open&&out.forward&&!out.back&&!out.left&&!out.right
            &&!out.sneak&&guide.state==GroundGuide.State.WALK;
        if(out.sprint)mode=Mode.SPRINT;
        if(!jumpsAllowed||!out.sprint||!grounded||ticks<jumpAfter)return;
        double a=Math.toRadians(yaw),dx=-Math.sin(a),dz=Math.cos(a);
        boolean safe=new GroundProbe(world).clear(x,y,z,x+dx*4,z+dz*4,.42);
        for(int i=0;safe&&i<=24;i++) {
            double t=i/24.0,h=1.3*Math.sin(Math.PI*t);
            safe=airClear(world,x+dx*4*t,y+h+.01,z+dz*4*t,x+dx*4*t,y+h+.01,z+dz*4*t,.32);
        }
        jumpAfter=ticks+random.nextInt(35,91);
        if(safe){out.jump=true;mode=Mode.JUMP;}
    }
    public float[] gaze(double x,double y,double z,double tx,double ty,double tz,double vx,double vz,float fallback) {
        double n=Math.hypot(tx-x,tz-z);
        float heading=n>.12?WalkCore.yawTo(x,z,tx,tz):fallback;
        if(variation&&Math.hypot(vx,vz)>.04&&n>.4&&mode!=Mode.CLIMB) {
            float velocity=WalkCore.yawTo(0,0,vx,vz);
            heading+=Math.clamp(WalkCore.wrap(velocity-heading),-24,24)*.2f;
        }
        float target=(float)(!heightLook?0:mode==Mode.FLY?Math.clamp(-Math.toDegrees(Math.atan2(ty-y,Math.max(1,n))),-35,35)
            :mode==Mode.CLIMB?(ty>y?-18:15):2);
        gazePitch+=(target-gazePitch)*.12f;
        return new float[]{heading,gazePitch};
    }
    /** Bounded 3D body sweep. Coarse/cache worlds also enforce top/edges/hazards. */
    public static boolean airClear(PathFinder.World w,double ax,double ay,double az,double bx,double by,double bz,double radius) {
        double d=Math.sqrt(Math.pow(bx-ax,2)+Math.pow(by-ay,2)+Math.pow(bz-az,2));
        if(w==null||!Double.isFinite(d)||d>32)return false;
        int n=Math.max(1,(int)Math.ceil(d/.15));
        for(int i=0;i<=n;i++) {
            double t=(double)i/n,x=ax+(bx-ax)*t,y=ay+(by-ay)*t,z=az+(bz-az)*t;
            if(!w.bodyClear(x,y+.001,z,radius,PathFinder.BODY-.002))return false;
            for(int cx=(int)Math.floor(x-radius);cx<=(int)Math.floor(x+radius);cx++)
              for(int cz=(int)Math.floor(z-radius);cz<=(int)Math.floor(z+radius);cz++)
                for(int cy=(int)Math.floor(y);cy<=(int)Math.floor(y+PathFinder.BODY-.001);cy++) {
                    int hz=w.hazard(cx,cy,cz); double top=w.top(cx,cy,cz);
                    if(hz!=PathFinder.H_NONE&&hz!=PathFinder.H_CLIMB)return false;
                    if(!w.exactAt(x,z,radius)&&top>=0&&cy+top>y+.005&&cy<y+PathFinder.BODY-.001)return false;
                    int e=w.edges(cx,cy,cz);
                    if(!w.exactAt(x,z,radius)&&(((e&PathFinder.E_NX)!=0&&x-radius<cx+.2)||((e&PathFinder.E_PX)!=0&&x+radius>cx+.8)
                        ||((e&PathFinder.E_NZ)!=0&&z-radius<cz+.2)||((e&PathFinder.E_PZ)!=0&&z+radius>cz+.8)))return false;
                }
        }
        return true;
    }
    /** Only shift an already necessary flight upstream; never skip a special/action vertex. */
    public static void earlyTakeoff(List<RoutePoint> points,PathFinder.World world,double sx,double sy,double sz,double ceiling) {
        for(int i=0;i<points.size();i++){
            RoutePoint fly=points.get(i);if(!fly.fly)continue;
            RoutePoint edge=i>0?points.get(i-1):new RoutePoint(sx,sy,sz);
            if(edge.fly||edge.climb||edge.jump||edge.drop||(edge.pointAction!=null&&edge.pointAction.active()))return;
            RoutePoint before=i>1?points.get(i-2):new RoutePoint(sx,sy,sz);
            if(before.fly||before.climb||before.jump||before.drop||Math.abs(before.y-edge.y)>.1)return;
            double len=Math.hypot(edge.x-before.x,edge.z-before.z);
            for(double lead:new double[]{4,2,1}){
                double k=len<.01?0:Math.max(0,(len-lead)/len),x=before.x+(edge.x-before.x)*k,z=before.z+(edge.z-before.z)*k;
                double altitude=Math.max(edge.y+1.3,fly.y);
                if(altitude>ceiling+.001)continue;
                if(!new GroundProbe(world).clear(before.x,before.y,before.z,x,z,.31)
                    ||!airClear(world,x,edge.y,z,x,altitude,z,.33)
                    ||!airClear(world,x,altitude,z,fly.x,fly.y,fly.z,.33))continue;
                RoutePoint launch=new RoutePoint(x,altitude,z);launch.fly=true;
                if(i>0){points.set(i-1,new RoutePoint(x,edge.y,z));points.add(i,launch);}
                else points.add(0,launch);
                return;
            }
            return;
        }
    }
}
