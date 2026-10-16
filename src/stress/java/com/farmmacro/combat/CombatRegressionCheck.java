package com.farmmacro.combat;
import com.farmmacro.camera.*;
import com.farmmacro.config.*;
import java.util.*;
public final class CombatRegressionCheck {
 private static void check(boolean b,String what){if(!b)throw new IllegalStateException("CombatRegressionCheck: "+what);}
 public static void main(String... args){
  for(int fps:new int[]{20,30,60,144,360}){
   var t=new AetherTrackingTurn();double y=179,p=0;
   for(int f=0;f<fps*3;f++){
    var d=t.frame(y,p,-170,12,.5,240,1.0/fps);
    check(Double.isFinite(d.yaw())&&Double.isFinite(d.pitch()),"finite Aether steps");
    double q=MouseTurn.stepDeg(.5);
    check(Math.abs(d.yaw()/q-Math.rint(d.yaw()/q))<1e-7&&Math.abs(d.pitch()/q-Math.rint(d.pitch()/q))<1e-7,"sensitivity lattice");
    check(Math.hypot(d.yaw(),d.pitch())<=240.0/fps+q*2,"joint angular cap plus quantization");
    check(d.yaw()>=0&&d.pitch()>=0,"shortest wrapped direction");y+=d.yaw();p+=d.pitch();
   }
   check(Math.abs(TrackingTurn.wrap(-170-y))<.13&&Math.abs(12-p)<.13,"converges at "+fps+"fps");
  }
  var step=AetherTrackingTurn.step(0,0,120,80,100,.01);
  check(Math.hypot(step.yaw(),step.pitch())<=1.00001,"upstream joint-axis speed bound");
  int jitterDistinct=0;Set<Long> jitterPlans=new HashSet<>();
  for(int seed=0;seed<128;seed++){
   var jitter=new AimJitter(new SplittableRandom(seed));var human=new HumanAimTurn(new SplittableRandom(seed));
   double y=0,p=0;human.retarget(y,p,.5,.12,.22);
   for(int f=0;f<360;f++){
    var off=jitter.frame(1.0/120,.12);
    check(Math.abs(off.yaw())<=.120001&&Math.abs(off.pitch())<=.066001,"bounded jitter, no cumulative drift");
    if(f==150)jitterPlans.add(Double.doubleToLongBits(off.yaw()));
    var d=human.frame(y,p,35,8,.5,240,1.0/120,.5,1,.5,.12,.22);
    if(f<10)check(!human.perceived(),"no instantaneous Human perception");
    check(Double.isFinite(d.yaw())&&Double.isFinite(d.pitch()),"finite Human steps");
    check(Math.abs(d.yaw())<=240.0/120+.14&&Math.abs(d.pitch())<=240.0/120+.14,"bounded Human step");
    y+=d.yaw();p+=d.pitch();
   }
   check(human.perceived()&&Math.abs(TrackingTurn.wrap(35-y))<2&&Math.abs(8-p)<1,"Human eventually tracks");
   var zero=jitter.frame(1.0/60,0);check(zero.yaw()==0&&zero.pitch()==0,"disabled jitter resets");
  }
  jitterDistinct=jitterPlans.size();check(jitterDistinct==128,"seeded varied plans, not identical trace");
  var c=new ModConfig();c.aimTrackingMode="human";c.sanitize();check(c.aimTrackingMode.equals("aether")&&!c.aimAssistEnabled&&!c.aimAutoFire,"migration never grants automation");
  c.aimTrackingMode="human";c.sanitize();check(c.aimTrackingMode.equals("human"),"migration only once");
  String code=CombatProfiles.exportSelected(c);int profiles=c.combatProfiles.size();String err=CombatProfiles.importSelected(c,code);
  check(err==null&&c.combatProfiles.size()==profiles+1&&c.aimTrackingMode.equals("human"),"explicit Human profile import survives: "+err);
  check(TargetChoice.chainBand(4,10,20,8)==0&&TargetChoice.chainBand(40,160,170,8)==4,"local group before rear");
  check(TargetChoice.better(0,100,30,9,4,1,1,1),"local band before rear score");
  check(TargetChoice.deferFallback(true,4,0,100,200)&&!TargetChoice.deferFallback(true,4,0,200,200),"bounded rear wait");
  System.out.println("CombatRegressionCheck: OK (Aether 20/30/60/144/360fps;128 seeded Human/jitter plans; camera migration/import and target-choice rules; no live mobs/shots/client)");
 }
}
