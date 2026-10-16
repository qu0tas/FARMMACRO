package com.farmmacro.stats;
import com.farmmacro.config.ModConfig;
import com.farmmacro.panic.*;
import com.farmmacro.pest.PestItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import java.util.*;
import java.util.regex.*;
/** Passive client-thread statistics. Receipts, inventory estimate and XP are deliberately separate measures. */
public final class Telemetry {
 public static final FarmingProgress FARMING=new FarmingProgress();
 private static final Map<String,SkillProgress> SKILLS=new LinkedHashMap<>();
 static {for(var s:SkillCatalog.all())SKILLS.put(s.id(),s.id().equals("FARMING")?FARMING.delegate:new SkillProgress(s));}
 private static PetProgress petProgress;
 public static SkillProgress skill(String id){return SKILLS.get(id);}
 public static SkillProgress selectedSkill(String group,Object w){String id=ModConfig.INSTANCE.telemetry.skill;if(!"AUTO".equals(id))return SKILLS.getOrDefault(id,SKILLS.get("FARMING"));
  if(group.equals("Combat"))return SKILLS.get("COMBAT");if(group.equals("Farms"))return SKILLS.get("FARMING");
  return SKILLS.values().stream().filter(p->p.fresh(w)&&System.nanoTime()-p.lastAt()<15_000_000_000L).max(Comparator.comparingLong(SkillProgress::lastAt)).orElse(SKILLS.get("FARMING"));}
 public static boolean recentSkill(){long now=System.nanoTime();return SKILLS.values().stream().anyMatch(p->p.fresh(world,now)&&now-p.lastAt()<15_000_000_000L);}
 private static void clearPet(){petProgress=null;pet="неизвестен";rarity="?";petIdentity="";petLevel=petMax=-1;petAt=0;}
 private static void loseProgress(){SKILLS.values().forEach(SkillProgress::contextLost);clearPet();}
 public static PetProgress petProgress(Object w){return petProgress!=null&&petProgress.fresh(w,System.nanoTime())?petProgress:null;}
 private static boolean inSkyblock;private static Object world;private static String scope="";private static long nano,online,saveAt,income,expense,receipts;private static int ticks;
 private static final long[] moneyBins=new long[30];private static final boolean[] binSeen=new boolean[30];private static long minute=Long.MIN_VALUE;
 private static String valuationSource="";private static double inventory=Double.NaN,shards=Double.NaN;private static int pricedStacks,unknownStacks;private static String pet="неизвестен",rarity="?",petIdentity="";private static int petLevel=-1,petMax=-1;private static long petAt;
 private static final Pattern PET=Pattern.compile("^(?:Pet:\\s*)?\\[Lvl ([0-9]{1,3})] (.{1,50})$");
 public static boolean context(Minecraft mc){if(mc.player==null||mc.level==null||mc.getConnection()==null)return false;var o=mc.level.getScoreboard().getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);return o!=null&&ServerText.clean(o.getDisplayName().getString()).startsWith("SKYBLOCK");}
 private static void scope(Minecraft mc){String here=com.farmmacro.route.RouteBuffer.worldId(mc)+":"+mc.player.getGameProfile().id();if(!scope.equals(here)){resetSession();scope=here;}}
 private static void bin(long now){long next=now/60_000_000_000L;if(minute==Long.MIN_VALUE){minute=next;return;}long shift=next-minute;if(shift<=0)return;int n=(int)Math.min(30,shift);System.arraycopy(moneyBins,n,moneyBins,0,30-n);System.arraycopy(binSeen,n,binSeen,0,30-n);Arrays.fill(moneyBins,30-n,30,0);Arrays.fill(binSeen,30-n,30,false);minute=next;}
 public static void tick(Minecraft mc){var c=ModConfig.INSTANCE.telemetry;long now=System.nanoTime();bin(now);boolean present=c.enabled&&context(mc);inSkyblock=present;
  if(!present){nano=0;if(world!=null){world=null;loseProgress();petAt=0;inventory=shards=Double.NaN;}return;}
  scope(mc);
  if(nano!=0)online+=Math.min(1000,Math.max(0,(now-nano)/1_000_000));nano=now;binSeen[29]=true;
  if(world!=mc.level){world=mc.level;loseProgress();petAt=0;inventory=shards=Double.NaN;}
  StatsPrices.tick();if(now-saveAt>=10_000_000_000L){StatsLedger.checkpoint();saveAt=now;}
  if(++ticks%20!=0)return;valueInventory(mc);readTabPet(mc);
 }
 public static void money(long received,long spent){var mc=Minecraft.getInstance();if(!ModConfig.INSTANCE.telemetry.enabled||!context(mc)||received<0||spent<0||received>1_000_000_000_000L||spent>1_000_000_000_000L)return;
  scope(mc);if(income>1_000_000_000_000_000L-received||expense>1_000_000_000_000_000L-spent)return;income+=received;expense+=spent;receipts++;bin(System.nanoTime());binSeen[29]=true;moneyBins[29]+=received-spent;StatsLedger.add(received,spent,0);StatsLedger.checkpoint();
 }
 public static void actionbar(Minecraft mc,String raw){if(!ModConfig.INSTANCE.telemetry.enabled||!context(mc))return;scope(mc);String text=ServerText.clean(raw);long now=System.nanoTime();for(var p:SKILLS.values()){double delta=p.observe(text,mc.level,now);if(delta>0&&p.skill().id().equals("FARMING"))StatsLedger.add(0,0,delta);}}
 private static boolean ordinary(ItemStack s){var d=s.get(DataComponents.CUSTOM_DATA);if(d==null)return false;var e=d.copyTag().getCompoundOrEmpty("ExtraAttributes");if(e.keySet().stream().anyMatch(k->!Set.of("id","timestamp").contains(k)))return false;var ench=s.get(DataComponents.ENCHANTMENTS);return ench==null||ench.isEmpty();}
 private static void valueInventory(Minecraft mc){double value=0,shardValue=0;int known=0,unknown=0,shardKnown=0,shardUnknown=0;
  for(int i=0;i<36;i++){var s=mc.player.getInventory().getItem(i);if(s.isEmpty())continue;String id=PestItems.id(s);boolean shard=id.equals("ATTRIBUTE_SHARD");String marketId=shard?StatsPrices.shardId(s):id;double price=shard?marketId.isEmpty()?Double.NaN:StatsPrices.value(marketId):ordinary(s)?StatsPrices.value(id):Double.NaN;
   if(Double.isFinite(price)){known++;value+=price*s.getCount();if(shard){shardValue+=price*s.getCount();shardKnown++;}}else{unknown++;if(shard)shardUnknown++;}}
  valuationSource=valuationKey();inventory=known>0?value:Double.NaN;shards=shardKnown>0?shardValue:Double.NaN;pricedStacks=known;unknownStacks=unknown;if(shardUnknown>0)shards=Double.NaN;
 }
 private static void readTabPet(Minecraft mc){boolean section=false;int expected=0;for(var i:mc.getConnection().getListedOnlinePlayers().stream().sorted(java.util.Comparator.comparingInt((net.minecraft.client.multiplayer.PlayerInfo x)->x.getTabListOrder()).reversed().thenComparing(x->x.getProfile().name())).limit(100).toList()){
   var text=i.getTabListDisplayName();if(text==null)continue;String s=ServerText.clean(text.getString()).strip();if(s.equals("Pet:")){section=true;expected=0;continue;}
   var m=PET.matcher(s);if(m.matches()&&(section||s.startsWith("Pet: "))){String name=m.group(2);int level=Integer.parseInt(m.group(1));if(level<1||level>200)return;String tier=colorTier(text);boolean changed=!pet.equals(name)||petLevel!=level||!tier.equals("?")&&!rarity.equals("?")&&!tier.equals(rarity);
    if(changed){petProgress=null;petMax=-1;petIdentity=name.toUpperCase(Locale.ROOT).replace(' ','_');rarity=tier;}
    else if(!tier.equals("?"))rarity=tier;pet=name;petLevel=level;petAt=System.nanoTime();return;}if(section&&!s.isBlank()&&++expected>2)section=false;
  }}
 private static String colorTier(net.minecraft.network.chat.Component c){Set<Integer> found=new HashSet<>();c.visit((style,s)->{if(style.getColor()!=null&&!s.isBlank())found.add(style.getColor().getValue());return Optional.<Boolean>empty();},net.minecraft.network.chat.Style.EMPTY);for(var e:Map.of(0xffffff,"COMMON",0x55ff55,"UNCOMMON",0x5555ff,"RARE",0xaa00aa,"EPIC",0xffaa00,"LEGENDARY",0xff55ff,"MYTHIC").entrySet())if(found.size()==1&&found.contains(e.getKey()))return e.getValue();return "?";}
 /** Only a visible server Pets menu item explicitly marked active. Never opens a menu or summons anything. */
 public static void petMenu(Minecraft mc,int container,List<ItemStack> items){if(!ModConfig.INSTANCE.telemetry.enabled||!context(mc)||mc.screen==null||mc.player==null||container!=mc.player.containerMenu.containerId||!ServerText.clean(mc.screen.getTitle().getString()).matches("Pets(?: \\([0-9]+/[0-9]+\\))?"))return;
  ItemStack selected=null;int top=mc.player.containerMenu.slots.size()-36;if(top<0||items.size()!=mc.player.containerMenu.slots.size())return;for(int i=0;i<top;i++){var s=items.get(i);if(PestItems.id(s).equals("PET")&&PestItems.lore(s).contains("Click to despawn!")){if(selected!=null)return;selected=s;}}
  if(selected==null)return;var m=PET.matcher(PestItems.name(selected));if(!m.matches())return;var data=selected.get(DataComponents.CUSTOM_DATA);if(data==null)return;String info=data.copyTag().getCompoundOrEmpty("ExtraAttributes").getStringOr("petInfo","");if(info.length()>8192)return;
  try{var o=com.google.gson.JsonParser.parseString(info).getAsJsonObject();String type=o.get("type").getAsString(),tier=o.get("tier").getAsString();if(!type.matches("[A-Z_]{1,50}")||!tier.matches("[A-Z_]{1,20}"))return;int level=Integer.parseInt(m.group(1));if(level<1||level>200)return;
   double xp=o.has("exp")?o.get("exp").getAsDouble():Double.NaN;var lore=selected.get(DataComponents.LORE);var lines=lore==null?List.<String>of():lore.lines().stream().map(c->ServerText.clean(c.getString())).toList();
   scope(mc);pet=m.group(2);petLevel=level;petIdentity=type;rarity=tier;petAt=System.nanoTime();petProgress=PetProgress.parse(lines,level,xp,mc.level,petAt);petMax=petProgress!=null&&petProgress.max()?level:-1;}catch(RuntimeException ignored){}
 }
 public static void resetSession(){pricedStacks=unknownStacks=0;online=income=expense=receipts=nano=0;Arrays.fill(moneyBins,0);Arrays.fill(binSeen,false);minute=Long.MIN_VALUE;SKILLS.values().forEach(SkillProgress::reset);clearPet();inventory=shards=Double.NaN;}
 public static void resetAll(){resetSession();StatsLedger.reset();}
 public static long onlineMs(){return online;}public static long receipts(){return receipts;}public static long income(){return income;}public static long expense(){return expense;}public static double net(){return receipts>0?income-expense:Double.NaN;}
 public static double perHour(){return receipts>0&&online>=60000?net()*3600000d/online:Double.NaN;}public static double xpHour(){return FARMING.delegate.perHour();}
 public static double eta(int level){double rate=xpHour(),remaining=FARMING.remaining(level);return remaining==0?0:rate>0&&Double.isFinite(remaining)?remaining/rate*3600:Double.NaN;}
 private static String valuationKey(){return ModConfig.INSTANCE.telemetry.priceMode+":"+StatsPrices.revision();}
 private static boolean valuationReady(){return inSkyblock&&valuationSource.equals(valuationKey())&&(!ModConfig.INSTANCE.telemetry.priceMode.equals("BAZAAR")||StatsPrices.live());}
 public static double inventory(){return valuationReady()?inventory:Double.NaN;}public static double shards(){return valuationReady()?shards:Double.NaN;}public static String coverage(){return pricedStacks+" оценено / "+unknownStacks+" неизвестно";}
 public static boolean present(){return inSkyblock;}public static long[] graph(){return moneyBins.clone();}public static boolean[] coverageBins(){return binSeen.clone();}
 public static String pet(){return petAt!=0&&System.nanoTime()-petAt<60_000_000_000L?"["+petLevel+"] "+pet+" • "+rarity:"питомец неизвестен";}
 public static String petKey(){return petIdentity+":"+rarity;}public static String petMax(){return petProgress!=null&&petProgress.fresh(world,System.nanoTime())&&petMax>0?Integer.toString(petMax):"?";}
 public static double petValue(){var c=ModConfig.INSTANCE.telemetry;return petAt!=0&&System.nanoTime()-petAt<60_000_000_000L&&c.petPrice>=0&&c.petPriceFor.equals(petKey())?c.petPrice:Double.NaN;}
 private Telemetry(){}
}
