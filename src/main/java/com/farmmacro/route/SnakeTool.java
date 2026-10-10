package com.farmmacro.route;

import com.farmmacro.config.ModConfig;
import com.farmmacro.visual.RouteRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/**
 * Выделение для «змейки» в редакторе: Ctrl+ЛКМ — первый угол, дальше под прицелом живое превью,
 * второй Ctrl+ЛКМ — построить. Ctrl+Z до второго угла — отменить выделение, после — откатить «змейку».
 */
public final class SnakeTool {
    private SnakeTool() {}

    private static int[] a;           // блок первого угла (x, y, z)
    private static int[] hoverB;
    private static List<RoutePoint> preview;

    public static void reset() {
        a = null; hoverB = null; preview = null;
        RouteRenderer.editorCornerA = null;
        RouteRenderer.editorCornerB = null;
        RouteRenderer.editorPreview = null;
    }

    public static boolean cancelIfPending() {
        if (a == null) return false;
        reset();
        return true;
    }

    private static int[] block(Vec3 top) {
        return new int[]{Mth.floor(top.x), Mth.floor(top.y - 0.01), Mth.floor(top.z)};
    }

    public static void corner(Minecraft mc, Vec3 at) {
        int[] b = block(at);
        if (a == null) {
            a = b;
            RouteRenderer.editorCornerA = new double[]{a[0] + 0.5, at.y, a[2] + 0.5};
            RouteEditor.msg(mc, "§6▣ Угол 1: " + a[0] + " " + a[2] + " §7· Ctrl+ЛКМ — второй угол, Ctrl+Z — отмена");
            return;
        }
        List<RoutePoint> pts = build(mc, a, b);
        if (pts.isEmpty()) { reset(); return; }
        ModConfig c = ModConfig.INSTANCE;
        RouteBuffer rb = RouteBuffer.INSTANCE;
        if (c.snakeAppend && !rb.isEmpty()) {
            java.util.ArrayList<RoutePoint> all = new java.util.ArrayList<>();
            for (RoutePoint p : rb.points()) all.add(p.copy());
            all.addAll(pts);
            if (all.size() > RouteBuffer.MAX_POINTS) { RouteEditor.msg(mc, "§cСлишком много точек (> " + RouteBuffer.MAX_POINTS + ")"); reset(); return; }
            rb.replaceAll(mc, all);
        } else {
            if (pts.size() > RouteBuffer.MAX_POINTS) { RouteEditor.msg(mc, "§cСлишком много точек (> " + RouteBuffer.MAX_POINTS + ")"); reset(); return; }
            rb.replaceAll(mc, pts);
        }
        int rows = (pts.size() + 1) / 2;
        RouteEditor.msg(mc, String.format(Locale.ROOT, "§a▣ «Змейка»: %d × %d бл, рядов %d, точек %d §7· Ctrl+Z — откатить",
                Math.abs(b[0] - a[0]) + 1, Math.abs(b[2] - a[2]) + 1, rows, pts.size()));
        reset();
    }

    private static List<RoutePoint> build(Minecraft mc, int[] a, int[] b) {
        ModConfig c = ModConfig.INSTANCE;
        Level level = mc.level;
        int hint = Math.max(a[1], b[1]) + 1;
        List<RoutePoint> pts = SnakeBuilder.build(a[0], a[2], b[0], b[2], c.snakeStep, c.snakeAxis, c.snakeRowAction, c.snakeTurnAction,
                (x, z) -> level == null ? hint : RouteEditor.topOf(level, new BlockPos(x, hint, z)).y);
        if (c.snakeOffsetX != 0 || c.snakeOffsetZ != 0) for (RoutePoint p : pts) p.setOffset(c.snakeOffsetX, c.snakeOffsetZ);
        return pts;
    }

    /** Живое превью до второго угла. */
    public static void tick(Minecraft mc) {
        if (a == null) return;
        Vec3 at = RouteEditor.targetPoint(mc);
        if (at == null) return;
        int[] b = block(at);
        if (hoverB != null && hoverB[0] == b[0] && hoverB[1] == b[1] && hoverB[2] == b[2]) return;
        hoverB = b;
        // огромные прямоугольники не считаем каждый тик
        long area = (long) (Math.abs(b[0] - a[0]) + 1) * (Math.abs(b[2] - a[2]) + 1);
        preview = area <= 512 * 512 ? build(mc, a, b) : null;
        RouteRenderer.editorCornerB = new double[]{b[0] + 0.5, at.y, b[2] + 0.5};
        RouteRenderer.editorPreview = preview;
    }

    /** Строка для HUD редактора. */
    public static String status() {
        if (a == null) return null;
        if (hoverB == null) return "▣ угол 1 выбран · Ctrl+ЛКМ — угол 2";
        int w = Math.abs(hoverB[0] - a[0]) + 1, h = Math.abs(hoverB[2] - a[2]) + 1;
        return "▣ " + w + "×" + h + (preview != null ? " · точек " + preview.size() : "") + " · Ctrl+ЛКМ — готово";
    }
}
