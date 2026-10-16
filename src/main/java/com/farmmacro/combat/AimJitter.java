package com.farmmacro.combat;
import java.util.concurrent.ThreadLocalRandom;
/** Bounded low-pass random angular offset, not cumulative mouse drift. */
public final class AimJitter {
    private final java.util.random.RandomGenerator random;
    public AimJitter(){this(ThreadLocalRandom.current());}
    AimJitter(java.util.random.RandomGenerator random){this.random=java.util.Objects.requireNonNull(random);}

    public record Offset(double yaw,double pitch){}
    private double y,p,targetY,targetP,clock;
    public void reset(){y=p=targetY=targetP=clock=0;}
    public Offset frame(double dt,double amplitude){
        if(!Double.isFinite(dt)||dt<=0||!Double.isFinite(amplitude)||amplitude<=0){reset();return new Offset(0,0);}
        dt=Math.min(.05,dt);amplitude=Math.clamp(amplitude,0,.5);clock-=dt;
        if(clock<=0){var r=random;clock=r.nextDouble(.45,1.1);targetY=r.nextDouble(-1,1);targetP=r.nextDouble(-.55,.55);}
        double response=1-Math.exp(-4*dt);y+=(targetY-y)*response;p+=(targetP-p)*response;
        return new Offset(y*amplitude,p*amplitude);
    }
}
