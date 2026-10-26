package com.farmmacro.combat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.decoration.ArmorStand;
import java.util.*;
/** Manual-only discovery. Mob names retain the existing association; named v2 NPCs are separate. */
public final class ManualTargets {
 public record Named(LivingEntity body,String name){}
 public static boolean valid(Minecraft mc,LivingEntity e){return mc.player!=null&&mc.level!=null&&e!=null&&e!=mc.player&&e.level()==mc.level&&e.isAlive()&&!e.isDeadOrDying()&&!e.isRemoved()&&!e.isInvisibleTo(mc.player);}
 public static List<Named> nearby(Minecraft mc,double range){
  var out=new ArrayList<Named>();if(mc.player==null||mc.level==null)return out;
  for(var n:MobNames.nearby(mc,range))if(!DungeonTargets.ALL.contains(n.name())||DungeonTargets.compatible(n.name(),n.mob().getType().getDescriptionId()))out.add(new Named(n.mob(),HuntingTargets.known(n.name())?HuntingTargets.normalize(n.name()):n.name()));
  var npcs=new ArrayList<Player>();var labels=new ArrayList<ArmorStand>();double rr=(range+4)*(range+4);
  for(var e:com.farmmacro.util.LoadedEntities.get(mc)){
   if(e.distanceToSqr(mc.player)>rr)continue;
   if(e instanceof Player p&&p!=mc.player&&p.getUUID().version()==2&&valid(mc,p)&&npcs.size()<64)npcs.add(p);
   else if(e instanceof ArmorStand s&&s.getCustomName()!=null&&labels.size()<128&&!DungeonTargets.canonical(s.getCustomName().getString()).isEmpty())labels.add(s);
  }
  var linked=new IdentityHashMap<Player,String>();var scores=new IdentityHashMap<Player,Double>();
  for(var label:labels){Player best=null;double cost=Double.POSITIVE_INFINITY;String name=DungeonTargets.canonical(label.getCustomName().getString());
   for(var p:npcs){if(!DungeonTargets.compatible(name,"entity.minecraft.player"))continue;
    var b=p.getBoundingBox();double dx=p.getX()-label.getX(),dz=p.getZ()-label.getZ(),dy=label.getY()-b.maxY;
    if(dx*dx+dz*dz>2.25||label.getY()<b.minY+.5||dy>3.5)continue;
    double score=4*(dx*dx+dz*dz)+dy*dy;
    if(score<cost||score==cost&&(best==null||p.getId()<best.getId())){best=p;cost=score;}
   }
   if(best!=null&&cost<scores.getOrDefault(best,Double.POSITIVE_INFINITY)){linked.put(best,name);scores.put(best,cost);}
  }
  for(var p:npcs){String name=linked.get(p);
   // Profile/display name alone is insufficient evidence: require a full known name, not a substring.
   String direct=com.farmmacro.panic.ServerText.clean(p.getName().getString()).strip();
   if(name==null&&DungeonTargets.ALL.contains(direct)&&DungeonTargets.compatible(direct,"entity.minecraft.player"))name=direct;
   if(name!=null&&DungeonTargets.npcEvidence(p.getUUID().version(),name))out.add(new Named(p,name));
  }
  return out;
 }
 /** Revalidate NPC evidence with each tick: disappeared labels never stick on another body. */
 public static boolean confirmedNpc(Minecraft mc,LivingEntity e,String name,double range){return !(e instanceof Player)||nearby(mc,range).stream().anyMatch(n->n.body()==e&&n.name().equals(name));}
 private ManualTargets(){}
}
