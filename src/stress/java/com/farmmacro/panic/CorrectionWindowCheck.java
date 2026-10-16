package com.farmmacro.panic;
public final class CorrectionWindowCheck {
 private static void check(boolean value,String what){if(!value)throw new IllegalStateException("CorrectionWindowCheck: "+what);}
 public static void main(String... args){
  var w=new CorrectionWindow();
  for(int i=0;i<4;i++)check(w.accept(i*100_000_000L,3,4,2,.1),"four allowed packets");
  check(!w.accept(400_000_000L,3,4,2,.1),"fifth correction stops");
  check(!w.accept(3_000_000_000L,3,4,2,.1),"window inclusive boundary");
  check(w.accept(3_000_000_001L,3,4,2,.1),"oldest expires after boundary");
  w.reset();check(w.accept(0,3,4,2,.8)&&w.accept(1,3,4,2,.8),"sum baseline");
  check(!w.accept(2,3,4,2,.5),"A/B corrections count path length, not displacement");
  check(w.accept(4_000_000_000L,3,4,2,1.9),"expired sum budget");
  for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1}){w.reset();check(!w.accept(0,3,4,2,bad),"bad distance");w.reset();check(!w.accept(0,3,4,bad,.1),"bad budget");}
  w.reset();check(!w.accept(0,0,4,2,.1)&&!w.accept(0,3,0,2,.1),"invalid limits");
  w.reset();for(int i=0;i<12;i++)check(w.accept(i,3,100,100,.1),"resource boundary "+i);
  check(!w.accept(12,3,100,100,.1),"hard twelve sample bound");
  check(com.farmmacro.config.ModConfig.INSTANCE.combatPanic.playerOverlap,"overlap default on");
  check(CombatPanic.isCombatId("combatOverlap")&&AutomationSafety.manualHandoff("combatOverlap"),"overlap uses local combat handoff");
  System.out.println("CorrectionWindowCheck: OK (count/sum/time/reset/invalid input/resource bounds; Combat overlap routing, not a live player test)");
 }
}
