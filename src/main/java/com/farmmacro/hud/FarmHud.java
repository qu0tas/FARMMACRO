package com.farmmacro.hud;

import com.farmmacro.FarmMacroMod;
import com.farmmacro.config.ModConfig;
import com.farmmacro.gui.Ui;
import com.farmmacro.macro.MacroFrame;
import com.farmmacro.macro.MacroManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.Locale;

/**
 * HUD мода (правый верхний угол): панель статуса, навигатор к точке старта/остановки,
 * крупный обратный отсчёт по центру. Стиль совпадает с меню (палитра {@link Ui}).
 */
public final class FarmHud {
    private FarmHud() {}

    private static final int MARGIN = 6;
    private static final int PANEL_W = 132;

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("farmmacro", "hud"), (g, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui) return;
            int y = MARGIN;
            if (mc.screen == null) {
                y = drawStatus(g, mc, y);
                drawNavigator(g, mc, y);
            }
            drawCountdown(g, mc);
        });
    }

    // ── Статус ───────────────────────────────────────────────────────────────

    private static int drawStatus(GuiGraphicsExtractor g, Minecraft mc, int y) {
        MacroManager m = MacroManager.INSTANCE;
        ModConfig c = ModConfig.INSTANCE;
        Font f = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        MacroManager.State st = m.getState();

        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        String head; int col;
        switch (st) {
            case RECORDING -> { head = "ЗАПИСЬ · " + MacroManager.formatTicks(m.getFrameCount()); col = Ui.DANGER; }
            case COUNTDOWN -> { head = "СТАРТ ЧЕРЕЗ " + (m.getCountdownTicks() + 19) / 20; col = Ui.WARN; }
            case PLAYING -> { head = "ИГРАЕТ"; col = Ui.ON; }
            default -> { head = "СТОП"; col = Ui.SUB; }
        }

        if (!c.statsHudEnabled) {
            if (st == MacroManager.State.IDLE) return y;
            // компактная «пилюля», чтобы запись/игра никогда не шли незаметно
            String s = st == MacroManager.State.PLAYING ? "▶ " + percent(m) + "%" : head;
            int w = f.width(s) + 16;
            int x = sw - w - MARGIN;
            Ui.pill(g, x, y, w, 14, 0xC0101420);
            Ui.circle(g, x + 7, y + 7, 2, blink || st != MacroManager.State.RECORDING ? col : Ui.alpha(col, 0.3f));
            Ui.text(g, f, s, x + 12, y + 3, Ui.TEXT);
            return y + 18;
        }
        if (st == MacroManager.State.IDLE && m.getSessionRuns() == 0) return y;

        int x = sw - PANEL_W - MARGIN;
        boolean playing = st == MacroManager.State.PLAYING;
        int h = playing ? 46 : 36;
        Ui.round(g, x, y, PANEL_W, h, 5, 0xC8101420);
        g.fill(x, y + 3, x + 2, y + h - 3, col);

        Ui.circle(g, x + 9, y + 8, 2, blink || st != MacroManager.State.RECORDING ? col : Ui.alpha(col, 0.3f));
        Ui.text(g, f, Ui.ellipsize(f, head, PANEL_W - 20), x + 15, y + 4, col);

        String name = m.getLoadedName() != null ? m.getLoadedName()
                : m.getFrameCount() > 0 ? "несохранённая запись" : "нет макроса";
        Ui.text(g, f, Ui.ellipsize(f, name, PANEL_W - 14), x + 8, y + 15, Ui.TEXT);

        String line;
        if (playing) {
            int pct = percent(m);
            int bx = x + 8, bw = PANEL_W - 16, by = y + 27;
            Ui.pill(g, bx, by, bw, 4, 0xFF2A3245);
            Ui.pill(g, bx, by, Math.max(4, bw * pct / 100), 4, Ui.ON);
            long sec = (System.currentTimeMillis() - m.getRunStartMs()) / 1000;
            String loops = c.loopEnabled ? "круг " + (m.getLoopsDone() + 1) + (c.loopLimit > 0 ? "/" + c.loopLimit : "") + " · " : "";
            line = loops + pct + "% · " + clock(sec);
            Ui.text(g, f, Ui.ellipsize(f, line, PANEL_W - 14), x + 8, y + 35, Ui.SUB);
        } else {
            long sec = m.getSessionStartMs() > 0 ? (System.currentTimeMillis() - m.getSessionStartMs()) / 1000 : 0;
            line = st == MacroManager.State.RECORDING
                    ? MacroManager.keyName(FarmMacroMod.keyRecord) + " — остановить"
                    : "сессия " + clock(sec) + " · кругов " + m.getSessionRuns();
            Ui.text(g, f, Ui.ellipsize(f, line, PANEL_W - 14), x + 8, y + 25, Ui.SUB);
        }
        return y + h + 4;
    }

    private static int percent(MacroManager m) {
        return m.getFrameCount() == 0 ? 0 : Math.min(100, m.getPlaybackIndex() * 100 / m.getFrameCount());
    }

    private static String clock(long sec) {
        return sec >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", sec / 3600, sec / 60 % 60, sec % 60)
                : String.format(Locale.ROOT, "%d:%02d", sec / 60, sec % 60);
    }

    // ── Навигатор ────────────────────────────────────────────────────────────

    private static void drawNavigator(GuiGraphicsExtractor g, Minecraft mc, int y) {
        MacroManager m = MacroManager.INSTANCE;
        ModConfig c = ModConfig.INSTANCE;
        if (!c.navHudEnabled || m.getState() != MacroManager.State.IDLE) return;
        LocalPlayer p = mc.player;

        double tx, ty, tz; String title, hint;
        if (m.hasSavedPosition()) {
            tx = m.getSavedX(); ty = m.getSavedY(); tz = m.getSavedZ();
            title = "Точка остановки";
            hint = MacroManager.keyName(FarmMacroMod.keyResume) + " — продолжить";
        } else {
            MacroFrame start = m.getStartFrame();
            if (start == null) return;
            tx = start.x; ty = start.y; tz = start.z;
            double d = Math.sqrt(sq(p.getX() - tx) + sq(p.getY() - ty) + sq(p.getZ() - tz));
            if (d <= c.startPointWarnDistance) return;   // уже на месте — не мешаем
            title = "Точка старта";
            hint = MacroManager.keyName(FarmMacroMod.keyPlay) + " — запуск";
        }

        Font f = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        int x = sw - PANEL_W - MARGIN, h = 36;
        double dist = Math.sqrt(sq(p.getX() - tx) + sq(p.getZ() - tz));
        double dy = ty - p.getY();

        float pulse = (float) (0.55 + 0.45 * Math.sin(System.currentTimeMillis() / 300.0));
        Ui.round(g, x, y, PANEL_W, h, 5, 0xC8101420);
        g.fill(x, y + 3, x + 2, y + h - 3, Ui.alpha(Ui.ACCENT, 0.5f + 0.5f * pulse));
        Ui.text(g, f, title, x + 8, y + 4, Ui.ACCENT_HI);
        String d = String.format(Locale.ROOT, "%.1f бл", dist)
                + (Math.abs(dy) >= 1 ? String.format(Locale.ROOT, "  %s%.0f", dy > 0 ? "↑" : "↓", Math.abs(dy)) : "");
        Ui.text(g, f, d, x + 8, y + 15, Ui.TEXT);
        Ui.text(g, f, Ui.ellipsize(f, hint, PANEL_W - 40), x + 8, y + 25, Ui.SUB);

        // стрелка: угол между взглядом и направлением на цель
        double targetYaw = Math.toDegrees(Math.atan2(tz - p.getZ(), tx - p.getX())) - 90.0;
        double rel = Math.toRadians(targetYaw - p.getYRot());
        int ax = x + PANEL_W - 18, ay = y + h / 2;
        Ui.circle(g, ax, ay, 12, 0xFF1C2230);
        if (dist < 0.75) {
            Ui.circle(g, ax, ay, 4, Ui.ON);
        } else {
            drawArrow(g, ax, ay, (float) rel, Ui.ACCENT_HI);
        }
    }

    /** Стрелка из прямоугольников, повёрнутая матрицей (0 = вперёд/вверх по экрану). */
    private static void drawArrow(GuiGraphicsExtractor g, int cx, int cy, float angle, int color) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.rotate(angle);
        g.fill(-1, -2, 1, 7, color);                 // древко
        for (int i = 0; i < 5; i++) g.fill(-i, -7 + i, i + 1, -6 + i, color);   // наконечник
        pose.popMatrix();
    }

    private static double sq(double v) { return v * v; }

    // ── Обратный отсчёт ──────────────────────────────────────────────────────

    private static void drawCountdown(GuiGraphicsExtractor g, Minecraft mc) {
        MacroManager m = MacroManager.INSTANCE;
        if (!m.isCountingDown()) return;
        int ticks = m.getCountdownTicks();
        int sec = (ticks + 19) / 20;
        float frac = (ticks % 20) / 20f;              // 1 → 0 в течение секунды
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        float scale = 4f + frac * 1.5f;
        pose.translate(sw / 2f, sh / 2f - 30);
        pose.scale(scale, scale);
        String s = String.valueOf(sec);
        g.text(mc.font, s, -mc.font.width(s) / 2, -4, Ui.alpha(Ui.WARN, 0.35f + 0.65f * frac), true);
        pose.popMatrix();
        Ui.textCentered(g, mc.font, "макрос стартует · " + MacroManager.keyName(FarmMacroMod.keyPlay) + " — отмена",
                sw / 2, sh / 2 - 4, Ui.SUB);
    }
}
