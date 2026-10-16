package com.farmmacro.gui;
/** Retired entrypoints stay inert even when stale settings ask to activate them. No client. */
public final class ModuleRetirementCheck {
 public static void main(){
  if(ModuleCatalog.ALL.size()!=18||ModuleCatalog.CATEGORIES.length!=5)throw new IllegalStateException("retired module catalogue size");
  for(var module:ModuleCatalog.ALL){
   if(module.tab()<0||module.tab()>=10||module.category()<1||module.category()>=5)throw new IllegalStateException("module index "+module);
   String s=module.title().toLowerCase(java.util.Locale.ROOT);
   if(s.contains("pest")||s.contains("визитор")||s.contains("ловушк")||s.contains("лассо")||s.contains("bazaar")||s.contains("лоадаут"))throw new IllegalStateException("retired card "+s);
  }
  var c=new com.farmmacro.config.ModConfig();c.pestHuntEnabled=true;c.pests.autoStart=true;c.pests.assist.traps=true;c.pests.assist.autoTraps=true;c.visitors.enabled=true;c.visitors.auto=true;c.sanitize();
  if(c.pestHuntEnabled||c.pests.autoStart||c.pests.assist.traps||c.pests.assist.autoTraps||c.visitors.enabled||c.visitors.auto)throw new IllegalStateException("legacy/import resurrection");
  c.stashSales.enabled=true;c.stashSales.auto=true;c.loadouts.enabled=true;c.loadouts.auto=true;c.loadouts.entries.getFirst().auto=true;c.telemetry.bazaar=true;c.telemetry.priceMode="BAZAAR";c.sanitize();
  if(c.stashSales.enabled||c.stashSales.auto||c.loadouts.enabled||c.loadouts.auto||c.loadouts.entries.stream().anyMatch(e->e.auto)||c.telemetry.bazaar||!c.telemetry.priceMode.equals("NPC"))throw new IllegalStateException("retired services resurrected");
  var sale=com.farmmacro.market.StashSales.INSTANCE;var load=com.farmmacro.loadout.GlobalLoadouts.INSTANCE;
  sale.request(null);sale.tick(null);sale.signal("stash");sale.serverSlot(1);sale.capture(null,true);sale.addHeld(null);sale.reportReadiness(null);
  load.request(null);load.tick(null);load.captureHand(null);load.captureScope(null);
  if(sale.active()||sale.awaitingConfirmation()||load.active()||!sale.technicalState().equals("REMOVED")||!load.technicalState().equals("REMOVED")||sale.expectScreen(null)||load.expectScreen(null))throw new IllegalStateException("retired service activation");
  var pests=com.farmmacro.pest.PestHunter.INSTANCE;
  pests.toggle(null);pests.requestTraps(null);pests.monitor(null);pests.tick(null);pests.signal(null,"hi",false);
  var guests=com.farmmacro.visitor.Visitors.INSTANCE;
  guests.request(null);guests.tick(null);guests.signal("OFFER ACCEPTED");
  if(pests.active()||guests.active()||!pests.visiblePests().isEmpty()||!pests.navigationPath().isEmpty())throw new IllegalStateException("retired activation");
  if(!pests.technicalState().equals("REMOVED")||!guests.technicalState().equals("REMOVED"))throw new IllegalStateException("retired status");
  System.out.println("ModuleRetirementCheck: OK (18 cards/5 categories/10 valid tab indices; legacy permission reset; inert Pests/Visitors/Bazaar/Loadouts entrypoints without game)");
 }
 private ModuleRetirementCheck(){}
}
