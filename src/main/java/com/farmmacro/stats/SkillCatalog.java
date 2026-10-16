package com.farmmacro.stats;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Pinned, offline Hypixel resource tables; table maximum is not a personal unlock. */
public final class SkillCatalog {
 public record Skill(String id,String name,List<Double> totals) {
  public int maxLevel(){return totals.size()-1;}
  public double total(int level){return totals.get(Math.clamp(level,0,maxLevel()));}
 }
 private static final Map<String,Skill> ALL=load();
 private static Map<String,Skill> load(){try(var in=SkillCatalog.class.getResourceAsStream("/assets/farmmacro/skills-xp.json")){
  var root=JsonParser.parseString(new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("skills");Map<String,Skill> result=new LinkedHashMap<>();
  for(var e:root.entrySet()){var v=e.getValue().getAsJsonObject();List<Double> a=new ArrayList<>();for(var n:v.getAsJsonArray("total")){double d=n.getAsDouble();if(!Double.isFinite(d)||d<0||!a.isEmpty()&&d<=a.getLast())throw new IllegalStateException("nonmonotonic XP");a.add(d);}if(a.size()<2||a.size()>201||a.getFirst()!=0||v.get("maxLevel").getAsInt()!=a.size()-1)throw new IllegalStateException("XP table");result.put(e.getKey(),new Skill(e.getKey(),v.get("name").getAsString(),List.copyOf(a)));}return Collections.unmodifiableMap(result);
 }catch(Exception e){throw new IllegalStateException("Skill XP catalog",e);}}
 public static Collection<Skill> all(){return ALL.values();}
 public static Skill get(String id){return ALL.get(id);}
 private SkillCatalog(){}
}
