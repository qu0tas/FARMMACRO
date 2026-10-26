package com.farmmacro.fishing;
/** Saved permission/preferences only. Runtime activation is never serialized. */
public final class FishingSettings {
 public boolean enabled=false,recast=true,fireveil=false,debug=false;
 public int toolSlot=2,reelMinMs=56,reelMaxMs=129,recastMinMs=390,recastMaxMs=980;
 public int selectBaseMs=100,selectSpreadMs=80,useBaseMs=80,useSpreadMs=70,returnBaseMs=90,returnSpreadMs=80;
 public int biteTimeoutSec=120;
 public void sanitize(){
  toolSlot=Math.clamp(toolSlot,1,9);biteTimeoutSec=Math.clamp(biteTimeoutSec,10,600);
  reelMinMs=Math.clamp(reelMinMs,50,2000);reelMaxMs=Math.clamp(reelMaxMs,reelMinMs,3000);
  recastMinMs=Math.clamp(recastMinMs,200,5000);recastMaxMs=Math.clamp(recastMaxMs,recastMinMs,8000);
  selectBaseMs=Math.clamp(selectBaseMs,50,2000);selectSpreadMs=Math.clamp(selectSpreadMs,0,1000);
  useBaseMs=Math.clamp(useBaseMs,50,2000);useSpreadMs=Math.clamp(useSpreadMs,0,1000);
  returnBaseMs=Math.clamp(returnBaseMs,50,2000);returnSpreadMs=Math.clamp(returnSpreadMs,0,1000);
 }
}
