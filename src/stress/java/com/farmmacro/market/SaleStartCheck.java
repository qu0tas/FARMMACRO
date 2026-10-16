package com.farmmacro.market;
import java.util.*;
public final class SaleStartCheck {
 private static void check(boolean b,String message){if(!b)throw new IllegalStateException("SaleStartCheck: "+message);}
 private static SaleStartPolicy.Context context(String fail,String access,String realm,boolean hotbar){
  return new SaleStartPolicy.Context(!fail.equals("world"),!fail.equals("focus"),!fail.equals("screen"),!fail.equals("cursor"),
   !fail.equals("hypixel"),!fail.equals("enabled"),!fail.equals("allow"),!fail.equals("free"),!fail.equals("farm"),realm,access,
   !fail.equals("travel"),!fail.equals("landings"),hotbar);
 }
 public static void main(String... args){
  for(String access:List.of("AUTO","REMOTE"))for(String realm:List.of("GARDEN","ISLAND","HUB","SKYBLOCK"))
   check(SaleStartPolicy.refusal(context("",access,realm,false)).isEmpty(),"remote should not need empty hand: "+access+"/"+realm);
  for(String fail:List.of("world","focus","screen","cursor","hypixel","enabled","allow","free","farm"))
   check(!SaleStartPolicy.refusal(context(fail,"REMOTE","GARDEN",true)).isEmpty(),"missing refusal "+fail);
  check(!SaleStartPolicy.refusal(context("","REMOTE","",true)).isEmpty(),"no lobby/unknown realm");
  check(!SaleStartPolicy.refusal(context("","OTHER","GARDEN",true)).isEmpty(),"unknown access");
  check(SaleStartPolicy.refusal(context("","HUB","GARDEN",true)).isEmpty(),"recorded HUB roundtrip");
  for(String fail:List.of("travel","landings"))check(!SaleStartPolicy.refusal(context(fail,"HUB","GARDEN",true)).isEmpty(),"HUB needs "+fail);
  check(!SaleStartPolicy.refusal(context("","HUB","GARDEN",false)).isEmpty(),"NPC requires empty hand");
  check(SaleStartPolicy.refusal(context("landings","HUB","HUB",true)).isEmpty(),"already HUB needs no warp landing");
  check(!SaleStartPolicy.refusal(context("landings","HUB","SKYBLOCK",true)).isEmpty(),"no invented End return");
  check(SaleStartPolicy.remoteRealm("GARDEN",List.of()).equals("GARDEN"),"known realm");
  check(SaleStartPolicy.remoteRealm("",List.of("§eSKYBLOCK","The End")).equals("SKYBLOCK"),"remote End allowed by exact title");
  check(SaleStartPolicy.remoteRealm("",List.of("SKYBLOCK CO-OP")).equals("SKYBLOCK"),"co-op");
  for(var labels:List.of(List.of("HYPIXEL","SKYBLOCK"),List.of("BED WARS"),List.of("NOT SKYBLOCK")))check(SaleStartPolicy.remoteRealm("",labels).isEmpty(),"not arbitrary line/title "+labels);
  check(SalePolicy.saleCoins("1,234.99")==1234&&SalePolicy.saleCoins("-1")==-1&&SalePolicy.saleCoins("1,23")==-1,"price parsing stays strict");
  var cfg=new SaleSettings();cfg.sanitize();check(!cfg.enabled&&cfg.allowed.isEmpty(),"no financial permission granted by defaults");
  System.out.println("SaleStartCheck: OK (preflight refusals, remote occupied hotbar/End, NPC hand/recorded-return gates, strict prices; NO commands/clicks/financial actions)");
 }
}
