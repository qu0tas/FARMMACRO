package com.farmmacro.release;
import com.farmmacro.route.*;
import com.farmmacro.motion.ManualStartPolicy;
/** Bounded smoke checks only. No Minecraft instance, entities, IO, timing sweeps or server. */
public final class AttendedQuickCheck {
 private static void ok(boolean b){if(!b)throw new IllegalStateException("quick check failed");}
 public static void main(String[] args){
  ok(ManualStartPolicy.allowed(.15,.15));ok(!ManualStartPolicy.allowed(.151,.15));ok(!ManualStartPolicy.allowed(Double.NaN,1));ok(!ManualStartPolicy.allowed(1,Double.POSITIVE_INFINITY));
  var s=new PointRoute();s.name="original";s.dimension="test";s.world="world";
  var p=new RoutePoint(10.7,64,20.4);p.ox=.2;p.oz=-.1;p.drop=true;p.dropDir="+x";p.farmKeys="wa";p.pointAction.lookBlock=true;p.pointAction.blockX=12;p.pointAction.blockY=64;p.pointAction.blockZ=21;s.points.add(p);s.points.add(new RoutePoint(14.5,64,20.5));s.settings.farm.yaw=30;s.settings.farm.firstKeys="wa";s.settings.routeRandom.alternatives.add(new RouteRandomSettings.Alternative("old"));
  var c=RouteCopies.copy(s,0,4,0,true,false);
  ok(Math.abs(c.points.getFirst().x-10.3)<1e-9);ok(c.points.getFirst().y==68);ok(c.points.getLast().x==6.5);ok(c.points.getFirst().dropDir.equals("-x"));ok(c.points.getFirst().farmKeys.equals("wd"));ok(c.settings.farm.yaw==-30);ok(c.settings.farm.firstKeys.equals("wd"));ok(c.points.getFirst().pointAction.blockX==8);ok(c.points.getFirst().pointAction.blockY==68);ok(c.settings.routeRandom.alternatives.isEmpty());ok(s.settings.routeRandom.alternatives.size()==1);ok(s.points.getFirst().x==10.7&&s.points.getFirst().y==64);ok(s.name.equals("original"));ok(c.name==null);
  var t=RouteCopies.copy(s,2,-4,3,false,false);ok(t.points.getFirst().x==12.7&&t.points.getFirst().y==60&&t.points.getFirst().z==23.4);
  var doubleMirror=RouteCopies.copy(RouteCopies.copy(s,0,0,0,true,true),0,0,0,true,true);ok(Math.abs(doubleMirror.points.getLast().x-s.points.getLast().x)<1e-9);ok(doubleMirror.settings.farm.yaw==30);
  boolean refused=false;try{RouteCopies.copy(s,0,4097,0,false,false);}catch(IllegalArgumentException e){refused=true;}ok(refused);
  var cfg=new com.farmmacro.config.ModConfig();cfg.returnEnabled=true;cfg.returnToStart=true;cfg.schedEnabled=true;cfg.autoReconnect=true;cfg.sessionSave=true;cfg.sanitize();ok(!cfg.returnEnabled&&!cfg.returnToStart&&!cfg.schedEnabled&&!cfg.autoReconnect&&!cfg.sessionSave&&cfg.requireStartPoint);cfg.returnEnabled=true;cfg.sanitize();ok(cfg.returnEnabled);
  cfg.hudStyle="LEGACY";cfg.sanitize();ok(cfg.hudStyle.equals("LEGACY"));cfg.hudStyle=null;cfg.sanitize();ok(cfg.hudStyle.equals("KAELIX"));
  com.farmmacro.macro.Scheduler.automatic(()->{throw new IllegalStateException("automatic callback ran");});ok(!com.farmmacro.macro.Scheduler.running());ok(!com.farmmacro.macro.Reconnect.wouldReconnect(null,true));ok(!com.farmmacro.macro.Reconnect.authorized());
  MotionQuickCheck.main();
  BindingQuickCheck.main();
  TelemetryQuickCheck.main();
  UiLayoutQuickCheck.main();
  OwnershipQuickCheck.main();
  ManualAimQuickCheck.main();
  ManualSlotQuickCheck.main();
  com.farmmacro.gui.ModuleRetirementCheck.main();
  System.out.println("AttendedQuickCheck: PASS (small start/transform/copy/config/retirement checks; no game or routeStress)");
 }
 private AttendedQuickCheck(){}
}
