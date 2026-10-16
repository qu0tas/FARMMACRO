package com.farmmacro.macro;
import net.minecraft.client.Minecraft;
/** Sessions/scheduled resumes removed. Compatibility API never runs automation. */
public final class Scheduler {
 public enum Phase{OFF,FARM,BREAK}
 public static boolean isBreak(){return false;}
 public static Phase phase(){return Phase.OFF;}
 public static long farmLeftTicks(){return 0;} public static long breakLeftTicks(){return 0;}
 public static long leaveLeftMs(){return -1;} public static long elapsedMs(){return 0;}
 public static boolean servicePaused(){return false;} public static long activeLeftMs(){return -1;}
 public static boolean running(){return false;} public static boolean automatic(){return false;}
 public static String status(){return "Сессии удалены; запуск только вручную";}
 /** Legacy pure formula; does not schedule any work. */
 public static double jitterMinutes(double base,int pct,double u01){return Math.max(1,base*(1+(u01*2-1)*pct/100.0));}
 public static void automatic(Runnable r){} // Retired automatic callback must not execute.
 public static boolean onStart(Minecraft mc){return !com.farmmacro.panic.PanicHandoff.pending();}
 public static void pauseService(){} public static void disconnected(boolean reconnect){}
 public static void cancel(){} public static void cancel(String code){}
 public static void manualInput(){} public static void panic(){}
 public static void mouseMove(double x,double y){} public static void maintenance(Minecraft mc){}
 public static void tick(Minecraft mc){}
 private Scheduler(){}
}
