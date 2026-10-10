package com.farmmacro.route;

import com.farmmacro.FarmMacroMod;
import com.farmmacro.config.ModConfig;
import com.farmmacro.gui.PointEditScreen;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.visual.RouteRenderer;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * Редактор маршрута в мире. Пока включён, ЛКМ/ПКМ не уходят в мир (миксин {@code EditorInputMixin}).
 *
 *  ЛКМ по блоку — точка в центре верхней грани; ЛКМ по точке — выбрать, зажать и вести — передвинуть;
 *  Shift+ЛКМ — вставить в ближайший отрезок; ПКМ по точке — удалить; Shift+ПКМ по точке — параметры;
 *  Ctrl+ЛКМ — угол выделения «змейки» (два угла → маршрут по рядам); Ctrl+Z — отменить;
 *  Ctrl+D — «Спуск» у выбранной/наведённой точки (если следующая ниже и по линии есть обрыв — вставить спуск на краю).
 */
public final class RouteEditor {
    private RouteEditor() {}

    private static boolean active;
    private static int hover = -1;
    private static int dragIndex = -1;
    private static boolean dragMoved;
    private static boolean undoWasDown, selAllWasDown, dropWasDown;
    /** Стрелки: какая была нажата в прошлом тике, сколько тиков держится, когда был последний сдвиг (для Ctrl+Z одним шагом). */
    private static int arrowHeld = -1, arrowTicks;
    private static long lastNudgeMs;
    private static List<Integer> lastNudgeSel = List.of();

    public static boolean isActive() { return active; }
    public static int hovered()      { return hover; }

    public static void toggle(Minecraft mc) {
        if (active) { setActive(mc, false); return; }
        MacroManager m = MacroManager.INSTANCE;
        if (m.getState() != MacroManager.State.IDLE) { msg(mc, "§cСначала останови запись/воспроизведение"); return; }
        setActive(mc, true);
    }

    public static void setActive(Minecraft mc, boolean on) {
        if (active == on) return;
        active = on;
        hover = -1; dragIndex = -1;
        SnakeTool.reset();
        RouteRenderer.editorActive = on;
        RouteRenderer.editorHover = -1;
        if (on) {
            MacroManager.INSTANCE.useRouteSource();
            msg(mc, "§d✎ Редактор маршрута: §7ЛКМ — точка, ПКМ — удалить, Shift+ПКМ — параметры, Ctrl+ЛКМ — «змейка», Ctrl+D — спуск, "
                    + MacroManager.keyName(FarmMacroMod.keyEditor) + " — выход");
        } else {
            RouteBuffer.INSTANCE.select(-1);         // подсветка выбранной точки — только в редакторе
            msg(mc, "§7Редактор закрыт: " + RouteBuffer.INSTANCE.size() + " точек"
                    + (RouteBuffer.INSTANCE.isDirty() ? " §e(не сохранено — меню → Макросы → Маршруты)" : ""));
        }
    }

    // ── Ввод (из миксина, главный поток, только без открытого окна) ─────────

    public static void onLeftClick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        boolean ctrl = mc.hasControlDown(), shift = mc.hasShiftDown();
        if (mc.hasAltDown()) {                   // Alt+ЛКМ — добавить/убрать из выделения
            int h = pickPoint(mc);
            if (h < 0) { msg(mc, "§7Alt+ЛКМ — по точке, чтобы выделить несколько"); return; }
            RouteBuffer.INSTANCE.toggleMark(h);
            msg(mc, "§7Выделено точек: " + RouteBuffer.INSTANCE.selection().size() + " · стрелки — сдвиг в блоке");
            return;
        }
        if (ctrl) {                              // угол выделения
            Vec3 at = targetPoint(mc);
            if (at == null) { msg(mc, "§7Наведи прицел на блок"); return; }
            SnakeTool.corner(mc, at);
            return;
        }
        int h = pickPoint(mc);
        if (h >= 0 && !shift) {
            RouteBuffer.INSTANCE.select(h);
            dragIndex = h;
            dragMoved = false;
            return;
        }
        Vec3 at = targetPoint(mc);
        if (at == null) { msg(mc, "§7Наведи прицел на блок (до " + (int) ModConfig.INSTANCE.routeEditReach + " бл)"); return; }
        if (!checkDimension(mc)) return;
        int idx = shift ? RouteBuffer.INSTANCE.insertNearest(mc, at.x, at.y, at.z)
                        : RouteBuffer.INSTANCE.add(mc, at.x, at.y, at.z);
        if (idx < 0) msg(mc, "§cНе больше " + RouteBuffer.MAX_POINTS + " точек");
        else if (shift) msg(mc, "§aВставлена точка " + (idx + 1));
    }

    public static void onRightClick(Minecraft mc) {
        int h = pickPoint(mc);
        if (h < 0) { RouteBuffer.INSTANCE.select(-1); return; }
        if (mc.hasShiftDown()) {
            RouteBuffer.INSTANCE.select(h);
            mc.setScreen(new PointEditScreen(null, h));
            return;
        }
        RouteBuffer.INSTANCE.remove(h);
        msg(mc, "§7Точка " + (h + 1) + " удалена · Ctrl+Z — вернуть");
    }

    private static boolean checkDimension(Minecraft mc) {
        String dim = RouteBuffer.INSTANCE.route().dimension;
        if (dim != null && !RouteBuffer.INSTANCE.isEmpty() && !dim.equals(RouteBuffer.dimensionId(mc))) {
            msg(mc, "§cМаршрут построен в другом измерении (" + dim + ")");
            return false;
        }
        return true;
    }

    // ── Тик ──────────────────────────────────────────────────────────────────

    public static void tick(Minecraft mc) {
        if (!active) return;
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            active = false; RouteRenderer.editorActive = false; RouteRenderer.editorHover = -1;
            SnakeTool.reset(); RouteBuffer.INSTANCE.select(-1);
            return;
        }
        if (MacroManager.INSTANCE.getState() != MacroManager.State.IDLE) { setActive(mc, false); return; }

        // перетаскивание
        if (dragIndex >= 0) {
            if (mc.screen != null || !mc.options.keyAttack.isDown() || RouteBuffer.INSTANCE.get(dragIndex) == null) {
                dragIndex = -1;
            } else {
                Vec3 at = targetPoint(mc);
                RoutePoint q = RouteBuffer.INSTANCE.get(dragIndex);
                if (at != null && (Math.abs(at.x - q.centerX()) > 1e-3 || Math.abs(at.z - q.centerZ()) > 1e-3 || Math.abs(at.y - q.y) > 1e-3)) {
                    if (!dragMoved) { RouteBuffer.INSTANCE.snapshot(); dragMoved = true; }
                    RouteBuffer.INSTANCE.moveNoUndo(dragIndex, at.x, at.y, at.z);
                }
            }
        }

        // Ctrl+Z
        boolean z = mc.screen == null && mc.hasControlDown()
                && InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_Z);
        if (z && !undoWasDown) {
            if (SnakeTool.cancelIfPending()) msg(mc, "§7Выделение отменено");
            else msg(mc, RouteBuffer.INSTANCE.undo() ? "§7Отменено · точек: " + RouteBuffer.INSTANCE.size() : "§7Нечего отменять");
        }
        undoWasDown = z;

        // Ctrl+A — выделить все / снять выделение
        boolean selAll = mc.screen == null && mc.hasControlDown()
                && InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_A);
        if (selAll && !selAllWasDown) {
            RouteBuffer rb = RouteBuffer.INSTANCE;
            if (rb.markedCount() >= rb.size() && rb.size() > 0) { rb.select(-1); msg(mc, "§7Выделение снято"); }
            else { rb.markAll(); msg(mc, "§7Выделены все точки: " + rb.size() + " · стрелки — сдвиг в блоке"); }
        }
        selAllWasDown = selAll;

        // Ctrl+D — спуск
        boolean dk = mc.screen == null && mc.hasControlDown()
                && InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_D);
        if (dk && !dropWasDown) toggleDrop(mc);
        dropWasDown = dk;

        tickArrows(mc);

        hover = dragIndex >= 0 ? dragIndex : pickPoint(mc);
        RouteRenderer.editorHover = hover;
        SnakeTool.tick(mc);
    }

    // ── Спуск ────────────────────────────────────────────────────────────────

    /**
     * Ctrl+D по выбранной (или наведённой) точке i:
     *  • уже спуск — снять;
     *  • следующая точка ниже, а по линии отрезка есть обрыв — вставить точку-спуск на последнем блоке перед краем;
     *  • иначе — сделать точку i спуском «по ходу» и посчитать глубину по миру.
     */
    private static void toggleDrop(Minecraft mc) {
        RouteBuffer rb = RouteBuffer.INSTANCE;
        int i = rb.selected() >= 0 ? rb.selected() : hover;
        RoutePoint p = rb.get(i);
        if (p == null) { msg(mc, "§7Ctrl+D — по выбранной точке (ЛКМ по точке)"); return; }
        if (p.drop) {
            rb.snapshot();
            p.drop = false; p.dropDepth = 0;
            rb.changed();
            msg(mc, "§7Точка " + (i + 1) + ": спуск снят · Ctrl+Z — вернуть");
            return;
        }
        if (mc.level == null) return;
        TerrainLevel w = new TerrainLevel(mc.level);
        RoutePoint next = rb.get(i + 1);
        if (next != null && p.y - next.y > Terrain.FLOOR_GAP) {
            double[] e = Terrain.edgeOnSegment(w, rb.points(), i);
            if (e != null && Math.hypot(e[0] - p.centerX(), e[2] - p.centerZ()) > 0.3) {
                RoutePoint q = p.withPos(e[0], e[1], e[2]);
                q.pauseTicks = 0; q.jump = false;
                q.drop = true; q.dropDir = RoutePoint.DIR_AUTO;
                int idx = rb.insertAt(i + 1, q);
                if (idx < 0) { msg(mc, "§cНе больше " + RouteBuffer.MAX_POINTS + " точек"); return; }
                double depth = Terrain.fillDepth(w, rb.points(), idx);
                rb.changed();
                msg(mc, "§b↓ Вставлен спуск: точка " + (idx + 1) + depthText(depth) + " §7· Ctrl+Z — отменить");
                return;
            }
        }
        rb.snapshot();
        p.drop = true;
        double depth = Terrain.fillDepth(w, rb.points(), i);
        rb.changed();
        msg(mc, "§b↓ Точка " + (i + 1) + ": спуск" + depthText(depth)
                + (Double.isNaN(depth) ? " §e· по ходу обрыва не видно — Shift+ПКМ → направление спуска" : "") + " §7· Ctrl+Z — отменить");
    }

    static String depthText(double depth) {
        return Double.isNaN(depth) ? "" : String.format(java.util.Locale.ROOT, ", вниз %.1f бл%s", depth, depth > 3.01 ? " §c(урон!)§b" : "");
    }

    // ── Смещение в блоке стрелками ───────────────────────────────────────────

    private static final int[] ARROWS = {InputConstants.KEY_UP, InputConstants.KEY_DOWN, InputConstants.KEY_LEFT, InputConstants.KEY_RIGHT};

    /**
     * Стрелки сдвигают выделенные точки внутри блока: ↑/↓ — вперёд/назад, ←/→ — влево/вправо относительно взгляда
     * (по ближайшей оси X/Z). Шаг — routeOffsetStep, с Shift — routeOffsetFineStep. Держать — повтор каждые 2 тика.
     * Серия нажатий подряд (пауза < 1 с, то же выделение) — один шаг Ctrl+Z.
     */
    private static void tickArrows(Minecraft mc) {
        int key = -1;
        if (mc.screen == null && !mc.hasControlDown() && !mc.hasAltDown())
            for (int k : ARROWS) if (InputConstants.isKeyDown(mc.getWindow(), k)) { key = k; break; }
        if (key < 0) { arrowHeld = -1; arrowTicks = 0; return; }
        boolean fire = key != arrowHeld || (arrowTicks >= 7 && arrowTicks % 2 == 1);
        arrowTicks = key == arrowHeld ? arrowTicks + 1 : 0;
        arrowHeld = key;
        if (!fire) return;
        RouteBuffer rb = RouteBuffer.INSTANCE;
        List<Integer> sel = rb.selection();
        if (sel.isEmpty()) { msg(mc, "§7Выбери точку (ЛКМ), Alt+ЛКМ — ещё, Ctrl+A — все; стрелки сдвигают в блоке"); return; }
        ModConfig c = ModConfig.INSTANCE;
        double step = mc.hasShiftDown() ? c.routeOffsetFineStep : c.routeOffsetStep;
        int dir = key == InputConstants.KEY_UP ? RoutePoint.FORWARD : key == InputConstants.KEY_DOWN ? RoutePoint.BACK
                : key == InputConstants.KEY_RIGHT ? RoutePoint.RIGHT : RoutePoint.LEFT;
        double[] d = RoutePoint.axisStep(mc.player.getYRot(), dir);
        long now = System.currentTimeMillis();
        boolean snap = now - lastNudgeMs > 1000 || !sel.equals(lastNudgeSel);
        rb.nudge(sel, d[0] * step, d[1] * step, snap);
        lastNudgeMs = now;
        lastNudgeSel = sel;
        RoutePoint p = rb.get(sel.get(0));
        msg(mc, String.format(java.util.Locale.ROOT, "§bСмещение%s: X %+.3f  Z %+.3f §7(шаг %s%s) · Ctrl+Z — отменить",
                sel.size() == 1 ? " точки " + (sel.get(0) + 1) : " " + sel.size() + " точек (у первой)", p.ox, p.oz,
                com.farmmacro.gui.Rows.num(step), mc.hasShiftDown() ? ", мелкий" : ""));
    }

    // ── Прицел ───────────────────────────────────────────────────────────────

    /** Точка под прицелом: ближайшая к лучу взгляда (допуск 0.45 бл), не дальше досягаемости редактора. */
    public static int pickPoint(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return -1;
        Vec3 eye = p.getEyePosition(1f), dir = p.getViewVector(1f);
        double reach = ModConfig.INSTANCE.routeEditReach;
        // блок перекрывает точки за ним
        HitResult hit = p.pick(reach, 1f, false);
        double limit = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().distanceTo(eye) + 0.6 : reach;
        List<RoutePoint> pts = RouteBuffer.INSTANCE.points();
        int best = -1; double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < pts.size(); i++) {
            RoutePoint q = pts.get(i);
            double wx = q.x - eye.x, wy = q.y + 0.12 - eye.y, wz = q.z - eye.z;
            double t = wx * dir.x + wy * dir.y + wz * dir.z;
            if (t < 0 || t > limit) continue;
            double dx = wx - dir.x * t, dy = wy - dir.y * t, dz = wz - dir.z * t;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double tol = 0.3 + t * 0.012;           // дальние точки чуть легче поймать
            if (d > tol) continue;
            double score = d / tol + t * 0.002;
            if (score < bestScore) { bestScore = score; best = i; }
        }
        return best;
    }

    /** Центр верхней грани блока под прицелом (растения и снег — по блоку под ними). null — нет блока. */
    public static Vec3 targetPoint(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return null;
        HitResult hit = p.pick(ModConfig.INSTANCE.routeEditReach, 1f, false);
        if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK) return null;
        return topOf(mc.level, bh.getBlockPos());
    }

    /** Центр верхней грани: если у блока нет коллизии (пшеница, трава), берём первый твёрдый ниже (до 3). */
    public static Vec3 topOf(Level level, BlockPos pos) {
        BlockPos cur = pos;
        for (int i = 0; i < 4; i++) {
            BlockState st = level.getBlockState(cur);
            VoxelShape sh = st.getCollisionShape(level, cur);
            if (!sh.isEmpty()) return new Vec3(cur.getX() + 0.5, cur.getY() + Math.min(1.5, sh.max(Direction.Axis.Y)), cur.getZ() + 0.5);
            cur = cur.below();
        }
        return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    static void msg(Minecraft mc, String text) {
        if (mc.player != null) mc.player.sendOverlayMessage(Component.literal("§8[§cFM§8] §r" + text));
    }
}
