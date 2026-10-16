package com.farmmacro.market;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
/** Removed module: no commands, transactions, navigation or input ownership. */
public final class StashSales {
 public static final StashSales INSTANCE=new StashSales();
 private StashSales(){}
 public static SaleSettings cfg(){return com.farmmacro.config.ModConfig.INSTANCE.stashSales;}
 public String technicalState(){return "REMOVED";}
 public boolean active(){return false;}
 public boolean awaitingConfirmation(){return false;}
 public String status(){return "Базар удалён";}
 public void request(Minecraft mc){}
 public void serverSlot(int slot){}
 public void userInput(){}
 public void capture(Minecraft mc,boolean hub){}
 public void addHeld(Minecraft mc){}
 public void tick(Minecraft mc){}
 public String readiness(Minecraft mc,boolean preview){return "Базар удалён";}
 public void reportReadiness(Minecraft mc){}
 public void signal(String text){}
 public boolean expectScreen(Screen s){return false;}
 public void screenOpening(Screen s){}
 public void cancel(Minecraft mc,String why){}
}
