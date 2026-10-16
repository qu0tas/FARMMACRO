package com.farmmacro.market;
/** Pure readiness policy: no command, click, permission change or guessed Cookie state. */
public final class SaleStartPolicy {
 public record Context(boolean world,boolean focus,boolean clearScreen,boolean emptyCursor,boolean hypixel,
     boolean enabled,boolean allowlisted,boolean freeControl,boolean farmAllowed,String realm,String access,
     boolean hubTravel,boolean landingsReady,boolean emptyHotbar){}
 public static String refusal(Context c){
  if(!c.world())return "нет живого игрока/мира — войди в SkyBlock";
  if(!c.focus())return "игра не в фокусе или на паузе";
  if(!c.clearScreen())return "закрой другое меню перед запуском";
  if(!c.emptyCursor())return "на курсоре предмет — сначала положи его в инвентарь";
  if(!c.hypixel())return "функция доступна только на Hypixel";
  if(!c.enabled())return "сначала включи разрешение стеша / продажи";
  if(!c.allowlisted())return "нет разрешённых поддержанных ID (или все в исключениях) — ничего не продаётся";
  if(!c.freeControl())return "другая автоматика/ввод занят — останови её перед продажей";
  if(!c.farmAllowed())return "приостанавливается только маршрут фермы, не запись/иной макрос";
  if(!java.util.Set.of("HUB","GARDEN","ISLAND","SKYBLOCK").contains(c.realm()))return "SkyBlock не подтверждён табло; продажи в лобби/неизвестном мире запрещены";
  if(!java.util.Set.of("AUTO","REMOTE","HUB").contains(c.access()))return "неизвестный режим доступа к Bazaar";
  if(c.access().equals("HUB")){
   if(!c.hubTravel())return "включи разрешение перехода через Hub";
   if(!c.realm().equals("HUB")&&!c.landingsReady())return "для Hub нужны записанные прибытия Hub и Garden/Island того же сервера/измерения";
   if(!c.emptyHotbar())return "для NPC Bazaar оставь один пустой слот хотбара (для /bazaar это не требуется)";
  }
  return "";
 }
 /** Remote-only fallback: do not broaden MarketTrip/ResumeTicket or authorize unknown warp targets. */
 public static String remoteRealm(String known,java.util.List<String> sidebar){
  if(known!=null&&!known.isEmpty())return known;
  if(sidebar!=null&&!sidebar.isEmpty()){
   String title=com.farmmacro.panic.ServerText.clean(sidebar.getFirst());
   if(title.equals("SKYBLOCK")||title.equals("SKYBLOCK CO-OP"))return "SKYBLOCK";
  }
  return "";
 }
 private SaleStartPolicy(){}
}
