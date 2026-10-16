package com.farmmacro.panic;
import com.farmmacro.combat.*;
import com.farmmacro.config.*;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.pest.PestHunter;
import net.minecraft.client.Minecraft;
/** Routes focused combat guards without mutating or borrowing farming settings. */
public final class CombatPanic {
    public static CombatPanicSettings settings(){return ModConfig.INSTANCE.combatPanic;}
    public static boolean context(){return !com.farmmacro.macro.Scheduler.isBreak()&&!MacroManager.INSTANCE.isActive()&&!PestHunter.INSTANCE.active()&&!com.farmmacro.market.StashSales.INSTANCE.active()&&!com.farmmacro.visitor.Visitors.INSTANCE.active()
        &&(SpecialZealotHunt.INSTANCE.active()||SpecialZealotHunt.INSTANCE.pending()||CombatSway.INSTANCE.active()||ModConfig.INSTANCE.aimAssistEnabled&&AimAssist.INSTANCE.keyActive());}
    public static boolean farmActive(){return MacroManager.INSTANCE.isActive()||PestHunter.INSTANCE.active()||com.farmmacro.visitor.Visitors.INSTANCE.active()||com.farmmacro.market.StashSales.INSTANCE.active()||com.farmmacro.macro.Scheduler.isBreak();}
    public static boolean enabled(){return context()?settings().enabled:ModConfig.INSTANCE.panicEnabled&&farmActive();}
    public static boolean teleport(){return context()?settings().teleport:ModConfig.INSTANCE.detectAutomationTeleport;}
    public static boolean weapon(){if(PestHunter.INSTANCE.manualControl())return false;return context()?settings().weapon:ModConfig.INSTANCE.detectAutomationWeapon;}
    public static boolean chat(){return context()?settings().nearbyChat:ModConfig.INSTANCE.detectNearbyChat;}
    public static boolean isCombatId(String id){return "combatTeleport".equals(id)||"combatWeapon".equals(id)||"combatChat".equals(id)||"combatRotation".equals(id)||"combatOverlap".equals(id);}
    public static boolean fire(Minecraft mc,String farmId,String reason){
        if(!context())return PanicDetector.INSTANCE.fire(mc,farmId,reason);
        if(!settings().enabled)return false;
        String id=switch(farmId){case "safetyTeleport"->"combatTeleport";case "safetyWeapon"->"combatWeapon";case "nearbyChat"->"combatChat";case "safetyRotation"->"combatRotation";default->throw new IllegalArgumentException(farmId);};
        EventLog.log("COMBAT-PANIC",reason);PanicDetector.INSTANCE.triggerPanic(mc,id,reason);return true;
    }
    public static void playSound(){var p=settings();PanicSound.play(p.soundId,false,(float)p.volume,1);}
    private CombatPanic(){}
}
