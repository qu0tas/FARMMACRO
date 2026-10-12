package com.farmmacro.hud;

import com.farmmacro.config.HudSlot;
import com.farmmacro.config.ModConfig;
import com.farmmacro.gui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * v1.14: редактор HUD. ЛКМ — тащить плашку (прилипает к краям и центру), колёсико — масштаб,
 * ПКМ — скрыть/показать, «Сброс» — всё обратно в стопку справа. Места хранятся в {@link ModConfig#hudSlots}.
 */
public class HudEditorScreen extends Screen {
    private final Screen parent;
    private List<FarmHud.Rect> rects = List.of();
    private String dragId;
    private double grabDx, grabDy;
    private Button mergedBtn;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("Редактор HUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int bw = 110, gap = 6, total = bw * 3 + gap * 2;
        int x = width / 2 - total / 2, y = height - 28;
        mergedBtn = addRenderableWidget(Button.builder(mergedText(), b -> {
            ModConfig.INSTANCE.hudMerged = !ModConfig.INSTANCE.hudMerged;
            b.setMessage(mergedText());
        }).bounds(x, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Сброс"), b -> ModConfig.INSTANCE.hudSlots.clear())
                .bounds(x + bw + gap, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Готово"), b -> onClose())
                .bounds(x + (bw + gap) * 2, y, bw, 20).build());
    }

    private static Component mergedText() {
        return Component.literal("Общая плашка: " + (ModConfig.INSTANCE.hudMerged ? "да" : "нет"));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        // без размытия: видно мир и сам HUD
        g.fill(0, 0, width, height, 0x30000000);
        int cx = width / 2, cy = height / 2;
        g.fill(cx, 0, cx + 1, height, 0x18FFFFFF);
        g.fill(0, cy, width, cy + 1, 0x18FFFFFF);
        if (minecraft == null || minecraft.player == null) return;
        FarmHud.prepare(minecraft);
        rects = FarmHud.layout(g, minecraft, true);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        FarmHud.Rect hover = hit(mx, my);
        for (FarmHud.Rect r : rects) {
            boolean on = hover != null && hover.id().equals(r.id()) || r.id().equals(dragId);
            int col = on ? 0xFFFFD24A : r.sub() ? 0x40FFFFFF : 0x90FFFFFF;
            outline(g, r.x(), r.y(), r.w(), r.h(), col);
        }
        var f = font;
        Ui.textCentered(g, f, "ЛКМ — тащить · колёсико — масштаб · ПКМ — скрыть/показать · Esc — готово", width / 2, 8, Ui.TEXT);
        if (hover != null) {
            HudSlot s = ModConfig.INSTANCE.hudSlots.get(scaleId(hover));
            String info = hover.label() + " · " + Math.round((s != null ? s.scale : 1) * 100) + "%" + (hover.hidden() ? " · скрыта" : "");
            Ui.textCentered(g, f, info, width / 2, 20, Ui.SUB);
        }
    }

    private static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int col) {
        g.fill(x - 1, y - 1, x + w + 1, y, col);
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, col);
        g.fill(x - 1, y, x, y + h, col);
        g.fill(x + w, y, x + w + 1, y + h, col);
    }

    /** Сначала строки общей плашки (они внутри неё), потом отдельные плашки, верхняя — последняя нарисованная. */
    private FarmHud.Rect hit(double mx, double my) {
        FarmHud.Rect best = null;
        for (FarmHud.Rect r : rects) {
            if (!r.contains(mx, my)) continue;
            if (best == null || r.sub() || !best.sub()) best = r;
        }
        return best;
    }

    /** В общей плашке тащим и масштабируем всю плашку. */
    private static String scaleId(FarmHud.Rect r) { return r.sub() ? "all" : r.id(); }

    private FarmHud.Rect rect(String id) {
        for (FarmHud.Rect r : rects) if (r.id().equals(id)) return r;
        return null;
    }

    private static HudSlot slot(String id) {
        return ModConfig.INSTANCE.hudSlots.computeIfAbsent(id, k -> new HudSlot());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        if (super.mouseClicked(e, doubled)) return true;
        FarmHud.Rect r = hit(e.x(), e.y());
        if (r == null) return false;
        if (e.button() == 1) {                                    // ПКМ — скрыть/показать
            if (r.id().equals("all")) return true;
            HudSlot s = slot(r.id());
            s.hidden = !s.hidden;
            return true;
        }
        if (e.button() != 0) return false;
        String id = scaleId(r);
        FarmHud.Rect whole = rect(id);
        if (whole == null) return false;
        HudSlot s = slot(id);
        if (!s.placed) HudLayout.store(s, whole.x(), whole.y(), whole.w(), whole.h(), width, height);
        dragId = id;
        grabDx = e.x() - whole.x();
        grabDy = e.y() - whole.y();
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragId == null) return super.mouseDragged(e, dx, dy);
        FarmHud.Rect r = rect(dragId);
        if (r == null) return true;
        double x = HudLayout.snap(e.x() - grabDx, r.w(), width);
        double y = HudLayout.snap(e.y() - grabDy, r.h(), height);
        HudLayout.store(slot(dragId), x, y, r.w(), r.h(), width, height);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (dragId != null && e.button() == 0) { dragId = null; return true; }
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        FarmHud.Rect r = hit(mx, my);
        if (r == null || sy == 0) return super.mouseScrolled(mx, my, sx, sy);
        String id = scaleId(r);
        FarmHud.Rect whole = rect(id);
        HudSlot s = slot(id);
        if (!s.placed && whole != null) HudLayout.store(s, whole.x(), whole.y(), whole.w(), whole.h(), width, height);
        double old = s.scale;
        s.scale = HudLayout.scaleStep(s.scale, sy);
        // держим центр плашки на месте
        if (whole != null && old != s.scale) {
            double k = s.scale / old;
            double nw = whole.w() * k, nh = whole.h() * k;
            double nx = whole.x() + whole.w() / 2.0 - nw / 2, ny = whole.y() + whole.h() / 2.0 - nh / 2;
            HudLayout.store(s, nx, ny, nw, nh, width, height);
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void removed() {
        ModConfig.INSTANCE.sanitize();
        ModConfig.save();
        super.removed();
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
