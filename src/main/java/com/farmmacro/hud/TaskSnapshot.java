package com.farmmacro.hud;

import com.farmmacro.FarmMacroMod;
import com.farmmacro.combat.*;
import com.farmmacro.config.*;
import com.farmmacro.fishing.AutoFish;
import com.farmmacro.macro.*;
import com.farmmacro.panic.*;
import com.farmmacro.route.NavigationInput;
import com.farmmacro.route.RouteWalker;
import java.util.Objects;
import net.minecraft.client.Minecraft;

/** Read-only projection of existing owners. It cannot start, stop or acknowledge any module. */
public record TaskSnapshot(
        String group, String task, String state, String detail, String hint, double progress, boolean crouch) {
    public static String combatProfile() {
        var c = ModConfig.INSTANCE;
        if (c.aimManualOnly)
            return "Только наведение / " + com.farmmacro.combat.BossTargets.label(c.aimManualTarget, c.aimManualName);
        if (c.combatProfiles != null)
            for (var p : c.combatProfiles)
                if (p != null && Objects.equals(c.combatProfileId, p.id)) return p.name == null ? "Профиль" : p.name;
        return "Профиль неизвестен";
    }

    public static TaskSnapshot read(Minecraft mc) {
        var m = MacroManager.INSTANCE;
        String hint = "Запуск вручную: " + MacroManager.keyName(FarmMacroMod.keyPlay);
        String group = "General", task = "Kaelix", state = "РУЧНОЕ УПРАВЛЕНИЕ", detail = "Автоматика не запущена";
        double progress = Double.NaN;
        if (PanicHandoff.pending()) {
            task = PanicHandoff.detector();
            state = "ОСТАНОВЛЕНО · ПАНИКА";
            detail = PanicHandoff.reason();
            hint = PanicHandoff.keyName() + " — взять управление; затем новый ручной запуск";
        } else if (AutoFish.INSTANCE.active() || AutoFish.INSTANCE.armed()) {
            group = "Fishing";
            task = "AutoFish · Alpha";
            state = "РАБОТА";
            detail = AutoFish.INSTANCE.status();
            hint = "Первый заброс вручную · End / бинд рыбалки — стоп";
        } else if (SpecialZealotHunt.INSTANCE.active() || SpecialZealotHunt.INSTANCE.pending()) {
            group = "Combat";
            task = "Special Zealot";
            state = "РАБОТА";
            detail = AimAssist.INSTANCE.specialStatus();
            hint = "End — стоп; при панике новый ручной запуск";
        } else if (m.isActive() || m.isRecording()) {
            group = "Farms";
            task = m.isRoutePlaying() ? RouteWalker.INSTANCE.activeName() : m.getLoadedName();
            if (task == null || task.isBlank()) task = "Несохранённый источник";
            state = m.isRecording() ? "ЗАПИСЬ" : m.getState() == MacroManager.State.COUNTDOWN ? "ПОДГОТОВКА" : "РАБОТА";
            detail = m.isReturning()
                    ? "Подход к прежнему источнику"
                    : m.isRecording()
                            ? "Записываются реальные действия игрока"
                            : "Элемент " + (m.getProgress() + 1) + " / " + m.getProgressTotal();
            progress = m.getProgressTotal() > 0 ? (double) m.getProgress() / m.getProgressTotal() : Double.NaN;
            hint = "End — стоп и сброс";
        } else if (AimAssist.INSTANCE.keyActive()) {
            group = "Combat";
            task = "Combat / " + combatProfile();
            state = "РАБОТА";
            detail = AimAssist.INSTANCE.status();
            hint = MacroManager.keyName(FarmMacroMod.keyAim) + " — управление Aim";
        } else if (NavigationInput.busy()) {
            group = "Navigation";
            task = "Навигация";
            state = "РАБОТА";
            detail = "Управление у существующего навигатора";
            hint = "Ручной ввод — перехват";
        }
        return new TaskSnapshot(group, task, state, detail, hint, progress, CombatSway.INSTANCE.holdingCrouch());
    }
}
