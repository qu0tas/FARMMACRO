package com.farmmacro.camera;

import com.farmmacro.config.ModConfig;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.panic.EventLog;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Locale;

/**
 * v1.16.3 Squeaky Mousemat: пресет камеры ставится предметом Skyblock — взять Mousemat в руку (с задержкой смены
 * слота «Задержки рук»), клик (ЛКМ «Snap» по умолчанию; клик через {@code KeyMapping.click}, как будто нажал сам),
 * сервер присылает поворот к углу, сохранённому в предмете, затем вернуть прежний слот.
 *
 * Детектор «Телепорт/поворот от сервера» этот поворот не считает, только если он пришёл в окне ожидания, без сдвига
 * позиции и совпал с пресетом ± допуск ({@link #acceptServerRotation}). Другой угол — обычная проверка + подсказка.
 */
public final class Mousemat {
    private Mousemat() {}

    private enum Ph { IDLE, SELECT, CLICK, WAIT, RESTORE }

    private static Ph ph = Ph.IDLE;
    private static int slot, prevSlot, waitTicks;
    private static float wantYaw, wantPitch;
    private static long windowUntil;
    private static boolean rotated;
    private static String label = "";

    public static boolean busy() { return ph != Ph.IDLE; }

    /** Слот хотбара с Mousemat (0–8) или -1. */
    public static int findSlot(LocalPlayer p, int wanted) {
        if (p == null) return -1;
        boolean[] mats = new boolean[9];
        for (int i = 0; i < 9; i++) mats[i] = isMat(p.getInventory().getItem(i));
        return MousematMath.pickSlot(mats, wanted, p.getInventory().getSelectedSlot());
    }

    public static boolean isMat(ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        String id = null;
        CustomData cd = s.get(DataComponents.CUSTOM_DATA);
        if (cd != null) {
            var tag = cd.copyTag();
            id = tag.getStringOr("id", null);
            if (id == null) id = tag.getCompoundOrEmpty("ExtraAttributes").getStringOr("id", null);
        }
        return MousematMath.isMat(id, s.getHoverName().getString());
    }

    /**
     * Начать Snap к пресету. @return false — Mousemat не найден (вызывающий решает: обычный поворот или ничего).
     */
    public static boolean start(Minecraft mc, ModConfig.CamPreset preset, String what) {
        LocalPlayer p = mc.player;
        if (p == null || preset == null) return false;
        int s = findSlot(p, preset.matSlot);
        if (s < 0) return false;
        SmoothTurn.stop();
        slot = s;
        prevSlot = p.getInventory().getSelectedSlot();
        wantYaw = preset.yaw;
        wantPitch = preset.pitch;
        label = what;
        rotated = false;
        waitTicks = 0;
        ph = Ph.SELECT;
        EventLog.log("MOUSEMAT", "старт: слот " + (s + 1) + ", ждём yaw " + CameraPresets.deg(wantYaw) + " pitch " + CameraPresets.deg(wantPitch));
        return true;
    }

    /** Стоп/паника: прекратить (слот не возвращаем — игрок сам). */
    public static void cancel() {
        if (ph != Ph.IDLE) EventLog.log("MOUSEMAT", "отмена");
        ph = Ph.IDLE;
        windowUntil = 0;
    }

    /** Каждый тик. */
    public static void tick(Minecraft mc) {
        if (ph == Ph.IDLE) return;
        LocalPlayer p = mc.player;
        if (p == null) { cancel(); return; }
        ModConfig c = ModConfig.INSTANCE;
        var rnd = java.util.concurrent.ThreadLocalRandom.current();
        switch (ph) {
            case SELECT -> {
                MacroManager.selectSlot(p, slot);
                if (p.getInventory().getSelectedSlot() == slot) { ph = Ph.CLICK; waitTicks = 2 + rnd.nextInt(4); }
            }
            case CLICK -> {
                if (mc.screen != null) return;                 // окно открыто — клик не дойдёт, ждём
                if (--waitTicks > 0) return;
                KeyMapping key = "use".equals(c.mousematClick) ? mc.options.keyUse : mc.options.keyAttack;
                KeyMapping.click(KeyMappingHelper.getBoundKeyOf(key));
                windowUntil = System.currentTimeMillis() + c.mousematWaitMs;
                ph = Ph.WAIT;
            }
            case WAIT -> {
                if (rotated) { ph = Ph.RESTORE; waitTicks = 3 + rnd.nextInt(6); return; }
                if (System.currentTimeMillis() > windowUntil) {
                    msg(mc, "§6Mousemat не повернул камеру за " + c.mousematWaitMs + " мс — проверь, что в нём сохранён угол пресета");
                    EventLog.log("MOUSEMAT", "нет поворота от сервера");
                    ph = Ph.RESTORE; waitTicks = 2;
                }
            }
            case RESTORE -> {
                if (--waitTicks > 0) return;
                if (!c.mousematRestoreSlot || prevSlot == slot) { done(mc); return; }
                MacroManager.selectSlot(p, prevSlot);
                if (p.getInventory().getSelectedSlot() == prevSlot) done(mc);
            }
            default -> {}
        }
    }

    private static void done(Minecraft mc) {
        ph = Ph.IDLE;
        windowUntil = 0;
        if (rotated) msg(mc, "§b◎ Mousemat: §f" + label);
    }

    /**
     * Из детектора (после пакета поворота/телепорта): это наш Snap? Тогда true — не паниковать.
     * Совпадение: окно ожидания, позиция не сдвинулась (≤ 0.05 бл), угол = пресет ± допуск.
     */
    public static boolean acceptServerRotation(float yaw, float pitch, double moved) {
        if (ph != Ph.WAIT || System.currentTimeMillis() > windowUntil || moved > 0.05) return false;
        ModConfig c = ModConfig.INSTANCE;
        if (!MousematMath.matches(yaw, pitch, wantYaw, wantPitch, c.mousematTolerance)) {
            EventLog.log("MOUSEMAT", String.format(Locale.ROOT, "поворот не тот: %.2f/%.2f, ждали %.2f/%.2f",
                    yaw, pitch, wantYaw, wantPitch));
            msg(Minecraft.getInstance(), String.format(Locale.ROOT,
                    "§6Mousemat повернул в %.1f / %.1f, а пресет %.1f / %.1f — запиши угол в Mousemat (ПКМ)", yaw, pitch, wantYaw, wantPitch));
            return false;
        }
        rotated = true;
        EventLog.log("MOUSEMAT", String.format(Locale.ROOT, "поворот от сервера: %.3f / %.3f", yaw, pitch));
        return true;
    }

    private static void msg(Minecraft mc, String s) {
        if (mc != null && mc.player != null) mc.player.sendOverlayMessage(Component.literal("§8[§9Kaelix§8] " + s));
    }
}
