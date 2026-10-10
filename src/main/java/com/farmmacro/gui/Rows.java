package com.farmmacro.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;
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

    // ── v1.11: что показывать (простой/расширенный режим, свёрнутые разделы, поиск) ──

    /** Расширенный режим меню: видны строки, помеченные {@link Row#adv()}. */
    public static BooleanSupplier advanced = () -> com.farmmacro.config.ModConfig.INSTANCE.uiAdvanced;
    /** Идёт поиск: видно всё найденное, без учёта режима и свёрнутых разделов. */
    public static boolean searching;

    /** Привязать строки к разделам (строка → ближайший Section выше). Вызывать после сборки списка. */
    public static void link(List<Row> rows) {
        Section cur = null;
        for (Row r : rows) {
            if (r instanceof Section s) { cur = s; s.children.clear(); continue; }
            if (r instanceof End) { cur = null; continue; }
            r.section = cur;
            if (cur != null) cur.children.add(r);
        }
    }

    /** Сколько строк скрыто только потому, что выключен расширенный режим. */
    public static int hiddenAdvanced(List<Row> rows) {
        if (advanced.getAsBoolean()) return 0;
        int n = 0;
        for (Row r : rows) if (r.adv && !(r instanceof Section) && !(r instanceof Note) && r.visible.getAsBoolean()
                && (r.section == null || r.section.visible.getAsBoolean())) n++;
        return n;
    }

    public abstract static class Row {
        /** Позиция с последней отрисовки (для кликов). */
        int lastX, lastY, lastW, lastH;
        BooleanSupplier enabled = () -> true;
        BooleanSupplier visible = () -> true;
        boolean adv;
        Section section;

        public Row enabledIf(BooleanSupplier s) { this.enabled = s; return this; }
        /** Показывать только при условии (например, пока включён «родительский» переключатель). */
        public Row showIf(BooleanSupplier s) { this.visible = s; return this; }
        /** Тонкая настройка: только в расширенном режиме (и в поиске). */
        public Row adv() { adv = true; return this; }
        /** enabledIf + showIf одним условием. */
        public Row under(BooleanSupplier s) { enabled = s; visible = s; return this; }
        boolean on() { return enabled.getAsBoolean(); }
        public boolean isAdv() { return adv; }

        /** Видна ли сама строка (без учёта свёрнутого раздела). */
        boolean selfShown() {
            return visible.getAsBoolean() && (!adv || advanced.getAsBoolean()) && (section == null || section.selfShown());
        }
        public boolean shown() {
            if (searching) return true;
            return selfShown() && (section == null || section.open());
        }
        /** Высота с учётом видимости — её используют экраны при раскладке. */
        public int h(Ctx c, int w) { return shown() ? height(c, w) : 0; }
        /** Текст для поиска (подпись и подсказка). */
        public String searchText() { return ""; }

        abstract int height(Ctx c, int w);
        abstract void render(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover);
        boolean click(Ctx c, double mx, double my, int button) { return false; }
        boolean scroll(Ctx c, double mx, double my, double amount) { return false; }
    }

    // ── Заголовок раздела ────────────────────────────────────────────────────

    public static final class Section extends Row {
        final String title;
        final List<Row> children = new java.util.ArrayList<>();
        private String key;
        private boolean defaultOpen = true;
        private Runnable onClick;
        public Section(String title) { this.title = title; this.key = title; }

        public String title() { return title; }
        /** Ключ состояния «свёрнут» (по умолчанию — заголовок). */
        public Section key(String k) { key = k; return this; }
        public String key() { return key; }
        /** Свёрнут, пока пользователь не развернёт. */
        public Section closed() { defaultOpen = false; return this; }
        /** Клик по заголовку вместо сворачивания (результаты поиска — перейти к разделу). */
        public Section onClick(Runnable r) { onClick = r; return this; }

        boolean collapsible() { return onClick == null && !children.isEmpty() && !searching; }
        public boolean open() {
            if (!collapsible()) return true;
            Boolean v = com.farmmacro.config.ModConfig.INSTANCE.uiSections.get(key);
            return v != null ? v : defaultOpen;
        }
        public void setOpen(boolean v) {
            com.farmmacro.config.ModConfig.INSTANCE.uiSections.put(key, v);
            com.farmmacro.config.ModConfig.save();
        }

        @Override boolean selfShown() {
            if (!visible.getAsBoolean() || (adv && !advanced.getAsBoolean())) return false;
            if (children.isEmpty()) return true;
            for (Row r : children)
                if (r.visible.getAsBoolean() && (!r.adv || advanced.getAsBoolean()) && !(r instanceof Note n && n.empty())) return true;
            return false;
        }

        @Override public String searchText() { return title; }

        int height(Ctx c, int w) { return 20; }
        void render(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            boolean col = collapsible(), op = open();
            String t = (col ? (op ? "▾ " : "▸ ") : onClick != null ? "› " : "") + title.toUpperCase();
            boolean hot = hover && (col || onClick != null);
            Ui.text(g, c.font(), t, x + 2, y + 9, hot ? Ui.TEXT : Ui.ACCENT_HI);
            // справа: сколько переключателей включено (и сколько строк спрятано, если свёрнут)
            String right = null;
            if (col) {
                int on = 0, all = 0, rows = 0;
                for (Row r : children) {
                    if (!r.visible.getAsBoolean() || (r.adv && !advanced.getAsBoolean()) || r instanceof Note) continue;
                    rows++;
                    if (r instanceof Toggle tg) { all++; if (tg.value()) on++; }
                }
                if (all > 0) right = on + "/" + all + " вкл";
                if (!op) right = (right != null ? right + " · " : "") + rows + " " + plural(rows);
            } else if (onClick != null) right = "перейти";
            int lx = x + 8 + c.font().width(t);
            int rx = x + w;
            if (right != null) {
                int rw = c.font().width(right);
                Ui.text(g, c.font(), right, x + w - rw - 2, y + 9, hot ? Ui.SUB : Ui.DIM);
                rx = x + w - rw - 8;
            }
            if (rx > lx) g.fill(lx, y + 13, rx, y + 14, Ui.BORDER);
            if (hot) { c.hand(); c.tooltip(onClick != null ? "Открыть вкладку с этим разделом" : op ? "Свернуть раздел" : "Развернуть раздел"); }
        }

        boolean click(Ctx c, double mx, double my, int button) {
            if (button != 0) return false;
            if (onClick != null) { c.clickSound(); onClick.run(); return true; }
            if (!collapsible()) return false;
            c.clickSound();
            setOpen(!open());
            return true;
        }

        private static String plural(int n) {
            int m10 = n % 10, m100 = n % 100;
            if (m10 == 1 && m100 != 11) return "настройка";
            if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return "настройки";
            return "настроек";
        }
    }

    /**
     * Переключатель «показать ещё N тонких настроек / скрыть» (простой/расширенный режим) в конце вкладки или окна.
     */
    public static final class MoreToggle extends Row {
        private final java.util.function.IntSupplier hidden;
        private final Runnable toggle;
        public MoreToggle(java.util.function.IntSupplier hidden, Runnable toggle) { this.hidden = hidden; this.toggle = toggle; }
        private String text() {
            if (advanced.getAsBoolean()) return "▴ Скрыть тонкие настройки (простой режим)";
            int n = hidden.getAsInt();
            return n <= 0 ? "" : "▾ Ещё " + n + " " + Section.plural(n) + " — показать все (расширенный режим)";
        }
        @Override public boolean shown() { return !searching && !text().isEmpty(); }
        int height(Ctx c, int w) { return 20; }
        void render(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            String t = text();
            Ui.round(g, x, y + 2, w, 15, 4, hover ? Ui.CARD_HOVER : Ui.alpha(Ui.ACCENT, 0.08f));
            Ui.textCentered(g, c.font(), Ui.ellipsize(c.font(), t, w - 12), x + w / 2, y + 6, hover ? Ui.TEXT : Ui.ACCENT_HI);
            if (hover) { c.hand(); c.tooltip("Пороги, веса, цвета и служебные шаги. Тот же переключатель — вверху окна"); }
        }
        boolean click(Ctx c, double mx, double my, int button) {
            if (button != 0) return false;
            c.clickSound();
            toggle.run();
            return true;
        }
    }

    /** Конец раздела: строки ниже не принадлежат предыдущему Section (не сворачиваются с ним). Высота 0. */
    public static final class End extends Row {
        @Override public boolean shown() { return false; }
        int height(Ctx c, int w) { return 0; }
        void render(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {}
    }

    // ── Базовая «карточка» с подписью и подсказкой ───────────────────────────

    abstract static class Labeled extends Row {
        final String label, hint;
        Labeled(String label, String hint) { this.label = label; this.hint = hint; }

        @Override public String searchText() { return label + (hint != null ? " " + hint : ""); }

        int height(Ctx c, int w) { return hintNow() == null ? 22 : 30; }

        /** Подсказка под подписью сейчас (у полей ввода при ошибке — текст ошибки). */
        String hintNow() { return hint; }
        int hintColor() { return on() ? Ui.SUB : Ui.DIM; }

        /** Ширина элемента управления справа. */
        abstract int controlWidth(Ctx c);

        void drawCard(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hover, boolean clickable) {
            Ui.round(g, x, y, w, h - 3, 4, hover && clickable && on() ? Ui.CARD_HOVER : Ui.CARD);
            int textW = w - controlWidth(c) - 20;
            int col = on() ? Ui.TEXT : Ui.DIM;
            Font f = c.font();
            String hn = hintNow();
            if (hn == null) {
                Ui.text(g, f, Ui.ellipsize(f, label, textW), x + 8, y + (h - 3 - 8) / 2, col);
            } else {
                Ui.text(g, f, Ui.ellipsize(f, label, textW), x + 8, y + 5, col);
                String hs = Ui.ellipsize(f, hn, textW);
                Ui.text(g, f, hs, x + 8, y + 16, hintColor());
                if (hover && !hs.equals(hn)) c.tooltip(hn);
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

        public boolean value() { return get.getAsBoolean(); }

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

    /** Короткая запись числа без хвостов float: 0.001 → «0.001», 2.0 → «2». До 6 знаков после точки. */
    public static String num(double v) {
        if (!Double.isFinite(v)) return String.valueOf(v);
        String t = new java.math.BigDecimal(v).setScale(6, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        return t.equals("-0") ? "0" : t;
    }

    /**
     * Разбор числа из поля ввода. @return текст ошибки или null (значение — в out[0]).
     * Точка или запятая, пробелы игнорируются; только конечные числа в [min, max]; integer — только целые.
     */
    public static String parseNumber(String s, double min, double max, boolean integer, double[] out) {
        String t = s == null ? "" : s.trim().replace(" ", "").replace(',', '.');
        if (t.startsWith("+")) t = t.substring(1);
        if (t.isEmpty()) return "Пусто — впиши число";
        double v;
        try { v = Double.parseDouble(t); } catch (NumberFormatException e) { return "«" + s.trim() + "» — не число"; }
        if (!Double.isFinite(v)) return "Слишком большое число";
        if (t.matches(".*[eEdDfFxX].*")) return "«" + s.trim() + "» — не число";
        if (integer && v != Math.rint(v)) return "Нужно целое число";
        if (v < min) return "Не меньше " + num(min) + (min == 0 ? " (отрицательное нельзя)" : "");
        if (v > max) return "Не больше " + num(max);
        out[0] = v;
        return null;
    }

    public static final class Number extends Input {
        private final DoubleSupplier get;
        private final DoubleConsumer set;
        private final double min, max, step, bigStep;
        private final DoubleFunction<String> fmt;
        private boolean integer;

        public Number(String label, String hint, DoubleSupplier get, DoubleConsumer set,
                      double min, double max, double step, double bigStep, DoubleFunction<String> fmt) {
            super(label, hint);
            this.get = get; this.set = set; this.min = min; this.max = max;
            this.step = step; this.bigStep = bigStep; this.fmt = fmt;
        }

        /** Только целые (поле в настройках — int). */
        public Number integer() { integer = true; return this; }

        /** Встроен в {@link Range}: поле рисуется в заданном месте, без своей карточки. */
        int ex = Integer.MIN_VALUE, ey, ew;
        boolean embedded() { return ex != Integer.MIN_VALUE; }

        int controlWidth(Ctx c) { return embedded() ? ew : 92; }

        /** {x, y, ширина} поля. */
        private int[] box(Ctx c) {
            if (embedded()) return new int[]{ex, ey, ew};
            int h = height(c, lastW), cw = controlWidth(c);
            return new int[]{lastX + lastW - cw - 6, lastY + (h - 3 - 14) / 2, cw};
        }

        int maxLen() { return 16; }
        boolean allowed(String ch) { return ch.matches("[0-9.,+\\- ]"); }
        String initialText() { return num(get.getAsDouble()); }
        String commitText(String text) {
            double[] out = new double[1];
            String err = parseNumber(text, min, max, integer, out);
            if (err != null) return err;
            if (out[0] != get.getAsDouble()) set.accept(out[0]);
            return null;
        }
        String editTip() { return "Enter — принять, Esc — отменить · от " + num(min) + " до " + num(max) + (integer ? ", целое" : ""); }

        void render(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            if (!embedded()) drawCard(c, g, x, y, w, height(c, w), hover, false);
            int[] bx = box(c);
            int cw = bx[2], cx = bx[0], cy = bx[1];
            boolean en = on();
            if (!en && focused()) textFocus = null;
            if (focused()) { drawEditing(c, g, cx, cy, cw, hover); return; }
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
            boolean mid = hover && en && Ui.inside(mx, my, cx + 16, cy, cw - 32, 14);
            if (mid) c.hand();
            if (mid) c.tooltip(wheelFocus == this ? "Клик — ввести число, колёсико — изменить (Shift — шаг ×" + trim(bigStep / step) + ")"
                        : "Клик — ввести число вручную (" + num(min) + "…" + num(max) + "), потом колёсико тоже меняет");
        }

        private static String trim(double d) {
            return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
        }

        private void change(Ctx c, int dir) {
            double s = c.shift() ? bigStep : step;
            double v = get.getAsDouble() + dir * s;
            v = Math.round(v * 1e6) / 1e6;                         // убираем хвосты float, ручной ввод не округляем к шагу
            if (integer) v = Math.rint(v);
            v = Math.max(min, Math.min(max, v));
            set.accept(v);
        }

        boolean click(Ctx c, double mx, double my, int button) {
            if (!on() || button != 0) return false;
            int[] bx = box(c);
            int cx = bx[0], cy = bx[1], cw = bx[2];
            if (!Ui.inside(mx, my, cx, cy, cw, 14)) return false;
            if (focused()) { placeCursor(c, mx, cx); return true; }
            wheelFocus = this;
            if (Ui.inside(mx, my, cx, cy, 16, 14)) { change(c, -1); c.clickSound(); return true; }
            if (Ui.inside(mx, my, cx + cw - 16, cy, 16, 14)) { change(c, +1); c.clickSound(); return true; }
            focus(c);                                              // клик по середине — ввод числом
            return true;
        }

        boolean scroll(Ctx c, double mx, double my, double amount) {
            if (!on() || wheelFocus != this) return false;
            int[] bx = box(c);
            if (!Ui.inside(mx, my, bx[0], bx[1], bx[2], 14)) return false;
            if (focused()) return true;                            // пока печатаем, колёсико значение не трогает
            change(c, amount > 0 ? 1 : -1);
            return true;
        }
    }

    /** v1.11: «от – до» в одной строке (два числовых поля). Вместо двух строк «…: от» и «…: до». */
    public static final class Range extends Labeled {
        private final Number lo, hi;
        public Range(String label, String hint, Number lo, Number hi) { super(label, hint); this.lo = lo; this.hi = hi; }

        private int fieldW() { return Math.max(60, Math.min(84, (lastW / 2 - 22) / 2)); }
        int controlWidth(Ctx c) { return fieldW() * 2 + 12; }

        private void place(Ctx c) {
            int h = height(c, lastW), fw = fieldW();
            int cy = lastY + (h - 3 - 14) / 2, right = lastX + lastW - 6;
            hi.ex = right - fw; hi.ey = cy; hi.ew = fw;
            lo.ex = right - fw * 2 - 12; lo.ey = cy; lo.ew = fw;
            for (Number n : new Number[]{lo, hi}) { n.enabled = enabled; n.lastX = lastX; n.lastY = lastY; n.lastW = lastW; n.lastH = lastH; }
        }

        void render(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            drawCard(c, g, x, y, w, height(c, w), hover, false);
            place(c);
            lo.render(c, g, x, y, w, mx, my, hover);
            hi.render(c, g, x, y, w, mx, my, hover);
            Ui.textCentered(g, c.font(), "–", lo.ex + lo.ew + 6, lo.ey + 3, on() ? Ui.SUB : Ui.DIM);
        }

        boolean click(Ctx c, double mx, double my, int button) {
            place(c);
            return lo.click(c, mx, my, button) || hi.click(c, mx, my, button);
        }

        boolean scroll(Ctx c, double mx, double my, double amount) {
            place(c);
            return lo.scroll(c, mx, my, amount) || hi.scroll(c, mx, my, amount);
        }
    }

    /** v1.11: поиск по всем настройкам (фильтр на лету, пока печатаешь). */
    public static final class Search extends Input {
        private final Consumer<String> onChange;
        private String query = "";

        public Search(Consumer<String> onChange) { super("Поиск", null); this.onChange = onChange; }

        public String query() { return query; }
        public void clear() { if (textFocus == this) textFocus = null; set(""); }
        private void set(String q) { if (!q.equals(query)) { query = q; onChange.accept(q); } }

        @Override public boolean shown() { return true; }
        int height(Ctx c, int w) { return 22; }
        int controlWidth(Ctx c) { return lastW - 12; }
        int maxLen() { return 40; }
        boolean allowed(String ch) { return true; }
        String initialText() { return query; }
        String commitText(String text) { set(text.strip()); return null; }
        String editTip() { return "Ищет по названиям и подсказкам во всех вкладках · Esc — выйти из поля, ✕ — очистить"; }

        void render(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            int cx = x + 2, cy = y + 3, cw = w - 4;
            if (focused()) {
                if (!buf.strip().equals(query)) set(buf.strip());
                drawEditing(c, g, cx, cy, cw, hover);
                return;
            }
            boolean hf = Ui.inside(mx, my, cx, cy, cw, 14);
            Ui.round(g, cx, cy, cw, 14, 4, hf ? Ui.CARD_HOVER : Ui.FIELD);
            if (query.isEmpty()) Ui.text(g, c.font(), "⌕ Поиск по всем настройкам…", cx + 6, cy + 3, Ui.DIM);
            else {
                Ui.text(g, c.font(), Ui.ellipsize(c.font(), "⌕ " + query, cw - 30), cx + 6, cy + 3, Ui.TEXT);
                boolean hx = Ui.inside(mx, my, cx + cw - 16, cy, 16, 14);
                Ui.textCentered(g, c.font(), "✕", cx + cw - 8, cy + 3, hx ? Ui.DANGER : Ui.SUB);
                if (hx) c.tooltip("Очистить поиск");
            }
            if (hf) c.hand();
        }

        boolean click(Ctx c, double mx, double my, int button) {
            if (button != 0) return false;
            int cx = lastX + 2, cy = lastY + 3, cw = lastW - 4;
            if (!Ui.inside(mx, my, cx, cy, cw, 14)) return false;
            if (!focused() && !query.isEmpty() && Ui.inside(mx, my, cx + cw - 16, cy, 16, 14)) { c.clickSound(); clear(); return true; }
            if (!focused()) focus(c);
            else placeCursor(c, mx, cx);
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

        @Override public String searchText() {
            StringBuilder b = new StringBuilder();
            for (Btn x : buttons) { b.append(x.label.get()).append(' '); if (x.tip != null) b.append(x.tip).append(' '); }
            return b.toString();
        }

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

        boolean empty() { String t = text.get(); return t == null || t.isEmpty(); }
        @Override public String searchText() { String t = text.get(); return t == null ? "" : t; }

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

    // ── Поле ввода текста/числа (клик — редактировать, Enter — принять, Esc — отменить) ──

    /** Поле, в которое сейчас печатают (одно на все экраны). */
    /** Поле, в котором сейчас печатают (TextField или число в Number), или null. */
    static Input textFocus;

    public static boolean textFocused() { return textFocus != null; }

    /** Строка с полем в фокусе (для «клик мимо — принять»). */
    public static Row focusedRow() { return textFocus; }

    /** Принять ввод (клик мимо, прокрутка, смена вкладки, закрытие). Неверное значение — откат. */
    public static void blurText() {
        Input f = textFocus;
        textFocus = null;
        if (f != null) { f.commitText(f.buf); f.error = null; }
    }

    /** Клавиша для поля в фокусе. @return true — съедена */
    public static boolean textKey(net.minecraft.client.input.KeyEvent e) {
        Input f = textFocus;
        return f != null && f.key(e);
    }

    public static boolean textChar(net.minecraft.client.input.CharacterEvent e) {
        Input f = textFocus;
        if (f == null || !e.isAllowedChatCharacter()) return false;
        f.insert(e.codepointAsString());
        return true;
    }

    /** Общая часть полей ввода: буфер, курсор, Enter/Esc/стрелки/вставка, ошибка под подписью. */
    abstract static class Input extends Labeled {
        String buf = "";
        int cursor;
        /** Текст ошибки последнего Enter или null. */
        String error;

        Input(String label, String hint) { super(label, hint); }

        abstract int maxLen();
        abstract boolean allowed(String ch);
        abstract String initialText();
        /** Применить; @return null — принято, иначе понятный текст ошибки. */
        abstract String commitText(String text);
        String editTip() { return "Enter — принять, Esc — отменить"; }

        boolean focused() { return textFocus == this; }

        @Override String hintNow() { return focused() && error != null ? "⚠ " + error : hint; }
        @Override int hintColor() { return focused() && error != null ? Ui.DANGER : super.hintColor(); }

        void focus(Ctx c) {
            if (textFocus != null && textFocus != this) blurText();
            textFocus = this;
            buf = initialText();
            cursor = buf.length();
            error = null;
            c.clickSound();
        }

        void placeCursor(Ctx c, double mx, int boxX) {
            int rel = (int) mx - (boxX + 4), best = buf.length();
            for (int i = 0; i <= buf.length(); i++) if (c.font().width(buf.substring(0, i)) >= rel) { best = i; break; }
            cursor = best;
        }

        void drawEditing(Ctx c, GuiGraphicsExtractor g, int cx, int cy, int cw, boolean hover) {
            Font f = c.font();
            Ui.round(g, cx - 1, cy - 1, cw + 2, 16, 5, error != null ? Ui.DANGER : Ui.ACCENT);
            Ui.round(g, cx, cy, cw, 14, 4, Ui.FIELD);
            String vis = buf;
            int off = 0;
            while (f.width(vis) > cw - 10 && off < cursor) { off++; vis = buf.substring(off); }
            vis = f.plainSubstrByWidth(vis, cw - 8);
            Ui.text(g, f, vis, cx + 4, cy + 3, Ui.TEXT);
            if ((System.currentTimeMillis() / 500) % 2 == 0) {
                int cur = Math.max(off, Math.min(cursor, off + vis.length()));
                int px = cx + 4 + f.width(buf.substring(off, cur));
                g.fill(px, cy + 2, px + 1, cy + 12, Ui.ACCENT_HI);
            }
            if (hover) c.tooltip(error != null ? error + " · Esc — вернуть как было" : editTip());
        }

        void insert(String add) {
            StringBuilder ok = new StringBuilder();
            add.codePoints().forEach(cp -> {
                String ch = new String(Character.toChars(cp));
                if (allowed(ch)) ok.append(ch);
            });
            int room = maxLen() - buf.length();
            if (room <= 0 || ok.isEmpty()) return;
            String a = ok.length() > room ? ok.substring(0, room) : ok.toString();
            buf = buf.substring(0, cursor) + a + buf.substring(cursor);
            cursor += a.length();
            error = null;
        }

        boolean key(net.minecraft.client.input.KeyEvent e) {
            switch (e.key()) {
                case 257, 335 -> {                                             // Enter
                    String err = commitText(buf);
                    if (err == null) { textFocus = null; error = null; } else error = err;
                    return true;
                }
                case 256 -> { textFocus = null; error = null; return true; }   // Esc — отменить
                case 259 -> { if (cursor > 0) { buf = buf.substring(0, cursor - 1) + buf.substring(cursor); cursor--; error = null; } return true; }
                case 261 -> { if (cursor < buf.length()) { buf = buf.substring(0, cursor) + buf.substring(cursor + 1); error = null; } return true; }
                case 263 -> { cursor = Math.max(0, cursor - 1); return true; }
                case 262 -> { cursor = Math.min(buf.length(), cursor + 1); return true; }
                case 268 -> { cursor = 0; return true; }
                case 269 -> { cursor = buf.length(); return true; }
                default -> { }
            }
            if (e.isPaste()) {
                insert(net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard().replace('\n', ' '));
                return true;
            }
            if (e.hasControlDown() && e.key() == 65) { buf = ""; cursor = 0; return true; }   // Ctrl+A — очистить
            return true;   // остальные клавиши в поле не уходят дальше (Tab, клавиша меню и т. п.)
        }
    }

    public static final class TextField extends Input {
        private final Supplier<String> get;
        /** Принять строку; false — значение не подходит (поле подсвечивается красным и остаётся в фокусе). */
        private final Predicate<String> commit;
        private final Predicate<String> allowedChar;
        private final int maxLen, width;
        private String errorText = "Не подходит — проверь значение и диапазон";

        /**
         * @param width ширина поля, 0 — половина строки
         * @param allowedChar какие символы можно печатать (null — любые допустимые в чате)
         */
        public TextField(String label, String hint, Supplier<String> get, Predicate<String> commit,
                         Predicate<String> allowedChar, int maxLen, int width) {
            super(label, hint);
            this.get = get; this.commit = commit; this.allowedChar = allowedChar; this.maxLen = maxLen; this.width = width;
        }

        /** Числа: цифры, знак, точка/запятая. */
        public static Predicate<String> numeric() { return ch -> ch.matches("[0-9.,+\\-]"); }

        /** Текст ошибки, если commit вернул false. */
        public TextField errorText(String t) { errorText = t; return this; }

        int maxLen() { return maxLen; }
        boolean allowed(String ch) { return allowedChar == null || allowedChar.test(ch); }
        String initialText() { return get.get(); }
        String commitText(String text) { return commit.test(text) ? null : errorText; }

        int controlWidth(Ctx c) { return width > 0 ? width : Math.min(160, Math.max(92, lastW / 2 - 10)); }

        private int[] box(Ctx c) {
            int h = height(c, lastW), cw = controlWidth(c);
            return new int[]{lastX + lastW - cw - 6, lastY + (h - 3 - 14) / 2, cw};
        }

        void render(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            int h = height(c, w);
            drawCard(c, g, x, y, w, h, hover, false);
            int cw = controlWidth(c);
            int cx = x + w - cw - 6, cy = y + (h - 3 - 14) / 2;
            boolean en = on();
            if (!en && focused()) textFocus = null;
            Font f = c.font();
            if (focused()) { drawEditing(c, g, cx, cy, cw, hover); return; }
            boolean hf = en && Ui.inside(mx, my, cx, cy, cw, 14);
            Ui.round(g, cx, cy, cw, 14, 4, hf ? Ui.CARD_HOVER : Ui.FIELD);
            String s = Ui.ellipsize(f, get.get(), cw - 8);
            Ui.textCentered(g, f, s, cx + cw / 2, cy + 3, en ? Ui.TEXT : Ui.DIM);
            if (hf) { c.hand(); c.tooltip("Кликни, чтобы ввести значение"); }
        }

        boolean click(Ctx c, double mx, double my, int button) {
            if (!on() || button != 0) return false;
            int[] b = box(c);
            if (!Ui.inside(mx, my, b[0], b[1], b[2], 14)) return false;
            if (!focused()) focus(c);
            else placeCursor(c, mx, b[0]);
            return true;
        }
    }
}
