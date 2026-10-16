package com.farmmacro.motion;
/** Fail closed: a manual start never authorizes walking toward the start for the user. */
public final class ManualStartPolicy {
 public static boolean allowed(double distance,double radius){return Double.isFinite(distance)&&Double.isFinite(radius)&&distance>=0&&radius>0&&distance<=radius;}
 private ManualStartPolicy(){}
}
