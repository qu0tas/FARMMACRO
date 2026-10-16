package com.farmmacro.combat;
import com.farmmacro.camera.MouseTurn;
import com.farmmacro.camera.TrackingTurn;
/** Fast bounded acceleration/braking, with native sensitivity quantization. No random shaking. */
public final class FastAimTurn {
    public record Delta(double yaw,double pitch){}
    private final Axis yawAxis=new Axis(),pitchAxis=new Axis();
    private static final class Axis {double velocity,residual;void reset(){velocity=residual=0;}}
    public void reset(){yawAxis.reset();pitchAxis.reset();}
    public Delta frame(double yaw,double pitch,double targetYaw,double targetPitch,double sensitivity,double speed,double dt){
        if(!Double.isFinite(dt)||dt<=0)return new Delta(0,0);
        dt=Math.min(.05,dt);double step=MouseTurn.stepDeg(Math.clamp(sensitivity,0,1));
        speed=Math.clamp(speed,20,1440);
        return new Delta(move(yawAxis,TrackingTurn.wrap(targetYaw-yaw),speed,dt,step),
            move(pitchAxis,Math.clamp(targetPitch,-89,89)-pitch,speed,dt,step));
    }
    private double move(Axis a,double error,double speed,double dt,double step){
        if(Math.abs(error)<step*.55){a.reset();return 0;}
        // Braking envelope plus proportional settling: quick large turn, gentle finish.
        double accel=speed*8,desired=Math.copySign(Math.min(speed,Math.min(Math.abs(error)*24,Math.sqrt(2*accel*Math.abs(error)))),error);
        a.velocity+=Math.clamp(desired-a.velocity,-accel*dt,accel*dt);
        double wanted=a.velocity*dt;
        if(wanted*error<0)wanted=0;
        wanted=Math.copySign(Math.min(Math.abs(wanted),Math.abs(error)),error);
        double delta=Math.rint((wanted+a.residual)/step)*step;
        if(delta*error<0)delta=0;
        if(Math.abs(delta)>Math.abs(error))delta=Math.copySign(Math.floor(Math.abs(error)/step)*step,error);
        a.residual=Math.clamp(wanted+a.residual-delta,-step,step);
        return delta;
    }
}
