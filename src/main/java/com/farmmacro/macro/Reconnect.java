package com.farmmacro.macro;
import net.minecraft.client.Minecraft;
/** Reconnect removed; no ConnectScreen, server commands or automatic resume. */
public final class Reconnect {
 public enum State{OFF,WAIT,CONNECTING,JOINED}
 public static State state(){return State.OFF;} public static int tries(){return 0;}
 public static int secondsLeft(){return 0;} public static String status(){return "Автореконнект удалён";}
 public static boolean authorized(){return false;}
 public static void userStart(){} public static void panic(){} public static void connectNow(){}
 public static void cancelByUser(){} public static void manualInput(){} public static void onJoin(Minecraft mc){}
 public static void markIntentional(){} public static void cancel(){}
 public static boolean wouldReconnect(Minecraft mc,boolean wasPlaying){return false;}
 public static void onDisconnect(Minecraft mc,boolean wasPlaying){} public static void tick(Minecraft mc){}
 /** Legacy pure formatting policy; cannot issue a command. */
 public static int delaySec(int base,int attempt,boolean backoff){if(!backoff||attempt<=1)return base;return (int)Math.min(600,Math.max(base,(long)base<<Math.min(20,attempt-1)));}
 public static boolean commandAllowed(String command){return false;}
 private Reconnect(){}
}
