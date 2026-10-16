package com.farmmacro.motion;
import com.farmmacro.route.PointRoute;
import java.util.random.RandomGenerator;
/** Only an ephemeral pass copy. No new pause on collinear row points, no change to saved route. */
public final class FarmBoundaryPauses {
 public static PointRoute prepare(PointRoute source,MotionSettings settings,RandomGenerator rng){
  PointRoute r=source.copy();MotionSettings s=settings.copy();
  if(!r.settings.farm.enabled||!s.boundaryPauses)return r;
  for(int i=0;i<r.points.size();i++){
   var p=r.points.get(i);boolean floor=p.drop;
   if(i>0)floor|=Math.abs(p.y-r.points.get(i-1).y)>.6;
   if(i+1<r.points.size())floor|=Math.abs(p.y-r.points.get(i+1).y)>.6;
   boolean corner=p.farmTurn;
   if(i>0&&i+1<r.points.size()){
    var a=r.points.get(i-1);var b=r.points.get(i+1);
    double ax=p.x-a.x,az=p.z-a.z,bx=b.x-p.x,bz=b.z-p.z,la=Math.hypot(ax,az),lb=Math.hypot(bx,bz);
    if(la>.01&&lb>.01)corner|=(ax*bx+az*bz)/(la*lb)<Math.cos(Math.toRadians(25));
   }
   if(!floor&&!corner)continue;
   int min=floor?s.floorMinMs:s.aether()?s.laneMinMs:s.cornerMinMs;
   int max=floor?s.floorMaxMs:s.aether()?s.laneMaxMs:s.cornerMaxMs;
   int ticks=(rng.nextInt(min,max+1)+49)/50;
   int builtIn=(p.drop?p.beforeDropTicks:0)+(p.farmTurn&&!r.settings.farm.fastSwitch?r.settings.farm.switchPauseTicks:0);
   p.pauseTicks=Math.max(p.pauseTicks,Math.max(0,ticks-builtIn));
   if(p.drop)p.landPauseTicks=Math.max(p.landPauseTicks,(rng.nextInt(s.floorMinMs,s.floorMaxMs+1)+49)/50);
  }
  return r;
 }
 private FarmBoundaryPauses(){}
}
