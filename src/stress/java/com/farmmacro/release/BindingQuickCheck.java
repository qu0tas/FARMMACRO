package com.farmmacro.release;
import com.farmmacro.motion.*;
/** Small deterministic lease/deadline/context table. No client initialization, world or simulation. */
public final class BindingQuickCheck {
 private static void ok(boolean b,String why){if(!b)throw new IllegalStateException(why);}
 public static void main(){
  var l=new CameraTurnLease();ok(l.acquire(Double.NaN,0)==null&&!l.busy(),"reject invalid target");
  ok(l.acquire(0,90)==null,"clamped pitch required");var t=l.acquire(179,3);ok(l.busy()&&l.owns(t),"acquire");
  ok(l.acquire(-20,5)==null,"never steal pending turn");ok(!l.release(t),"cannot ack before completion");
  var other=new CameraTurnLease();var unrelated=other.acquire(0,0);other.arrive();ok(!l.release(unrelated)&&l.owns(t),"wrong ticket");
  var gate=new BindingTurnGate(t,1,64,2,1000,5);
  ok(gate.check(1,64,2,179,3,.5,true,2000)==BindingTurnGate.Result.WAIT,"angle alone not arrival");
  l.arrive();ok(l.busy()&&l.acquire(1,0)==null,"retain ownership until observed ack");
  ok(gate.check(1,64,2,-181,3,.5,true,2000)==BindingTurnGate.Result.READY,"wrapped arrival");
  ok(gate.check(1,64,2,160,3,.5,true,2000)==BindingTurnGate.Result.FAILED,"angle mismatch after completion");
  ok(gate.check(1,64,2,179,3,.5,false,2000)==BindingTurnGate.Result.FAILED,"context world/player/focus/support/panic");
  ok(gate.check(2,64,2,179,3,.5,true,2000)==BindingTurnGate.Result.FAILED,"horizontal shove");
  ok(gate.check(1,65,2,179,3,.5,true,2000)==BindingTurnGate.Result.FAILED,"fall/floor change");
  ok(gate.check(Double.NaN,64,2,179,3,.5,true,2000)==BindingTurnGate.Result.FAILED,"invalid observation");
  ok(gate.check(1,64,2,179,3,.5,true,5000001000L)==BindingTurnGate.Result.FAILED,"exact timeout");
  ok(gate.check(1,64,2,179,3,.5,true,999)==BindingTurnGate.Result.FAILED,"clock moved back");
  ok(l.release(t)&&!l.busy(),"ack releases owner");
  var newer=l.acquire(0,0);ok(!l.release(t)&&l.owns(newer),"stale ack cannot release newer turn");
  l.cancel();ok(newer.state()==CameraTurnLease.State.CANCELLED&&!l.busy(),"stop not arrival");
  gate=new BindingTurnGate(newer,0,64,0,0,5);ok(gate.check(0,64,0,0,0,.5,true,1)==BindingTurnGate.Result.FAILED,"cancel never resumes even at goal");
  var s=new MotionSettings();s.bindingSpeed=Double.POSITIVE_INFINITY;s.sanitize();ok(s.bindingSpeed==120&&s.smoothBindings,"old default");
  s.bindingSpeed=20;ok(s.copy().bindingSpeed==20&&s.copy().smoothBindings,"snapshot settings");
  double limit=BindingTurnGate.timeout(180,178,20,s);ok(limit>16&&limit<=30,"slow diagonal deadline");
  s.bindingSpeed=80;s.smoothBindings=false;s.preset("safe");ok(s.bindingSpeed==80&&!s.smoothBindings,"preset not clobber local choice");
  var gson=new com.google.gson.Gson();var saved=gson.fromJson(gson.toJson(s),MotionSettings.class);saved.sanitize();ok(!saved.smoothBindings&&saved.bindingSpeed==80,"persist opt-out/speed");
  System.out.println("BindingQuickCheck: PASS (lease/stale-owner/cancel/angle/context/anchor/deadline table; no game)");
 }
 private BindingQuickCheck(){}
}
