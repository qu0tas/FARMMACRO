package com.farmmacro.stats;
import com.google.gson.*;
import com.farmmacro.config.ModConfig;
import java.util.*;
/** Offline NPC price catalogue. Bazaar network retrieval removed. */
public final class StatsPrices {
 public record Price(String name,double npc){}
 public static final Map<String,Price> NPC=load();
 public static final Map<String,String> SHARDS=loadShards();
 private static Map<String,String> loadShards(){try(var in=StatsPrices.class.getResourceAsStream("/assets/farmmacro/shard-prices.json")){var a=JsonParser.parseString(new String(Objects.requireNonNull(in).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("shards");Map<String,String> out=new HashMap<>();for(var row:a){var o=row.getAsJsonObject();out.put(o.get("attribute").getAsString(),o.get("bazaar").getAsString());}return Map.copyOf(out);}catch(Exception e){throw new IllegalStateException("Shard catalog",e);}}
 public static String shardId(net.minecraft.world.item.ItemStack s){if(!com.farmmacro.pest.PestItems.id(s).equals("ATTRIBUTE_SHARD"))return "";var d=s.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);if(d==null)return "";var e=d.copyTag().getCompoundOrEmpty("ExtraAttributes");if(e.keySet().stream().anyMatch(k->!Set.of("id","timestamp","attributes").contains(k)))return "";var a=e.getCompoundOrEmpty("attributes");if(a.keySet().size()!=1)return "";String key=a.keySet().iterator().next();if(a.getIntOr(key,0)!=1)return "";return SHARDS.getOrDefault(key,"");}
 private static Map<String,Price> load(){try(var in=StatsPrices.class.getResourceAsStream("/assets/farmmacro/stat-prices.json")){var o=JsonParser.parseString(new String(Objects.requireNonNull(in).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();Map<String,Price> out=new HashMap<>();for(var e:o.getAsJsonArray("items")){var p=e.getAsJsonObject();out.put(p.get("id").getAsString(),new Price(p.get("name").getAsString(),p.get("npc").getAsDouble()));}return Map.copyOf(out);}catch(Exception e){throw new IllegalStateException("NPC catalog",e);}}
 public static double value(String id){var p=NPC.get(id);return p==null?Double.NaN:p.npc();}
 public static long revision(){return 0;}
 public static boolean live(){return false;}
 public static String source(){return "NPC • встроенный справочник";}
 public static String status(){return "Офлайн-оценка NPC; сетевые котировки удалены";}
 public static void disabled(){}
 public static void tick(){}
 public static void refresh(){}
 private StatsPrices(){}
}
