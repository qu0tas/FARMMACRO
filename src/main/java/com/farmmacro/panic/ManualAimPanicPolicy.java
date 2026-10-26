package com.farmmacro.panic;
/** A manual camera helper is not automation. Other running modules keep their own alarms. */
public final class ManualAimPanicPolicy {
 public static boolean cameraOnly(boolean manualMode,boolean otherAutomation){return manualMode&&!otherAutomation;}
 private ManualAimPanicPolicy(){}
}
