package com.farmmacro.motion;
/** Stateless deterministic center tracking: no RNG, reaction queue, prediction or overshoot. */
public final class DirectAimTurn {
 public record Delta(double yaw,double pitch){}
 public static Delta frame(double yaw,double pitch,double targetYaw,double targetPitch,double speed,double dt){
  if(!Double.isFinite(yaw)||!Double.isFinite(pitch)||!Double.isFinite(targetYaw)||!Double.isFinite(targetPitch)||!Double.isFinite(speed)||!Double.isFinite(dt)||speed<=0||dt<=0)return new Delta(0,0);
  double y=AetherTurn.wrap(targetYaw-yaw),p=Math.clamp(targetPitch,-89,89)-pitch,len=Math.hypot(y,p),budget=Math.clamp(speed,20,1440)*Math.min(dt,.05);
  double scale=len>budget?budget/len:1;return new Delta(y*scale,p*scale);
 }
 private DirectAimTurn(){}
}
