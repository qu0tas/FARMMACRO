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
 * присесть, спринт, прыжок, слот, пауза, свой pitch. В стиле меню мода (Ui/Rows).
 * Одно открытие окна = одно действие для Ctrl+Z.
 */
public class PointEditScreen extends Screen implements Rows.Ctx {

    private static final List<Rows.Choice> ACTIONS = List.of(
            new Rows.Choice(RoutePoint.NONE, "Ничего"),
            new Rows.Choice(RoutePoint.ATTACK, "Держать ЛКМ"),
            new Rows.Choice(RoutePoint.USE, "Держать ПКМ"));

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

    private void build() {
        rows.clear();
        rows.add(new Rows.Section("Отрезок до следующей точки"));
        rows.add(new Rows.Selector("Действие", "Что держать, пока идём к следующей точке", () -> ACTIONS,
                () -> pt() != null ? pt().action : RoutePoint.NONE, v -> { if (pt() != null) { pt().action = v; changed(); } }));
        rows.add(new Rows.Toggle("Присесть", null, () -> pt() != null && pt().sneak, v -> { if (pt() != null) { pt().sneak = v; changed(); } }));
        rows.add(new Rows.Toggle("Спринт", null, () -> pt() != null && pt().sprint, v -> { if (pt() != null) { pt().sprint = v; changed(); } }));
        rows.add(new Rows.Toggle("Прыжок", "Один прыжок в начале отрезка", () -> pt() != null && pt().jump,
                v -> { if (pt() != null) { pt().jump = v; changed(); } }));
        rows.add(new Rows.Number("Слот хотбара", "0 — не менять", () -> pt() != null ? pt().slot : 0,
                v -> { if (pt() != null) { pt().slot = (int) v; changed(); } }, 0, 9, 1, 1,
                v -> v == 0 ? "не менять" : String.valueOf((int) v)));
        rows.add(new Rows.Section("В точке"));
        rows.add(new Rows.Number("Пауза", "Стоять перед следующим отрезком (действие и слот уже включены)",
                () -> pt() != null ? pt().pauseTicks : 0, v -> { if (pt() != null) { pt().pauseTicks = (int) v; changed(); } },
                0, 1200, 1, 20, v -> v == 0 ? "нет" : (int) v + " т · " + String.format(Locale.ROOT, "%.1f", v / 20) + " с"));
        rows.add(new Rows.Toggle("Свой pitch", "Иначе — pitch маршрута", () -> pt() != null && pt().pitch != null,
                v -> { if (pt() != null) { pt().pitch = v ? RouteBuffer.INSTANCE.route().pitch : null; changed(); } }));
        rows.add(new Rows.Number("Pitch отрезка", "−90 вверх, 90 вниз", () -> pt() != null && pt().pitch != null ? pt().pitch : 0,
                v -> { if (pt() != null) { pt().pitch = (float) v; changed(); } }, -90, 90, 0.5, 5,
                v -> String.format(Locale.ROOT, "%.1f°", v)).enabledIf(() -> pt() != null && pt().pitch != null));
        rows.add(new Rows.Number("Pitch маршрута", "Для всех точек без своего pitch", () -> RouteBuffer.INSTANCE.route().pitch,
                v -> { RouteBuffer.INSTANCE.setPitch((float) v); modified = true; }, -90, 90, 0.5, 5,
                v -> String.format(Locale.ROOT, "%.1f°", v)));
        rows.add(new Rows.Buttons(
                new Rows.Btn("Взять pitch камеры", Rows.Style.SECONDARY, () -> {
                    if (pt() != null && minecraft.player != null) { pt().pitch = minecraft.player.getXRot(); changed(); }
                }).tip("Свой pitch отрезка = куда ты сейчас смотришь по вертикали"),
                new Rows.Btn("На все следующие", Rows.Style.SECONDARY, () -> {
                    RoutePoint p = pt();
                    if (p == null) return;
                    for (int i = index + 1; i < RouteBuffer.INSTANCE.size(); i++) {
                        RoutePoint q = RouteBuffer.INSTANCE.get(i);
                        q.action = p.action; q.sneak = p.sneak; q.sprint = p.sprint; q.slot = p.slot; q.pitch = p.pitch;
                    }
                    changed();
                }).tip("Действие, присед, спринт, слот и pitch — всем точкам после этой")));
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
        for (Rows.Row r : rows) total += r.height(this, cw);
        scroll = Math.max(0, Math.min(Math.max(0, total - ch), scroll));
        boolean in = Ui.inside(mx, my, cx, cy, cw, ch);
        g.enableScissor(cx - 2, cy, cx + cw + 2, cy + ch);
        int ry = cy - (int) scroll;
        for (Rows.Row r : rows) {
            int rh = r.height(this, cw);
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
        scroll -= sy * 20;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == 257 || e.key() == 335) { onClose(); return true; }   // Enter
        return super.keyPressed(e);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        Rows.clearWheelFocus();
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
