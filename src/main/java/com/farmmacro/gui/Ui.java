package com.farmmacro.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Палитра и примитивы рисования для собственного интерфейса мода (без текстур Minecraft):
 * скруглённые прямоугольники, «пилюли», круги, мягкие тени, обрезка текста.
 * Всё рисуется через fill(), поэтому одинаково выглядит на любом масштабе GUI.
 */
public final class Ui {
    private Ui() {}

    // ── Палитра (v1.16.0: темы — Визуал → Интерфейс → Тема, см. applyTheme) ─────
    public static int WINDOW, SIDEBAR, HEADER, CARD, CARD_HOVER, FIELD, BORDER;
    public static int TEXT, SUB, DIM, ACCENT, ACCENT_HI, ON, DANGER, WARN, TRACK_OFF;
    /** Включённый переключатель, ручка вкл/выкл, текст на акцентной кнопке, подсказка, фон HUD. */
    public static int TOGGLE_ON, KNOB_ON, KNOB_OFF, ON_ACCENT, TOOLTIP, HUD_BG;
    /** Скругление углов (1 — как раньше, меньше — строже) и мягкие тени. */
    public static float RADIUS = 1f;
    public static boolean SHADOWS = true;
    private static String theme = null;

    /** id → название в меню. По умолчанию — «quiet» (синий под логотип Kaelix). */
    public static final String[][] THEMES = {
            {"quiet",    "Чёрный + синий акцент"},
            {"mono",     "Чёрный монохром"},
            {"graphite", "Графит + зелёный статус"},
            {"paper",    "Светлая"},
            {"classic",  "Классика (как до 1.16)"},
    };

    static { applyTheme("quiet"); }

    public static String theme() { return theme; }

    /** Плоский стиль (минималистичные темы): без заливки активной вкладки, мелкие скругления. */
    public static boolean flat() { return RADIUS < 1f; }

    /** Переключить палитру. Неизвестный id — «quiet». */
    public static void applyTheme(String id) {
        if (id == null) id = "quiet";
        switch (id) {
            case "mono" -> {
                set(0xFF0C0C0C, 0xFF0A0A0A, 0xFF0A0A0A, 0xFF121212, 0xFF181818, 0xFF080808, 0xFF262626,
                    0xFFECECEC, 0xFF9A9A9A, 0xFF5E5E5E, 0xFFECECEC, 0xFFFFFFFF, 0xFFECECEC, 0xFFFF5470, 0xFFFFB547, 0xFF333333);
                TOGGLE_ON = 0xFFECECEC; KNOB_ON = 0xFF0C0C0C; KNOB_OFF = 0xFF7A7A7A; ON_ACCENT = 0xFF0C0C0C;
                TOOLTIP = 0xF0050505; HUD_BG = 0xC80C0C0C; RADIUS = 0.35f; SHADOWS = false;
            }
            case "graphite" -> {
                set(0xFF141414, 0xFF111111, 0xFF111111, 0xFF1A1A1A, 0xFF212121, 0xFF0D0D0D, 0xFF2C2C2C,
                    0xFFE8E8E8, 0xFF9C9C9C, 0xFF626262, 0xFFE8E8E8, 0xFFFFFFFF, 0xFF4ADE80, 0xFFFF5470, 0xFFFFB547, 0xFF3A3A3A);
                TOGGLE_ON = 0xFFE8E8E8; KNOB_ON = 0xFF141414; KNOB_OFF = 0xFF7E7E7E; ON_ACCENT = 0xFF141414;
                TOOLTIP = 0xF00A0A0A; HUD_BG = 0xC8141414; RADIUS = 0.35f; SHADOWS = false;
            }
            case "paper" -> {
                set(0xFFF6F6F4, 0xFFEFEFEC, 0xFFEFEFEC, 0xFFFFFFFF, 0xFFEDEDEA, 0xFFE9E9E6, 0xFFDCDCD8,
                    0xFF111111, 0xFF5F5F5F, 0xFF9A9A9A, 0xFF111111, 0xFF333333, 0xFF16A34A, 0xFFDC2643, 0xFFC27803, 0xFFCFCFCC);
                TOGGLE_ON = 0xFF111111; KNOB_ON = 0xFFFFFFFF; KNOB_OFF = 0xFFFFFFFF; ON_ACCENT = 0xFFFFFFFF;
                TOOLTIP = 0xF8FFFFFF; HUD_BG = 0xC8141414; RADIUS = 0.35f; SHADOWS = false;
            }
            case "classic" -> {
                set(0xFF151A24, 0xFF11151D, 0xFF11151D, 0xFF1C2230, 0xFF232A3B, 0xFF0E1218, 0xFF2A3245,
                    0xFFE8EBF3, 0xFF98A1B6, 0xFF5E677D, 0xFF6D8BFF, 0xFF8AA2FF, 0xFF37D18A, 0xFFFF5470, 0xFFFFB547, 0xFF394158);
                TOGGLE_ON = 0xFF37D18A; KNOB_ON = 0xFFFFFFFF; KNOB_OFF = 0xFF9AA0AE; ON_ACCENT = 0xFFFFFFFF;
                TOOLTIP = 0xF00B0E14; HUD_BG = 0xC8101420; RADIUS = 1f; SHADOWS = true;
            }
            default -> {
                id = "quiet";
                set(0xFF0E0E10, 0xFF0B0B0D, 0xFF0B0B0D, 0xFF151518, 0xFF1B1B1F, 0xFF09090B, 0xFF26262B,
                    0xFFE6E6E6, 0xFF9696A0, 0xFF5E5E66, 0xFF6F8FFF, 0xFF9AB0FF, 0xFF4ADE80, 0xFFFF5470, 0xFFFFB547, 0xFF34343A);
                TOGGLE_ON = 0xFF6F8FFF; KNOB_ON = 0xFFFFFFFF; KNOB_OFF = 0xFF7A7A82; ON_ACCENT = 0xFFFFFFFF;
                TOOLTIP = 0xF0070708; HUD_BG = 0xC80E0E10; RADIUS = 0.35f; SHADOWS = false;
            }
        }
        theme = id;
    }

    /** Применить тему из конфига, если она сменилась (дёшево — сравнение строк). */
    public static void sync(String id) {
        if (id == null) id = "quiet";
        if (!id.equals(theme)) applyTheme(id);
    }

    private static void set(int window, int sidebar, int header, int card, int cardHover, int field, int border,
                            int text, int sub, int dim, int accent, int accentHi, int on, int danger, int warn, int trackOff) {
        WINDOW = window; SIDEBAR = sidebar; HEADER = header; CARD = card; CARD_HOVER = cardHover; FIELD = field; BORDER = border;
        TEXT = text; SUB = sub; DIM = dim; ACCENT = accent; ACCENT_HI = accentHi; ON = on; DANGER = danger; WARN = warn; TRACK_OFF = trackOff;
    }

    // ── Цвет ─────────────────────────────────────────────────────────────────
    public static int alpha(int color, float a) {
        int al = Math.round(((color >>> 24) & 0xFF) * Math.max(0, Math.min(1, a)));
        return (al << 24) | (color & 0xFFFFFF);
    }

    public static int lerp(int c1, int c2, float t) {
        t = Math.max(0, Math.min(1, t));
        int a = (int) (((c1 >>> 24) & 0xFF) + (((c2 >>> 24) & 0xFF) - ((c1 >>> 24) & 0xFF)) * t);
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ── Фигуры ───────────────────────────────────────────────────────────────

    /** Скруглённый прямоугольник: углы строятся построчно по окружности радиуса r. */
    public static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        roundExact(g, x, y, w, h, RADIUS >= 1f ? r : Math.round(r * RADIUS), color);
    }

    /** Без учёта темы: радиус ровно r (пилюли и круги). */
    public static void roundExact(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) { g.fill(x, y, x + w, y + h, color); return; }
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
        }
        g.fill(x, y + r, x + w, y + h - r, color);
    }

    /** Скруглённая плашка с рамкой толщиной 1. */
    public static void roundBordered(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int fill, int border) {
        int rr = RADIUS >= 1f ? r : Math.round(r * RADIUS);
        roundExact(g, x, y, w, h, rr, border);
        roundExact(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, rr - 1), fill);
    }

    public static void pill(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        roundExact(g, x, y, w, h, h / 2, color);
    }

    public static void circle(GuiGraphicsExtractor g, int cx, int cy, int radius, int color) {
        roundExact(g, cx - radius, cy - radius, radius * 2, radius * 2, radius, color);
    }

    /** Мягкая тень из нескольких полупрозрачных слоёв. */
    public static void shadow(GuiGraphicsExtractor g, int x, int y, int w, int h, int r) {
        if (!SHADOWS) return;
        for (int i = 6; i >= 1; i--) {
            round(g, x - i, y - i + 2, w + i * 2, h + i * 2, r + i, alpha(0xFF000000, 0.05f));
        }
    }

    // ── Текст ────────────────────────────────────────────────────────────────

    public static void text(GuiGraphicsExtractor g, Font f, String s, int x, int y, int color) {
        g.text(f, s, x, y, color, false);
    }

    public static void textCentered(GuiGraphicsExtractor g, Font f, String s, int cx, int y, int color) {
        g.text(f, s, cx - f.width(s) / 2, y, color, false);
    }

    public static void textRight(GuiGraphicsExtractor g, Font f, String s, int right, int y, int color) {
        g.text(f, s, right - f.width(s), y, color, false);
    }

    /** v1.16.0: фон строки списка (пресет, макрос, детектор). Минималистично — линия снизу, выбранная — полоска слева. */
    public static void listBg(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, boolean sel, boolean hover) {
        if (!flat()) { round(g, x, y, w, h, r, sel ? alpha(ACCENT, 0.22f) : hover ? CARD_HOVER : CARD); return; }
        if (sel) g.fill(x, y, x + w, y + h - 1, alpha(ACCENT, 0.08f));
        else if (hover) g.fill(x, y, x + w, y + h - 1, alpha(CARD_HOVER, 0.55f));
        if (sel) g.fill(x, y + 3, x + 2, y + h - 4, ACCENT);
        g.fill(x, y + h - 1, x + w, y + h, BORDER);
    }

    /** v1.16.0: подложка окна. Минималистично — тонкая рамка. */
    public static void window(GuiGraphicsExtractor g, int x, int y, int w, int h, int r) {
        if (flat()) roundBordered(g, x, y, w, h, r, WINDOW, BORDER);
        else round(g, x, y, w, h, r, WINDOW);
    }

    /** v1.16.0: блок-карточка (текущий макрос/маршрут). Минималистично — тонкая рамка без заливки. */
    public static void box(GuiGraphicsExtractor g, int x, int y, int w, int h, int r) {
        if (!flat()) { round(g, x, y, w, h, r, CARD); return; }
        roundBordered(g, x, y, w, h, r, WINDOW, BORDER);
    }

    /** Текст вразрядку (между буквами +gap px). @return x после текста. */
    public static int spaced(GuiGraphicsExtractor g, Font f, String s, int x, int y, int color, int gap) {
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            g.text(f, ch, x, y, color, false);
            x += f.width(ch) + gap;
        }
        return x;
    }

    /** Обрезает строку по ширине с «…». */
    public static String ellipsize(Font f, String s, int maxW) {
        if (maxW <= 0) return "";
        if (f.width(s) <= maxW) return s;
        int ell = f.width("…");
        return f.plainSubstrByWidth(s, Math.max(0, maxW - ell)) + "…";
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}
