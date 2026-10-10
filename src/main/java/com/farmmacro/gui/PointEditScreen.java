package com.farmmacro.gui;

import com.farmmacro.route.RouteBuffer;
import com.farmmacro.route.RoutePoint;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Параметры точки маршрута (Shift+ПКМ по точке в редакторе): действие на отрезке до следующей точки,
 * зажим мыши, смещение в блоке, присесть, спринт, прыжок, слот, пауза, свой pitch. В стиле меню мода (Ui/Rows).
 * Одно открытие окна = одно действие для Ctrl+Z.
 */
public class PointEditScreen extends Screen implements Rows.Ctx {

    private static final List<Rows.Choice> ACTIONS = List.of(
            new Rows.Choice(RoutePoint.NONE, "Ничего"),
            new Rows.Choice(RoutePoint.ATTACK, "Держать ЛКМ"),
            new Rows.Choice(RoutePoint.USE, "Держать ПКМ"));

    private static final List<Rows.Choice> DROP_DIRS = List.of(
            new Rows.Choice(RoutePoint.DIR_AUTO, "По ходу"),
            new Rows.Choice("+x", "+X (восток)"), new Rows.Choice("-x", "−X (запад)"),
            new Rows.Choice("+z", "+Z (юг)"), new Rows.Choice("-z", "−Z (север)"));

    private final Screen parent;
    private final int index;
    private final List<Rows.Row> rows = new ArrayList<>();
    private double scroll;
    private boolean modified;
    private String tooltip;
    private boolean wantHand;

    public PointEditScreen(Screen parent, int index) {
        super(Component.literal("Точка маршрута"));
        this.parent = parent;
        this.index = index;
    }

    private RoutePoint pt() { return RouteBuffer.INSTANCE.get(index); }

    private int wW() { return Math.min(320, width - 16); }
    private int wH() { return Math.min(300, height - 16); }
    private int wX() { return (width - wW()) / 2; }
    private int wY() { return (height - wH()) / 2; }
    private int cX() { return wX() + 10; }
    private int cY() { return wY() + 30; }
    private int cW() { return wW() - 20; }
    private int cH() { return wH() - 38; }

    @Override
    protected void init() {
        if (rows.isEmpty()) {
            RouteBuffer.INSTANCE.snapshot();
            build();
        }
    }

    private void changed() { modified = true; RouteBuffer.INSTANCE.changed(); }

    /** Пересчитать глубину спуска по миру (после смены флага/направления/смещения). */
    private void dropChanged() {
        RoutePoint p = pt();
        if (p == null) return;
        if (p.drop && minecraft != null && minecraft.level != null)
            com.farmmacro.route.Terrain.fillDepth(new com.farmmacro.route.TerrainLevel(minecraft.level), RouteBuffer.INSTANCE.points(), index);
        else if (!p.drop) p.dropDepth = 0;
        changed();
    }

    /** Сколько выделенных точек, кроме этой. */
    private int others(List<Integer> sel) { int n = 0; for (int i : sel) if (i != index) n++; return n; }

    /** Смещение этой точки — точкам which (внутри уже открытого снимка окна, одно действие для Ctrl+Z). */
    private void applyOffset(List<Integer> which) {
        RoutePoint p = pt();
        if (p == null) return;
        for (int i : which) { RoutePoint q = RouteBuffer.INSTANCE.get(i); if (q != null) q.setOffset(p.ox, p.oz); }
        changed();
    }

    private void build() {
        rows.clear();
        rows.add(new Rows.Section("Отрезок до следующей точки"));
        rows.add(new Rows.Selector("Действие", "Что держать, пока идём к следующей точке", () -> ACTIONS,
                () -> pt() != null ? pt().action : RoutePoint.NONE, v -> { if (pt() != null) { pt().action = v; changed(); } }));
        rows.add(new Rows.Toggle("Присесть", null, () -> pt() != null && pt().sneak, v -> { if (pt() != null) { pt().sneak = v; changed(); } }));
        rows.add(new Rows.Toggle("Спринт", null, () -> pt() != null && pt().sprint, v -> { if (pt() != null) { pt().sprint = v; changed(); } }));
        rows.add(new Rows.Toggle("Прыжок", "Один прыжок в начале отрезка", () -> pt() != null && pt().jump,
                v -> { if (pt() != null) { pt().jump = v; changed(); } }));
        rows.add(new Rows.Toggle("Зажим мыши", "Если в настройках маршрута (⚙) «Зажим мыши: по точкам» — держать выбранную там кнопку на этом отрезке",
                () -> pt() != null && pt().hold, v -> { if (pt() != null) { pt().hold = v; changed(); } }));
        rows.add(new Rows.Number("Слот хотбара", "0 — не менять", () -> pt() != null ? pt().slot : 0,
                v -> { if (pt() != null) { pt().slot = (int) v; changed(); } }, 0, 9, 1, 1,
                v -> v == 0 ? "не менять" : String.valueOf((int) v)).integer());
        rows.add(new Rows.Section("Смещение в блоке"));
        rows.add(new Rows.Number("Смещение X", "−0.5…+0.5 от центра блока (+ — на восток)", () -> pt() != null ? pt().ox : 0,
                v -> { if (pt() != null) { pt().setOffset(v, pt().oz); changed(); } }, -0.5, 0.5, 0.05, 0.01,
                v -> (v > 0 ? "+" : "") + Rows.num(v)));
        rows.add(new Rows.Number("Смещение Z", "−0.5…+0.5 от центра блока (+ — на юг)", () -> pt() != null ? pt().oz : 0,
                v -> { if (pt() != null) { pt().setOffset(pt().ox, v); changed(); } }, -0.5, 0.5, 0.05, 0.01,
                v -> (v > 0 ? "+" : "") + Rows.num(v)));
        rows.add(new Rows.Buttons(
                new Rows.Btn("В центр", Rows.Style.SECONDARY, () -> {
                    if (pt() != null) { pt().setOffset(0, 0); changed(); }
                }),
                new Rows.Btn(() -> "Выделенным (" + others(RouteBuffer.INSTANCE.selection()) + ")", () -> Rows.Style.SECONDARY, () -> {
                    if (pt() != null) { applyOffset(RouteBuffer.INSTANCE.selection()); }
                }).enabledIf(() -> others(RouteBuffer.INSTANCE.selection()) > 0)
                        .tip("Это же смещение — всем выделенным точкам (Alt+ЛКМ, Ctrl+A в редакторе)"),
                new Rows.Btn("Всему маршруту", Rows.Style.SECONDARY, () -> {
                    if (pt() != null) applyOffset(RouteBuffer.INSTANCE.all());
                }).tip("Это же смещение — всем точкам маршрута")));
        rows.add(new Rows.Note("В редакторе: выбрать точку → стрелки сдвигают в блоке (Shift — мелкий шаг), Alt+ЛКМ — выделить ещё, Ctrl+A — все.", Ui.SUB));
        rows.add(new Rows.Section("Спуск на этаж ниже"));
        rows.add(new Rows.Toggle("Спуск", "Дойти до точки, шагнуть за край по направлению спуска, упасть, потом к следующей",
                () -> pt() != null && pt().drop, v -> { if (pt() != null) { pt().drop = v; dropChanged(); } }));
        rows.add(new Rows.Selector("Направление", "Куда шагнуть с точки, чтобы упасть", () -> DROP_DIRS,
                () -> pt() != null ? pt().dropDir : RoutePoint.DIR_AUTO,
                v -> { if (pt() != null) { pt().dropDir = v; dropChanged(); } }).enabledIf(() -> pt() != null && pt().drop));
        rows.add(new Rows.Number("Пауза после приземления", null, () -> pt() != null ? pt().landPauseTicks : 0,
                v -> { if (pt() != null) { pt().landPauseTicks = (int) v; changed(); } }, 0, 1200, 1, 5,
                v -> v == 0 ? "нет" : (int) v + " т · " + String.format(Locale.ROOT, "%.2f", v / 20) + " с").integer()
                .enabledIf(() -> pt() != null && pt().drop));
        rows.add(new Rows.Toggle("В полёте держать клавишу", "Выкл — отпустить, как только оторвался от края (падать почти вертикально)",
                () -> pt() != null && pt().airHold, v -> { if (pt() != null) { pt().airHold = v; changed(); } })
                .enabledIf(() -> pt() != null && pt().drop));
        rows.add(new Rows.Note(() -> {
            RoutePoint p = pt();
            if (p == null || !p.drop) return "Ctrl+D в редакторе — сделать точку спуском (или вставить спуск на краю).";
            return p.dropDepth > 0 ? String.format(Locale.ROOT, "Падение по миру: %.1f бл%s", p.dropDepth, p.dropDepth > 3.01 ? " — будет урон" : "")
                    : "Обрыв по направлению не найден — выбери направление или сдвинь точку к краю.";
        }, Ui.SUB));
        rows.add(new Rows.Section("В точке"));
        rows.add(new Rows.Number("Пауза", "Стоять перед следующим отрезком (действие и слот уже включены)",
                () -> pt() != null ? pt().pauseTicks : 0, v -> { if (pt() != null) { pt().pauseTicks = (int) v; changed(); } },
                0, 1200, 1, 20, v -> v == 0 ? "нет" : (int) v + " т · " + String.format(Locale.ROOT, "%.1f", v / 20) + " с").integer());
        rows.add(new Rows.Buttons(
                new Rows.Btn("На все следующие", Rows.Style.SECONDARY, () -> {
                    RoutePoint p = pt();
                    if (p == null) return;
                    for (int i = index + 1; i < RouteBuffer.INSTANCE.size(); i++) {
                        RoutePoint q = RouteBuffer.INSTANCE.get(i);
                        q.action = p.action; q.sneak = p.sneak; q.sprint = p.sprint; q.slot = p.slot; q.hold = p.hold;
                    }
                    changed();
                }).tip("Действие, присед, спринт, зажим и слот — всем точкам после этой")));
        rows.add(new Rows.Spacer(4));
        rows.add(new Rows.Buttons(
                new Rows.Btn("Удалить точку", Rows.Style.DANGER, () -> {
                    if (!modified) RouteBuffer.INSTANCE.undoDrop();
                    RouteBuffer.INSTANCE.remove(index);
                    modified = true;
                    minecraft.setScreen(parent);
                }),
                new Rows.Btn("Готово", Rows.Style.PRIMARY, this::onClose)));
    }

    // ── Отрисовка ────────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        tooltip = null; wantHand = false;
        if (pt() == null) { minecraft.setScreen(parent); return; }
        int x = wX(), y = wY(), w = wW(), h = wH();
        Ui.shadow(g, x, y, w, h, 8);
        Ui.round(g, x, y, w, h, 8, Ui.WINDOW);
        RoutePoint p = pt();
        Ui.text(g, font, "Точка " + (index + 1) + " из " + RouteBuffer.INSTANCE.size(), x + 10, y + 9, Ui.TEXT);
        Ui.textRight(g, font, String.format(Locale.ROOT, "%.1f  %.1f  %.1f", p.x, p.y, p.z), x + w - 10, y + 9, Ui.SUB);
        g.fill(x + 10, y + 22, x + w - 10, y + 23, Ui.BORDER);

        int cx = cX(), cy = cY(), cw = cW(), ch = cH();
        int total = 0;
        for (Rows.Row r : rows) total += r.h(this, cw);
        scroll = Math.max(0, Math.min(Math.max(0, total - ch), scroll));
        boolean in = Ui.inside(mx, my, cx, cy, cw, ch);
        g.enableScissor(cx - 2, cy, cx + cw + 2, cy + ch);
        int ry = cy - (int) scroll;
        for (Rows.Row r : rows) {
            int rh = r.h(this, cw);
            r.lastX = cx; r.lastY = ry; r.lastW = cw; r.lastH = rh;
            if (rh > 0 && ry + rh > cy && ry < cy + ch)
                r.render(this, g, cx, ry, cw, in ? mx : -1, in ? my : -1, in && my >= ry && my < ry + rh);
            ry += rh;
        }
        g.disableScissor();
        if (wantHand) g.requestCursor(CursorTypes.POINTING_HAND);
        if (tooltip != null) {
            g.nextStratum();
            var lines = font.split(Component.literal(tooltip), Math.min(220, width - 20));
            int tw = 0;
            for (var l : lines) tw = Math.max(tw, font.width(l));
            int tx = Math.min(mx + 10, width - tw - 14), ty = my + 12;
            if (ty + lines.size() * 10 + 8 > height - 4) ty = my - lines.size() * 10 - 10;
            Ui.roundBordered(g, tx, ty, tw + 12, lines.size() * 10 + 8, 4, 0xF00B0E14, Ui.BORDER);
            int ly = ty + 5;
            for (var l : lines) { g.text(font, l, tx + 6, ly, Ui.TEXT, false); ly += 10; }
        }
    }

    // ── Ввод ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        Rows.clearWheelFocus();
        double mx = e.x(), my = e.y();
        Rows.Row typing = Rows.focusedRow();
        if (typing != null && !(my >= typing.lastY && my < typing.lastY + typing.lastH)) Rows.blurText();
        if (Ui.inside(mx, my, cX(), cY(), cW(), cH())) {
            for (Rows.Row r : new ArrayList<>(rows)) {
                if (r.lastH > 0 && my >= r.lastY && my < r.lastY + r.lastH) {
                    if (r.click(this, mx, my, e.button())) return true;
                    break;
                }
            }
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        for (Rows.Row r : rows) {
            if (r.lastH > 0 && my >= r.lastY && my < r.lastY + r.lastH) {
                if (r.scroll(this, mx, my, sy)) return true;
                break;
            }
        }
        Rows.clearWheelFocus();
        Rows.blurText();
        scroll -= sy * 20;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (Rows.textFocused() && Rows.textKey(e)) return true;
        if (e.key() == 257 || e.key() == 335) { onClose(); return true; }   // Enter
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent e) {
        if (Rows.textChar(e)) return true;
        return super.charTyped(e);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        Rows.clearWheelFocus();
        Rows.blurText();
        if (!modified) RouteBuffer.INSTANCE.undoDrop();
        minecraft.setScreen(parent);
    }

    // ── Rows.Ctx ─────────────────────────────────────────────────────────────
    @Override public Font font() { return font; }
    @Override public void clickSound() { minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f)); }
    @Override public void tooltip(String text) { tooltip = text; }
    @Override public void hand() { wantHand = true; }
    @Override public boolean shift() { return minecraft.hasShiftDown(); }
}
