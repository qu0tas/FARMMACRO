package com.farmmacro.camera;

import com.farmmacro.FarmMacroMod;
import com.farmmacro.macro.MacroManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * «Блокировка мыши» (по умолчанию M): движение мыши не поворачивает камеру, кнопки мыши работают.
 * Гасится только поворот игрока ({@code MouseHandler.turnPlayer}, см. MouseLockMixin) — окна и курсор в GUI как обычно.
 * Пресеты (K/J/H), привязка камеры и повороты из записи ставят поворот сами и работают при блокировке.
 * Разблокируется сама: паника (первым шагом), End, выход из мира; по настройке — обычный стоп.
 * Состояние не сохраняется: после входа в игру всегда разблокировано.
 */
public final class MouseLock {
    private static boolean locked;

    private MouseLock() {}

    public static boolean isLocked() { return locked; }

    /** Гасить ли поворот от мыши прямо сейчас (вызывается из миксина). */
    public static boolean shouldBlock() {
        if (!locked) return false;
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.screen == null;
    }

    /** Клавиша «Блокировка мыши». */
    public static void toggle(Minecraft mc) {
        if (locked) unlock(mc, null);
        else lock(mc, null);
    }

    public static void lock(Minecraft mc, String why) {
        if (locked) return;
        locked = true;
        msg(mc, "§b■ Мышь заблокирована" + (why != null ? ": " + why : "")
                + " §7(" + MacroManager.keyName(FarmMacroMod.keyMouseLock) + " — снять)");
    }

    /** why = null — вручную клавишей; иначе причина авторазблокировки. */
    public static void unlock(Minecraft mc, String why) {
        if (!locked) return;
        locked = false;
        msg(mc, why == null ? "§7□ Мышь разблокирована" : "§e□ Мышь разблокирована: " + why);
    }

    /** Каждый тик: без игрока (вышли из мира) — тихо снять блокировку. */
    public static void tick(Minecraft mc) {
        if (locked && (mc.player == null || mc.level == null)) locked = false;
    }

    private static void msg(Minecraft mc, String text) {
        if (mc != null && mc.player != null) mc.player.sendOverlayMessage(Component.literal("§8[§cFM§8] §r" + text));
    }
}
