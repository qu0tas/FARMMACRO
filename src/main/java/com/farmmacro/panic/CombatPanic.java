package com.farmmacro.panic;

import com.farmmacro.combat.*;
import com.farmmacro.config.*;
import com.farmmacro.fishing.AutoFish;
import com.farmmacro.macro.MacroManager;
import net.minecraft.client.Minecraft;

/** Routes focused combat guards without mutating or borrowing farming settings. */
public final class CombatPanic {
    public static boolean manualCameraOnly() {
        return ManualAimPanicPolicy.cameraOnly(
                AimAssist.manualMode(),
                farmActive()
                        || SpecialZealotHunt.INSTANCE.active()
                        || SpecialZealotHunt.INSTANCE.pending()
                        || CombatSway.INSTANCE.active());
    }

    public static CombatPanicSettings settings() {
        return ModConfig.INSTANCE.combatPanic;
    }

    public static boolean context() {
        return !AutoFish.INSTANCE.active()
                && !MacroManager.INSTANCE.isActive()
                && (SpecialZealotHunt.INSTANCE.active()
                        || SpecialZealotHunt.INSTANCE.pending()
                        || CombatSway.INSTANCE.active()
                        || !AimAssist.manualMode()
                                && ModConfig.INSTANCE.aimAssistEnabled
                                && AimAssist.INSTANCE.keyActive());
    }

    public static boolean farmActive() {
        return AutoFish.INSTANCE.active() || MacroManager.INSTANCE.isActive();
    }

    public static boolean enabled() {
        if (manualCameraOnly()) return false;
        return context() ? settings().enabled : ModConfig.INSTANCE.panicEnabled && farmActive();
    }

    public static boolean teleport() {
        return context() ? settings().teleport : ModConfig.INSTANCE.detectAutomationTeleport;
    }

    public static boolean weapon() {
        return context() ? settings().weapon : ModConfig.INSTANCE.detectAutomationWeapon;
    }

    public static boolean chat() {
        return context() ? settings().nearbyChat : ModConfig.INSTANCE.detectNearbyChat;
    }

    public static boolean isCombatId(String id) {
        return "combatGaze".equals(id)
                || "combatTeleport".equals(id)
                || "combatWeapon".equals(id)
                || "combatChat".equals(id)
                || "combatRotation".equals(id)
                || "combatOverlap".equals(id);
    }

    public static boolean fire(Minecraft mc, String farmId, String reason) {
        if (manualCameraOnly()) return false;
        if (!context()) return PanicDetector.INSTANCE.fire(mc, farmId, reason);
        if (!settings().enabled) return false;
        String id = switch (farmId) {
            case "safetyTeleport" -> "combatTeleport";
            case "safetyWeapon" -> "combatWeapon";
            case "nearbyChat" -> "combatChat";
            case "safetyRotation" -> "combatRotation";
            default -> throw new IllegalArgumentException(farmId);
        };
        EventLog.log("COMBAT-PANIC", reason);
        PanicDetector.INSTANCE.triggerPanic(mc, id, reason);
        return true;
    }

    public static void playSound() {
        var p = settings();
        PanicSound.play(p.soundId, false, (float) p.volume, 1);
    }

    private CombatPanic() {}
}
