/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Adapted on 2026-10-15 for Kaelix alpha24 from Aether contributors' TrackingRotationStrategy:
 * https://github.com/mizly/aether/blob/34d3b894d407fe06a5ba1e822f0616f17beccd7b/src/main/java/dev/aether/modules/pathfinding/rotation/strategy/TrackingRotationStrategy.java
 * Original source/license: third_party/aether/. Changes: local return record,
 * local angle helper, explicit frame timing and sensitivity-residual adapter.
 * No upstream failsafe suppression or direct view/old-view writes are ported.
 */
package com.farmmacro.combat;

import com.farmmacro.camera.MouseTurn;
import com.farmmacro.camera.TrackingTurn;

/** Continuous Aether exponential tracking, bounded jointly across yaw and pitch. */
public final class AetherTrackingTurn {
    public record Rotation(float yaw,float pitch) {}
    private double yawResidual,pitchResidual;
    public void reset(){yawResidual=pitchResidual=0;}

    /** Ported upstream tracking step; retargeting never restarts a timed easing curve. */
    public static Rotation step(float yaw,float pitch,float targetYaw,float targetPitch,
                                float degreesPerSecond,double seconds){
        float yawDelta=(float)TrackingTurn.wrap(targetYaw-yaw);
        float pitchDelta=Math.clamp(targetPitch,-90.0f,90.0f)-pitch;
        double magnitude=Math.hypot(yawDelta,pitchDelta);
        double response=1.0-Math.exp(-10.0*Math.max(0.0,seconds));
        double scale=magnitude<1.0e-6?0.0
            :Math.min(response,Math.max(0.0,degreesPerSecond*seconds)/magnitude);
        return new Rotation(yaw+(float)(yawDelta*scale),
            Math.clamp(pitch+(float)(pitchDelta*scale),-90.0f,90.0f));
    }

    /** Kaelix adapter: mouse sensitivity steps, no packet/silent rotation or forced finish. */
    public FastAimTurn.Delta frame(double yaw,double pitch,double targetYaw,double targetPitch,
                                   double sensitivity,double speed,double dt){
        if(!Double.isFinite(dt)||dt<=0)return new FastAimTurn.Delta(0,0);
        dt=Math.min(.05,dt);
        var next=step((float)yaw,(float)pitch,(float)targetYaw,(float)Math.clamp(targetPitch,-89,89),
            (float)Math.clamp(speed,20,1440),dt);
        double quantum=MouseTurn.stepDeg(Math.clamp(sensitivity,0,1));
        double ey=TrackingTurn.wrap(targetYaw-yaw),ep=Math.clamp(targetPitch,-89,89)-pitch;
        if(yawResidual*ey<0)yawResidual=0;
        if(pitchResidual*ep<0)pitchResidual=0;
        double wy=TrackingTurn.wrap(next.yaw()-yaw)+yawResidual,wp=next.pitch()-pitch+pitchResidual;
        double dy=quantize(wy,ey,quantum),dp=quantize(wp,ep,quantum);
        yawResidual=Math.abs(ey)<quantum*.5?0:Math.clamp(wy-dy,-quantum,quantum);
        pitchResidual=Math.abs(ep)<quantum*.5?0:Math.clamp(wp-dp,-quantum,quantum);
        return new FastAimTurn.Delta(dy,dp);
    }
    private static double quantize(double wanted,double error,double quantum){
        if(Math.abs(error)<quantum*.5)return 0;
        double q=Math.rint(wanted/quantum)*quantum;
        if(q*error<0)return 0;
        return Math.abs(q)>Math.abs(error)?Math.copySign(Math.floor(Math.abs(error)/quantum)*quantum,error):q;
    }
}
