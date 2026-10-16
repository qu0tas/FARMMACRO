package com.farmmacro.release;
import com.farmmacro.stats.*;
import java.util.*;
/** Small deterministic parser tables, no Minecraft instance/network/clock sleeps. */
public final class TelemetryQuickCheck {
 private static void ok(boolean b,String msg){if(!b)throw new IllegalStateException("Telemetry: "+msg);}
 private static void eq(double a,double b,String msg){ok(Math.abs(a-b)<.001,msg);}
 public static void main(){Object w=new Object(),w2=new Object();long s=1_000_000_000L;var catalog=SkillCatalog.get("COMBAT");
  ok(SkillCatalog.all().size()==12&&catalog.maxLevel()==60&&SkillCatalog.get("FORAGING").maxLevel()==57,"catalog");
  var p=new SkillProgress(catalog);eq(p.observe("+7 Combat (10/50)",w,s),0,"baseline");eq(p.observe("+7 Combat (10/50)",w,2*s),0,"duplicate");eq(p.observe("+7 Combat (17/50)",w,3*s),7,"absolute gain");eq(p.observe("Combat (2/50)",w,4*s),0,"backwards ignored");eq(p.total(),17,"no rewind");
  eq(p.observe("Combat (20/50)",w2,5*s),0,"world rebase");eq(p.observe("Combat (30/50)",w2,21*s),0,"gap rebase");eq(p.gained(),7,"no invented gap xp");ok(!p.fresh(w,21*s)&&p.fresh(w2,21*s)&&!p.fresh(w2,82*s),"TTL");
  var farming=new FarmingProgress();eq(farming.observe("Farming (12/50)",w,s),0,"farming facade");eq(farming.observe("Combat (40/50)",w,2*s),0,"independent skill");eq(farming.total(),12,"no cross skill");
  var q=new SkillProgress(catalog);q.observe("Combat (0/125)",w,s);for(int i=1;i<=6;i++)q.observe("Combat ("+i+"/125)",w,(1+10*i)*s);eq(q.perHour(),360,"per skill warmup");eq(q.remaining(2),119,"remaining");eq(q.eta(2),1190,"eta");
  var early=new SkillProgress(catalog);early.observe("Combat (1/50)",w,s);ok(Double.isNaN(early.perHour()),"no premature rate");early.observe("Combat (2/50) Combat (3/50)",w,2*s);eq(early.total(),1,"duplicate fields rejected");early.observe("NotCombat (20/50)",w,2*s);eq(early.total(),1,"name boundary");early.observe("Combat (51/50)",w,2*s);eq(early.total(),1,"invalid progress");
  var ambiguous=new SkillProgress(new SkillCatalog.Skill("X","X",List.of(0d,50d,100d)));ambiguous.observe("X (10/50)",w,s);ok(Double.isNaN(ambiguous.total()),"ambiguous level rejected");q.contextLost();ok(!q.fresh(w,61*s),"lost context");q.reset();ok(Double.isNaN(q.gained()),"reset");
  var pet=PetProgress.parse(List.of("Ability: 2/5","Progress to Level 11: 25%","","1.25k/5k"),10,50000,w,s);ok(pet!=null&&pet.approximate(),"pet marker");eq(pet.remaining(),3750,"pet remaining not total");eq(pet.progress(),.25,"pet progress");ok(pet.fresh(w,60*s)&&!pet.fresh(w,61*s)&&!pet.fresh(w2,2*s),"pet independent TTL");
  ok(PetProgress.parse(List.of("Ability: 2/5"),10,Double.NaN,w,s)==null,"not ability ratio");
  ok(PetProgress.parse(List.of("Progress to Level 12: 25%","1/5"),10,Double.NaN,w,s)==null,"wrong next level");
  ok(PetProgress.parse(List.of("Progress to Level 11:","6/5"),10,Double.NaN,w,s)==null,"invalid pet progress");
  ok(PetProgress.parse(List.of("Progress to Level 11:","-1/5"),10,Double.NaN,w,s)==null,"negative pet ratio rejected");
  var totalOnly=PetProgress.parse(List.of("Ability: 2/5"),10,50000,w,s);ok(totalOnly!=null&&Double.isNaN(totalOnly.remaining()),"total != level XP");
  var max=PetProgress.parse(List.of("MAX LEVEL"),200,Double.NaN,w,s);ok(max!=null&&max.max()&&max.level()==200,"explicit max");eq(max.remaining(),0,"max remaining");
  ok(PetProgress.parse(List.of("MAX LEVEL 100"),100,Double.NaN,w,s)==null,"no guessed max");
  System.out.println("TelemetryQuickCheck: PASS (12 offline tables, absolute XP, independent skills, gaps/TTL/rate, Pets snapshots; no game)");
 }
 private TelemetryQuickCheck(){}
}
