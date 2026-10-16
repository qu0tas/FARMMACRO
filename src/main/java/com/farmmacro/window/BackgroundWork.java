package com.farmmacro.window;
import com.farmmacro.config.ModConfig;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.combat.AimAssist;
import net.minecraft.client.Minecraft;
/** Explicit background permission, not fake focus. Screens, real pause and alarms still stop work. */
public final class BackgroundWork {
 private static Boolean savedPause;
 public static boolean permitted(Minecraft mc){return mc!=null&&com.farmmacro.motion.MotionPolicy.background(ModConfig.INSTANCE.backgroundWork,mc.isWindowActive(),false,false);}
 public static boolean combatInput(Minecraft mc){return mc!=null&&com.farmmacro.motion.MotionPolicy.background(ModConfig.INSTANCE.backgroundWork,mc.isWindowActive(),mc.mouseHandler.isMouseGrabbed(),true);}
 public static boolean running(){return MacroManager.INSTANCE.isActive()||com.farmmacro.macro.MouseHold.INSTANCE.isManual()||AimAssist.INSTANCE.keyActive()&&ModConfig.INSTANCE.aimAssistEnabled;}
 public static void tick(Minecraft mc){
  if(mc==null||mc.options==null)return;
  if(!ModConfig.INSTANCE.backgroundWork&&!mc.isWindowActive()&&mc.player!=null){
   if(MacroManager.INSTANCE.isActive())MacroManager.INSTANCE.stopPlayback(mc,"Фоновая работа выключена");
   if(AimAssist.INSTANCE.keyActive())AimAssist.INSTANCE.handoff("фоновая работа выключена");
  }
  boolean own=ModConfig.INSTANCE.backgroundWork&&mc.player!=null&&mc.level!=null&&running()&&!com.farmmacro.panic.PanicHandoff.pending()&&!com.farmmacro.camera.Freecam.active();
  if(own){if(savedPause==null)savedPause=mc.options.pauseOnLostFocus;mc.options.pauseOnLostFocus=false;}
  else if(savedPause!=null){mc.options.pauseOnLostFocus=savedPause;savedPause=null;}
 }
 private BackgroundWork(){}
}
