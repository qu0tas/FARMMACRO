package com.farmmacro.motion;
/** Global Motion tracking settings; combat profiles may select targets, never override this camera. */
public final class SharedCameraSettings {
 public String aimTrackingMode="human";
 public int aimHumanStrength=50,aimSpeedVariationPct=25,aimReactionMinMs=120,aimReactionMaxMs=220,aimAcquireSpeedPct=135,aimTrackSpeedPct=100,aimIdleDelayMs=1000;
 public double aimSpecialTurnSpeed=900,aimJitterDegrees=.12,aimTurnSpeed=720,aimIdleYaw=60,aimIdleSpeed=115;
 public boolean aimJitterEnabled=false,aimYieldMouse=true;
 public static final java.util.Set<String> FIELDS=java.util.Set.of("aimTrackingMode","aimHumanStrength","aimSpeedVariationPct","aimReactionMinMs","aimReactionMaxMs","aimAcquireSpeedPct","aimTrackSpeedPct","aimSpecialTurnSpeed","aimJitterEnabled","aimJitterDegrees","aimTurnSpeed","aimIdleDelayMs","aimIdleYaw","aimIdleSpeed","aimYieldMouse");
 public String mode(MotionSettings motion){return motion.aether()?"aether":aimTrackingMode;}
 public void importLegacy(com.farmmacro.config.ModConfig c){
  for(var f:getClass().getFields())if(FIELDS.contains(f.getName()))try{f.set(this,c.getClass().getField(f.getName()).get(c));}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}sanitize();
 }
 public SharedCameraSettings copy(){var s=new SharedCameraSettings();for(var f:getClass().getFields())if(FIELDS.contains(f.getName()))try{f.set(s,f.get(this));}catch(IllegalAccessException e){throw new IllegalStateException(e);}s.sanitize();return s;}
 public void sanitize(){
  if(!"precise".equals(aimTrackingMode)&&!"human".equals(aimTrackingMode))aimTrackingMode="human";
  aimHumanStrength=Math.clamp(aimHumanStrength,0,100);aimSpeedVariationPct=Math.clamp(aimSpeedVariationPct,0,60);
  aimReactionMinMs=Math.clamp(aimReactionMinMs,0,500);aimReactionMaxMs=Math.clamp(aimReactionMaxMs,aimReactionMinMs,700);
  aimAcquireSpeedPct=Math.clamp(aimAcquireSpeedPct,100,250);aimTrackSpeedPct=Math.clamp(aimTrackSpeedPct,25,150);aimIdleDelayMs=Math.clamp(aimIdleDelayMs,400,5000);
  aimSpecialTurnSpeed=number(aimSpecialTurnSpeed,20,1440,900);aimTurnSpeed=number(aimTurnSpeed,20,1440,720);
  aimJitterDegrees=number(aimJitterDegrees,0,.5,.12);aimIdleYaw=number(aimIdleYaw,20,100,60);aimIdleSpeed=number(aimIdleSpeed,30,300,115);
 }
 private static double number(double n,double lo,double hi,double fallback){return Double.isFinite(n)?Math.clamp(n,lo,hi):fallback;}
}
