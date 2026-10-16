package com.farmmacro.release;
import com.farmmacro.motion.*;
import com.farmmacro.route.*;
import java.util.SplittableRandom;
/** A few bounded pure smoke cases, not a flight/gameplay or anti-cheat test. */
public final class MotionQuickCheck {
 private static void ok(boolean v,String what){if(!v)throw new IllegalStateException(what);}
 private static void near(double a,double b){ok(Math.abs(a-b)<1e-6,a+" != "+b);}
 public static void main(){
  var s=new MotionSettings();ok(!s.aether(),"old backend default");s.backend="aether";s.preset("normal");
  ok(s.rotationMs==100&&s.msPerDegree==2&&s.laneMaxMs==500&&s.yawRange==0&&s.pitchRange==0,"normal source values");
  s.preset("efficient");ok(s.msPerDegree==6&&s.laneMaxMs==1000,"efficient source values");s.preset("safe");ok(s.msPerDegree==2&&s.laneMaxMs==1000,"safe source values");s.preset("normal");
  near(AetherTurn.wrap(-358),2);near(AetherTurn.wrap(358),-2);
  for(var e:AetherEasing.values()){near(e.ease(0),0);near(e.ease(1),1);ok(Float.isFinite(e.ease(.45f)),"curve finite");}
  near(AetherTurn.easing(0,s),0);near(AetherTurn.easing(1,s),1);near(AetherTurn.easing(.5,s),.5);
  // <=300 frames total; high-FPS coarse sensitivity must retain subpixel intent.
  var t=new AetherTurn(179,0,-179,0,false,s,new SplittableRandom(1));double y=179,pitch=0;
  for(int i=0;i<80&&!t.finished();i++){var d=t.frame(y,pitch,-179,0,1,120,1.0/120);ok(Math.hypot(d.yaw(),d.pitch())<=7.2+1e-8,"angular cap");y+=d.yaw();pitch+=d.pitch();}
  ok(Math.abs(AetherTurn.wrap(-179-y))<=AetherTurn.tolerance(1),"shortest timed convergence");
  t=new AetherTurn(0,0,2,1,true,s,new SplittableRandom(2));y=pitch=0;
  for(int i=0;i<180;i++){var d=t.frame(y,pitch,2,1,1,120,1.0/240);y+=d.yaw();pitch+=d.pitch();}
  ok(Math.abs(2-y)<=AetherTurn.tolerance(1)&&Math.abs(1-pitch)<=AetherTurn.tolerance(1),"tracking residual convergence");
  var d=t.frame(y,pitch,-20,0,1,120,.016);ok(d.yaw()<=0&&!t.finished(),"retarget direction");
  d=t.frame(Double.NaN,0,0,0,.5,120,.01);ok(d.yaw()==0&&d.pitch()==0&&t.finished(),"invalid input failclosed");
  var frozen=s.copy();var snap=new AetherTurn(0,0,30,0,false,s,new SplittableRandom(1));
  var same=new AetherTurn(0,0,30,0,false,frozen,new SplittableRandom(1));s.msPerDegree=20;s.curve="LINEAR";
  near(snap.frame(0,0,30,0,.5,120,.016).yaw(),same.frame(0,0,30,0,.5,120,.016).yaw());
  s.msPerDegree=Double.NaN;s.noiseMinPct=Double.POSITIVE_INFINITY;s.curve="bad";s.backend=null;s.preset=null;s.sanitize();
  ok(Double.isFinite(s.msPerDegree)&&Double.isFinite(s.noiseMinPct)&&s.curve.equals("MANAGER")&&!s.aether(),"sanitize");
  s.backend="aether";s.preset("normal");
  var gson=new com.google.gson.Gson();var encoded=gson.toJson(s);var decoded=gson.fromJson(encoded,MotionSettings.class);decoded.sanitize();
  ok(decoded.aether()&&decoded.rotationMs==s.rotationMs&&decoded.laneMaxMs==s.laneMaxMs,"config roundtrip");
  var old=gson.fromJson("{}",com.farmmacro.config.ModConfig.class);old.sanitize();ok(old.motion!=null&&!old.motion.aether(),"old config default");
  // Exact one-quantum steps must never exceed the accumulated angular allowance.
  double total=0;var capped=new AetherTurn(0,0,90,0,true,s,new SplittableRandom(4));double cy=0;
  for(int i=0;i<12;i++){var step=capped.frame(cy,0,90,0,1,20,.001);cy+=step.yaw();total+=Math.abs(step.yaw());ok(total<=20*.001*(i+1)+1e-8,"cumulative speed cap");}
  var r=new PointRoute();r.settings.farm.enabled=true;r.settings.farm.fastSwitch=true;
  r.points.add(new RoutePoint(0,64,0));r.points.add(new RoutePoint(4,64,0));r.points.add(new RoutePoint(8,64,0));r.points.add(new RoutePoint(8,64,4));
  s.laneMinMs=s.laneMaxMs=300;var copy=FarmBoundaryPauses.prepare(r,s,new SplittableRandom(3));
  ok(copy.points.get(1).pauseTicks==0&&copy.points.get(2).pauseTicks==6,"corner vs collinear");ok(r.points.get(2).pauseTicks==0,"source immutability");
  r.points.get(2).pauseTicks=20;copy=FarmBoundaryPauses.prepare(r,s,new SplittableRandom(3));ok(copy.points.get(2).pauseTicks==20,"preserve explicit pause");
  r.settings.farm.fastSwitch=false;r.settings.farm.switchPauseTicks=4;r.points.get(2).farmTurn=true;r.points.get(2).pauseTicks=0;copy=FarmBoundaryPauses.prepare(r,s,new SplittableRandom(3));ok(copy.points.get(2).pauseTicks==2,"max not sum with existing switch");
  r.points.get(2).drop=true;s.floorMinMs=s.floorMaxMs=350;r.points.get(2).beforeDropTicks=2;copy=FarmBoundaryPauses.prepare(r,s,new SplittableRandom(3));ok(copy.points.get(2).pauseTicks==1&&copy.points.get(2).landPauseTicks==7,"drop/landing pause budget");
  s.boundaryPauses=false;copy=FarmBoundaryPauses.prepare(r,s,new SplittableRandom(3));ok(copy.points.get(2).pauseTicks==0,"disabled");
  System.out.println("MotionQuickCheck: PASS (presets/easing/quantized turns/boundary copies; <=276 frames, no game)");
 }
 private MotionQuickCheck(){}
}
