package com.farmmacro.combat;

import com.farmmacro.config.ModConfig;
import com.farmmacro.route.WalkCore;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Explicit manual-only exception for confirmed Egg Sac models, NOT a general ArmorStand target mode.
 * Independent implementation from observed SkyHanni label facts, provenance in SLAYER-CATALOG.md.
 * The head visual center is approximate, not a claim about the server's arrow hitbox. */
public final class EggSacAim {
 private record Target(ArmorStand body,ArmorStand timer,ArmorStand prompt,Mob boss){}
 private Target lock;
 public void clear(){lock=null;}
 private static String name(ArmorStand e){return e.getCustomName()==null?"":e.getCustomName().getString();}
 private static boolean live(Minecraft mc,ArmorStand e){return e!=null&&e.level()==mc.level&&!e.isRemoved()&&!e.isDeadOrDying();}
 private static boolean model(ArmorStand e){return !e.getItemBySlot(EquipmentSlot.HEAD).isEmpty();}
 private static boolean permitted(ModConfig c){return c.aimManualOnly&&c.aimManualEggSacs&&
  (BossTargets.matches(c.aimManualTarget,"Tarantula Broodfather",c.aimManualName)
   ||BossTargets.matches(c.aimManualTarget,"Primordial Broodfather",c.aimManualName)
   ||BossTargets.matches(c.aimManualTarget,"Conjoined Brood",c.aimManualName));}
 private static boolean area(Mob boss,ArmorStand body){return EggSacEvidence.bossArea(body.getX()-boss.getX(),body.getY()-boss.getY(),body.getZ()-boss.getZ());}
 private static Vec3 center(ArmorStand body,float partial){return body.getPosition(partial).add(0,EggSacEvidence.headHeight(body.isSmall()),0);}
 private static boolean evidence(Minecraft mc,Target t){
  return live(mc,t.body)&&live(mc,t.timer)&&live(mc,t.prompt)&&VisibleBody.valid(mc,t.boss)
   &&EggSacEvidence.timer(name(t.timer))&&EggSacEvidence.prompt(name(t.prompt))&&model(t.body)
   &&(t.body==t.timer||EggSacEvidence.bodyName(name(t.body)))&&area(t.boss,t.body)
   &&EggSacEvidence.linked(t.timer.position().distanceTo(t.prompt.position()),t.timer.position().distanceTo(t.body.position()));
 }
 public Vec3 framePoint(Minecraft mc,ModConfig c,float partial){
  if(lock==null)return null;
  if(!permitted(c)||!evidence(mc,lock)){clear();return null;}
  Vec3 point=center(lock.body,partial),eye=mc.player.getEyePosition(partial);
  return eye.distanceTo(point)<=c.aimManualRange&&ShotPath.clear(mc,eye,point)?point:null;
 }
 public boolean update(Minecraft mc,ModConfig c,Mob previous,String previousName,int tick){
  if(!permitted(c)){clear();return false;}
  if(lock!=null){
   if(framePoint(mc,c,1)!=null)return true;
   // Do not hold an occluded/stale visual while turning away from visible egg models.
   clear();
  }
  if(tick%2!=0)return false;
  var bosses=new ArrayList<Mob>();
  if(VisibleBody.valid(mc,previous)&&!EggSacEvidence.spider(previousName))return false;
  if(VisibleBody.valid(mc,previous)&&EggSacEvidence.spider(previousName))bosses.add(previous);
  else for(var n:MobNames.nearby(mc,c.aimManualRange)){
   if(EggSacEvidence.spider(n.name())&&BossTargets.matches(c.aimManualTarget,n.name(),c.aimManualName)&&VisibleBody.valid(mc,n.mob())){
    bosses.add(n.mob());if(bosses.size()==4)break;
   }
  }
  if(bosses.isEmpty())return false;
  var timers=new ArrayList<ArmorStand>();var prompts=new ArrayList<ArmorStand>();var models=new ArrayList<ArmorStand>();
  // Tick-local bounded discovery, not a trust source for liveness. Rechecked every rendered frame.
  for(var e:com.farmmacro.util.LoadedEntities.get(mc)){
   if(!(e instanceof ArmorStand stand)||!live(mc,stand)||stand.distanceToSqr(mc.player)>(c.aimManualRange+3)*(c.aimManualRange+3))continue;
   if(bosses.stream().noneMatch(b->area(b,stand)))continue;
   String label=name(stand);
   if(timers.size()<16&&EggSacEvidence.timer(label))timers.add(stand);
   if(prompts.size()<32&&EggSacEvidence.prompt(label))prompts.add(stand);
   if(models.size()<128&&model(stand)&&(EggSacEvidence.bodyName(label)||EggSacEvidence.timer(label)))models.add(stand);
  }
  Target best=null;double bestScore=Double.POSITIVE_INFINITY;Vec3 eye=mc.player.getEyePosition();int rays=0;
  for(var timer:timers){
   var prompt=prompts.stream().filter(p->p.position().distanceTo(timer.position())<2.5)
    .min(Comparator.comparingDouble(p->p.distanceToSqr(timer))).orElse(null);if(prompt==null)continue;
   // Choose the actual equipped-head model near this timer, never the floating text label alone.
   var body=models.stream().filter(p->p.position().distanceTo(timer.position())<1.2)
    .min(Comparator.comparingDouble(p->p.distanceToSqr(timer))).orElse(null);if(body==null)continue;
   Mob boss=bosses.stream().filter(b->area(b,body)).min(Comparator.comparingDouble(b->b.distanceToSqr(body))).orElse(null);if(boss==null)continue;
   Vec3 point=center(body,1);double distance=eye.distanceTo(point);if(distance>c.aimManualRange)continue;
   double dx=point.x-eye.x,dy=point.y-eye.y,dz=point.z-eye.z;
   double yaw=Math.toDegrees(Math.atan2(-dx,dz)),pitch=-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
   double error=Math.hypot(WalkCore.wrap((float)(yaw-mc.player.getYRot())),pitch-mc.player.getXRot());
   // Visible eggs can appear above/behind the held boss: explicit priority uses range/LOS, not initial boss FOV.
   if(++rays>16||!ShotPath.clear(mc,eye,point))continue;
   double score=BossTargets.score(error,distance);
   if(score<bestScore||score==bestScore&&(best==null||body.getId()<best.body.getId())){bestScore=score;best=new Target(body,timer,prompt,boss);}
  }
  lock=best;return lock!=null;
 }
 public boolean locked(){return lock!=null;}

}
