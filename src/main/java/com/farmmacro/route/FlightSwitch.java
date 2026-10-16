package com.farmmacro.route;

/** Vanilla jump pulses with observed confirmation, not abilities writes or forged permission packets. */
public final class FlightSwitch {
    public enum Result { WAIT,CONFIRMED,FAIL }
    public enum State { IDLE,LAUNCH,CONFIRM,FLYING,FAILED }
    public State state=State.IDLE;
    public String reason="";
    public boolean jump,hadFlight;
    private int ticks,observations;
    public void reset(){state=State.IDLE;ticks=observations=0;jump=hadFlight=false;reason="";}
    public boolean starting(){return state==State.LAUNCH||state==State.CONFIRM;}
    public Result tick(boolean permitted,boolean flying,PathFinder.World w,double x,double y,double z){
        return tick(permitted,flying,false,w,x,y,z);
    }
    public Result tick(boolean permitted,boolean flying,boolean grounded,PathFinder.World w,double x,double y,double z){
        jump=false;
        if(!permitted)return fail("сервер отозвал/не выдал разрешение полёта");
        if(state==State.IDLE||state==State.FLYING){state=State.LAUNCH;ticks=observations=0;}
        if(++ticks>48)return fail("полёт не подтвердился после трёх двойных прыжков");
        if(flying){
            state=State.CONFIRM;hadFlight=true;
            // Maintain lift while the client observed state stabilises; a ground-level fly flag is not arrival.
            jump=NavigationMotion.airClear(w,x,y,z,x,y+.4,z,.32);
            if(grounded)observations=0;else observations++;
            if(observations>=3){state=State.FLYING;jump=false;return Result.CONFIRMED;}
            return Result.WAIT;
        }
        observations=0;state=State.LAUNCH;
        if(!NavigationMotion.airClear(w,x,y,z,x,y+1.3,z,.32))return fail("нет свободного объёма для взлёта");
        int cycle=(ticks-1)%16;
        if(ticks<=48)jump=cycle==0||cycle==3;
        return Result.WAIT;
    }
    private Result fail(String why){state=State.FAILED;reason=why;jump=false;return Result.FAIL;}
}
