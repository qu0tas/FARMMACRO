package com.farmmacro.release;
import com.farmmacro.panic.FarmStopDelay;
/** Bounded deadline/policy table; no client/server or timing simulation. */
public final class PanicStopQuickCheck {
 private static void ok(boolean v){if(!v)throw new IllegalStateException("panic stop check");}
 public static void main(){
  for(String id:new String[]{"playerNear","chat","nearbyChat","staff","pm","title","bps","farmingXp","suspicion"}){
   ok(FarmStopDelay.eligible(id,true,false,true));ok(!FarmStopDelay.eligible(id,false,false,true));
   ok(!FarmStopDelay.eligible(id,true,true,true));ok(!FarmStopDelay.eligible(id,true,false,false));
  }
  for(String id:new String[]{"combatGaze","combatTeleport","combatWeapon","combatChat","combatOverlap","combatRotation","safetyTeleport","safetyWeapon","serverMove","heldItem","damage","disconnect","gui","obstacle","stuck","newUnknown"})ok(!FarmStopDelay.eligible(id,true,false,true));
  ok(!FarmStopDelay.eligible(null,true,false,true));
  var d=new FarmStopDelay();ok(!d.active());d.arm("chat","first",100,350);
  ok(!d.due(350_000_099L));ok(d.due(350_000_100L));
  d.arm("staff","second",500_000_000L,1200);ok(d.id().equals("chat")&&d.reason().equals("first")&&d.due(350_000_100L));
  d.clear();ok(!d.active()&&!d.due(Long.MAX_VALUE)&&d.reason()==null);
  d.arm("chat","bounded",0,99999);ok(!d.due(1_199_999_999L)&&d.due(1_200_000_000L));
  d.clear();d.arm("chat","zero",50,-100);ok(d.due(50));d.clear();
  var c=new com.farmmacro.config.ModConfig();c.sanitize();ok(c.farmPanicDelay&&c.farmPanicMinMs==350&&c.farmPanicMaxMs==750);
  c.farmPanicMinMs=2000;c.farmPanicMaxMs=-1;c.sanitize();ok(c.farmPanicMinMs==1200&&c.farmPanicMaxMs==1200);
  c.farmPanicDelay=false;c.sanitize();ok(!c.farmPanicDelay);
  ok(!com.farmmacro.panic.PanicHandoff.pending());ok(!com.farmmacro.panic.PanicHandoff.onKey(null,1,null));ok(!com.farmmacro.panic.PanicHandoff.onButton(null,1,null));
  System.out.println("PanicStopQuickCheck: PASS (soft farm only, combat/emergency immediate, first deadline, cancel/clamps/config, no B latch; no game)");
 }
 private PanicStopQuickCheck(){}
}
