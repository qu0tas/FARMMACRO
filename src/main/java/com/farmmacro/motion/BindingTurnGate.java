package com.farmmacro.motion;
/** Fail-closed wait: ownership + observed angle + unchanged context/position + bounded monotonic deadline. */
public final class BindingTurnGate {
 public enum Result { WAIT, READY, FAILED }
 private final CameraTurnLease.Ticket ticket;
 private final double x,y,z;
 private final long began,limitNs;
 public BindingTurnGate(CameraTurnLease.Ticket t,double x,double y,double z,long now,double timeoutSeconds){
  if(t==null||!finite(x,y,z,timeoutSeconds)||timeoutSeconds<=0||timeoutSeconds>30)throw new IllegalArgumentException("binding wait");
  ticket=t;this.x=x;this.y=y;this.z=z;began=now;limitNs=(long)(timeoutSeconds*1e9);
 }
 public Result check(double px,double py,double pz,double yaw,double pitch,double sensitivity,boolean contextValid,long now){
  long elapsed=now-began;
  if(!contextValid||!finite(px,py,pz,yaw,pitch,sensitivity)||elapsed<0||elapsed>=limitNs
    ||Math.hypot(px-x,pz-z)>.75||Math.abs(py-y)>.6||ticket.state()==CameraTurnLease.State.CANCELLED)return Result.FAILED;
  if(ticket.state()==CameraTurnLease.State.WAITING)return Result.WAIT;
  double tolerance=AetherTurn.tolerance(sensitivity)+.0001;
  return Math.abs(AetherTurn.wrap(ticket.yaw-yaw))<=tolerance&&Math.abs(ticket.pitch-pitch)<=tolerance?Result.READY:Result.FAILED;
 }
 public static double timeout(double yawDelta,double pitchDelta,double speed,MotionSettings s){
  if(!finite(yawDelta,pitchDelta,speed)||speed<=0||s==null)return 5;
  double distance=Math.hypot(AetherTurn.wrap(yawDelta),pitchDelta);
  double curve=Math.max(s.rotationMs*1.15,Math.max(Math.abs(AetherTurn.wrap(yawDelta)),Math.abs(pitchDelta))*s.msPerDegree)/1000;
  return Math.max(5,Math.min(30,Math.max(distance/speed,curve)+4));
 }
 private static boolean finite(double... vs){for(double v:vs)if(!Double.isFinite(v))return false;return true;}
}
