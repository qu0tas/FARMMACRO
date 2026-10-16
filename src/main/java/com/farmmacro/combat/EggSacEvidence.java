package com.farmmacro.combat;
import com.farmmacro.panic.ServerText;
import java.util.regex.Pattern;
/** Observed label/geometry evidence only; never infers damage, owner or boss HP phase. */
public final class EggSacEvidence {
 private static final Pattern TIMER=Pattern.compile("\\d{1,3}s \\d{1,3}/\\d{1,3}");
 public static boolean timer(String name){return name!=null&&TIMER.matcher(ServerText.clean(name).strip()).matches();}
 public static boolean prompt(String name){return name!=null&&ServerText.clean(name).strip().equalsIgnoreCase("SHOOT ME!");}
 public static boolean bodyName(String name){return name==null||name.isBlank()||ServerText.clean(name).strip().equalsIgnoreCase("Armor Stand");}
 public static boolean spider(String name){return "Tarantula Broodfather".equalsIgnoreCase(name)||"Primordial Broodfather".equalsIgnoreCase(name)||"Conjoined Brood".equalsIgnoreCase(name);}
 public static boolean linked(double labelDistance,double bodyDistance){return Double.isFinite(labelDistance)&&Double.isFinite(bodyDistance)&&labelDistance>=0&&labelDistance<2.5&&bodyDistance>=0&&bodyDistance<1.2;}
 public static boolean bossArea(double dx,double dy,double dz){return Double.isFinite(dx)&&Double.isFinite(dy)&&Double.isFinite(dz)&&dx*dx+dz*dz<=16*16&&dy>=0&&dy<=24;}
 public static double headHeight(boolean small){return small?.85:1.7;}
 private EggSacEvidence(){}
}
