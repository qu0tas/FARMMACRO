package com.farmmacro.combat;
import java.util.*;
/** Manual-only filters: named catalogue, observed aquatic bodies, or explicit any-Mob mode. */
public final class HuntingTargets {
 private static final Set<String> AQUATIC=Set.of("frog","tadpole","axolotl","cod","salmon","pufferfish","tropical_fish","squid","glow_squid");
 public static List<MobCatalog.Group> groups(){return MobCatalog.GROUPS.stream().filter(g->g.id().startsWith("hunting_")).toList();}
 public static boolean selection(String value){return value!=null&&value.startsWith("hunting:");}
 public static boolean target(String value){return selection(value)||known(value);}
 public static boolean known(String name){return groups().stream().anyMatch(g->g.mobs().stream().anyMatch(n->n.equalsIgnoreCase(normalize(name))));}
 private static final java.util.regex.Pattern LEVEL=java.util.regex.Pattern.compile("(?i)^\\[\\s*Lv\\.?\\s*[\\d,]+\\]\\s*"),
  HP=java.util.regex.Pattern.compile("(?i)\\s+[\\d,.]+[kmbt]?(?:/[\\d,.]+[kmbt]?)?\\s*[❤♥\uE010]?\\s*$");
 public static String normalize(String name){
  if(name==null)return "";
  return HP.matcher(LEVEL.matcher(com.farmmacro.panic.ServerText.clean(name)).replaceFirst("")).replaceFirst("").strip();
 }
 public static boolean matches(String value,String name){String normalized=normalize(name);return groups().stream().filter(g->value.equals("hunting:all")||value.equals("hunting:"+g.id())).anyMatch(g->g.mobs().stream().anyMatch(n->n.equalsIgnoreCase(normalized)));}
 /** Called only after the caller proves this is a real Mob, never a player, label or display. */
 public static boolean body(String value,String name,String type){
  if(!selection(value))return false;
  if("hunting:nearby".equals(value))return true;
  String key=type==null?"":type.substring(type.lastIndexOf('.')+1);
  return matches(value,name)||"hunting:all".equals(value)&&AQUATIC.contains(key);
 }
 public static double acquisitionScore(double distance,double angle){return distance+Math.clamp(angle,0,180)*.0001;}
 private HuntingTargets(){}
}
