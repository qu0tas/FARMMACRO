package com.farmmacro.combat;
import java.util.*;
import java.util.stream.Stream;
/** Explicit observed encounter names. Sources/limitations: SLAYER-CATALOG.md. No ownership/phase inference. */
public final class SlayerCatalog {
 public record Branch(String id,String title,List<String> bosses,List<String> minis,List<String> forms){
  public List<String> bossForms(){return Stream.concat(bosses.stream(),forms.stream()).toList();}
  public List<String> encounter(){return Stream.concat(bossForms().stream(),minis.stream()).toList();}
 }
 private static List<String> names(String text){return text.isEmpty()?List.of():List.of(text.split(";"));}
 private static Branch branch(String id,String title,String bosses,String minis,String forms){return new Branch(id,title,names(bosses),names(minis),names(forms));}
 public static final List<Branch> BRANCHES=List.of(
  branch("zombie","Zombie","Revenant Horror","Revenant Sycophant;Revenant Champion;Deformed Revenant;Atoned Champion;Atoned Revenant",""),
  branch("spider","Spider","Tarantula Broodfather;Primordial Broodfather","Tarantula Vermin;Tarantula Beast;Mutant Tarantula;Primordial Jockey;Primordial Viscount","Conjoined Brood"),
  branch("wolf","Wolf","Sven Packmaster","Pack Enforcer;Sven Follower;Sven Alpha",""),
  branch("enderman","Enderman","Voidgloom Seraph","Voidling Devotee;Voidling Radical;Voidcrazed Maniac",""),
  branch("blaze","Blaze","Inferno Demonlord","Flare Demon;Kindleheart Demon;Burningsoul Demon","Quazii;Typhoeus"),
  branch("vampire","Vampire","Riftstalker Bloodfiend","Clotgoyle",""));
 public static Branch get(String id){return BRANCHES.stream().filter(b->b.id().equals(id)).findFirst().orElseThrow();}
 public static boolean matches(String key,String observed){
  for(Branch b:BRANCHES){
   if(key.equals("slayer:"+b.id()+":boss"))return b.bossForms().stream().anyMatch(n->n.equalsIgnoreCase(observed));
   if(key.equals("slayer:"+b.id()+":encounter"))return b.encounter().stream().anyMatch(n->n.equalsIgnoreCase(observed));
  }
  return false;
 }
 public static String withEncounter(String id,String existing){
  var out=new LinkedHashSet<>(names(existing));out.addAll(get(id).encounter());return String.join(";",out);
 }
 private SlayerCatalog(){}
}
