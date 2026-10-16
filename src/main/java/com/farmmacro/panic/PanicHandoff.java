package com.farmmacro.panic;

import com.farmmacro.FarmMacroMod;
import com.farmmacro.combat.AimAssist;
import com.farmmacro.combat.CombatSway;
import com.farmmacro.gui.FarmMacroScreen;
import com.farmmacro.macro.MacroManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/** Acknowledgement, not a delayed reaction or an input lock. Never resumes automation. */
public final class PanicHandoff {
    private static boolean pending;
    private static String reason="",detector="";
    private static long at;
    public static boolean pending(){return pending;}
    public static String reason(){return reason;}
    public static String detector(){return detector;}
    public static long ageSeconds(){return Math.max(0,(System.nanoTime()-at)/1_000_000_000L);}
    public static String keyName(){return FarmMacroMod.keyTakeControl==null?"B":MacroManager.keyName(FarmMacroMod.keyTakeControl);}
    public static void show(String id,String text){
        detector=switch(id==null?"":id){
            case "combatTeleport","safetyTeleport"->"Перемещение / мир";
            case "combatRotation"->"Серверная камера";
            case "combatOverlap"->"Игрок пересёк персонажа";
            case "combatWeapon","safetyWeapon"->"Предмет в руке";
            case "combatChat","nearbyChat"->"Обращение игрока";
            default->id==null?"Паника":Detectors.name(id);
        };
        reason=ServerText.clean(text==null?"Автоматика остановлена":text);
        if(reason.length()>600)reason=reason.substring(0,600);
        at=System.nanoTime();pending=true;
    }
    public static void clear(){pending=false;reason=detector="";at=0;}
    public static void takeControl(Minecraft mc){
        com.farmmacro.camera.Freecam.off(mc);
        // Clear last: if a stop fails, acknowledgement must not silently authorize a new run.
        AimAssist.INSTANCE.handoff("управление передано игроку");
        CombatSway.INSTANCE.stop(mc,"управление передано игроку");
        com.farmmacro.macro.MouseHold.INSTANCE.stopAll(mc);
        com.farmmacro.camera.SmoothTurn.stop();
        com.farmmacro.macro.Scheduler.cancel();
        com.farmmacro.macro.Reconnect.cancel();
        com.farmmacro.camera.Mousemat.cancel();
        HumanReaction.stop("управление передано игроку");
        for(var key:FarmMacroMod.bindings())while(key.consumeClick()){}
        PanicDetector.INSTANCE.acknowledgeHandoff();
        clear();
    }
    public static boolean allowStart(Minecraft mc){
        if(!pending)return true;
        if(mc.player!=null)mc.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
            "§cKaelix: сначала возьми управление — "+keyName()+"; затем запусти нужный модуль"));
        return false;
    }
    /** Intercept only this acknowledgement press so B cannot also open the route editor. */
    public static boolean onKey(Minecraft mc,int action,KeyEvent event){
        if(!pending||mc.screen!=null||action!=1||FarmMacroMod.keyTakeControl==null
            ||!FarmMacroMod.keyTakeControl.matches(event))return false;
        takeControl(mc);return true;
    }
    public static boolean onButton(Minecraft mc,int action,MouseButtonInfo info){
        if(!pending||mc.screen!=null||action!=1||FarmMacroMod.keyTakeControl==null
            ||!FarmMacroMod.keyTakeControl.matchesMouse(new MouseButtonEvent(0,0,info)))return false;
        takeControl(mc);return true;
    }
    /** Block new module starts while the notice is pending; real movement/mouse are never blocked. */
    public static boolean blockStarts(Minecraft mc){
        if(!pending)return false;
        if(FarmMacroMod.keyResetPos.consumeClick()){
            MacroManager.INSTANCE.resetAll(mc);PanicDetector.INSTANCE.acknowledgeHandoff();clear();return true;
        }
        while(FarmMacroMod.keyOpenGui.consumeClick())mc.setScreen(new FarmMacroScreen(mc.screen));
        for(var key:FarmMacroMod.bindings())while(key.consumeClick()){}
        com.farmmacro.macro.MouseHold.INSTANCE.stopAll(mc);
        com.farmmacro.camera.SmoothTurn.stop();
        CombatSway.INSTANCE.stopWhenInactive(mc,"ожидание передачи управления");
        return true;
    }
    private PanicHandoff(){}
}