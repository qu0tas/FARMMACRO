package com.farmmacro.visual;
import com.farmmacro.FarmMacroMod;
import com.farmmacro.config.ModConfig;
import com.farmmacro.macro.MacroManager;
import net.minecraft.client.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ParticleStatus;
/** Temporary option leases. Restore only values still matching ours; never repeatedly overwrite a manual change. */
public final class PerformanceMode {
 private static boolean active,auto,chunksLost;
 private PerformanceMode(){}
 public static boolean active(){return active;}
 public static void toggle(Minecraft mc){if(active)off(mc,null);else on(mc,null);}
 private static boolean saved(){var c=ModConfig.INSTANCE;return c.perfSavedFps>=0||c.perfSavedClouds!=null||c.perfSavedParticles!=null||c.perfSavedShadows>=0||c.perfSavedEntityDist>0||c.perfSavedChunks>=0;}
 public static void on(Minecraft mc,String why){
  if(active||mc.options==null||mc.player==null)return;if(saved()){restore(mc);if(saved())return;}
  var c=ModConfig.INSTANCE;var o=mc.options;
  c.perfSavedFps=o.framerateLimit().get();c.perfAppliedFps=c.perfFps;c.perfSavedClouds=o.cloudStatus().get().name();c.perfSavedParticles=o.particles().get().name();c.perfSavedShadows=o.entityShadows().get()?1:0;c.perfSavedEntityDist=o.entityDistanceScaling().get();
  // Persist original/applied before mutation, including crash recovery; no options.txt write.
  ModConfig.save();active=true;auto=why!=null;chunksLost=false;
  o.framerateLimit().set(c.perfAppliedFps);o.cloudStatus().set(CloudStatus.OFF);o.particles().set(ParticleStatus.MINIMAL);o.entityShadows().set(false);o.entityDistanceScaling().set(.5);chunks(mc);
  msg(mc,"§a⚡ Производительность"+(why==null?"":": "+why)+" §7(FPS ≤ "+c.perfFps+", "+MacroManager.keyName(FarmMacroMod.keyPerf)+" — выкл)");
 }
 public static void off(Minecraft mc,String why){boolean was=active;active=auto=false;chunksLost=false;restore(mc);if(was)msg(mc,why==null?"§7⚡ Производительность выключена":"§e⚡ Производительность выключена: "+why);}
 private static boolean restoreStep(String id,Runnable task){return com.farmmacro.util.Guard.run("perf/"+id,task);}
 public static void restore(Minecraft mc){
  if(mc.options==null||!saved())return;var c=ModConfig.INSTANCE;var o=mc.options;
  if(c.perfSavedFps>=0&&restoreStep("fps",()->{if(o.framerateLimit().get()==(c.perfAppliedFps>=0?c.perfAppliedFps:c.perfFps))o.framerateLimit().set(c.perfSavedFps);}))c.perfSavedFps=c.perfAppliedFps=-1;
  if(c.perfSavedClouds!=null&&restoreStep("clouds",()->{if(o.cloudStatus().get()==CloudStatus.OFF)o.cloudStatus().set(CloudStatus.valueOf(c.perfSavedClouds));}))c.perfSavedClouds=null;
  if(c.perfSavedParticles!=null&&restoreStep("particles",()->{if(o.particles().get()==ParticleStatus.MINIMAL)o.particles().set(ParticleStatus.valueOf(c.perfSavedParticles));}))c.perfSavedParticles=null;
  if(c.perfSavedShadows>=0&&restoreStep("shadows",()->{if(!o.entityShadows().get())o.entityShadows().set(c.perfSavedShadows==1);}))c.perfSavedShadows=-1;
  if(c.perfSavedEntityDist>0&&restoreStep("entities",()->{if(Double.compare(o.entityDistanceScaling().get(),.5)==0)o.entityDistanceScaling().set(c.perfSavedEntityDist);}))c.perfSavedEntityDist=-1;
  restoreChunks(mc);ModConfig.save();
 }
 private static void restoreChunks(Minecraft mc){var c=ModConfig.INSTANCE;var o=mc.options;if(c.perfSavedChunks>=0&&restoreStep("chunks",()->{if(o.renderDistance().get()==c.perfAppliedChunks)o.renderDistance().set(c.perfSavedChunks);}))c.perfSavedChunks=c.perfAppliedChunks=-1;}
 private static void chunks(Minecraft mc){var c=ModConfig.INSTANCE;var option=mc.options.renderDistance();
  if(c.perfChunks==0){if(c.perfSavedChunks>=0){restoreChunks(mc);ModConfig.save();}chunksLost=false;return;}
  if(c.perfSavedChunks>=0&&option.get()!=c.perfAppliedChunks){c.perfSavedChunks=c.perfAppliedChunks=-1;chunksLost=true;ModConfig.save();return;}
  if(chunksLost)return;
  if(c.perfSavedChunks<0){c.perfSavedChunks=option.get();c.perfAppliedChunks=Math.min(c.perfSavedChunks,c.perfChunks);ModConfig.save();option.set(c.perfAppliedChunks);}
  else {int desired=Math.min(c.perfSavedChunks,c.perfChunks);if(desired!=c.perfAppliedChunks){c.perfAppliedChunks=desired;ModConfig.save();option.set(desired);}}
 }
 public static void tick(Minecraft mc){
  if(!active&&saved()){restore(mc);return;}
  if(mc.player==null||mc.level==null){if(active)off(mc,"выход из мира");return;}
  boolean playing=MacroManager.INSTANCE.isActive()||com.farmmacro.macro.Scheduler.isBreak()||ModConfig.INSTANCE.aimAssistEnabled&&com.farmmacro.combat.AimAssist.INSTANCE.keyActive()||com.farmmacro.pest.PestHunter.INSTANCE.active()||com.farmmacro.visitor.Visitors.INSTANCE.active()||com.farmmacro.market.StashSales.INSTANCE.active()||com.farmmacro.loadout.GlobalLoadouts.INSTANCE.active();
  if(!active&&playing&&ModConfig.INSTANCE.perfOnStart&&!com.farmmacro.panic.PanicHandoff.pending())on(mc,"старт автоматики");
  else if(active&&auto&&!playing)off(mc,"автоматика остановлена");
  if(active){var c=ModConfig.INSTANCE;var fps=mc.options.framerateLimit();if(c.perfSavedFps>=0&&fps.get()==c.perfAppliedFps&&c.perfAppliedFps!=c.perfFps){c.perfAppliedFps=c.perfFps;ModConfig.save();fps.set(c.perfAppliedFps);}chunks(mc);}
 }
 /** Extra cap only lowers vanilla's limit, never raises a minimized/AFK limit or fakes focus. */
 public static int backgroundLimit(int vanilla){if(!active)return vanilla;var mc=Minecraft.getInstance();int cap=ModConfig.INSTANCE.perfBackgroundFps;return mc.player!=null&&mc.level!=null&&!mc.isWindowActive()&&!com.farmmacro.panic.PanicHandoff.pending()&&cap>0?Math.min(vanilla,cap):vanilla;}
 public static boolean hideBlockParticles(){return active&&ModConfig.INSTANCE.perfBlockParticles&&!com.farmmacro.panic.PanicHandoff.pending();}
 public static float worldVolume(float volume,net.minecraft.sounds.SoundSource source){if(!active||com.farmmacro.panic.PanicHandoff.pending())return volume;return switch(source){case BLOCKS,HOSTILE,NEUTRAL,AMBIENT,WEATHER->volume*(ModConfig.INSTANCE.perfWorldVolume/100f);default->volume;};}
 private static void msg(Minecraft mc,String text){if(mc.player!=null)mc.player.sendOverlayMessage(Component.literal("§8[§9Kaelix§8] §r"+text));}
}
