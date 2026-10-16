package com.farmmacro.motion;
import com.farmmacro.panic.PanicDetector;
import net.minecraft.client.Minecraft;
/** Single automation rotation sink and shared movement arbiter. Never rotates a packet or the render camera. */
public final class MotionController {
 public static boolean allowed(MotionPolicy.Owner owner){
  var mc=Minecraft.getInstance();var p=mc.player;
  return MotionPolicy.allowed(owner,new MotionPolicy.Context(p!=null&&mc.level!=null&&p.isAlive(),mc.screen!=null,mc.isPaused(),com.farmmacro.window.BackgroundWork.permitted(mc),com.farmmacro.panic.PanicHandoff.pending(),com.farmmacro.camera.Freecam.active(),com.farmmacro.macro.MacroManager.INSTANCE.isActive(),com.farmmacro.route.NavigationInput.busy(),com.farmmacro.combat.AimAssist.INSTANCE.keyActive(),com.farmmacro.camera.SmoothTurn.isActive()));
 }
 public static boolean movement(MotionPolicy.Owner owner,boolean wanted){return !wanted||allowed(owner);}
 public static boolean turn(MotionPolicy.Owner owner,double dy,double dp){
  if(!Double.isFinite(dy)||!Double.isFinite(dp)||!allowed(owner))return false;
  var p=Minecraft.getInstance().player;float y=p.getYRot(),x=p.getXRot();p.turn(dy/.15,dp/.15);
  PanicDetector.INSTANCE.expectTurn(p.getYRot()-y,p.getXRot()-x);return true;
 }
 public static boolean absolute(MotionPolicy.Owner owner,float yaw,float pitch,boolean snap){
  if(!Float.isFinite(yaw)||!Float.isFinite(pitch)||!allowed(owner))return false;
  var p=Minecraft.getInstance().player;float y=p.getYRot(),x=p.getXRot();pitch=Math.clamp(pitch,-90,90);
  if(snap)p.absSnapRotationTo(yaw,pitch);else{p.setYRot(yaw);p.setXRot(pitch);p.yRotO+=yaw-y;p.xRotO+=pitch-x;}
  PanicDetector.INSTANCE.expectTurn(p.getYRot()-y,p.getXRot()-x);return true;
 }
 private MotionController(){}
}
