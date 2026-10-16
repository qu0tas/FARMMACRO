package com.farmmacro.combat;
import com.farmmacro.camera.MouseTurn;
import com.farmmacro.camera.TrackingTurn;
import java.util.ArrayDeque;
import java.util.concurrent.ThreadLocalRandom;
/** Timestamped delayed perception, not periodic sampling of the latest position. */
public final class HumanAimTurn {
    private final java.util.random.RandomGenerator random;
    public HumanAimTurn(){this(ThreadLocalRandom.current());}
    HumanAimTurn(java.util.random.RandomGenerator random){this.random=java.util.Objects.requireNonNull(random);}

    private record Observation(double at,double yaw,double pitch){}
    private static final class Axis {double velocity,residual;void reset(){velocity=residual=0;}}
    private final Axis yawAxis=new Axis(),pitchAxis=new Axis();
    private final ArrayDeque<Observation> history=new ArrayDeque<>();
    private double clock,sampleClock,observedYaw,observedPitch,delay,damping=1.08,overshootScale,correctionLeft;
    private boolean started,perceived,overshootPending;
    public void reset(){yawAxis.reset();pitchAxis.reset();history.clear();clock=sampleClock=correctionLeft=overshootScale=0;started=perceived=overshootPending=false;}
    /** Carry only modest, aligned horizontal momentum; a reverse handoff must not look away first. */
    public void handoff(double error){
        if(yawAxis.velocity*error<=0)yawAxis.reset();
        else {yawAxis.velocity*=.65;yawAxis.residual=0;}
    }
    public boolean correcting(){return correctionLeft>0;}
    /** Keep momentum across an autonomous handoff, but never keep observations of another mob. */
    public void retarget(double yaw,double pitch,double amount,double reactionMin,double reactionMax){
        // A previous ground/low-body aim point must not tilt the next target's reaction wait.
        // Preserve horizontal handoff momentum, but do not carry unrelated vertical velocity.
        pitchAxis.reset();
        var r=random;history.clear();sampleClock=0;observedYaw=yaw+Math.clamp(yawAxis.velocity*.035,-1.5,1.5);observedPitch=Math.clamp(pitch,-89,89);
        delay=(reactionMin==reactionMax?reactionMin:r.nextDouble(reactionMin,reactionMax))*(amount>0?1:0);
        damping=r.nextDouble(1.04,1.18);overshootScale=0;correctionLeft=0;
        overshootPending=r.nextDouble()<.18*amount;
        if(overshootPending)overshootScale=r.nextDouble(.2,.65)*amount;
        started=true;perceived=false;
    }
    public boolean perceived(){return perceived;}
    public double error(double yaw,double pitch){return Math.hypot(TrackingTurn.wrap(observedYaw-yaw),observedPitch-pitch);}
    public FastAimTurn.Delta frame(double yaw,double pitch,double targetYaw,double targetPitch,double sensitivity,double speed,double dt,
                                  double amount,double yawMargin,double pitchMargin,double reactionMin,double reactionMax){
        if(!Double.isFinite(dt)||dt<=0)return new FastAimTurn.Delta(0,0);
        double elapsed=dt;dt=Math.min(.05,dt);amount=Math.clamp(amount,0,1);speed=Math.clamp(speed,20,1440);
        if(!started)retarget(yaw,pitch,amount,reactionMin,reactionMax);
        // Perception ages in real elapsed time; only numerical integration is capped.
        clock+=elapsed;sampleClock-=elapsed;
        if(sampleClock<=0){history.addLast(new Observation(clock,targetYaw,targetPitch));sampleClock=random.nextDouble(.025,.045);}
        while(!history.isEmpty()&&history.peekFirst().at()<=clock-delay){var o=history.removeFirst();observedYaw=o.yaw();observedPitch=o.pitch();perceived=true;}
        while(history.size()>32)history.removeFirst();
        double step=MouseTurn.stepDeg(Math.clamp(sensitivity,0,1)),omega=Math.clamp(12+speed/100,12,26);
        yawMargin=Math.max(0,yawMargin);pitchMargin=Math.max(0,pitchMargin);
        // One bounded settling window per new target, never continuous underdamped ringing on tracking.
        if(perceived&&overshootPending&&error(yaw,pitch)<=Math.max(3,Math.hypot(yawMargin,pitchMargin)*4)){
            overshootPending=false;correctionLeft=.4;
        }
        double correctionWeight=Math.clamp(correctionLeft/.15,0,1);
        double extra=overshootScale*correctionWeight;
        double currentDamping=damping+(.92-damping)*correctionWeight;
        correctionLeft=Math.max(0,correctionLeft-elapsed);
        // Tolerance scales with apparent target size; do not chase every tiny displacement.
        double yd=Math.min(.4,Math.max(0,yawMargin)*.45)*amount,pd=Math.min(.25,Math.max(0,pitchMargin)*.45)*amount;
        return new FastAimTurn.Delta(move(yawAxis,TrackingTurn.wrap(observedYaw-yaw),speed,omega,dt,step,Math.min(.65,yawMargin*extra),yd,currentDamping),
            move(pitchAxis,Math.clamp(observedPitch,-89,89)-pitch,speed,omega,dt,step,Math.min(.3,pitchMargin*extra),pd,currentDamping));
    }
    private double move(Axis axis,double error,double speed,double omega,double dt,double step,double overshoot,double deadzone,double currentDamping){
        double controlled=Math.copySign(Math.max(0,Math.abs(error)-deadzone),error);
        if(Math.abs(error)<=Math.max(step*.55,deadzone)&&Math.abs(axis.velocity)<step*8){axis.reset();return 0;}
        int n=Math.max(1,(int)Math.ceil(dt/.004));double h=dt/n,moved=0;
        for(int i=0;i<n;i++){
            double e=controlled-moved;
            double acceleration=Math.clamp(omega*omega*e-2*currentDamping*omega*axis.velocity,-speed*10,speed*10);
            axis.velocity=Math.clamp(axis.velocity+acceleration*h,-speed,speed);double delta=axis.velocity*h;
            if(delta*e>=0&&Math.abs(delta)>Math.abs(e)+overshoot){delta=Math.copySign(Math.abs(e)+overshoot,delta);axis.velocity*=.4;}
            else if(delta*e<0&&Math.abs(e)+Math.abs(delta)>overshoot){delta=Math.copySign(Math.max(0,overshoot-Math.abs(e)),delta);axis.velocity*=.4;}
            moved+=delta;
        }
        double total=moved+axis.residual,q=Math.rint(total/step)*step;
        if(q*controlled>=0&&Math.abs(q)>Math.abs(controlled)+overshoot)q=Math.copySign(Math.floor((Math.abs(controlled)+overshoot)/step)*step,q);
        else if(q*controlled<0&&Math.abs(controlled)+Math.abs(q)>overshoot)q=Math.copySign(Math.floor(Math.max(0,overshoot-Math.abs(controlled))/step)*step,q);
        axis.residual=Math.clamp(total-q,-step,step);return q;
    }
}
