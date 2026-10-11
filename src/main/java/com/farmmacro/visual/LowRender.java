package com.farmmacro.visual;

import com.farmmacro.FarmMacroMod;
import com.farmmacro.config.ModConfig;
import com.farmmacro.macro.MacroManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * v1.12 «Low render»: рисуются только секции мира (кубы 16×16×16), сущности и блок-сущности в радиусе
 * lowRenderRadius блоков от игрока ({@code LowRenderFrustumMixin} — отсечение в Frustum). По желанию дальность
 * прорисовки на это время — 2 чанка (меньше чанков строится), прежняя возвращается при выключении и при следующем
 * запуске игры, если игра закрылась с включённым режимом (lowRenderSavedChunks).
 * На сервер не влияет: это только то, что рисует клиент.
 */
public final class LowRender {
    private static volatile boolean active;
    /** Позиция игрока для отсечения (обновляется каждый тик и кадр, читается из потока рендера). */
    private static volatile double px, py, pz;

    private LowRender() {}

    public static boolean active() { return active; }

    public static void toggle(Minecraft mc) {
        if (active) off(mc); else on(mc);
    }

    public static void on(Minecraft mc) {
        if (active || mc.player == null) return;
        ModConfig c = ModConfig.INSTANCE;
        follow(mc.player);
        active = true;
        if (c.lowRenderChunks) {
            int cur = mc.options.renderDistance().get();
            if (cur > 2) {
                c.lowRenderSavedChunks = cur;
                ModConfig.save();
                mc.options.renderDistance().set(2);
            }
        }
        mc.levelRenderer.needsUpdate();
        msg(mc, "§b▣ Low render: " + c.lowRenderRadius + " бл §7(" + MacroManager.keyName(FarmMacroMod.keyLowRender) + " — выкл)");
    }

    public static void off(Minecraft mc) {
        if (!active) return;
        active = false;
        restoreChunks(mc);
        mc.levelRenderer.needsUpdate();
        msg(mc, "§7□ Low render выключен");
    }

    /** Вернуть дальность прорисовки, если её уменьшали (и после перезапуска игры). */
    public static void restoreChunks(Minecraft mc) {
        ModConfig c = ModConfig.INSTANCE;
        if (c.lowRenderSavedChunks > 2 && mc.options != null) {
            mc.options.renderDistance().set(c.lowRenderSavedChunks);
            c.lowRenderSavedChunks = 0;
            ModConfig.save();
            mc.options.save();
        }
    }

    private static boolean startChecked;

    public static void tick(Minecraft mc) {
        if (!startChecked && mc.options != null) {        // игра закрылась в режиме Low render — вернуть дальность
            startChecked = true;
            if (!active && ModConfig.INSTANCE.lowRenderSavedChunks > 2) restoreChunks(mc);
        }
        if (!active) return;
        if (mc.player == null || mc.level == null) { active = false; restoreChunks(mc); return; }
        follow(mc.player);
    }

    private static void follow(LocalPlayer p) { px = p.getX(); py = p.getEyeY(); pz = p.getZ(); }

    /** Из миксина Frustum: OUTSIDE/PARTIAL/INSIDE относительно радиуса (вне режима — INSIDE). */
    public static int classify(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (!active) return LowRenderMath.INSIDE;
        return LowRenderMath.classify(minX, minY, minZ, maxX, maxY, maxZ, px, py, pz, ModConfig.INSTANCE.lowRenderRadius);
    }

    private static void msg(Minecraft mc, String text) {
        if (mc != null && mc.player != null) mc.player.sendOverlayMessage(Component.literal("§8[§cFM§8] §r" + text));
    }
}
