package com.farmmacro.pest;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import com.farmmacro.panic.PanicDetector;
import java.util.List;
import java.util.Locale;

/** Retired module compatibility seam. No controller, input, search or auto-start remains.
 * Shared Garden recognition is still used by unrelated navigation/market guards. */
public final class PestHunter {
 public static final PestHunter INSTANCE=new PestHunter();
 private PestHunter(){}
 public boolean active(){return false;}
 public boolean manualControl(){return false;}
 public boolean controlKey(net.minecraft.client.input.KeyEvent event){return false;}
 public String technicalState(){return "REMOVED";}
 public String status(){return "Песты удалены";}
 public List<Entity> visiblePests(){return List.of();}
 public List<Vec3> navigationPath(){return List.of();}
 public Vec3 targetPoint(){return null;}
 public int travelVacuum(){return -1;}
 public void signal(Minecraft mc,String text,boolean overlay){}
 public void requestTraps(Minecraft mc){}
 public void pauseManual(Minecraft mc){}
 public void finishEarly(Minecraft mc){}
 public void monitor(Minecraft mc){}
 public void toggle(Minecraft mc){}
 public void abort(Minecraft mc,String why,boolean tell){}
 public void onMouseMove(double x,double y){}
 public void userInput(){}
 public void tick(Minecraft mc){}
    public static boolean garden(Minecraft mc) {
        if(mc.player==null||mc.level==null||mc.isSingleplayer()||mc.getCurrentServer()==null)return false;
        String host=mc.getCurrentServer().ip.toLowerCase(Locale.ROOT).split(":")[0];
        if(!host.equals("hypixel.net")&&!host.endsWith(".hypixel.net"))return false;
        for(String line:PanicDetector.sidebar(mc)) {
            String s=line.toLowerCase(Locale.ROOT);
            if(s.contains("the garden")||s.equals("garden")||s.startsWith("plot - "))return true;
        }
        return false;
    }
}
