package com.farmmacro.visitor;
import com.farmmacro.panic.*;
import com.farmmacro.pest.PestItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.level.GameType;
import net.minecraft.network.chat.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.regex.*;
import java.math.*;
import java.nio.charset.StandardCharsets;

/** Narrow English protocol adapter; unknown prices, products and lore are not guessed. */
public final class VisitorData {
 public record Product(String id,String name){}
 public record Need(Product product,int amount){}
 public record Offer(List<Need> needs,int copper,String fingerprint){}
 public record Visitor(String name,String rarity){}
 public record Queue(List<Visitor> visitors,String fingerprint,boolean known){}
 public static final Map<String,Product> PRODUCTS=products();
 private static Map<String,Product> products(){try(var in=VisitorData.class.getResourceAsStream("/assets/farmmacro/visitor-products.json")){if(in==null)throw new IllegalStateException("catalog missing");var a=new com.google.gson.Gson().fromJson(new String(in.readAllBytes(),StandardCharsets.UTF_8),Product[].class);Map<String,Product> m=new LinkedHashMap<>();for(var x:a){if(m.put(clean(x.name()),x)!=null)throw new IllegalStateException("duplicate product");}return Map.copyOf(m);}catch(Exception e){throw new IllegalStateException("Visitor product catalog",e);}}
 public static String clean(String s){return ServerText.clean(s==null?"":s).strip();}
 public static List<String> lore(ItemStack s){var l=s.get(DataComponents.LORE);return l==null?List.of():l.lines().stream().map(c->clean(c.getString())).toList();}
 public static long coins(String s){if(s==null||!s.matches("(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d{1,3})?"))return -1;try{return new BigDecimal(s.replace(",","")).setScale(0,RoundingMode.CEILING).longValueExact();}catch(Exception e){return -1;}}
 private static long funds(String s){long n=coins(s);if(n<0)return -1;try{return new BigDecimal(s.replace(",","")).setScale(0,RoundingMode.FLOOR).longValueExact();}catch(Exception e){return -1;}}
 public static int count(String s){try{if(!s.matches("(?:\\d{1,3}(?:,\\d{3})+|\\d+)"))return -1;long n=Long.parseLong(s.replace(",",""));return n>0&&n<=1000000?(int)n:-1;}catch(Exception e){return -1;}}
 public static long purse(Minecraft mc){for(String s:PanicDetector.sidebar(mc)){var m=Pattern.compile("^(?:Purse|Piggy): ([0-9,.]+)(?: .*)?$").matcher(clean(s));if(m.matches())return funds(m.group(1));}return -1;}
 public static boolean jacob(Minecraft mc){return PanicDetector.sidebar(mc).stream().map(VisitorData::clean).anyMatch(s->s.contains("Jacob's Contest"));}
 public static Queue queue(Minecraft mc){
  if(mc.getConnection()==null)return new Queue(List.of(),"",false);
  var entries=mc.getConnection().getListedOnlinePlayers().stream().sorted(
   Comparator.comparingInt((PlayerInfo x)->x.getTabListOrder()).reversed()
    .thenComparingInt(x->x.getGameMode()==GameType.SPECTATOR?1:0)
    .thenComparing(x->x.getTeam()==null?"":x.getTeam().getName())
    .thenComparing(x->x.getProfile().name(),String.CASE_INSENSITIVE_ORDER)).limit(80).toList();
  List<Visitor> out=new ArrayList<>();boolean found=false;int expected=-1;
  for(var e:entries){Component c=e.getTabListDisplayName();if(c==null)continue;String s=clean(c.getString());
   var head=Pattern.compile("^Visitors: \\(?([0-5])\\)?$").matcher(s);if(head.matches()){if(found)return new Queue(List.of(),"",false);found=true;expected=Integer.parseInt(head.group(1));continue;}
   if(!found||out.size()>=expected)continue;if(s.isBlank()||s.startsWith("Next Visitor"))break;
   s=s.replaceFirst("\\s*NEW!$","").strip();if(!s.matches("[A-Za-z][A-Za-z '\\-]{0,47}"))return new Queue(List.of(),"",false);
   out.add(new Visitor(s,tier(c)));
  }
  if(!found||out.size()!=expected||out.stream().map(Visitor::name).distinct().count()!=out.size())return new Queue(List.of(),"",false);
  return new Queue(List.copyOf(out),out.toString(),true);
 }
 private static String tier(Component c){
  Map<Integer,String> colors=Map.of(0xffffff,"COMMON",0x55ff55,"UNCOMMON",0x5555ff,"RARE",0xaa00aa,"EPIC",0xffaa00,"LEGENDARY",0xff55ff,"MYTHIC",0xff5555,"SPECIAL");
  Set<String> found=new HashSet<>();c.visit((style,text)->{if(!clean(text).isBlank()&&!clean(text).equals("NEW!")&&style.getColor()!=null){String k=colors.get(style.getColor().getValue());if(k!=null)found.add(k);}return Optional.<Boolean>empty();},Style.EMPTY);
  // Legacy formatting may be embedded by protocol translation.
  if(found.isEmpty()){var m=Pattern.compile("§([fa95d6c])").matcher(c.getString());if(m.find()){String k=switch(m.group(1)){case "f"->"COMMON";case "a"->"UNCOMMON";case "9"->"RARE";case "5"->"EPIC";case "6"->"LEGENDARY";case "d"->"MYTHIC";default->"SPECIAL";};found.add(k);}}
  return found.size()==1?found.iterator().next():"UNKNOWN";
 }
 public static Offer offer(ItemStack accept){
  if(!PestItems.name(accept).equals("Accept Offer"))return null;boolean required=false,rewards=false;List<Need> needs=new ArrayList<>();int copper=-1;
  List<String> lines=lore(accept);for(String line:lines){if(line.equals("Required:")||line.equals("Items Required:")){if(required||rewards)return null;required=true;continue;}if(line.equals("Rewards:")){if(!required)return null;required=false;rewards=true;continue;}
   if(required&&!line.isBlank()){var m=Pattern.compile("^(.+?) x([0-9,]+)$").matcher(line);if(!m.matches())return null;Product p=PRODUCTS.get(m.group(1));int n=count(m.group(2));if(p==null||n<1||needs.stream().anyMatch(x->x.product.id.equals(p.id)))return null;needs.add(new Need(p,n));}
   if(rewards){var m=Pattern.compile("^\\+?([0-9,]+) Copper$").matcher(line);if(m.matches()){if(copper!=-1)return null;copper=count(m.group(1));}}
  }
  return !rewards||needs.isEmpty()||needs.size()>8||copper<1?null:new Offer(List.copyOf(needs),copper,String.join("\n",lines));
 }
 public static int inventory(Minecraft mc,Product p){int n=0;for(int i=0;i<36;i++){ItemStack s=mc.player.getInventory().getItem(i);if(PestItems.id(s).equals(p.id))n+=s.getCount();}return n;}
 public static String token(ItemStack s,int slot){String uuid=PestItems.uuid(s);return uuid.isBlank()?PestItems.id(s)+"@"+slot:PestItems.id(s)+"#"+uuid;}
 private VisitorData(){}
}
