package com.farmmacro.market;
import com.farmmacro.pest.PestItems;
import com.farmmacro.visitor.VisitorData;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import java.util.*;
import java.nio.charset.StandardCharsets;
/** Explicit commodity allowlist. Sell Instantly consumes EVERY inventory stack with that product ID. */
public final class SalePolicy {
 public record Product(String id,String name){}
 public static final Map<String,Product> CATALOG=load();
 private static Map<String,Product> load(){try(var in=SalePolicy.class.getResourceAsStream("/assets/farmmacro/sale-products.json")){var a=new com.google.gson.Gson().fromJson(new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.UTF_8),Product[].class);Map<String,Product> m=new LinkedHashMap<>();for(var x:a){if(m.put(x.id,x)!=null)throw new IllegalStateException("duplicate id");}return Collections.unmodifiableMap(m);}catch(Exception e){throw new IllegalStateException(e);}}
 public static String reason(ItemStack s,int slot,SaleSettings c,int originalSlot,int heldSlot){
  if(s.isEmpty())return "пустой слот";String id=PestItems.id(s);
  if(!CATALOG.containsKey(id))return "ID вне поддержанного каталога";
  if(!c.allow().contains(id))return "ID не разрешён";if(c.deny().contains(id))return "ID в исключениях";
  if(slot==originalSlot||slot==heldSlot||c.protectHotbar&&slot<9)return "защищённый хотбар/предмет руки";
  if(!PestItems.name(s).equals(CATALOG.get(id).name()))return "нестандартное имя";
  var data=s.get(DataComponents.CUSTOM_DATA);if(data==null)return "нет ExtraAttributes";var extra=data.copyTag().getCompoundOrEmpty("ExtraAttributes");
  if(extra.keySet().stream().anyMatch(k->!Set.of("id","timestamp").contains(k)))return "UUID/улучшения/неизвестные ExtraAttributes";
  var ench=s.get(DataComponents.ENCHANTMENTS);if(ench!=null&&!ench.isEmpty())return "зачарования";
  String lore=PestItems.lore(s).toLowerCase(Locale.ROOT);if(lore.contains("soulbound")||lore.contains("favorite")||lore.contains("locked")||lore.contains("museum"))return "ценный/закреплённый предмет";
  return "";
 }
 public static long saleCoins(String value){
  if(value==null||!value.matches("(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]{1,3})?"))return -1;
  try{return new java.math.BigDecimal(value.replace(",","")).setScale(0,java.math.RoundingMode.FLOOR).longValueExact();}catch(Exception e){return -1;}
 }
 public static boolean outsideMain(Minecraft mc,String id){for(int i=36;i<mc.player.getInventory().getContainerSize();i++)if(PestItems.id(mc.player.getInventory().getItem(i)).equals(id))return true;return false;}
 public static int count(Minecraft mc,String id){int n=0;for(int i=0;i<36;i++)if(PestItems.id(mc.player.getInventory().getItem(i)).equals(id))n+=mc.player.getInventory().getItem(i).getCount();return n;}
 public static List<ItemStack> snapshot(Minecraft mc){List<ItemStack> out=new ArrayList<>();for(int i=0;i<36;i++)out.add(mc.player.getInventory().getItem(i).copy());return List.copyOf(out);}
 public static boolean same(List<ItemStack> a,List<ItemStack> b){if(a.size()!=b.size())return false;for(int i=0;i<a.size();i++)if(!ItemStack.matches(a.get(i),b.get(i)))return false;return true;}
 public static boolean onlyAdded(List<ItemStack> before,List<ItemStack> after){boolean added=false;for(int i=0;i<36;i++){var a=before.get(i);var b=after.get(i);if(!a.isEmpty()&&(!ItemStack.isSameItemSameComponents(a,b)||b.getCount()<a.getCount()))return false;added|=b.getCount()>a.getCount();}return added;}
 private SalePolicy(){}
}
