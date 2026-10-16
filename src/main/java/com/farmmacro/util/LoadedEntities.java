package com.farmmacro.util;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import java.util.*;
/** Client-thread, lazy tick-local discovery only. NEVER used by panic guards or as shot/collision evidence. */
public final class LoadedEntities {
 public static final int LIMIT=8192;
 private static Object world;private static List<Entity> snapshot;private static boolean truncated;
 public static void beginTick(){world=null;snapshot=null;truncated=false;}
 public static List<Entity> get(Minecraft mc){
  if(mc.level==null){beginTick();return List.of();}
  if(world!=mc.level||snapshot==null){world=mc.level;var out=new ArrayList<Entity>();truncated=false;
   for(Entity e:mc.level.entitiesForRendering()){if(out.size()==LIMIT){truncated=true;break;}out.add(e);}
   snapshot=List.copyOf(out);Diagnostics.add(Diagnostics.Counter.ENTITY_SNAPSHOTS);if(truncated)Diagnostics.add(Diagnostics.Counter.ENTITY_LIMIT);
  }return snapshot;
 }
 public static boolean truncated(){return truncated;}public static int size(){return snapshot==null?0:snapshot.size();}
 private LoadedEntities(){}
}
