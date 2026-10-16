package com.farmmacro.motion;
/** Pure shared arbitration. Releasing input never needs permission. No frame-count-dependent ownership. */
public final class MotionPolicy {
 public enum Owner { FARM,RECORDING,NAVIGATION,COMBAT,PRESET }
 public record Context(boolean live,boolean screen,boolean paused,boolean focusAllowed,boolean alarm,boolean freecam,boolean macro,boolean navigation,boolean combat,boolean smooth){}
 public static boolean allowed(Owner owner,Context c){
  if(owner==null||c==null||!c.live||c.screen||c.paused||!c.focusAllowed||c.alarm||c.freecam)return false;
  return switch(owner){case NAVIGATION->c.navigation;case FARM,RECORDING->c.macro&&!c.navigation;case COMBAT->c.combat&&!c.macro&&!c.navigation&&!c.smooth;case PRESET->!c.navigation&&!c.combat;};
 }
 public static boolean background(boolean permission,boolean focused,boolean cursorGrabbed,boolean combat){return (focused||permission)&&(!combat||cursorGrabbed||permission);}
 private MotionPolicy(){}
}
