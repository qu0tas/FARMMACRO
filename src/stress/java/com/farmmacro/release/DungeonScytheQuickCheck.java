package com.farmmacro.release;
import com.farmmacro.combat.*;
import com.farmmacro.config.*;
/** Small pure catalogue/prediction/config table only. No Minecraft bootstrap, rendering or server. */
public final class DungeonScytheQuickCheck {
 private static void ok(boolean b){if(!b)throw new IllegalStateException("dungeon/scythe regression");}
 private static void close(double a,double b){ok(Math.abs(a-b)<1e-8);}
 public static void main(){
  ok(DungeonTargets.BOSSES.size()==11&&DungeonTargets.MINIS.size()==5);
  for(String name:DungeonTargets.ALL){
   ok(BossTargets.canonical("§c[Lv100] "+name+" 50M❤").equals(name));
   ok(BossTargets.matches("dungeon:all",name,""));ok(!BossTargets.matches("slayers",name,""));
   ok(BossTargets.matches(name,name,""));ok(!BossTargets.canonical(name+"ling").equals(name));
  }
  for(String name:DungeonTargets.MINIS){ok(BossTargets.matches("dungeon:minis",name,""));ok(!BossTargets.matches("dungeon:bosses",name,""));}
  ok(BossTargets.canonical("§cVendetta Livid 6M❤").equals("Livid"));
  ok(BossTargets.canonical("Master Shadow Assassin 20M❤").equals("Shadow Assassin"));
  ok(!BossTargets.matches("dungeon:all","Zombie",""));ok(!BossTargets.matches("dungeon:unknown","Bonzo",""));
  ok(DungeonTargets.npcEvidence(2,"Bonzo"));ok(!DungeonTargets.npcEvidence(4,"Bonzo"));ok(!DungeonTargets.npcEvidence(3,"Livid"));ok(!DungeonTargets.npcEvidence(2,"Random Player"));
  ok(DungeonTargets.compatible("Lost Adventurer","entity.minecraft.player"));ok(!DungeonTargets.compatible("Thorn","entity.minecraft.player"));
  ok(DungeonTargets.compatible("Thorn","entity.minecraft.ghast"));ok(!DungeonTargets.compatible("Thorn","entity.minecraft.zombie"));
  ok(!DungeonTargets.compatible("The Watcher","entity.minecraft.armor_stand"));ok(DungeonTargets.compatible("Necron","entity.minecraft.wither"));
  ok(DungeonTargets.compatible("The Professor","entity.minecraft.elder_guardian"));ok(DungeonTargets.compatible("The Professor","entity.minecraft.guardian"));
  var still=ProjectileLead.solve(0,0,20,0,0,0,40,.05,.65,4);close(still.x(),0);close(still.y(),0);close(still.z(),0);
  var crossing=ProjectileLead.solve(0,0,20,4,0,0,40,0,1,6);
  double t=crossing.seconds();ok(t>.5&&t<.51);close(Math.hypot(4*t,20),40*t);close(crossing.x(),4*t);close(crossing.z(),0);
  var recede=ProjectileLead.solve(0,0,20,0,0,10,40,0,1,6);close(recede.seconds(),.6);close(recede.z(),6); // displacement cap
  var timed=ProjectileLead.solve(0,0,20,0,0,10,40,0,.2,4);close(timed.seconds(),.2);close(timed.z(),2);
  var toward=ProjectileLead.solve(0,0,20,0,0,-10,40,0,1,6);close(toward.seconds(),.4);close(toward.z(),-4);
  var delayed=ProjectileLead.solve(0,0,20,4,0,0,40,.1,1,6);ok(delayed.x()>crossing.x());
  ok(ProjectileLead.solve(0,0,20,0,0,24,10,0,1,4).seconds()==0);
  ok(ProjectileLead.solve(0,0,20,0,0,100,40,0,1,4).seconds()==0);
  ok(ProjectileLead.solve(Double.NaN,0,20,1,0,0,40,0,1,4).seconds()==0);
  ok(ProjectileLead.solve(0,0,20,1,0,0,0,0,1,4).seconds()==0);
  ok(ProjectileLead.plausibleStep(.2,0,0,.05));ok(!ProjectileLead.plausibleStep(5,0,0,.05));ok(!ProjectileLead.plausibleStep(0,0,0,1));ok(!ProjectileLead.plausibleStep(0,0,0,0));ok(!ProjectileLead.plausibleStep(Double.NaN,0,0,.05));
  var c=new ModConfig();c.sanitize();ok(c.aimManualScytheLead&&c.aimManualScytheFeet&&c.aimManualScytheSpeed==40&&c.aimManualScytheDelayMs==50);
  c.aimManualOnly=true;c.aimManualTarget="dungeon:minis";c.aimManualScytheSpeed=Double.NaN;c.aimManualScytheMaxLead=99;c.aimManualScytheDelayMs=-99;c.sanitize();
  ok(c.aimManualScytheSpeed==40&&c.aimManualScytheMaxLead==1&&c.aimManualScytheDelayMs==0);
  c.aimManualScytheLead=false;c.aimManualScytheFeet=false;CombatProfiles.ensure(c);CombatProfiles.select(c,CombatProfiles.ZEALOTS);
  ok(c.aimManualOnly&&c.aimManualTarget.equals("dungeon:minis")&&!c.aimManualScytheLead&&!c.aimManualScytheFeet);
  var snapshot=CombatProfiles.snapshot(c);for(String key:new String[]{"aimManualScytheLead","aimManualScytheFeet","aimManualScytheSpeed","aimManualScytheMaxLead","aimManualScytheDelayMs"})ok(!snapshot.has(key));
  System.out.println("DungeonScytheQuickCheck: PASS (16 names/groups, NPC exclusion/type rules, bounded intercept, reset evidence and profile isolation; no game)");
 }
 private DungeonScytheQuickCheck(){}
}
