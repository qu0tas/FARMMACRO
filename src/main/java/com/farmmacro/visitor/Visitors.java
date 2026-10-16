package com.farmmacro.visitor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
/** Retired automation seam for old callbacks/config compatibility. No NPC/trade/input logic. */
public final class Visitors {
 public static final Visitors INSTANCE=new Visitors();
 private Visitors(){}
 public static VisitorSettings cfg(){return com.farmmacro.config.ModConfig.INSTANCE.visitors;}
 public boolean active(){return false;}
 public String technicalState(){return "REMOVED";}
 public String status(){return "Гости удалены";}
 public void request(Minecraft mc){}
 public void tick(Minecraft mc){}
 public void signal(String text){}
 public boolean expectScreen(Screen screen){return false;}
 public boolean owns(Screen screen){return false;}
 public void screenOpening(Screen screen){}
 public void userInput(){}
 public void cancel(Minecraft mc,String why){}
}
