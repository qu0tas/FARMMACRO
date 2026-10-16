package com.farmmacro.route;

/** Attachment-aware ladder/vine/scaffold input. Confirmation is observed contact/Y progress, not a jump key alone. */
public final class NavigationClimb {
    public enum State { APPROACH,ATTACH,ASCEND,DESCEND,EXIT,BLOCKED }
    public State state=State.APPROACH;
    public String reason="";
    private int target=-1,stall,elapsed;
    private double best=Double.POSITIVE_INFINITY;
    public void reset(){target=-1;stall=elapsed=0;best=Double.POSITIVE_INFINITY;reason="";state=State.APPROACH;}
    public boolean tick(PathFinder.World w,int index,RoutePoint to,double x,double y,double z,float yaw,boolean attached,WalkCore.Out out){
        if(index!=target){reset();target=index;}
        out.jump=out.sprint=out.sneak=false;
        PathFinder.World.Climb surface=null;
        int cy=(int)Math.floor(y+.01);
        for(int k=0;k<3&&surface==null;k++){
            int yy=cy+(k==0?0:k==1?-1:1);
            surface=w.climb((int)Math.floor(to.x),yy,(int)Math.floor(to.z));
            if(surface==null)surface=w.climb((int)Math.floor(x),yy,(int)Math.floor(z));
        }
        if(surface==null){state=State.EXIT;if(Math.abs(to.y-y)<.35)return true;return fail("лестница/лиана/леса не подтверждены рядом");}
        double d=Math.hypot(surface.x()-x,surface.z()-z),dy=to.y-y,metric=d+Math.abs(dy);
        if(metric<best-.04){best=metric;stall=0;}else stall++;
        if(++elapsed>240||stall>60)return fail("нет прогресса/контакта при подъёме или спуске по лестнице");
        if(!w.climbClear(surface.x(),y+.001,surface.z(),.30,PathFinder.BODY-.002))return fail("лестничный проход занят или не загружен");
        if(d>.16){WalkCore.keysFor(surface.x()-x,surface.z()-z,yaw,out);state=State.APPROACH;}
        else out.forward=out.back=out.left=out.right=false;
        if(Math.abs(dy)<.20){state=State.EXIT;return true;}
        if(dy>0){
            state=attached?State.ASCEND:State.ATTACH;
            if(attached||surface.scaffold())out.jump=true;
            // Keep physical contact with the attachment wall; do not retreat to the centre every tick.
            if(!surface.scaffold()&&d<.35&&(surface.pushX()!=0||surface.pushZ()!=0))
                WalkCore.keysFor(surface.pushX(),surface.pushZ(),yaw,out);
        }else{
            state=State.DESCEND;out.jump=false;out.sneak=surface.scaffold()&&d<.30;
        }
        return true;
    }
    /** Last grounded metre into a confirmed climb cell. GroundProbe deliberately rejects
     * CLIMB hazards, so this bounded handoff has its own support/body/hazard envelope. */
    public boolean approach(PathFinder.World w,int index,RoutePoint to,double x,double y,double z,float yaw,WalkCore.Out out){
        if(index!=target){reset();target=index;}
        state=State.APPROACH;out.jump=out.sprint=out.sneak=false;
        double d=Math.hypot(to.x-x,to.z-z);
        if(w==null||d>1.4||Math.abs(to.y-y)>.19||w.climb((int)Math.floor(to.x),(int)Math.floor(to.y+.01),(int)Math.floor(to.z))==null)
            return fail("подход к лестнице не подтверждён");
        if(d<best-.025){best=d;stall=0;}else stall++;
        if(++elapsed>80||stall>40)return fail("нет прогресса при подходе к лестнице");
        int samples=Math.max(1,(int)Math.ceil(d/.10));
        for(int i=0;i<=samples;i++){
            double t=i/(double)samples,cx=x+(to.x-x)*t,cz=z+(to.z-z)*t;
            if(!w.climbClear(cx,y+.001,cz,.30,PathFinder.BODY-.002))return fail("проход к лестнице занят");
            for(int ix=-1;ix<=1;ix++)for(int iz=-1;iz<=1;iz++){
                double sx=cx+ix*.30,sz=cz+iz*.30;
                double floor=w.supportY(sx,sz,y-.08,y+.08);
                if(!Double.isFinite(floor))return fail("нет ровной опоры перед лестницей");
                for(int cy=(int)Math.floor(y-.001);cy<=(int)Math.floor(y+PathFinder.BODY-.01);cy++){
                    int h=w.hazard((int)Math.floor(sx),cy,(int)Math.floor(sz));
                    if(h!=PathFinder.H_NONE&&h!=PathFinder.H_CLIMB)return fail("опасность перед лестницей");
                }
            }
        }
        WalkCore.keysFor(to.x-x,to.z-z,yaw,out);return true;
    }

    /** Move off an observed climb surface onto the verified adjacent platform; no ground required under the ladder origin. */
    public boolean exit(PathFinder.World w,int index,RoutePoint to,double x,double y,double z,float yaw,WalkCore.Out out){
        if(index!=target){reset();target=index;}
        state=State.EXIT;out.jump=out.sneak=out.sprint=false;
        double distance=Math.hypot(to.x-x,to.z-z),dy=to.y-y;
        if(w==null||distance>2.2||Math.abs(dy)>.6)return fail("нет соседнего выхода с лестницы на платформу");
        double support=w.supportY(to.x,to.z,to.y-.06,to.y+.06);
        if(!Double.isFinite(support)||!w.bodyClear(to.x,to.y+.001,to.z,.30,PathFinder.BODY-.002))return fail("выход с лестницы не свободен/не имеет опоры");
        double metric=distance+Math.abs(dy);
        if(metric<best-.04){best=metric;stall=0;}else stall++;
        if(++elapsed>80||stall>40)return fail("не удалось сойти с лестницы за ограниченное время");
        int n=Math.max(1,(int)Math.ceil(distance/.10));
        for(int i=0;i<=n;i++){
            double t=i/(double)n,cx=x+(to.x-x)*t,cz=z+(to.z-z)*t,cy=y+dy*t;
            if(!w.climbClear(cx,cy+.001,cz,.30,PathFinder.BODY-.002))return fail("препятствие на выходе с лестницы");
            for(int k=0;k<2;k++){int h=w.hazard((int)Math.floor(cx),(int)Math.floor(cy)+k,(int)Math.floor(cz));
                if(h!=PathFinder.H_NONE&&h!=PathFinder.H_CLIMB)return fail("опасность на выходе с лестницы");}
        }
        WalkCore.keysFor(to.x-x,to.z-z,yaw,out);return true;
    }
    private boolean fail(String why){state=State.BLOCKED;reason=why;return false;}
}
