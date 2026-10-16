package com.farmmacro.route;
/** Copy-only block transforms around the first point's block centre. No world/input/IO. */
public final class RouteCopies {
 public static PointRoute copy(PointRoute source,int dx,int dy,int dz,boolean mirrorX,boolean mirrorZ){
  if(source==null||source.points==null||source.points.isEmpty()||source.points.size()>RouteBuffer.MAX_POINTS)throw new IllegalArgumentException("empty/oversized route");
  if(Math.abs((long)dx)>4096||Math.abs((long)dy)>4096||Math.abs((long)dz)>4096)throw new IllegalArgumentException("offset limit");
  for(var p:source.points)if(p==null||!p.finite())throw new IllegalArgumentException("invalid point");
  double cx=source.points.getFirst().centerX(),cz=source.points.getFirst().centerZ();
  var out=source.copy();out.name=null;out.createdAt=0;out.version=PointRoute.VERSION;
  boolean handed=mirrorX!=mirrorZ;
  for(var p:out.points){
   p.x=(mirrorX?2*cx-p.x:p.x)+dx;p.y+=dy;p.z=(mirrorZ?2*cz-p.z:p.z)+dz;
   if(mirrorX)p.ox=-p.ox;if(mirrorZ)p.oz=-p.oz;
   if(mirrorX)p.dropDir=swap(p.dropDir,"+x","-x");if(mirrorZ)p.dropDir=swap(p.dropDir,"+z","-z");
   if(handed)p.farmKeys=FarmSchemeSettings.mirror(p.farmKeys==null?"":p.farmKeys);
   var a=p.pointAction;if(a!=null&&a.lookBlock){
    a.blockX=block(a.blockX,cx,dx,mirrorX);a.blockY=Math.addExact(a.blockY,dy);a.blockZ=block(a.blockZ,cz,dz,mirrorZ);
   }
   if(!p.finite()||Math.abs(p.x)>30_000_000||Math.abs(p.z)>30_000_000||Math.abs(p.y)>1_000_000)throw new IllegalArgumentException("world bounds");
  }
  var f=out.settings.farm;f.yaw=yaw(f.yaw,mirrorX,mirrorZ);
  if(handed){f.firstKeys=FarmSchemeSettings.mirror(f.firstKeys);f.laneKeys=FarmSchemeSettings.mirror(f.laneKeys);}
  // UI resolves live presets into local snapshots before copy; a transformed preset must not relink on save.
  if(mirrorX||mirrorZ)for(var c:out.settings.camera.changes){c.yaw=(float)yaw(c.yaw,mirrorX,mirrorZ);c.presetId=-1;}
  // Alternative file paths and named preset seeds refer to the original geometry; do not silently select it.
  out.settings.routeRandom.alternatives.clear();
  return out;
 }
 private static int block(int v,double pivot,int delta,boolean mirror){double result=(mirror?Math.floor(2*pivot-(v+.5)):v)+delta;if(!Double.isFinite(result)||result<Integer.MIN_VALUE||result>Integer.MAX_VALUE)throw new IllegalArgumentException("block bounds");return (int)result;}
 private static String swap(String s,String a,String b){return a.equals(s)?b:b.equals(s)?a:s;}
 static double yaw(double v,boolean x,boolean z){if(x)v=-v;if(z)v=180-v;v=(v+180)%360;if(v<0)v+=360;return v-180;}
 private RouteCopies(){}
}
