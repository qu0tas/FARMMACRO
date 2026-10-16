package com.farmmacro.release;
import com.farmmacro.motion.*;
import com.farmmacro.camera.FreecamMath;
/** Small pure policy/migration/math table. No client, rendering, IO or workload simulation. */
public final class OwnershipQuickCheck {
 private static void ok(boolean b){if(!b)throw new IllegalStateException("shared Motion check");}
 private static MotionPolicy.Context c(boolean alarm,boolean freecam,boolean macro,boolean nav,boolean combat,boolean smooth){return new MotionPolicy.Context(true,false,false,true,alarm,freecam,macro,nav,combat,smooth);}
 public static void main(){
  ok(MotionPolicy.allowed(MotionPolicy.Owner.FARM,c(false,false,true,false,false,false)));
  ok(!MotionPolicy.allowed(MotionPolicy.Owner.FARM,c(false,false,true,true,false,false)));
  ok(MotionPolicy.allowed(MotionPolicy.Owner.NAVIGATION,c(false,false,true,true,true,true)));
  ok(MotionPolicy.allowed(MotionPolicy.Owner.COMBAT,c(false,false,false,false,true,false)));
  ok(!MotionPolicy.allowed(MotionPolicy.Owner.COMBAT,c(false,false,false,false,true,true)));
  for(var owner:MotionPolicy.Owner.values()){ok(!MotionPolicy.allowed(owner,c(true,false,true,true,true,true)));ok(!MotionPolicy.allowed(owner,c(false,true,true,true,true,true)));}
  ok(MotionPolicy.background(true,false,false,true));ok(!MotionPolicy.background(false,false,true,true));ok(!MotionPolicy.background(false,true,false,true));ok(MotionPolicy.background(false,true,true,true));
  var anchor=new FreecamMath.Point(0,64,0);var a=FreecamMath.step(anchor,anchor,0,1,0,0,8,.05,32);ok(a.z()==.4&&a.x()==0&&a.y()==64);
  var diagonal=FreecamMath.step(anchor,anchor,90,1,1,1,8,.05,32);ok(Math.abs(Math.sqrt(diagonal.x()*diagonal.x()+Math.pow(diagonal.y()-64,2)+diagonal.z()*diagonal.z())-.4)<1e-9);
  var edge=FreecamMath.step(new FreecamMath.Point(0,64,32),anchor,0,1,0,0,8,100,32);ok(edge.z()==32);
  var cfg=new com.farmmacro.config.ModConfig();cfg.aimTurnSpeed=111;cfg.aimTrackingMode="precise";cfg.aimAetherRevision=1;cfg.sanitize();ok(cfg.motionSharedRevision&&cfg.motion.camera.aimTurnSpeed==111&&cfg.motion.camera.mode(cfg.motion).equals("precise"));
  cfg.aimTurnSpeed=999;cfg.sanitize();ok(cfg.motion.camera.aimTurnSpeed==111);
  com.farmmacro.config.CombatProfiles.ensure(cfg);ok(com.farmmacro.config.CombatProfiles.select(cfg,com.farmmacro.config.CombatProfiles.ZEALOTS));ok(cfg.motion.camera.aimTurnSpeed==111);
  cfg.motion.backend="aether";cfg.sanitize();ok(cfg.motion.camera.mode(cfg.motion).equals("aether"));
  var copy=cfg.motion.copy();copy.camera.aimTurnSpeed=222;ok(cfg.motion.camera.aimTurnSpeed==111);
  cfg.motion.camera.aimTurnSpeed=Double.NaN;cfg.freecamSpeed=Double.NaN;cfg.freecamRadius=500;cfg.sanitize();ok(cfg.motion.camera.aimTurnSpeed==720&&cfg.freecamSpeed==8&&cfg.freecamRadius==64);
  System.out.println("OwnershipQuickCheck: PASS (exclusive camera/movement policy, background permissions, freecam math, one-shot migration/profile isolation; no game)");
 }
 private OwnershipQuickCheck(){}
}
