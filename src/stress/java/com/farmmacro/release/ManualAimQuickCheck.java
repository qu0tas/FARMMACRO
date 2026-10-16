package com.farmmacro.release;
import com.farmmacro.combat.BossTargets;
import com.farmmacro.motion.DirectAimTurn;
/** Tiny pure name/filter/direct-turn/config regression table; never starts Minecraft. */
public final class ManualAimQuickCheck {
 private static void ok(boolean value){if(!value)throw new IllegalStateException("manual aim regression");}
 public static void main(){
  ok(BossTargets.canonical("§c[Lv100] Revenant Horror 3.2M❤").equals("Revenant Horror"));
  ok(BossTargets.canonical("[Lv500] Voidgloom Seraph 12M/210M❤").equals("Voidgloom Seraph"));
  ok(BossTargets.canonical("Revenant Champion").isEmpty());ok(BossTargets.canonical("Sven Alpha").isEmpty());ok(BossTargets.canonical("Voidgloom Seraphling").isEmpty());
  for(String boss:BossTargets.BOSSES)ok(BossTargets.matches("slayers",boss,""));
  ok(!BossTargets.matches("slayers","Zombie",""));ok(!BossTargets.matches("Revenant Horror","Sven Packmaster",""));
  ok(BossTargets.matches("custom","Ashfang","Ashfang"));ok(BossTargets.matches("Zombie","zombie",""));
  ok(BossTargets.compatible("Sven Packmaster","entity.minecraft.wolf"));ok(!BossTargets.compatible("Sven Packmaster","entity.minecraft.zombie"));
  ok(!BossTargets.compatible("Voidgloom Seraph","entity.minecraft.zombie"));
  var branches=com.farmmacro.combat.SlayerCatalog.BRANCHES;
  ok(branches.size()==6&&BossTargets.BOSSES.size()==7);
  ok(branches.stream().mapToInt(b->b.minis().size()).sum()==20);
  ok(branches.stream().mapToInt(b->b.forms().size()).sum()==3);
  for(var b:branches){
   for(String n:b.bossForms())ok(BossTargets.matches("slayer:"+b.id()+":boss",n,""));
   for(String n:b.minis()){ok(!BossTargets.matches("slayer:"+b.id()+":boss",n,""));ok(BossTargets.matches("slayer:"+b.id()+":encounter",n,""));}
   ok(!BossTargets.matches("slayer:"+b.id()+":encounter","Random Player",""));
   var g=com.farmmacro.combat.MobCatalog.get("slayer_"+b.id());for(String n:b.encounter())ok(g.mobs().contains(n));
  }
  ok(!BossTargets.matches("slayers","Quazii",""));ok(!BossTargets.matches("slayer:zombie:encounter","Sven Alpha",""));
  ok(BossTargets.canonical("IMMUNE Quazii 1M❤").equals("Quazii"));ok(BossTargets.canonical("Quaziiling").isEmpty());
  ok(BossTargets.canonical("Conjoined Brood 20M❤").equals("Conjoined Brood"));
  ok(BossTargets.compatible("Quazii","entity.minecraft.wither_skeleton"));ok(!BossTargets.compatible("Quazii","entity.minecraft.blaze"));
  ok(BossTargets.compatible("Typhoeus","entity.minecraft.zombified_piglin"));ok(!BossTargets.compatible("Typhoeus","entity.minecraft.zombie"));
  ok(BossTargets.compatible("Conjoined Brood","entity.minecraft.spider"));
  var wrap=DirectAimTurn.frame(179,0,-179,0,720,.05);ok(Math.abs(wrap.yaw()-2)<1e-9&&wrap.pitch()==0);
  var slow=DirectAimTurn.frame(0,0,90,0,100,.05);ok(slow.yaw()==5&&slow.pitch()==0);
  var diagonal=DirectAimTurn.frame(0,0,90,50,100,.05);ok(Math.abs(Math.hypot(diagonal.yaw(),diagonal.pitch())-5)<1e-9);
  var settle=DirectAimTurn.frame(0,0,1,2,1440,.05);ok(settle.yaw()==1&&settle.pitch()==2);
  var cap=DirectAimTurn.frame(0,0,90,0,100,100);ok(cap.yaw()==5);
  ok(DirectAimTurn.frame(0,0,Double.NaN,0,100,.05).yaw()==0);
  double yaw=0;for(int i=0;i<10;i++)yaw+=DirectAimTurn.frame(yaw,0,90,0,100,.01).yaw();ok(Math.abs(yaw-10)<1e-9);
  ok(com.farmmacro.combat.EggSacEvidence.timer("§e5s 1/3"));ok(com.farmmacro.combat.EggSacEvidence.timer("12s 3/4"));
  ok(!com.farmmacro.combat.EggSacEvidence.timer("Egg 5s 1/3"));ok(!com.farmmacro.combat.EggSacEvidence.timer("12.5s 1/3"));ok(!com.farmmacro.combat.EggSacEvidence.timer("5s 1/3 HP"));
  ok(com.farmmacro.combat.EggSacEvidence.prompt("§cSHOOT ME!"));ok(!com.farmmacro.combat.EggSacEvidence.prompt("SHOOT ME! hello"));
  ok(com.farmmacro.combat.EggSacEvidence.bodyName("Armor Stand"));ok(com.farmmacro.combat.EggSacEvidence.bodyName(""));ok(!com.farmmacro.combat.EggSacEvidence.bodyName("My pet"));
  ok(com.farmmacro.combat.EggSacEvidence.spider("Primordial Broodfather"));ok(!com.farmmacro.combat.EggSacEvidence.spider("Sven Packmaster"));
  ok(com.farmmacro.combat.EggSacEvidence.linked(2.49,1.19));ok(!com.farmmacro.combat.EggSacEvidence.linked(2.5,1));ok(!com.farmmacro.combat.EggSacEvidence.linked(1,1.2));ok(!com.farmmacro.combat.EggSacEvidence.linked(Double.NaN,1));
  ok(com.farmmacro.combat.EggSacEvidence.bossArea(5,3,5));ok(!com.farmmacro.combat.EggSacEvidence.bossArea(20,3,0));ok(!com.farmmacro.combat.EggSacEvidence.bossArea(1,-1,1));ok(!com.farmmacro.combat.EggSacEvidence.bossArea(1,25,1));
  ok(com.farmmacro.combat.EggSacEvidence.headHeight(true)==.85&&com.farmmacro.combat.EggSacEvidence.headHeight(false)==1.7);
  var c=new com.farmmacro.config.ModConfig();c.sanitize();ok(!c.aimManualOnly&&c.aimManualTarget.equals("slayers")&&c.aimManualEggSacs);
  c.aimManualOnly=true;c.aimManualTarget="Sven Packmaster";c.aimManualRange=Double.NaN;c.aimManualFov=999;c.sanitize();ok(c.aimManualRange==32&&c.aimManualFov==180);
  com.farmmacro.config.CombatProfiles.ensure(c);com.farmmacro.config.CombatProfiles.select(c,com.farmmacro.config.CombatProfiles.ZEALOTS);ok(c.aimManualOnly&&c.aimManualTarget.equals("Sven Packmaster"));
  ok(!com.farmmacro.config.CombatProfiles.snapshot(c).has("aimManualOnly"));ok(!com.farmmacro.config.CombatProfiles.snapshot(c).has("aimManualEggSacs"));
  System.out.println("ManualAimQuickCheck: PASS (boss names/type filters, deterministic bounded center turns, config/profile isolation; no game)");
 }
 private ManualAimQuickCheck(){}
}
