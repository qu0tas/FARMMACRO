package com.farmmacro.window;

import com.farmmacro.FarmMacroMod;
import com.farmmacro.config.ModConfig;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.panic.EventLog;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * v1.16.3 «Картинка в картинке»: окно игры — маленькое, в углу экрана, поверх всех окон и без рамки; повторно —
 * прежние размер и место. Полноэкранный режим сначала выключается (окно ставится в PiP на следующем тике).
 * Во время макроса курсор отпускается (как U), чтобы можно было работать в других программах.
 *
 * {@link #toFront}: паника — выйти из PiP, развернуть свёрнутое окно, на миг сделать его «поверх всех» (так оно
 * поднимается над другими окнами даже без фокуса), запросить фокус и мигнуть в панели задач.
 * Всё через GLFW в главном потоке (javap LWJGL 3.4.1: glfwSetWindowAttrib/GLFW_FLOATING/GLFW_DECORATED,
 * glfwGetMonitorWorkarea, glfwFocusWindow, glfwRequestWindowAttention, glfwRestoreWindow).
 */
public final class Pip {
    private Pip() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("FarmMacro/Pip");

    private static boolean active, pendingOn, wasMaximized, wasDecorated = true, wasFloating;
    private static int sx, sy, sw, sh;
    /** Тиков до снятия временного «поверх всех» после паники (0 — нет). */
    private static int unfloatTicks;
    private static boolean wasMacroActive;

    public static boolean active() { return active; }

    public static void toggle(Minecraft mc) {
        if (active || pendingOn) off(mc, null);
        else on(mc);
    }

    public static void on(Minecraft mc) {
        Window w = mc.getWindow();
        long h = w.handle();
        if (h == 0) return;
        if (w.isFullscreen()) {                 // из полноэкранного — сначала в окно, PiP на следующем тике
            w.toggleFullScreen();
            pendingOn = true;
            return;
        }
        pendingOn = false;
        ModConfig c = ModConfig.INSTANCE;
        try {
            int[] x = new int[1], y = new int[1], ww = new int[1], hh = new int[1];
            wasMaximized = GLFW.glfwGetWindowAttrib(h, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE;
            if (wasMaximized) GLFW.glfwRestoreWindow(h);
            GLFW.glfwGetWindowPos(h, x, y);
            GLFW.glfwGetWindowSize(h, ww, hh);
            sx = x[0]; sy = y[0]; sw = ww[0]; sh = hh[0];
            wasDecorated = GLFW.glfwGetWindowAttrib(h, GLFW.GLFW_DECORATED) == GLFW.GLFW_TRUE;
            wasFloating = GLFW.glfwGetWindowAttrib(h, GLFW.GLFW_FLOATING) == GLFW.GLFW_TRUE;
            int[] area = workArea(sx + sw / 2, sy + sh / 2);
            PipMath.Rect r = PipMath.place(area[0], area[1], area[2], area[3], c.pipWidth, c.pipHeight, c.pipCorner, c.pipMargin);
            if (c.pipBorderless) GLFW.glfwSetWindowAttrib(h, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
            if (c.pipOnTop) GLFW.glfwSetWindowAttrib(h, GLFW.GLFW_FLOATING, GLFW.GLFW_TRUE);
            GLFW.glfwSetWindowSize(h, r.w(), r.h());
            GLFW.glfwSetWindowPos(h, r.x(), r.y());
            active = true;
            unfloatTicks = 0;
            EventLog.log("PIP", "вкл " + r.w() + "×" + r.h() + " в (" + r.x() + ", " + r.y() + ")");
            if (c.pipUngrab && com.farmmacro.window.BackgroundWork.running()) com.farmmacro.camera.Ungrab.on(mc, "картинка в картинке");
            msg(mc, "§b▣ Картинка в картинке §7(" + MacroManager.keyName(FarmMacroMod.keyPip) + " — обычное окно)");
        } catch (Throwable t) {
            LOGGER.warn("Картинка в картинке не включилась", t);
            msg(mc, "§cКартинка в картинке не включилась: " + t.getClass().getSimpleName());
        }
    }

    public static void off(Minecraft mc, String why) {
        pendingOn = false;
        if (!active) return;
        active = false;
        long h = mc.getWindow().handle();
        try {
            GLFW.glfwSetWindowAttrib(h, GLFW.GLFW_FLOATING, wasFloating ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
            GLFW.glfwSetWindowAttrib(h, GLFW.GLFW_DECORATED, wasDecorated ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
            if (sw > 0 && sh > 0) {
                GLFW.glfwSetWindowSize(h, sw, sh);
                GLFW.glfwSetWindowPos(h, sx, sy);
            }
            if (wasMaximized) GLFW.glfwMaximizeWindow(h);
            EventLog.log("PIP", "выкл" + (why != null ? " (" + why + ")" : ""));
            msg(mc, why == null ? "§7▣ Обычное окно" : "§e▣ Обычное окно: " + why);
        } catch (Throwable t) {
            LOGGER.warn("Не удалось вернуть окно из «картинки в картинке»", t);
        }
    }

    /** Каждый тик (и вне мира). */
    public static void tick(Minecraft mc) {
        if (pendingOn && !mc.getWindow().isFullscreen()) on(mc);
        // макрос запустили уже в PiP — один раз отпустить курсор (U вернёт захват, повторно не отпускаем)
        boolean m = com.farmmacro.window.BackgroundWork.running();
        if (active && m && !wasMacroActive && ModConfig.INSTANCE.pipUngrab && !com.farmmacro.camera.Ungrab.active())
            com.farmmacro.camera.Ungrab.on(mc, "картинка в картинке");
        wasMacroActive = m;
        if (unfloatTicks > 0 && --unfloatTicks == 0 && !active) {
            try { GLFW.glfwSetWindowAttrib(mc.getWindow().handle(), GLFW.GLFW_FLOATING, GLFW.GLFW_FALSE); }
            catch (Throwable ignored) {}
        }
    }

    /** Паника: в основное окно — что бы игрок ни делал. */
    public static void toFront(Minecraft mc, String why) {
        ModConfig c = ModConfig.INSTANCE;
        toFront(mc,why,c.panicExitPip,c.panicFocusWindow);
    }
    public static void toFront(Minecraft mc,String why,boolean exitPip,boolean focus){
        if (exitPip && (active || pendingOn)) off(mc, why);
        if (!focus) return;
        Window w = mc.getWindow();
        long h = w.handle();
        if (h == 0) return;
        try {
            if (GLFW.glfwGetWindowAttrib(h, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE) GLFW.glfwRestoreWindow(h);
            GLFW.glfwShowWindow(h);
            if (!active && !w.isFullscreen() && GLFW.glfwGetWindowAttrib(h, GLFW.GLFW_FLOATING) == GLFW.GLFW_FALSE) {
                GLFW.glfwSetWindowAttrib(h, GLFW.GLFW_FLOATING, GLFW.GLFW_TRUE);   // поднять над другими окнами…
                unfloatTicks = 20;                                                   // …и через 1 с вернуть как было
            }
            GLFW.glfwFocusWindow(h);
            if (GLFW.glfwGetWindowAttrib(h, GLFW.GLFW_FOCUSED) != GLFW.GLFW_TRUE) GLFW.glfwRequestWindowAttention(h);
            EventLog.log("FOCUS", "окно игры вперёд" + (w.isFocused() ? "" : " (фокус не дали — мигаю в панели задач)"));
        } catch (Throwable t) {
            LOGGER.warn("Не удалось вывести окно вперёд", t);
        }
    }

    /** Рабочая область монитора, где центр окна: {x, y, w, h}. */
    private static int[] workArea(int cx, int cy) {
        PointerBuffer ms = GLFW.glfwGetMonitors();
        int n = ms == null ? 0 : ms.limit();
        int[][] areas = new int[n][];
        for (int i = 0; i < n; i++) {
            int[] x = new int[1], y = new int[1], w = new int[1], h = new int[1];
            GLFW.glfwGetMonitorWorkarea(ms.get(i), x, y, w, h);
            areas[i] = new int[]{x[0], y[0], w[0], h[0]};
        }
        int k = PipMath.monitorFor(areas, cx, cy);
        if (k >= 0) return areas[k];
        long prim = GLFW.glfwGetPrimaryMonitor();
        int[] x = new int[1], y = new int[1], w = new int[1], h = new int[1];
        if (prim != 0) GLFW.glfwGetMonitorWorkarea(prim, x, y, w, h);
        return new int[]{x[0], y[0], Math.max(640, w[0]), Math.max(360, h[0])};
    }

    private static void msg(Minecraft mc, String s) {
        if (mc.player != null) mc.player.sendOverlayMessage(Component.literal("§8[§9Kaelix§8] " + s));
    }
}
