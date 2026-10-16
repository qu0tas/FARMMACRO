package com.farmmacro.route;

/** Best-so-far progress, not accumulated motion: an A/D oscillation cannot renew the deadline. */
public final class NavigationProgress {
    public static final int STALL_TICKS=80;
    private int target=-1,stall;
    private double best=Double.POSITIVE_INFINITY,bestAngle=Double.POSITIVE_INFINITY;
    public void reset(){target=-1;stall=0;best=bestAngle=Double.POSITIVE_INFINITY;}
    public int stalledTicks(){return stall;}
    public boolean tick(int index,double distance,double angle,boolean aligning,boolean intentionalPause){
        if(index!=target){reset();target=index;}
        boolean progress=distance<best-.08;
        if(progress){best=distance;bestAngle=angle;stall=0;}
        else if(aligning&&angle<bestAngle-2){bestAngle=angle;stall=0;}
        else if(!intentionalPause)stall++;
        return stall>STALL_TICKS;
    }
}