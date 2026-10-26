package com.farmmacro.fishing;
import com.farmmacro.config.ModConfig;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.panic.PanicDetector;
import com.farmmacro.panic.PanicHandoff;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;
/** Attended, explicitly armed fishing. Adapted CC0 workflow; no executors/custom packets/camera/movement. */
public final class AutoFish {
 public static final AutoFish INSTANCE=new AutoFish();
 private final FishingCycle cycle=new FishingCycle(new SplittableRandom());
 private ClientLevel world;private Object player;private ItemStack rod=ItemStack.EMPTY,tool=ItemStack.EMPTY;private int rodSlot,toolSlot,expectedSlot;private boolean toolSelected;
 private long armUntil;private Object armScreen;private double x,y,z;private String status="Рыбалка выключена";private int markerChecks;
 public boolean active(){return cycle.active();}public boolean armed(){return armUntil>0;}public String status(){return status;}
 private static long now(){return System.nanoTime()/1_000_000L;}
 private FishingSettings config(){return ModConfig.INSTANCE.fishing;}
 public void armFromGui(){if(active()||armed()){stop("остановлено вручную");return;}if(!config().enabled){status="Сначала включи разрешение рыбалки";return;}armScreen=Minecraft.getInstance().screen;armUntil=now()+15_000;status="Закрой меню за 15 с, держи удочку; первый заброс вручную";}
 public void armFromCommand(Minecraft mc){
  if(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen){stop("новый ручной запуск");armScreen=mc.screen;armUntil=now()+15_000;status="Закрой чат: запуск с удочкой, первый заброс вручную";}else start(mc);
 }
 public void toggle(Minecraft mc){if(active()||armed())stop("остановлено вручную");else start(mc);}
 private boolean busy(Minecraft mc){return MacroManager.INSTANCE.isActive()||MacroManager.INSTANCE.isRecording()||com.farmmacro.route.RouteEditor.isActive()
  ||com.farmmacro.route.NavigationInput.busy()||com.farmmacro.camera.SmoothTurn.isActive()||com.farmmacro.camera.Mousemat.busy()
  ||com.farmmacro.macro.MouseHold.INSTANCE.isManual()||com.farmmacro.macro.MouseHold.INSTANCE.running()!=null
  ||com.farmmacro.camera.Freecam.active()||com.farmmacro.camera.Freelook.active()||com.farmmacro.camera.MouseLock.isLocked()
  ||com.farmmacro.combat.CombatSway.INSTANCE.active()||com.farmmacro.combat.SpecialZealotHunt.INSTANCE.active()
  ||com.farmmacro.combat.AimAssist.INSTANCE.keyActive()||com.farmmacro.panic.HumanReaction.running();}
 private boolean playable(Minecraft mc){return mc!=null&&mc.player!=null&&mc.level!=null&&mc.gameMode!=null&&mc.player.isAlive()&&!mc.player.isSpectator()
  &&mc.screen==null&&mc.getOverlay()==null&&!mc.isPaused()&&!PanicHandoff.pending()&&com.farmmacro.window.BackgroundWork.permitted(mc);}
 public void start(Minecraft mc){
  armUntil=0;armScreen=null;
  if(!config().enabled||!playable(mc)||busy(mc)){status="Старт отклонён: разрешение, окно, паника или другая автоматика";return;}
  if(!mc.player.getMainHandItem().is(Items.FISHING_ROD)){status="Возьми удочку в основную руку";return;}
  rodSlot=expectedSlot=mc.player.getInventory().getSelectedSlot();toolSlot=config().toolSlot-1;rod=mc.player.getMainHandItem().copy();tool=mc.player.getInventory().getItem(toolSlot).copy();toolSelected=false;
  if(config().fireveil&&(toolSlot==rodSlot||tool.isEmpty()||tool.is(Items.FISHING_ROD))){status="Fireveil: выбери отдельный непустой слот предмета";return;}
  world=mc.level;player=mc.player;x=mc.player.getX();y=mc.player.getY();z=mc.player.getZ();cycle.start(now());PanicDetector.INSTANCE.beginRun();status="Ожидаю твой первый заброс";
 }
 public void stop(String why){armUntil=0;armScreen=null;cycle.stop(why);world=null;player=null;rod=tool=ItemStack.EMPTY;toolSelected=false;status="Стоп: "+why;}
 /** Physical clicks/hotbar interrupt queued work; vanilla input itself is never swallowed. */
 public void userInput(){if(active()&&cycle.stage()!=FishingCycle.Stage.WAIT_HOOK)stop("ручной ввод — новый запуск вручную");}
 private static boolean same(ItemStack a,ItemStack b){
  if(a.isEmpty()||b.isEmpty()||!a.is(b.getItem()))return false;
  // Custom item identity only. Ignore dynamic lore, durability/count/mana changes.
  return identity(a).equals(identity(b));
 }
 private static String identity(ItemStack s){var d=s.get(DataComponents.CUSTOM_DATA);if(d==null)return "";var e=d.copyTag().getCompoundOrEmpty("ExtraAttributes");return e.getStringOr("id","")+"/"+e.getStringOr("uuid","");}
 private FishingHook hook(Minecraft mc){var h=mc.player.fishing;return h!=null&&!h.isRemoved()&&h.level()==mc.level&&h.getPlayerOwner()==mc.player?h:null;}
 private boolean bite(Minecraft mc,FishingHook hook){
  if(hook==null)return false;
  var box=hook.getBoundingBox().inflate(4,4,4);var hooks=mc.level.getEntitiesOfClass(FishingHook.class,box);markerChecks=0;if(hooks.size()>64)return false;
  for(var stand:mc.level.getEntitiesOfClass(ArmorStand.class,box)){
   if(++markerChecks>64)return false;
   if(!stand.hasCustomName()||!FishingMessages.clean(stand.getCustomName().getString()).equals("!!!"))continue;
   double own=stand.distanceToSqr(hook);if(!FishingMarker.candidate(own))continue;
   boolean nearest=true;for(var other:hooks){if(other!=hook&&!other.isRemoved()&&FishingMarker.competitor(own,stand.distanceToSqr(other))){nearest=false;break;}}
   if(nearest)return true;
  }return false;
 }
 public void message(String text,boolean overlay){
  if(!overlay&&active()&&config().fireveil&&FishingMessages.creature(text))cycle.creature(now());
 }
 public void screenOpening(net.minecraft.client.gui.screens.Screen screen){
  if(screen!=null&&(active()||armed()&&screen!=armScreen))stop("открыто окно");
 }
 public void tick(Minecraft mc){
  long time=now();
  if(armed()){
   if(time>armUntil){stop("время подготовки истекло");return;}
   if(mc.screen==armScreen&&mc.screen!=null)return;
   if(mc.screen!=null){stop("открыто другое окно");return;}start(mc);
  }
  if(!active())return;
  try{
   if(!config().enabled||!playable(mc)||mc.level!=world||mc.player!=player||busy(mc)){stop("мир, окно, пауза или другой модуль");return;}
   if(mc.player.position().distanceToSqr(new net.minecraft.world.phys.Vec3(x,y,z))>16){stop("игрок переместился больше 4 блоков");return;}
   if(mc.player.getInventory().getSelectedSlot()!=expectedSlot||!same(mc.player.getMainHandItem(),toolSelected?tool:rod)){
    stop("предмет/слот изменился — передаю управление");return;
   }
   var h=hook(mc);var action=cycle.tick(time,h==null?-1:h.getId(),bite(mc,h),config());
   switch(action){
    case REEL -> {if(h==null||h.getId()!=cycle.expectedHook())throw new IllegalStateException("поплавок исчез");use(mc);}
    case CAST -> {if(h!=null)throw new IllegalStateException("уже есть поплавок");use(mc);}
    case SELECT_TOOL -> {
     if(!same(mc.player.getInventory().getItem(toolSlot),tool))throw new IllegalStateException("предмет Fireveil изменился");
     select(mc,toolSlot);toolSelected=true;
    }
    case USE_TOOL -> {if(!config().fireveil||h!=null||!same(mc.player.getMainHandItem(),tool))throw new IllegalStateException("Fireveil: контекст изменился");use(mc);}
    case RESTORE_ROD -> {
     if(!same(mc.player.getInventory().getItem(rodSlot),rod))throw new IllegalStateException("удочка исчезла");
     select(mc,rodSlot);toolSelected=false;
    }
    default -> {}
   }
   if(!active()){stop(cycle.stopReason());return;}
   status=switch(cycle.stage()){
    case WAIT_HOOK->"Ожидаю ручной заброс";case WAIT_BITE->"Жду поклёвку · свой поплавок";
    case REEL_DELAY->"Поклёвка · готовлю подсечку";case WAIT_GONE->"Подсечка · жду исчезновения поплавка";
    case RECAST_DELAY->"Перезаброс · задержка";case WAIT_CAST->"Жду появления нового поплавка";
    case SELECT_TOOL,USE_TOOL,RESTORE_ROD->"Fireveil · "+cycle.stage();default->"Выключена";
   };
   if(action!=FishingCycle.Action.NONE&&config().debug)mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[Kaelix Fishing] "+action+" · "+cycle.stage()+" · "+time+" ms"));
  }catch(RuntimeException e){com.farmmacro.FarmMacroMod.LOGGER.warn("AutoFish stopped",e);stop("ошибка/изменение контекста: "+e.getMessage());}
 }
 private void select(Minecraft mc,int slot){mc.player.getInventory().setSelectedSlot(slot);expectedSlot=slot;PanicDetector.INSTANCE.expectSlot(slot);}
 private void use(Minecraft mc){mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);mc.player.swing(InteractionHand.MAIN_HAND);}
 public String counters(){return "Подсечки "+cycle.reeled()+" · забросы "+cycle.casts()+" · Fireveil "+cycle.tools();}
 public String technical(){return cycle.stage()+" · маркеры "+markerChecks+" · сообщений "+FishingMessages.size();}
 private AutoFish(){}
}
