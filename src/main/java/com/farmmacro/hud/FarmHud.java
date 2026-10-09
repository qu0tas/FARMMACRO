package com.farmmacro.hud;

import com.farmmacro.FarmMacroMod;
import com.farmmacro.config.ModConfig;
import com.farmmacro.gui.Ui;
import com.farmmacro.macro.MacroFrame;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.util.Guard;
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
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("farmmacro", "hud"), Guard.hud("hud", (g, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui) return;
            int y = MARGIN;
            if (mc.screen == null) {
                float pt = delta.getGameTimeDeltaPartialTick(true);
                Target t = target(mc, pt);
                y = drawStatus(g, mc, y);
                drawNavigator(g, mc, y, t);
                drawCrosshairArrow(g, mc, t);
            }
            drawCountdown(g, mc);
        }));
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

    /** Цель навигатора, посчитанная по интерполированной позиции и взгляду игрока. */
    private record Target(String title, String hint, double dist, double dy, float relAngle, boolean arrived) {}

    /** null — навигатор не нужен (макрос идёт или цели нет). */
    private static Target target(Minecraft mc, float pt) {
        MacroManager m = MacroManager.INSTANCE;
        LocalPlayer p = mc.player;
        if (p == null || m.getState() != MacroManager.State.IDLE) return null;
        double tx, ty, tz; String title, hint;
        if (m.hasSavedPosition()) {
            tx = m.getSavedX(); ty = m.getSavedY(); tz = m.getSavedZ();
            title = "Точка остановки";
            hint = MacroManager.keyName(FarmMacroMod.keyResume) + " — продолжить";
        } else {
            MacroFrame start = m.getStartFrame();
            if (start == null) return null;
            tx = start.x; ty = start.y; tz = start.z;
            title = "Точка старта";
            hint = MacroManager.keyName(FarmMacroMod.keyPlay) + " — запуск";
        }
        var pos = p.getPosition(pt);
        double dist = Math.sqrt(sq(pos.x - tx) + sq(pos.z - tz));
        double dy = ty - pos.y;
        double targetYaw = Math.toDegrees(Math.atan2(tz - pos.z, tx - pos.x)) - 90.0;
        float rel = (float) Math.toRadians(targetYaw - p.getViewYRot(pt));
        // «на месте» — в пределах допуска точки старта (раньше панель в этот момент просто исчезала)
        boolean arrived = Math.sqrt(dist * dist + dy * dy) <= ModConfig.INSTANCE.startPointWarnDistance;
        return new Target(title, hint, dist, dy, rel, arrived);
    }

    private static void drawNavigator(GuiGraphicsExtractor g, Minecraft mc, int y, Target t) {
        if (t == null || !ModConfig.INSTANCE.navHudEnabled) return;
        Font f = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        int x = sw - PANEL_W - MARGIN, h = 36;
        int accent = t.arrived() ? Ui.ON : Ui.ACCENT;

        float pulse = (float) (0.55 + 0.45 * Math.sin(System.currentTimeMillis() / 300.0));
        Ui.round(g, x, y, PANEL_W, h, 5, 0xC8101420);
        g.fill(x, y + 3, x + 2, y + h - 3, Ui.alpha(accent, 0.5f + 0.5f * pulse));
        Ui.text(g, f, Ui.ellipsize(f, t.arrived() ? "✔ На месте" : t.title(), PANEL_W - 44), x + 8, y + 4,
                t.arrived() ? Ui.ON : Ui.ACCENT_HI);
        Ui.text(g, f, distText(t), x + 8, y + 15, Ui.TEXT);
        Ui.text(g, f, Ui.ellipsize(f, t.hint(), PANEL_W - 40), x + 8, y + 25, Ui.SUB);

        int ax = x + PANEL_W - 18, ay = y + h / 2;
        Ui.circle(g, ax, ay, 12, 0xFF1C2230);
        if (t.dist() < 0.75) Ui.circle(g, ax, ay, 4, Ui.ON);
        else drawArrow(g, ax, ay, t.relAngle(), t.arrived() ? Ui.ON : Ui.ACCENT_HI);
    }

    /** Маленькая стрелка вокруг прицела, указывает на цель; рядом расстояние. */
    private static void drawCrosshairArrow(GuiGraphicsExtractor g, Minecraft mc, Target t) {
        if (t == null || !ModConfig.INSTANCE.navCrosshairEnabled) return;
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        int cx = sw / 2, cy = sh / 2;
        float pulse = (float) (0.6 + 0.4 * Math.sin(System.currentTimeMillis() / 250.0));
        if (t.dist() < 0.6) {
            // стоим на точке: зелёное кольцо вокруг прицела
            int col = Ui.alpha(Ui.ON, 0.55f + 0.45f * pulse);
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI / 8;
                int px = cx + (int) Math.round(Math.cos(a) * 9), py = cy + (int) Math.round(Math.sin(a) * 9);
                g.fill(px - 1, py - 1, px + 1, py + 1, col);
            }
        } else {
            float r = 17f;
            int ax = cx + Math.round((float) Math.sin(t.relAngle()) * r);
            int ay = cy - Math.round((float) Math.cos(t.relAngle()) * r);
            drawArrow(g, ax, ay, t.relAngle(), Ui.alpha(t.arrived() ? Ui.ON : Ui.ACCENT_HI, 0.75f + 0.25f * pulse));
        }
        String d = distText(t);
        Ui.textCentered(g, mc.font, d, cx, cy + 24, Ui.alpha(t.arrived() ? Ui.ON : 0xFFFFFFFF, 0.9f));
    }

    private static String distText(Target t) {
        return String.format(Locale.ROOT, "%.1f бл", t.dist())
                + (Math.abs(t.dy()) >= 1 ? String.format(Locale.ROOT, "  %s%.0f", t.dy() > 0 ? "↑" : "↓", Math.abs(t.dy())) : "");
    }

    /** Стрелка из прямоугольников, повёрнутая матрицей (0 = вперёд/вверх по экрану). */
    private static void drawArrow(GuiGraphicsExtractor g, int cx, int cy, float angle, int color) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        try {
            pose.translate(cx, cy);
            pose.rotate(angle);
            g.fill(-1, -2, 1, 7, color);                 // древко
            for (int i = 0; i < 5; i++) g.fill(-i, -7 + i, i + 1, -6 + i, color);   // наконечник
        } finally {
            pose.popMatrix();
        }
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
        try {
            float scale = 4f + frac * 1.5f;
            pose.translate(sw / 2f, sh / 2f - 30);
            pose.scale(scale, scale);
            String s = String.valueOf(sec);
            g.text(mc.font, s, -mc.font.width(s) / 2, -4, Ui.alpha(Ui.WARN, 0.35f + 0.65f * frac), true);
        } finally {
            pose.popMatrix();
        }
        Ui.textCentered(g, mc.font, "макрос стартует · " + MacroManager.keyName(FarmMacroMod.keyPlay) + " — отмена",
                sw / 2, sh / 2 - 4, Ui.SUB);
    }
}
