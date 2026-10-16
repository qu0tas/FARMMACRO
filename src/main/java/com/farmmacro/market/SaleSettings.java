package com.farmmacro.market;
import java.util.*;
public final class SaleSettings {
 public boolean enabled=false,auto=false,hubTravel=false,resume=false,protectHotbar=true;
 public String access="AUTO",allowed="",excluded="";
 public int batches=3,maxTransactions=12,maxItems=8192,timeout=240,minUnitCoins=0;
 public Point hub=new Point(),home=new Point();
 public static final class Point {public boolean saved=false;public String server="",dimension="",realm="";public double x,y,z;public float yaw,pitch;}
 public void sanitize(){
  if(!Set.of("AUTO","REMOTE","HUB").contains(access==null?"":access))access="AUTO";
  allowed=ids(allowed);excluded=ids(excluded);batches=Math.clamp(batches,1,10);maxTransactions=Math.clamp(maxTransactions,1,36);maxItems=Math.clamp(maxItems,1,100000);timeout=Math.clamp(timeout,60,600);minUnitCoins=Math.clamp(minUnitCoins,0,1000000000);
  if(hub==null)hub=new Point();if(home==null)home=new Point();sanitize(hub);sanitize(home);
 }
 private static String ids(String s){if(s==null)return "";return String.join(";",Arrays.stream(s.split("[;,\\s]+")).filter(x->x.matches("[A-Z0-9_:]{1,96}")).distinct().limit(128).toList());}
 private static void sanitize(Point p){if(p.server==null)p.server="";if(p.dimension==null)p.dimension="";if(p.realm==null)p.realm="";
  if(!Double.isFinite(p.x)||!Double.isFinite(p.y)||!Double.isFinite(p.z)||!Float.isFinite(p.yaw)||!Float.isFinite(p.pitch)||Math.abs(p.x)>30000000||Math.abs(p.z)>30000000||Math.abs(p.y)>2048||Math.abs(p.pitch)>90||!Set.of("HUB","GARDEN","ISLAND").contains(p.realm)||p.server.length()>256||p.dimension.length()>128)p.saved=false;
 }
 public Set<String> allow(){return Set.copyOf(Arrays.asList(allowed.split(";")));}
 public Set<String> deny(){return Set.copyOf(Arrays.asList(excluded.split(";")));}
}
