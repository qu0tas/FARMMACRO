package com.farmmacro.release;
import com.farmmacro.combat.*;
import com.farmmacro.config.*;
import java.util.*;
/** Small pure regression tables, not entity/raycast/game integration tests. */
public final class HuntingQuickCheck {
 private static void ok(boolean b,String why){if(!b)throw new IllegalStateException("hunting: "+why);}
 public static void main(){
  for(String type:List.of("frog","tadpole","axolotl","cod","salmon","pufferfish","tropical_fish","squid","glow_squid")){
   ok(HuntingTargets.body("hunting:all","Unknown server name","entity.minecraft."+type),type);
   ok(HuntingTargets.body("hunting:nearby","Unknown server name","entity.minecraft."+type),"near "+type);
  }
  ok(HuntingTargets.target("Azure")&&HuntingTargets.target("hunting:nearby")&&!HuntingTargets.target("Sven Packmaster"),"individual hunting lead routing");
  ok(!HuntingTargets.body("hunting:all","Unknown","entity.minecraft.zombie"),"all is not all monsters");
  ok(HuntingTargets.body("hunting:nearby","Unknown","entity.minecraft.zombie"),"explicit nearby");
  ok(!HuntingTargets.body("slayers","Unknown","entity.minecraft.frog"),"isolated target mode");
  ok(!HuntingTargets.body("hunting:unknown","Frog","entity.minecraft.frog"),"unknown selection");
  ok(HuntingTargets.matches("hunting:all","§a[Lv. 80] Azure 100/200❤"),"label formatting");
  ok(HuntingTargets.matches("hunting:hunting_marsh","[Lv20] Coralot 12k♥"),"known axolotl");
  ok(!HuntingTargets.matches("hunting:all","Azureling"),"not substring");
  ok(HuntingTargets.acquisitionScore(2,120)<HuntingTargets.acquisitionScore(3,0),"nearest not angle");
  var c=new ModConfig();c.huntingAimProjectileSpeed=Double.NaN;c.huntingAimMaxLead=99;c.huntingAimLeadDelayMs=-20;c.sanitize();
  ok(c.huntingAimLead&&!c.huntingAimLeadOtherItems,"defaults/cap");
  ok(c.huntingAimProjectileSpeed==40&&c.huntingAimMaxLead==1&&c.huntingAimLeadDelayMs==0,"finite lead clamps");
  c.aimManualTarget="hunting:nearby";c.aimManualOnly=true;CombatProfiles.ensure(c);CombatProfiles.select(c,CombatProfiles.ZEALOTS);
  ok(c.aimManualTarget.equals("hunting:nearby")&&c.aimManualOnly,"profile does not change manual target");
  for(String key:List.of("huntingAimLead","huntingAimProjectileSpeed"))ok(!CombatProfiles.snapshot(c).has(key),"profile isolation "+key);
  var lead=ProjectileLead.solve(0,0,15,3,0,0,40,.05,.65,4);ok(lead.x()>0&&lead.x()<=4,"moving target ahead");
  var stopped=ProjectileLead.solve(0,0,15,0,0,0,40,.05,.65,4);ok(stopped.x()==0&&stopped.z()==0,"stationary target no offset");
  ok(!ProjectileLead.plausibleStep(10,0,0,.05),"teleport rejected");
  System.out.println("HuntingQuickCheck: PASS (9 aquatic body types, explicit nearest filter, formatted labels, lead/config/profile tables; no game)");
 }
 private HuntingQuickCheck(){}
}
