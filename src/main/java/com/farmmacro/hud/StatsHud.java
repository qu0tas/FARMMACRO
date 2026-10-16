package com.farmmacro.hud;
import com.farmmacro.config.ModConfig;
import com.farmmacro.gui.Ui;
import com.farmmacro.stats.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.*;
/** Text-first cards; no sample numbers, no invented revenue, bounded per-minute receipt chart. */
public final class StatsHud {
 private record Card(String title,List<String> rows,int color,double progress,int extra){}
 private record Prepared(Card card,int width,Object font,Object world,Object config,int style,long at,long frame){}
 private static final Prepared[] prepared=new Prepared[6];private static Card captured;private static long frame;private static TaskSnapshot task;
 private static long[] graphValues=new long[30];private static boolean[] graphValid=new boolean[30];
 private static final long[] usedFrame=new long[6];private static int layoutStyle;private static long graphMax=1;private static String graphLabel="";
 public static void clear(){Arrays.fill(prepared,null);task=null;}
 public static void beginLayout(){frame++;task=null;var c=c();int flags=(c.enabled?1:0)|(c.hud?2:0)|(c.compact?4:0)|(c.graph?8:0)|(c.save?16:0)|(c.general?32:0)|(c.farms?64:0)|(c.combat?128:0)|(c.pests?256:0)|(c.bazaar?512:0);
  int h=31*(31*(31*flags+c.farmingCap)+c.theme.hashCode())+c.priceMode.hashCode();h=31*h+c.petPriceFor.hashCode();layoutStyle=31*(31*(31*h+Double.hashCode(c.petPrice))+c.skill.hashCode())+(c.skillHud?1:0)+(c.petHud?2:0);
 }
 private static TaskSnapshot task(Minecraft mc){if(task==null)task=TaskSnapshot.read(mc);return task;}
 @FunctionalInterface private interface Builder{int build(GuiGraphicsExtractor g,Minecraft mc,int x,int y,int w);}
 private static int cached(int id,GuiGraphicsExtractor g,Minecraft mc,int x,int y,int w,Builder builder){
  int style=31*layoutStyle+ModConfig.INSTANCE.hudStyle.hashCode();
  long now=System.nanoTime();Prepared p=prepared[id];boolean hot=id==0||id>=4;
  if(p==null||p.width!=w||p.font!=mc.font||p.world!=mc.level||p.config!=ModConfig.INSTANCE||p.style!=style||(hot?p.frame!=frame:usedFrame[id]!=frame&&now-p.at>=100_000_000L)){
   captured=null;builder.build(null,mc,0,0,w);p=new Prepared(captured,w,mc.font,mc.level,ModConfig.INSTANCE,style,now,frame);prepared[id]=p;
   if(id==1){graphValues=Telemetry.graph();graphValid=Telemetry.coverageBins();graphMax=1;for(int i=0;i<30;i++)if(graphValid[i])graphMax=Math.max(graphMax,Math.abs(graphValues[i]));graphLabel="30 мин · coins/мин · ±"+num(graphMax);}
   com.farmmacro.util.Diagnostics.add(com.farmmacro.util.Diagnostics.Counter.HUD_PREPARES);
  }
  usedFrame[id]=frame;Card data=p.card;if(data==null)return 0;int h=card(g,mc,x,y,w,data.title,data.rows,data.color,data.progress,data.extra);
  if(id==1&&data.extra>0&&g!=null)graph(g,mc,x+9,y+h-49,w-18);return h;
 }
 public static int activity(GuiGraphicsExtractor g,Minecraft mc,int x,int y,int w){return cached(0,g,mc,x,y,w,StatsHud::buildActivity);}
 public static int profit(GuiGraphicsExtractor g,Minecraft mc,int x,int y,int w){return cached(1,g,mc,x,y,w,StatsHud::buildProfit);}
 public static int farming(GuiGraphicsExtractor g,Minecraft mc,int x,int y,int w){return cached(2,g,mc,x,y,w,StatsHud::buildFarming);}
 public static int pet(GuiGraphicsExtractor g,Minecraft mc,int x,int y,int w){return cached(3,g,mc,x,y,w,StatsHud::buildPet);}
 public static int combat(GuiGraphicsExtractor g,Minecraft mc,int x,int y,int w){return cached(4,g,mc,x,y,w,StatsHud::buildCombat);}
 public static int pests(GuiGraphicsExtractor g,Minecraft mc,int x,int y,int w){return cached(5,g,mc,x,y,w,StatsHud::buildPests);}
 private static StatsSettings c(){return ModConfig.INSTANCE.telemetry;}
 public static int fg(){return c().theme.equals("PAPER")?0xff17212d:0xffeff4fa;}public static int sub(){return c().theme.equals("PAPER")?0xff3a4c5c:0xffb2c0d0;}
 public static String num(double n){if(!Double.isFinite(n))return "—";double a=Math.abs(n);return String.format(Locale.ROOT,a>=1e9?"%.2fb":a>=1e6?"%.2fm":a>=1e3?"%.1fk":"%.0f",a>=1e9?n/1e9:a>=1e6?n/1e6:a>=1e3?n/1e3:n);}
 public static String eta(double sec){if(!Double.isFinite(sec)||sec<0)return "—";if(sec==0)return "достигнуто";return sec>=86400?String.format(Locale.ROOT,"%.1f д",sec/86400):String.format(Locale.ROOT,"%dч %02dм",(long)sec/3600,(long)sec/60%60);}
 private static boolean show(){return c().enabled&&c().hud;}
 private static int card(GuiGraphicsExtractor g,Minecraft mc,int x,int y,int w,String title,List<String> rows,int color,double progress){
  return card(g,mc,x,y,w,title,rows,color,progress,0);
 }
 private static int card(GuiGraphicsExtractor g,Minecraft mc,int x,int y,int w,String title,List<String> rows,int color,double progress,int extra){
  boolean modern="KAELIX".equals(ModConfig.INSTANCE.hudStyle);int h=(modern?24:22)+rows.size()*11+(Double.isFinite(progress)?8:0)+extra;if(g==null){captured=new Card(title,List.copyOf(rows),color,progress,extra);return h;}FarmHud.panel(g,x,y,w,h,color);Ui.text(g,mc.font,Ui.ellipsize(mc.font,title,w-18),x+9,y+6,fg());if(modern)g.fill(x+9,y+16,x+w-9,y+17,Ui.alpha(sub(),.16f));int yy=y+(modern?21:19),index=0;for(String row:rows){Ui.text(g,mc.font,Ui.ellipsize(mc.font,row,w-18),x+9,yy,modern&&index++==0?fg():sub());yy+=11;}
  if(Double.isFinite(progress)){g.fill(x+9,yy+1,x+w-9,yy+4,c().theme.equals("PAPER")?0xffbfcbd3:0xff334256);int len=(int)((w-18)*Math.clamp(progress,0,1));if(len>0)g.fill(x+9,yy+1,x+9+len,yy+4,color);}return h;
 }
 private static int buildActivity(GuiGraphicsExtractor g,Minecraft mc,int x,int y,int w){if(!c().general&&!com.farmmacro.panic.PanicHandoff.pending())return 0;var t=task(mc);List<String> lines=new ArrayList<>();lines.add(t.group()+" / "+t.task());if(c().compact&&!com.farmmacro.panic.PanicHandoff.pending())lines.add(t.detail());else lines.addAll(FarmHud.wrap(mc.font,t.detail(),w-18));if(!c().compact||com.farmmacro.panic.PanicHandoff.pending())lines.addAll(FarmHud.wrap(mc.font,t.hint(),w-18));if(t.crouch())lines.add("SHIFT УДЕРЖИВАЕТСЯ · перехват вручную");return card(g,mc,x,y,w,t.state(),lines,t.state().contains("ПАНИКА")?0xffff7278:0xff67d8b4,t.progress());}
 private static int buildProfit(GuiGraphicsExtractor g,Minecraft mc,int x,int y,int w){if(!show())return 0;List<String> rows=new ArrayList<>();rows.add("Учтённый поток: "+num(Telemetry.net())+" coins");rows.add("+"+num(Telemetry.income())+" / −"+num(Telemetry.expense())+" • "+num(Telemetry.perHour())+"/ч");if(!c().compact){rows.add("Только свои квитанции • "+Telemetry.receipts());rows.add("Не чистая прибыль; другие доходы/затраты неизвестны");rows.add("Инвентарь ≈ "+num(Telemetry.inventory())+" • "+Telemetry.coverage());rows.add("Шарды ≈ "+num(Telemetry.shards())+" (только инвентарь)");rows.add(StatsPrices.source());if(c().save){var t=StatsLedger.totals();rows.add("День: "+num(t.dayIncome()-t.dayExpense())+" / всего: "+num(t.income()-t.expense()));rows.add("Все профили устройства • локальная дата");}}
  boolean chart=c().graph&&!c().compact;int h=card(g,mc,x,y,w,"Экономика · наблюдения",rows,0xff67d8b4,Double.NaN,chart?49:0);if(chart&&g!=null)graph(g,mc,x+9,y+h-49,w-18);return h;
 }
 private static void graph(GuiGraphicsExtractor g,Minecraft mc,int x,int y,int w){var values=graphValues;var valid=graphValid;long max=graphMax;Ui.text(g,mc.font,Ui.ellipsize(mc.font,graphLabel,w),x,y,sub());int top=y+12,mid=top+13;g.fill(x,mid,x+w,mid+1,0x8067788c);int barW=Math.max(1,w/30);for(int i=0;i<30;i++){int xx=x+i*w/30;if(!valid[i]){g.fill(xx,top+28,xx+Math.max(1,barW-1),top+29,0x807d8592);continue;}int a=(int)Math.round(Math.abs((double)values[i])/max*12);if(a>0)g.fill(xx,values[i]>=0?mid-a:mid+1,xx+Math.max(1,barW-1),values[i]>=0?mid:mid+1+a,values[i]>=0?0xff67d8b4:0xffff7278);}Ui.text(g,mc.font,Ui.ellipsize(mc.font,"−30м · серые=пропуск · сейчас*",w),x,top+32,sub());}
 private static List<String> wrapRows(Minecraft mc,List<String> rows,int w){List<String> result=new ArrayList<>();for(String row:rows)result.addAll(FarmHud.wrap(mc.font,row,w-18));return result;}
 private static int buildFarming(GuiGraphicsExtractor g,Minecraft mc,int x,int y,int w){if(!show()||!c().skillHud)return 0;var p=Telemetry.selectedSkill(task(mc).group(),mc.level);boolean fresh=Telemetry.present()&&p.fresh(mc.level);int level=fresh?p.level():-1;List<String> rows=new ArrayList<>();
  rows.add("Уровень "+(level<0?"—":level)+" • +"+num(p.gained())+" XP за наблюдения");
  rows.add("До уровня: "+(fresh?num(p.remaining(level+1)):"—")+" XP");
  rows.add(num(fresh?p.perHour():Double.NaN)+" XP/ч • ETA "+(fresh?eta(p.eta(level+1)):"—"));
  if(!c().compact){rows.add(fresh?"Actionbar • округлённые данные":"Нет свежего однозначного progress");rows.add("Темп: ≥60 с подтверждённых интервалов");
   if(p.skill().id().equals("FARMING")){rows.add("Farming cap задан: "+c().farmingCap);rows.add("До cap: "+(fresh?num(p.remaining(c().farmingCap)):"—")+" XP");if(c().save){var t=StatsLedger.totals();rows.add("Только Farming: день "+num(t.dayXp())+" / всего "+num(t.xp()));}}}
  return card(g,mc,x,y,w,"Навык · "+p.skill().name(),wrapRows(mc,rows,w),0xffe7bd73,fresh?p.progress():Double.NaN);
 }
 private static int buildPet(GuiGraphicsExtractor g,Minecraft mc,int x,int y,int w){if(!show()||!c().petHud)return 0;List<String> rows=new ArrayList<>();rows.add(Telemetry.pet());var p=Telemetry.petProgress(mc.level);
  if(p==null){rows.add("XP неизвестен • открой меню Pets");rows.add("Снимок XP хранится до 60 с");}
  else {if(p.max())rows.add("MAX LEVEL • до уровня: 0 XP");else {rows.add("До уровня "+(p.level()+1)+": "+(p.approximate()?"≈ ":"")+num(p.remaining())+" XP");if(Double.isFinite(p.required()))rows.add(num(p.current())+" / "+num(p.required())+" XP");else rows.add("Формат next-level не распознан");}
   rows.add("Pets • снимок "+p.ageSeconds(System.nanoTime())+" с назад");if(!c().compact&&Double.isFinite(p.total()))rows.add("Всего XP (petInfo): "+num(p.total()));}
  if(!c().compact){rows.add("Цена ≈ "+num(Telemetry.petValue())+" • ручная пометка");rows.add("Tab не обновляет снимок XP");}
  return card(g,mc,x,y,w,"Питомец",wrapRows(mc,rows,w),0xffb8a5f6,p==null?Double.NaN:p.progress());}
 private static int buildCombat(GuiGraphicsExtractor g,Minecraft mc,int x,int y,int w){if(!c().combat)return 0;var a=com.farmmacro.combat.AimAssist.INSTANCE;var sw=com.farmmacro.combat.CombatSway.INSTANCE;var t=task(mc);if(!a.keyActive()&&!sw.holdingCrouch()&&!t.group().equals("Combat"))return 0;List<String> rows=new ArrayList<>();rows.add(TaskSnapshot.combatProfile());if(c().compact)rows.add(a.status());else rows.addAll(FarmHud.wrap(mc.font,a.status(),w-18));if(!c().compact){rows.addAll(FarmHud.wrap(mc.font,a.fireStatus(),w-18));rows.addAll(FarmHud.wrap(mc.font,a.specialStatus(),w-18));}if(c().enabled){var p=Telemetry.skill("COMBAT");boolean fresh=Telemetry.present()&&p.fresh(mc.level);rows.add("Combat XP +"+num(p.gained())+" • "+num(fresh?p.perHour():Double.NaN)+"/ч");if(!c().compact)rows.add("До уровня: "+(fresh?num(p.remaining(p.level()+1)):"—")+" XP");}if(sw.holdingCrouch())rows.add("Shift удерживается до передачи управления");return card(g,mc,x,y,w,"Combat",rows,0xffb8a5f6,Double.NaN);}
 private static int buildPests(GuiGraphicsExtractor g,Minecraft mc,int x,int y,int w){if(!c().pests||!com.farmmacro.pest.PestHunter.INSTANCE.active())return 0;return card(g,mc,x,y,w,"PESTS · ALPHA",c().compact?List.of(com.farmmacro.pest.PestHunter.INSTANCE.status()):FarmHud.wrap(mc.font,com.farmmacro.pest.PestHunter.INSTANCE.status(),w-18),0xffe7bd73,Double.NaN);}
 private StatsHud(){}
}
