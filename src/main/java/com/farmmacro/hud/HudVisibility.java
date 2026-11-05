package com.farmmacro.hud;

import com.farmmacro.combat.AimAssist;
import com.farmmacro.combat.CombatSway;
import com.farmmacro.combat.SpecialZealotHunt;
import com.farmmacro.config.ModConfig;
import com.farmmacro.fishing.AutoFish;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.panic.PanicHandoff;
import com.farmmacro.route.NavigationInput;
import com.farmmacro.stats.Telemetry;
import net.minecraft.client.Minecraft;

/** Presentation only. None of these observations grant permission or change any owner. */
final class HudVisibility {
    private static boolean farm, combat, service, navigation, active;

    static void capture(Minecraft mc) {
        var m = MacroManager.INSTANCE;
        farm = m.isActive() || m.isRecording();
        combat = AimAssist.INSTANCE.keyActive()
                || CombatSway.INSTANCE.holdingCrouch()
                || SpecialZealotHunt.INSTANCE.active()
                || SpecialZealotHunt.INSTANCE.pending();
        service = AutoFish.INSTANCE.active();
        navigation = NavigationInput.busy();
        active = farm || combat || service || navigation;
    }

    static boolean show(String id) {
        var slot = ModConfig.INSTANCE.hudSlots.get(id);
        if (slot != null && slot.always || !ModConfig.INSTANCE.hudContextOnly) return true;
        // Actual toggled modes / editor / warnings remain governed by their own drawers.
        return switch (id) {
            case "activity" -> active || PanicHandoff.pending();
            case "profit" -> Telemetry.present() && (farm || service);
            case "skill", "pet" -> Telemetry.present() && (farm || combat || Telemetry.recentSkill());
            case "inventory" -> active;
            case "status" -> farm || service && !AutoFish.INSTANCE.active();
            case "nav" -> navigation || farm;
            default -> true;
        };
    }

    private HudVisibility() {}
}
