package com.farmmacro.camera;

import com.farmmacro.FarmMacroMod;
import com.farmmacro.macro.MacroManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * v1.13.1 «Отпустить курсор» (по умолчанию U): курсор свободен — можно пользоваться ПК, а макрос дальше ходит и ломает.
 * Как устроено: {@code MouseHandler.releaseMouse()}; повторный захват ({@code grabMouse}) гасится UngrabMixin;
 * зажатая ЛКМ ломает блоки и без захвата (MinecraftAttackMixin подменяет isMouseGrabbed в handleKeybinds);
 * «пауза при потере фокуса» на это время выключается. Мышь камеру не крутит (курсор не захвачен).
 * Снимается: клавишей, паникой, End, стопом макроса, выходом из мира.
 */
public final class Ungrab {
    private static boolean active;
    private static Boolean savedPauseOnLostFocus;

    private Ungrab() {}

    public static boolean active() { return active; }

    public static void toggle(Minecraft mc) {
        if (active) off(mc, null);
        else on(mc, null);
    }

    public static void on(Minecraft mc, String why) {
        if (active || mc.player == null) return;
        if (why == null && !MacroManager.INSTANCE.isActive() && !com.farmmacro.macro.MouseHold.INSTANCE.isManual()) {
            msg(mc, "§7Курсор отпускается, пока идёт макрос (или зажим мыши L)");
            return;
        }
        active = true;
        savedPauseOnLostFocus = mc.options.pauseOnLostFocus;
        mc.options.pauseOnLostFocus = false;
        mc.mouseHandler.releaseMouse();
        msg(mc, "§b◌ Курсор отпущен" + (why != null ? ": " + why : "") + " §7(" + MacroManager.keyName(FarmMacroMod.keyUngrab) + " — вернуть)");
    }

    public static void off(Minecraft mc, String why) {
        if (!active) return;
        active = false;
        if (savedPauseOnLostFocus != null) mc.options.pauseOnLostFocus = savedPauseOnLostFocus;
        savedPauseOnLostFocus = null;
        if (mc.screen == null && mc.player != null) mc.mouseHandler.grabMouse();
        msg(mc, why == null ? "§7◉ Курсор захвачен" : "§e◉ Курсор захвачен: " + why);
    }

    /** Гасить ли захват курсора (из миксина). */
    public static boolean blockGrab() { return active; }

    /** Ломать ли блоки без захвата курсора (из миксина): только пока отпущен и нет окна. */
    public static boolean attackWithoutGrab() {
        Minecraft mc = Minecraft.getInstance();
        return active && mc.screen == null && mc.player != null;
    }

    /** Каждый тик: вышли из мира или макрос остановился — вернуть курсор. */
    public static void tick(Minecraft mc) {
        if (!active) return;
        if (mc.player == null || mc.level == null) { active = false; if (savedPauseOnLostFocus != null) mc.options.pauseOnLostFocus = savedPauseOnLostFocus; savedPauseOnLostFocus = null; return; }
        if (!MacroManager.INSTANCE.isActive() && !com.farmmacro.macro.MouseHold.INSTANCE.isManual()
                && com.farmmacro.macro.Scheduler.phase() != com.farmmacro.macro.Scheduler.Phase.BREAK) off(mc, "макрос остановлен");
    }

    private static void msg(Minecraft mc, String text) {
        if (mc != null && mc.player != null) mc.player.sendOverlayMessage(Component.literal("§8[§9Kaelix§8] §r" + text));
    }
}
