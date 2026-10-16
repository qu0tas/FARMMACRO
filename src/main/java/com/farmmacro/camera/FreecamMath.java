package com.farmmacro.camera;
/** Pure bounded local-camera translation. Diagonal input cannot increase speed. */
public final class FreecamMath {
 public record Point(double x,double y,double z){}
 public static Point step(Point from,Point anchor,double yaw,int forward,int right,int up,double speed,double dt,double radius){
  if(from==null||anchor==null||!Double.isFinite(yaw)||!Double.isFinite(speed)||!Double.isFinite(dt)||!Double.isFinite(radius))throw new IllegalArgumentException("freecam");
  double n=Math.max(1,Math.sqrt(forward*forward+right*right+up*up)),d=Math.clamp(speed,1,40)*Math.clamp(dt,0,.05)/n,a=Math.toRadians(yaw);
  double x=from.x+(-Math.sin(a)*forward-Math.cos(a)*right)*d,y=from.y+up*d,z=from.z+(Math.cos(a)*forward-Math.sin(a)*right)*d;
  double dx=x-anchor.x,dy=y-anchor.y,dz=z-anchor.z,len=Math.sqrt(dx*dx+dy*dy+dz*dz),limit=Math.clamp(radius,4,64);
  if(len>limit){x=anchor.x+dx*limit/len;y=anchor.y+dy*limit/len;z=anchor.z+dz*limit/len;}return new Point(x,y,z);
 }
 private FreecamMath(){}
}
