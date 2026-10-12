package com.farmmacro.visual;

import com.farmmacro.FarmMacroMod;
import com.farmmacro.config.ModConfig;
import com.farmmacro.macro.MacroManager;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ParticleStatus;

/**
 * v1.13.1 «Режим производительности» (по умолчанию F9): на время фарма — лимит FPS, без облаков и теней сущностей,
 * минимум частиц, дальность сущностей 50 %. Прежние значения сохраняются в конфиг (perfSaved*) и возвращаются
 * при выключении — и при следующем запуске игры, если она закрылась в этом режиме. С Low render (F7) сочетается.
 */
public final class PerformanceMode {
    private static boolean active, auto;

    private PerformanceMode() {}

    public static boolean active() { return active; }

    public static void toggle(Minecraft mc) {
        if (active) off(mc, null);
        else on(mc, null);
    }

    public static void on(Minecraft mc, String why) {
        if (active || mc.options == null) return;
        ModConfig c = ModConfig.INSTANCE;
        Options o = mc.options;
        if (c.perfSavedFps < 0) {                       // не затирать сохранённое прошлым (незакрытым) режимом
            c.perfSavedFps = o.framerateLimit().get();
            c.perfSavedClouds = o.cloudStatus().get().name();
            c.perfSavedParticles = o.particles().get().name();
            c.perfSavedShadows = o.entityShadows().get() ? 1 : 0;
            c.perfSavedEntityDist = o.entityDistanceScaling().get();
            ModConfig.save();
        }
        o.framerateLimit().set(c.perfFps);
        o.cloudStatus().set(CloudStatus.OFF);
        o.particles().set(ParticleStatus.MINIMAL);
        o.entityShadows().set(false);
        o.entityDistanceScaling().set(0.5);
        active = true;
        auto = why != null;
        msg(mc, "§a⚡ Режим производительности" + (why != null ? ": " + why : "") + " §7(FPS ≤ " + c.perfFps + ", "
                + MacroManager.keyName(FarmMacroMod.keyPerf) + " — выкл)");
    }

    public static void off(Minecraft mc, String why) {
        boolean was = active;
        active = false; auto = false;
        restore(mc);
        if (was) msg(mc, why == null ? "§7⚡ Режим производительности выключен" : "§e⚡ Режим производительности выключен: " + why);
    }

    /** Вернуть сохранённые настройки графики (и после незакрытого режима при запуске игры). */
    public static void restore(Minecraft mc) {
        ModConfig c = ModConfig.INSTANCE;
        if (c.perfSavedFps < 0 || mc.options == null) return;
        Options o = mc.options;
        try {
            o.framerateLimit().set(c.perfSavedFps);
            if (c.perfSavedClouds != null) o.cloudStatus().set(CloudStatus.valueOf(c.perfSavedClouds));
            if (c.perfSavedParticles != null) o.particles().set(ParticleStatus.valueOf(c.perfSavedParticles));
            if (c.perfSavedShadows >= 0) o.entityShadows().set(c.perfSavedShadows == 1);
            if (c.perfSavedEntityDist > 0) o.entityDistanceScaling().set(c.perfSavedEntityDist);
        } catch (Exception e) {
            FarmMacroMod.LOGGER.warn("Режим производительности: не удалось вернуть настройки", e);
        }
        c.perfSavedFps = -1; c.perfSavedClouds = null; c.perfSavedParticles = null; c.perfSavedShadows = -1; c.perfSavedEntityDist = -1;
        ModConfig.save();
    }

    /** Каждый тик: «включать при старте» — вкл с макросом, выкл после (если включил сам мод). */
    public static void tick(Minecraft mc) {
        if (!active && ModConfig.INSTANCE.perfSavedFps >= 0) { restore(mc); return; }   // осталось с прошлого запуска
        if (mc.player == null) return;
        boolean playing = MacroManager.INSTANCE.isActive()
                || com.farmmacro.macro.Scheduler.phase() == com.farmmacro.macro.Scheduler.Phase.BREAK;
        if (!active && playing && ModConfig.INSTANCE.perfOnStart && MacroManager.INSTANCE.isPlaying()) on(mc, "старт макроса");
        else if (active && auto && !playing) off(mc, "макрос остановлен");
    }

    private static void msg(Minecraft mc, String text) {
        if (mc != null && mc.player != null) mc.player.sendOverlayMessage(Component.literal("§8[§cFM§8] §r" + text));
    }
}
