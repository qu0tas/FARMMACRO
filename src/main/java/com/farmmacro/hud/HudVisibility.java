package com.farmmacro.hud;

import com.farmmacro.config.ModConfig;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.macro.Scheduler;
import com.farmmacro.panic.PanicHandoff;
import net.minecraft.client.Minecraft;

/** Presentation only. None of these observations grant permission or change any owner. */
final class HudVisibility {
    private static boolean farm,combat,pests,service,navigation,active;
    static void capture(Minecraft mc) {
        var m=MacroManager.INSTANCE;
        farm=m.isActive()||m.isRecording()||Scheduler.running();
        combat=com.farmmacro.combat.AimAssist.INSTANCE.keyActive()||com.farmmacro.combat.CombatSway.INSTANCE.holdingCrouch()
            ||com.farmmacro.combat.SpecialZealotHunt.INSTANCE.active()||com.farmmacro.combat.SpecialZealotHunt.INSTANCE.pending();
        pests=com.farmmacro.pest.PestHunter.INSTANCE.active();
        service=com.farmmacro.market.StashSales.INSTANCE.active()||com.farmmacro.visitor.Visitors.INSTANCE.active()
            ||com.farmmacro.loadout.GlobalLoadouts.INSTANCE.active();
        navigation=com.farmmacro.route.NavigationInput.busy();
        active=farm||combat||pests||service||navigation||com.farmmacro.macro.Reconnect.state()!=com.farmmacro.macro.Reconnect.State.OFF;
    }
    static boolean show(String id) {
        var slot=ModConfig.INSTANCE.hudSlots.get(id);
        if(slot!=null&&slot.always||!ModConfig.INSTANCE.hudContextOnly)return true;
        // Actual toggled modes / editor / warnings remain governed by their own drawers.
        return switch(id) {
            case "activity" -> active||PanicHandoff.pending();
            case "profit" -> com.farmmacro.stats.Telemetry.present()&&(farm||pests||service);
            case "skill","pet" -> com.farmmacro.stats.Telemetry.present()&&(farm||combat||com.farmmacro.stats.Telemetry.recentSkill());
            case "inventory" -> active;
            case "status" -> farm||service;
            case "nav" -> navigation||farm;
            default -> true;
        };
    }
    private HudVisibility() {}
}
