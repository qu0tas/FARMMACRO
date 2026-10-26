package com.farmmacro.combat;
import java.util.*;
/** Bounded recent observations, never live entity references or hidden entity discovery. */
public final class RecentTargetMemory {
 public record Point(int id,double x,double y,double z,long time){}
 private final LinkedHashMap<Integer,Point> points=new LinkedHashMap<>();
 public void observe(int id,double x,double y,double z,long now){
  if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))return;
  prune(now);points.remove(id);points.put(id,new Point(id,x,y,z,now));
  while(points.size()>24)points.remove(points.keySet().iterator().next());
 }
 public List<Point> recent(long now){prune(now);var list=new ArrayList<>(points.values());Collections.reverse(list);return List.copyOf(list);}
 private void prune(long now){points.values().removeIf(p->now<p.time||now-p.time>20_000_000_000L);}
 public void clear(){points.clear();}
}
