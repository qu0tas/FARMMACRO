package com.farmmacro.loadout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
/** Removed module: inert seam for existing input/guard callbacks. No equipment changes. */
public final class GlobalLoadouts {
 public static final GlobalLoadouts INSTANCE=new GlobalLoadouts();
 private GlobalLoadouts(){}
 public String technicalState(){return "REMOVED";}
 public boolean active(){return false;}
 public String status(){return "Лоадауты удалены";}
 public void request(Minecraft mc){}
 public boolean captureHand(Minecraft mc){return false;}
 public void captureScope(Minecraft mc){}
 public void tick(Minecraft mc){}
 public boolean expectScreen(Screen s){return false;}
 public boolean owns(Screen s){return false;}
 public void screenOpening(Screen s){}
 public void cancel(Minecraft mc,String why){}
 public void userInput(){}
}
