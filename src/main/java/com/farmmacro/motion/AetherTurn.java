// SPDX-License-Identifier: GPL-3.0-only
// Adapted from Aether RotationManager and TimedEaseStrategy (Aether contributors).
// https://github.com/mizly/aether/tree/34d3b894d407fe06a5ba1e822f0616f17beccd7b
// Originals and adapter differences: third_party/aether/alpha28, docs/MOTION-AETHER.md.
package com.farmmacro.motion;
import java.util.random.RandomGenerator;
/** Pure rotation planner. Caller applies deltas through vanilla Entity.turn, never player teleport. */
public final class AetherTurn {
 public record Delta(double yaw,double pitch){}
 private final MotionSettings s;
 private final RandomGenerator random;
 private final boolean tracking;
 private final double fromYaw,fromPitch,duration;
 private double elapsed,resYaw,resPitch,budget,lastTargetYaw=Double.NaN,lastTargetPitch=Double.NaN;
 private boolean finished;
 public AetherTurn(double yaw,double pitch,double toYaw,double toPitch,boolean tracking,MotionSettings settings,RandomGenerator random){
  if(!finite(yaw,pitch,toYaw,toPitch)||settings==null||random==null)throw new IllegalArgumentException("rotation input");
  s=settings.copy();this.random=random;this.tracking=tracking;fromYaw=yaw;fromPitch=pitch;
  // Aether ConfigHelpers single-value delay uses inclusive +/-15% integer jitter.
  int jitter=(int)Math.round(s.rotationMs*.15);int base=s.rotationMs+(jitter==0?0:random.nextInt(-jitter,jitter+1));
  duration=Math.max(150,Math.max(base,Math.round(Math.max(Math.abs(wrap(toYaw-yaw)),Math.abs(toPitch-pitch))*s.msPerDegree)))/1000.0;
 }
 public boolean tracking(){return tracking;}
 public boolean finished(){return finished;}
 public static double wrap(double n){return n-360*Math.floor((n+180)/360);}
 public static double quantum(double sensitivity){double v=Math.max(0,Math.min(1,sensitivity))*.6+.2;return v*v*v*1.2;}
 public static double tolerance(double sensitivity){return Math.max(.25,quantum(sensitivity)*.51);}
 public static double easing(double t,MotionSettings s){
  t=Math.max(0,Math.min(1,t));if(!"MANAGER".equals(s.curve))return AetherEasing.valueOf(s.curve).ease((float)t);
  if(s.easeIn&&s.easeOut)return t<.5?Math.pow(t*2,s.inFactor)*.5:1-Math.pow((1-t)*2,s.outFactor)*.5;
  if(s.easeIn)return Math.pow(t,s.inFactor);if(s.easeOut)return 1-Math.pow(1-t,s.outFactor);return t;
 }
 public Delta frame(double yaw,double pitch,double targetYaw,double targetPitch,double sensitivity,double speed,double dt){
  if(!finite(yaw,pitch,targetYaw,targetPitch,sensitivity,speed,dt)||dt<0||speed<=0){finished=true;return new Delta(0,0);}
  dt=Math.min(.06,dt);if(dt==0)return new Delta(0,0);
  targetPitch=Math.max(-89,Math.min(89,targetPitch));elapsed+=dt;
  double ey=wrap(targetYaw-yaw),ep=targetPitch-pitch,q=quantum(sensitivity),tol=tolerance(sensitivity);
  if(tracking){
   // Retargeting must not retain a previous target's sub-pixel intent.
   if(Double.isFinite(lastTargetYaw)&&(Math.abs(wrap(targetYaw-lastTargetYaw))>tol||Math.abs(targetPitch-lastTargetPitch)>tol)){resYaw=resPitch=0;}
   lastTargetYaw=targetYaw;lastTargetPitch=targetPitch;
  }
  finished=Math.abs(ey)<=tol&&Math.abs(ep)<=tol;
  if(finished){resYaw=resPitch=0;return new Delta(0,0);}
  double dy,dp;
  if(tracking){
   double closed=1-Math.exp(-dt*1000/s.trackingMs);
   double noise=(s.noiseMinPct+(s.noiseMaxPct-s.noiseMinPct)*random.nextDouble())/100;
   dy=ey*closed*(1+(random.nextDouble()*2-1)*noise);dp=ep*closed*(1+(random.nextDouble()*2-1)*noise);
   resYaw=boundedResidual(resYaw+dy,ey);resPitch=boundedResidual(resPitch+dp,ep);dy=resYaw;dp=resPitch;
  }else{
   double t=easing(elapsed/duration,s);
   dy=wrap(fromYaw+wrap(targetYaw-fromYaw)*t-yaw);dp=Math.max(-89,Math.min(89,fromPitch+(targetPitch-fromPitch)*t))-pitch;
  }
  // Quantization is bounded by accumulated angular budget. Prevent rounded steps exceeding speed at high FPS.
  budget=Math.min(speed*.06,budget+speed*dt);double len=Math.hypot(dy,dp);
  if(len>budget&&len>0){dy*=budget/len;dp*=budget/len;}
  dy=Math.rint(dy/q)*q;dp=Math.rint(dp/q)*q;
  if(Math.hypot(dy,dp)>budget+1e-9){dy=Math.copySign(Math.floor(Math.abs(dy)/q+1e-9)*q,dy);dp=Math.copySign(Math.floor(Math.abs(dp)/q+1e-9)*q,dp);
   // At most two quantum removals after round-to-nearest on two axes.
   for(int n=0;n<2&&Math.hypot(dy,dp)>budget+1e-9;n++){if(Math.abs(dy)>=Math.abs(dp))dy-=Math.copySign(q,dy);else dp-=Math.copySign(q,dp);}
  }
  if(pitch+dp>89)dp=Math.floor((89-pitch)/q)*q;
  if(pitch+dp< -89)dp=-Math.floor((pitch+89)/q)*q;
  budget=Math.max(0,budget-Math.hypot(dy,dp));
  if(tracking){resYaw-=dy;resPitch-=dp;}
  // Do NOT finish on a zero rounded step far from target (upstream high-FPS stall).
  finished=Math.abs(wrap(ey-dy))<=tol&&Math.abs(ep-dp)<=tol;
  return new Delta(dy,dp);
 }
 private static double boundedResidual(double r,double e){return e==0||Math.signum(r)!=Math.signum(e)?0:Math.copySign(Math.min(Math.abs(r),Math.abs(e)),e);}
 private static boolean finite(double... ns){for(double n:ns)if(!Double.isFinite(n))return false;return true;}
}
