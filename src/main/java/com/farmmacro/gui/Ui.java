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

    // ── Палитра ──────────────────────────────────────────────────────────────
    public static final int WINDOW     = 0xFF151A24;
    public static final int SIDEBAR    = 0xFF11151D;
    public static final int HEADER     = 0xFF11151D;
    public static final int CARD       = 0xFF1C2230;
    public static final int CARD_HOVER = 0xFF232A3B;
    public static final int FIELD      = 0xFF0E1218;
    public static final int BORDER     = 0xFF2A3245;
    public static final int TEXT       = 0xFFE8EBF3;
    public static final int SUB        = 0xFF98A1B6;
    public static final int DIM        = 0xFF5E677D;
    public static final int ACCENT     = 0xFF6D8BFF;
    public static final int ACCENT_HI  = 0xFF8AA2FF;
    public static final int ON         = 0xFF37D18A;
    public static final int DANGER     = 0xFFFF5470;
    public static final int WARN       = 0xFFFFB547;
    public static final int TRACK_OFF  = 0xFF394158;

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
        round(g, x, y, w, h, r, border);
        round(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }

    public static void pill(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        round(g, x, y, w, h, h / 2, color);
    }

    public static void circle(GuiGraphicsExtractor g, int cx, int cy, int radius, int color) {
        round(g, cx - radius, cy - radius, radius * 2, radius * 2, radius, color);
    }

    /** Мягкая тень из нескольких полупрозрачных слоёв. */
    public static void shadow(GuiGraphicsExtractor g, int x, int y, int w, int h, int r) {
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
