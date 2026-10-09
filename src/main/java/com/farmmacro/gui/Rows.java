package com.farmmacro.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Строки содержимого вкладок (немедленный режим: значения читаются из конфига при каждой отрисовке,
 * поэтому переключатели всегда показывают актуальное состояние без пересоздания экрана).
 */
public final class Rows {
    private Rows() {}

    /** Что строкам нужно от экрана. */
    public interface Ctx {
        Font font();
        void clickSound();
        void tooltip(String text);
        void hand();
        boolean shift();
    }

    /**
     * Строка, которой разрешено менять значение колёсиком. Колёсико действует только после клика
     * по полю: иначе прокрутка списка, проходя курсором над числом, незаметно меняла пороги (v1.2.1).
     */
    static Row wheelFocus;

    /** Сбросить фокус колёсика (клик мимо поля, смена вкладки, закрытие меню). */
    public static void clearWheelFocus() { wheelFocus = null; }

    public abstract static class Row {
        /** Позиция с последней отрисовки (для кликов). */
        int lastX, lastY, lastW, lastH;
        BooleanSupplier enabled = () -> true;

        public Row enabledIf(BooleanSupplier s) { this.enabled = s; return this; }
        boolean on() { return enabled.getAsBoolean(); }

        abstract int height(Ctx c, int w);
        abstract void render(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover);
        boolean click(Ctx c, double mx, double my, int button) { return false; }
        boolean scroll(Ctx c, double mx, double my, double amount) { return false; }
    }

    // ── Заголовок раздела ────────────────────────────────────────────────────

    public static final class Section extends Row {
        private final String title;
        public Section(String title) { this.title = title; }
        int height(Ctx c, int w) { return 20; }
        void render(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            String t = title.toUpperCase();
            Ui.text(g, c.font(), t, x + 2, y + 9, Ui.ACCENT_HI);
            int lx = x + 8 + c.font().width(t);
            g.fill(lx, y + 13, x + w, y + 14, Ui.BORDER);
        }
    }

    // ── Базовая «карточка» с подписью и подсказкой ───────────────────────────

    abstract static class Labeled extends Row {
        final String label, hint;
        Labeled(String label, String hint) { this.label = label; this.hint = hint; }

        int height(Ctx c, int w) { return hint == null ? 22 : 30; }

        /** Ширина элемента управления справа. */
        abstract int controlWidth(Ctx c);

        void drawCard(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hover, boolean clickable) {
            Ui.round(g, x, y, w, h - 3, 4, hover && clickable && on() ? Ui.CARD_HOVER : Ui.CARD);
            int textW = w - controlWidth(c) - 20;
            int col = on() ? Ui.TEXT : Ui.DIM;
            Font f = c.font();
            if (hint == null) {
                Ui.text(g, f, Ui.ellipsize(f, label, textW), x + 8, y + (h - 3 - 8) / 2, col);
            } else {
                Ui.text(g, f, Ui.ellipsize(f, label, textW), x + 8, y + 5, col);
                String hs = Ui.ellipsize(f, hint, textW);
                Ui.text(g, f, hs, x + 8, y + 16, on() ? Ui.SUB : Ui.DIM);
                if (hover && !hs.equals(hint)) c.tooltip(hint);
            }
        }
    }

    // ── Переключатель ────────────────────────────────────────────────────────

    public static final class Toggle extends Labeled {
        private final BooleanSupplier get;
        private final Consumer<Boolean> set;
        private float anim = -1;
        private long lastNs;

        public Toggle(String label, String hint, BooleanSupplier get, Consumer<Boolean> set) {
            super(label, hint); this.get = get; this.set = set;
        }

        int controlWidth(Ctx c) { return 24; }

        void render(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            int h = height(c, w);
            drawCard(c, g, x, y, w, h, hover, true);
            boolean v = get.getAsBoolean();
            long now = System.nanoTime();
            float target = v ? 1 : 0;
            if (anim < 0) anim = target;
            float dt = lastNs == 0 ? 0 : (now - lastNs) / 1e9f;
            lastNs = now;
            anim += (target - anim) * Math.min(1, dt * 14);
            if (Math.abs(target - anim) < 0.01f) anim = target;

            int tw = 22, th = 12;
            int tx = x + w - tw - 8, ty = y + (h - 3 - th) / 2;
            int track = Ui.lerp(Ui.TRACK_OFF, Ui.ON, anim);
            if (!on()) track = Ui.alpha(track, 0.45f);
            Ui.pill(g, tx, ty, tw, th, track);
            int kx = tx + 6 + Math.round(anim * (tw - 12));
            Ui.circle(g, kx, ty + 6, 4, on() ? 0xFFFFFFFF : 0xFF9AA0AE);
            if (hover && on()) c.hand();
        }

        boolean click(Ctx c, double mx, double my, int button) {
            if (!on() || button != 0) return false;
            set.accept(!get.getAsBoolean());
            c.clickSound();
            return true;
        }
    }

    // ── Числовое поле со стрелками (клик, колёсико; Shift — крупный шаг) ─────

    public static final class Number extends Labeled {
        private final DoubleSupplier get;
        private final DoubleConsumer set;
        private final double min, max, step, bigStep;
        private final DoubleFunction<String> fmt;

        public Number(String label, String hint, DoubleSupplier get, DoubleConsumer set,
                      double min, double max, double step, double bigStep, DoubleFunction<String> fmt) {
            super(label, hint);
            this.get = get; this.set = set; this.min = min; this.max = max;
            this.step = step; this.bigStep = bigStep; this.fmt = fmt;
        }

        int controlWidth(Ctx c) { return 92; }

        void render(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            int h = height(c, w);
            drawCard(c, g, x, y, w, h, hover, false);
            int cw = controlWidth(c);
            int cx = x + w - cw - 6, cy = y + (h - 3 - 14) / 2;
            boolean en = on();
            double v = get.getAsDouble();
            // поле (рамка — колёсико сейчас меняет это значение)
            if (en && wheelFocus == this) Ui.round(g, cx - 1, cy - 1, cw + 2, 16, 5, Ui.ACCENT);
            Ui.round(g, cx, cy, cw, 14, 4, Ui.FIELD);
            boolean hm = en && Ui.inside(mx, my, cx, cy, 16, 14) && v > min;
            boolean hp = en && Ui.inside(mx, my, cx + cw - 16, cy, 16, 14) && v < max;
            Ui.round(g, cx, cy, 16, 14, 4, hm ? Ui.ACCENT : Ui.BORDER);
            Ui.round(g, cx + cw - 16, cy, 16, 14, 4, hp ? Ui.ACCENT : Ui.BORDER);
            int arrowCol = en ? Ui.TEXT : Ui.DIM;
            Ui.textCentered(g, c.font(), "−", cx + 8, cy + 3, v > min ? arrowCol : Ui.DIM);
            Ui.textCentered(g, c.font(), "+", cx + cw - 8, cy + 3, v < max ? arrowCol : Ui.DIM);
            String s = Ui.ellipsize(c.font(), fmt.apply(v), cw - 36);
            Ui.textCentered(g, c.font(), s, cx + cw / 2, cy + 3, en ? Ui.TEXT : Ui.DIM);
            if (hm || hp) c.hand();
            if (hover && en && Ui.inside(mx, my, cx + 16, cy, cw - 32, 14))
                c.tooltip(wheelFocus == this ? "Колёсико — изменить, Shift — шаг ×" + trim(bigStep / step)
                        : "Кликни по полю, чтобы менять колёсиком");
        }

        private static String trim(double d) {
            return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
        }

        private void change(Ctx c, int dir) {
            double s = c.shift() ? bigStep : step;
            double v = get.getAsDouble() + dir * s;
            v = Math.round(v / step) * step;                      // убираем хвосты float
            v = Math.max(min, Math.min(max, v));
            set.accept(v);
        }

        boolean click(Ctx c, double mx, double my, int button) {
            if (!on() || button != 0) return false;
            int h = height(c, lastW), cw = controlWidth(c);
            int cx = lastX + lastW - cw - 6, cy = lastY + (h - 3 - 14) / 2;
            if (!Ui.inside(mx, my, cx, cy, cw, 14)) return false;
            wheelFocus = this;
            if (Ui.inside(mx, my, cx, cy, 16, 14)) { change(c, -1); c.clickSound(); return true; }
            if (Ui.inside(mx, my, cx + cw - 16, cy, 16, 14)) { change(c, +1); c.clickSound(); return true; }
            return true;   // клик по середине — только фокус для колёсика
        }

        boolean scroll(Ctx c, double mx, double my, double amount) {
            if (!on() || wheelFocus != this) return false;
            int h = height(c, lastW), cw = controlWidth(c);
            int cx = lastX + lastW - cw - 6, cy = lastY + (h - 3 - 14) / 2;
            if (!Ui.inside(mx, my, cx, cy, cw, 14)) return false;
            change(c, amount > 0 ? 1 : -1);
            return true;
        }
    }

    // ── Выбор из списка «‹ значение ›» ───────────────────────────────────────

    public record Choice(String id, String label) {}

    public static final class Selector extends Labeled {
        private final Supplier<List<Choice>> options;
        private final Supplier<String> get;
        private final Consumer<String> set;

        public Selector(String label, String hint, Supplier<List<Choice>> options, Supplier<String> get, Consumer<String> set) {
            super(label, hint); this.options = options; this.get = get; this.set = set;
        }

        int controlWidth(Ctx c) { return Math.min(150, Math.max(110, lastW / 2 - 10)); }

        private String currentLabel() {
            String id = get.get();
            for (Choice ch : options.get()) if (ch.id().equals(id)) return ch.label();
            return id;
        }

        void render(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            int h = height(c, w);
            drawCard(c, g, x, y, w, h, hover, false);
            int cw = controlWidth(c);
            int cx = x + w - cw - 6, cy = y + (h - 3 - 14) / 2;
            boolean en = on();
            boolean hl = en && Ui.inside(mx, my, cx, cy, cw / 2, 14);
            boolean hr = en && Ui.inside(mx, my, cx + cw / 2, cy, cw - cw / 2, 14);
            if (en && wheelFocus == this) Ui.round(g, cx - 1, cy - 1, cw + 2, 16, 5, Ui.ACCENT);
            Ui.round(g, cx, cy, cw, 14, 4, (hl || hr) ? Ui.CARD_HOVER : Ui.FIELD);
            Ui.text(g, c.font(), "‹", cx + 5, cy + 3, hl ? Ui.ACCENT_HI : Ui.SUB);
            Ui.textRight(g, c.font(), "›", cx + cw - 5, cy + 3, hr ? Ui.ACCENT_HI : Ui.SUB);
            String s = Ui.ellipsize(c.font(), currentLabel(), cw - 24);
            Ui.textCentered(g, c.font(), s, cx + cw / 2, cy + 3, en ? Ui.TEXT : Ui.DIM);
            if (hl || hr) c.hand();
        }

        private void cycle(int dir) {
            List<Choice> list = options.get();
            if (list.isEmpty()) return;
            int idx = 0;
            for (int i = 0; i < list.size(); i++) if (list.get(i).id().equals(get.get())) idx = i;
            idx = Math.floorMod(idx + dir, list.size());
            set.accept(list.get(idx).id());
        }

        boolean click(Ctx c, double mx, double my, int button) {
            if (!on()) return false;
            int h = height(c, lastW), cw = controlWidth(c);
            int cx = lastX + lastW - cw - 6, cy = lastY + (h - 3 - 14) / 2;
            if (!Ui.inside(mx, my, cx, cy, cw, 14)) return false;
            wheelFocus = this;
            int dir = button == 1 ? -1 : (mx < cx + cw / 2.0 ? -1 : 1);
            cycle(dir);
            c.clickSound();
            return true;
        }

        boolean scroll(Ctx c, double mx, double my, double amount) {
            if (!on() || wheelFocus != this) return false;
            int h = height(c, lastW), cw = controlWidth(c);
            int cx = lastX + lastW - cw - 6, cy = lastY + (h - 3 - 14) / 2;
            if (!Ui.inside(mx, my, cx, cy, cw, 14)) return false;
            cycle(amount > 0 ? -1 : 1);
            return true;
        }
    }

    // ── Ряд кнопок ───────────────────────────────────────────────────────────

    public enum Style { PRIMARY, SECONDARY, DANGER, SUCCESS }

    public static final class Btn {
        final Supplier<String> label;
        final Supplier<Style> style;
        final Runnable action;
        BooleanSupplier enabled = () -> true;
        String tip;

        public Btn(String label, Style style, Runnable action) { this(() -> label, () -> style, action); }
        public Btn(Supplier<String> label, Supplier<Style> style, Runnable action) {
            this.label = label; this.style = style; this.action = action;
        }
        public Btn enabledIf(BooleanSupplier s) { enabled = s; return this; }
        public Btn tip(String t) { tip = t; return this; }
    }

    /** Рисует одну кнопку; возвращает true, если под мышью. Используется и вне строк. */
    public static boolean drawButton(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int h, String label,
                                     Style style, boolean enabled, int mx, int my) {
        boolean hover = enabled && Ui.inside(mx, my, x, y, w, h);
        int base = switch (style) {
            case PRIMARY -> Ui.ACCENT;
            case DANGER -> Ui.DANGER;
            case SUCCESS -> Ui.ON;
            case SECONDARY -> Ui.BORDER;
        };
        int col = !enabled ? Ui.alpha(Ui.BORDER, 0.5f) : hover ? Ui.lerp(base, 0xFFFFFFFF, 0.15f) : base;
        Ui.round(g, x, y, w, h, 4, col);
        int tc = !enabled ? Ui.DIM : (style == Style.SECONDARY ? Ui.TEXT : 0xFFFFFFFF);
        String s = Ui.ellipsize(c.font(), label, w - 6);
        Ui.textCentered(g, c.font(), s, x + w / 2, y + (h - 8) / 2, tc);
        if (hover) c.hand();
        return hover;
    }

    public static final class Buttons extends Row {
        private final Btn[] buttons;
        public Buttons(Btn... buttons) { this.buttons = buttons; }

        int height(Ctx c, int w) { return 22; }

        private int bw(int w) { return (w - (buttons.length - 1) * 4) / buttons.length; }

        void render(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            int bw = bw(w);
            for (int i = 0; i < buttons.length; i++) {
                Btn b = buttons[i];
                int bx = x + i * (bw + 4);
                boolean en = on() && b.enabled.getAsBoolean();
                if (drawButton(c, g, bx, y, bw, 18, b.label.get(), b.style.get(), en, mx, my) && b.tip != null)
                    c.tooltip(b.tip);
            }
        }

        boolean click(Ctx c, double mx, double my, int button) {
            if (!on() || button != 0) return false;
            int bw = bw(lastW);
            for (int i = 0; i < buttons.length; i++) {
                Btn b = buttons[i];
                int bx = lastX + i * (bw + 4);
                if (b.enabled.getAsBoolean() && Ui.inside(mx, my, bx, lastY, bw, 18)) {
                    c.clickSound();
                    b.action.run();
                    return true;
                }
            }
            return false;
        }
    }

    // ── Текстовая заметка (переносится по ширине) ────────────────────────────

    public static final class Note extends Row {
        private final Supplier<String> text;
        private final int color;
        public Note(String text, int color) { this(() -> text, color); }
        public Note(Supplier<String> text, int color) { this.text = text; this.color = color; }

        int height(Ctx c, int w) {
            String t = text.get();
            if (t == null || t.isEmpty()) return 0;
            return c.font().split(net.minecraft.network.chat.Component.literal(t), w - 16).size() * 10 + 8;
        }

        void render(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            String t = text.get();
            if (t == null || t.isEmpty()) return;
            int h = height(c, w);
            Ui.round(g, x, y, w, h - 3, 4, Ui.alpha(color, 0.10f));
            g.fill(x, y + 2, x + 2, y + h - 5, color);
            int ly = y + 3;
            for (var line : c.font().split(net.minecraft.network.chat.Component.literal(t), w - 16)) {
                g.text(c.font(), line, x + 8, ly, Ui.lerp(color, Ui.TEXT, 0.55f), false);
                ly += 10;
            }
        }
    }

    /** Пустой отступ. */
    public static final class Spacer extends Row {
        private final int h;
        public Spacer(int h) { this.h = h; }
        int height(Ctx c, int w) { return h; }
        void render(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {}
    }
}
