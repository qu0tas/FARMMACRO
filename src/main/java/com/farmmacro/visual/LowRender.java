package com.farmmacro.visual;

import com.farmmacro.FarmMacroMod;
import com.farmmacro.config.ModConfig;
import com.farmmacro.macro.MacroManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * «Low render» (v1.12.2 — поблочно): рисуются только блоки в радиусе lowRenderRadius (по умолчанию 3) вокруг блока,
 * где стоит игрок, — остальной мир пустой (небо). {@code LowRenderRegionMixin}: при сборке секций блоки за радиусом
 * — воздух (поэтому у краевых блоков видны боковые грани), {@code LowRenderFrustumMixin}: секции, сущности и
 * блок-сущности целиком за радиусом не рисуются. Игрок перешёл в другой блок — пересобираются секции вокруг
 * (старый и новый круг). Дальность прорисовки не меняется. На сервер не влияет: это только то, что рисует клиент.
 */
public final class LowRender {
    /** Центр (блок под ногами игрока) и радиус — одним объектом: читается из потоков сборки секций. */
    public record Center(int x, int y, int z, int r) {}

    private static volatile boolean active;
    private static volatile Center center;
    /** v1.14.1: сколько раз сборка секций спросила блок в режиме (0 через 4 с — наш код не вызывается). */
    private static final java.util.concurrent.atomic.LongAdder asked = new java.util.concurrent.atomic.LongAdder();
    private static long onMs;
    private static boolean warned;

    private LowRender() {}

    public static boolean active() { return active; }

    public static void toggle(Minecraft mc) {
        if (active) off(mc); else on(mc);
    }

    public static void on(Minecraft mc) {
        if (active || mc.player == null) return;
        center = centerOf(mc.player);
        asked.reset();
        onMs = System.currentTimeMillis();
        warned = false;
        active = true;
        mc.levelRenderer.allChanged();                      // пересобрать всё: за радиусом — пусто
        msg(mc, "§b▣ Low render: " + ModConfig.INSTANCE.lowRenderRadius + " бл §7("
                + MacroManager.keyName(FarmMacroMod.keyLowRender) + " — выкл)");
    }

    public static void off(Minecraft mc) {
        if (!active) return;
        active = false;
        center = null;
        mc.levelRenderer.allChanged();                      // вернуть мир целиком
        msg(mc, "§7□ Low render выключен");
    }

    /** До 1.12.2 режим снижал дальность прорисовки — вернуть её, если игра закрылась в режиме. */
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
        if (!startChecked && mc.options != null) {
            startChecked = true;
            if (ModConfig.INSTANCE.lowRenderSavedChunks > 2) restoreChunks(mc);
        }
        if (!active) return;
        if (mc.player == null || mc.level == null) { active = false; center = null; return; }
        if (!warned && System.currentTimeMillis() - onMs > 4000) {
            warned = true;
            if (asked.sum() == 0) {
                FarmMacroMod.LOGGER.warn("FarmMacro Low render: сборка чанков не вызвала ни RenderSectionRegion, ни Sodium LevelSlice — "
                        + "чанки собирает другой мод, режим не работает");
                if (mc.player != null) mc.player.sendSystemMessage(Component.literal(
                        "§8[§cFM§8] §eLow render не сработал: чанки собирает другой мод рендера (не vanilla и не Sodium). Подробности — в логе."));
            } else FarmMacroMod.LOGGER.info("FarmMacro Low render: работает ({} запросов блоков за 4 с, Sodium: {})", asked.sum(),
                    net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium"));
        }
        Center old = center, now = centerOf(mc.player);
        if (now.equals(old)) return;
        center = now;
        // пересобрать секции, которые задевают старый и новый круг (+1 — грани соседей)
        int m = Math.max(old == null ? 0 : old.r, now.r) + 1;
        int x0 = now.x, y0 = now.y, z0 = now.z, x1 = now.x, y1 = now.y, z1 = now.z;
        if (old != null) {
            x0 = Math.min(x0, old.x); y0 = Math.min(y0, old.y); z0 = Math.min(z0, old.z);
            x1 = Math.max(x1, old.x); y1 = Math.max(y1, old.y); z1 = Math.max(z1, old.z);
        }
        if (old != null && (Math.abs(old.x - now.x) > 32 || Math.abs(old.y - now.y) > 32 || Math.abs(old.z - now.z) > 32)) {
            mc.levelRenderer.allChanged();                  // телепорт далеко — проще пересобрать всё
            return;
        }
        mc.levelRenderer.setBlocksDirty(x0 - m, y0 - m, z0 - m, x1 + m, y1 + m, z1 + m);
    }

    private static Center centerOf(LocalPlayer p) {
        return new Center((int) Math.floor(p.getX()), (int) Math.floor(p.getY() + 0.01), (int) Math.floor(p.getZ()),
                ModConfig.INSTANCE.lowRenderRadius);
    }

    /** Из сборки секций (любой поток): блок спрятан (за радиусом)? */
    public static boolean hides(int x, int y, int z) {
        Center c = center;
        if (!active || c == null) return false;
        asked.increment();
        return !LowRenderMath.blockInside(x, y, z, c.x, c.y, c.z, c.r);
    }

    /** Из миксина Frustum: OUTSIDE/PARTIAL/INSIDE относительно радиуса (вне режима — INSIDE). */
    public static int classify(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        Center c = center;
        if (!active || c == null) return LowRenderMath.INSIDE;
        return LowRenderMath.classify(minX, minY, minZ, maxX, maxY, maxZ,
                c.x + 0.5, c.y + 0.5, c.z + 0.5, c.r + LowRenderMath.MARGIN);
    }

    private static void msg(Minecraft mc, String text) {
        if (mc != null && mc.player != null) mc.player.sendOverlayMessage(Component.literal("§8[§cFM§8] §r" + text));
    }
}
