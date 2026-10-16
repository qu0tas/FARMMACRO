package com.farmmacro.camera;
import com.farmmacro.config.ModConfig;
import com.farmmacro.macro.MacroManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
/** Render-only detached camera. No fake player, movement packets, server flight or auto-resume. */
public final class Freecam {
 private static boolean active;private static Object world,player;private static CameraType previous;
 private static FreecamMath.Point anchor,position;private static float yaw,pitch;private static long last;
 public static boolean active(){return active;}
 public static FreecamMath.Point position(){return position;}
 public static float yaw(){return yaw;}public static float pitch(){return pitch;}
 public static void toggle(Minecraft mc){if(active)off(mc);else on(mc);}
 public static void on(Minecraft mc){
  if(mc.player==null||mc.level==null||mc.screen!=null||!mc.isWindowActive()||mc.isPaused()||com.farmmacro.panic.PanicHandoff.pending())return;
  MacroManager.INSTANCE.stopPlayback(mc,"Остановлено для Freecam");com.farmmacro.combat.AimAssist.INSTANCE.handoff("freecam");
  com.farmmacro.combat.CombatSway.INSTANCE.userTakeover(mc);com.farmmacro.macro.MouseHold.INSTANCE.stopAll(mc);
  SmoothTurn.stop();Freelook.off(mc,null);MouseLock.unlock(mc,null);Ungrab.off(mc,null);
  if(com.farmmacro.route.NavigationInput.busy()||MacroManager.INSTANCE.isRecording())return;
  world=mc.level;player=mc.player;previous=mc.options.getCameraType();mc.options.setCameraType(CameraType.FIRST_PERSON);
  var eye=mc.player.getEyePosition();anchor=position=new FreecamMath.Point(eye.x,eye.y,eye.z);yaw=mc.player.getYRot();pitch=mc.player.getXRot();last=0;active=true;
  release(mc);
  mc.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Kaelix: Freecam · WASD / Space / Shift · ввод игрока отключён"));
 }
 public static void off(Minecraft mc){
  if(!active)return;active=false;last=0;world=player=null;anchor=position=null;
  if(mc!=null&&mc.options!=null){if(previous!=null)mc.options.setCameraType(previous);release(mc);}previous=null;
 }
 public static void turn(double dx,double dy){if(!active)return;float[] a=FreelookMath.turn(yaw,pitch,dx,dy);yaw=a[0];pitch=a[1];}
 public static void tick(Minecraft mc){if(active&&(mc.player!=player||mc.level!=world||mc.player==null||!mc.player.isAlive()||mc.screen!=null||!mc.isWindowActive()||mc.isPaused()||com.farmmacro.panic.PanicHandoff.pending()))off(mc);}
 public static void frame(){var mc=Minecraft.getInstance();tick(mc);if(!active)return;long now=System.nanoTime();double dt=last==0?0:(now-last)/1e9;last=now;var o=mc.options;
  int forward=(o.keyUp.isDown()?1:0)-(o.keyDown.isDown()?1:0),right=(o.keyRight.isDown()?1:0)-(o.keyLeft.isDown()?1:0),up=(o.keyJump.isDown()?1:0)-(o.keyShift.isDown()?1:0);
  position=FreecamMath.step(position,anchor,yaw,forward,right,up,ModConfig.INSTANCE.freecamSpeed,dt,ModConfig.INSTANCE.freecamRadius);
 }
 private static void release(Minecraft mc){var o=mc.options;for(var key:new net.minecraft.client.KeyMapping[]{o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keyShift,o.keySprint,o.keyAttack,o.keyUse}){
  boolean toggle=key==o.keyShift&&o.toggleCrouch().get()||key==o.keySprint&&o.toggleSprint().get();if(toggle){if(key.isDown())key.setDown(true);}else key.setDown(false);
 }}
 private Freecam(){}
}
