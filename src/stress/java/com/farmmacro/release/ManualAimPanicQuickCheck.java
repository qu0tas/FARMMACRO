package com.farmmacro.release;
import com.farmmacro.panic.ManualAimPanicPolicy;
/** Four truth-table cases; runtime call-site invariants checked during packaging. No game. */
public final class ManualAimPanicQuickCheck {
 public static void main(){
  if(!ManualAimPanicPolicy.cameraOnly(true,false)||ManualAimPanicPolicy.cameraOnly(false,false)
    ||ManualAimPanicPolicy.cameraOnly(true,true)||ManualAimPanicPolicy.cameraOnly(false,true))
   throw new IllegalStateException("manual camera must not mute another automatic module");
  System.out.println("ManualAimPanicQuickCheck: PASS (manual-only suppression; automatic mode and simultaneous automation retain alarms; no game)");
 }
 private ManualAimPanicQuickCheck(){}
}
