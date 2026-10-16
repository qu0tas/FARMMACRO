package com.farmmacro.visitor;
import java.util.*;
/** Experimental visitor permissions; never enabled by migration. */
public final class VisitorSettings {
 public boolean enabled=false,auto=false,purchases=false,accept=false,refuse=false,resume=false,skipJacob=true,compactors=false,tipJar=false;
 public int threshold=3,timeout=240,perVisitor=100000,perSession=500000,minPurse=100000,coinsPerCopper=10000;
 public String ignore="Spaceman",reject="",server="",dimension="",handId="",handUuid="",handName="";
 public boolean hand=false,barnSaved=false,jarSaved=false;
 public double barnX,barnY,barnZ; public int jarX,jarY,jarZ;
 public Map<String,String> rarity=new LinkedHashMap<>();
 /** UUID when present, otherwise exact ID@hotbar index. OFF confirmed after our click only. */
 public List<String> pendingCompactors=new ArrayList<>();
 public VisitorSettings(){for(String k:List.of("COMMON","UNCOMMON","RARE","EPIC","LEGENDARY","MYTHIC","SPECIAL"))rarity.put(k,"SERVE");}
 public void sanitize(){
  threshold=Math.clamp(threshold,1,5);timeout=Math.clamp(timeout,60,600);perVisitor=Math.clamp(perVisitor,0,1000000000);perSession=Math.clamp(perSession,0,1000000000);minPurse=Math.clamp(minPurse,0,1000000000);coinsPerCopper=Math.clamp(coinsPerCopper,0,1000000000);
  ignore=limit(ignore,512);reject=limit(reject,512);server=limit(server,256);dimension=limit(dimension,128);handId=limit(handId,128);handUuid=limit(handUuid,128);handName=limit(handName,128);
  if(rarity==null)rarity=new LinkedHashMap<>();for(String k:List.of("COMMON","UNCOMMON","RARE","EPIC","LEGENDARY","MYTHIC","SPECIAL"))if(!Set.of("SERVE","SKIP","REFUSE").contains(rarity.getOrDefault(k,"")))rarity.put(k,"SKIP");
  if(pendingCompactors==null)pendingCompactors=new ArrayList<>();pendingCompactors=new ArrayList<>(pendingCompactors.stream().filter(Objects::nonNull).filter(x->x.length()<256).distinct().limit(9).toList());
  if(!Double.isFinite(barnX)||!Double.isFinite(barnY)||!Double.isFinite(barnZ)||Math.abs(barnX)>30000000||Math.abs(barnZ)>30000000||Math.abs(barnY)>2048)barnSaved=false;
 }
 private static String limit(String s,int n){return s==null?"":s.substring(0,Math.min(n,s.length())).strip();}
 public String action(String name,String tier){if(names(ignore).contains(name))return "SKIP";if(names(reject).contains(name))return "REFUSE";return rarity.getOrDefault(tier,"SKIP");}
 private static Set<String> names(String s){return new HashSet<>(Arrays.asList(s.split("\\s*;\\s*")));}
}
