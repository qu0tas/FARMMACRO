package com.farmmacro.combat;
import java.util.*;
import java.util.regex.Pattern;
import com.farmmacro.panic.ServerText;
/** Observed encounter names only, never an assertion of vulnerability or player ownership. */
public final class DungeonTargets {
 public static final List<String> BOSSES=List.of("The Watcher","Bonzo","Scarf","The Professor","Thorn","Livid","Sadan","Maxor","Storm","Goldor","Necron");
 public static final List<String> MINIS=List.of("Lost Adventurer","Frozen Adventurer","Angry Archaeologist","Shadow Assassin","King Midas");
 public static final List<String> ALL=java.util.stream.Stream.concat(BOSSES.stream(),MINIS.stream()).toList();
 private static final List<Pattern> PATTERNS=ALL.stream().map(n->Pattern.compile("(?i)(?<![a-z])"+Pattern.quote(n)+"(?![a-z])")).toList();
 public static String canonical(String raw){String s=ServerText.clean(raw);for(int i=0;i<ALL.size();i++)if(PATTERNS.get(i).matcher(s).find())return ALL.get(i);return "";}
 public static boolean matches(String selection,String name){
  var list=switch(selection){case "dungeon:all"->ALL;case "dungeon:bosses"->BOSSES;case "dungeon:minis"->MINIS;default->List.<String>of();};
  return list.stream().anyMatch(n->n.equalsIgnoreCase(name));
 }
 /** Hypixel NPC evidence: v2 UUID and a separately observed, exact encounter label.
  * No UUIDv3/v4 real player may become a target even when standing under that label. */
 public static boolean npcEvidence(int uuidVersion,String observed){return uuidVersion==2&&ALL.contains(observed);}
 public static boolean compatible(String name,String type){
  if(!ALL.contains(name))return false;
  if(type.endsWith(".player"))return !name.equals("Thorn")&&!name.equals("King Midas")&&!name.equals("The Watcher");
  return switch(name){
   case "Thorn"->type.endsWith(".ghast");
   case "King Midas"->type.endsWith(".zombie");
   case "Bonzo"->type.endsWith(".zombie")||type.endsWith(".skeleton");
   case "The Watcher"->false; // Floating head / label is not an attackable body.
   case "The Professor"->type.endsWith(".guardian")||type.endsWith(".elder_guardian")||type.endsWith(".zombie");
   case "Sadan"->type.endsWith(".giant")||type.endsWith(".zombie");
   case "Maxor","Storm","Goldor","Necron"->type.endsWith(".wither");
   default->type.endsWith(".zombie")||type.endsWith(".skeleton");
  };
 }
 private DungeonTargets(){}
}
