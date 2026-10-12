package com.farmmacro.hud;

import com.farmmacro.config.HudSlot;

/**
 * v1.14: математика редактора HUD без Minecraft (проверка — PlayersCheck): из места на экране в якорь и обратно,
 * прилипание к краям и центру, масштаб. Все размеры — в пикселях GUI, w/h — уже с масштабом.
 */
public final class HudLayout {
    private HudLayout() {}

    public static final int MARGIN = 6, SNAP = 5;
    public static final double SCALE_MIN = 0.5, SCALE_MAX = 2.0, SCALE_STEP = 0.1;

    /** Левый верхний угол плашки w×h на экране sw×sh по слоту (с удержанием на экране). */
    public static int[] place(HudSlot s, double w, double h, int sw, int sh) {
        return new int[]{(int) Math.round(clamp(axis(s.ax, s.ox, w, sw), w, sw)), (int) Math.round(clamp(axis(s.ay, s.oy, h, sh), h, sh))};
    }

    private static double axis(double a, double off, double size, int screen) {
        if (a <= 0.25) return off;
        if (a >= 0.75) return screen - size - off;
        return screen / 2.0 + off - size / 2.0;
    }

    private static double clamp(double v, double size, int screen) {
        return Math.max(0, Math.min(Math.max(0, screen - size), v));
    }

    /** Записать в слот место (x, y) плашки w×h: якорь — ближайшая треть экрана. */
    public static void store(HudSlot s, double x, double y, double w, double h, int sw, int sh) {
        s.placed = true;
        s.ax = anchor(x + w / 2, sw);
        s.ay = anchor(y + h / 2, sh);
        s.ox = offset(s.ax, x, w, sw);
        s.oy = offset(s.ay, y, h, sh);
    }

    private static double anchor(double center, int screen) {
        return center < screen / 3.0 ? 0 : center > screen * 2 / 3.0 ? 1 : 0.5;
    }

    private static double offset(double a, double pos, double size, int screen) {
        if (a <= 0.25) return pos;
        if (a >= 0.75) return screen - size - pos;
        return pos + size / 2.0 - screen / 2.0;
    }

    /** Прилипание по одной оси: к отступу у краёв и к центру экрана. */
    public static double snap(double pos, double size, int screen) {
        double left = MARGIN, right = screen - size - MARGIN, center = screen / 2.0 - size / 2.0;
        if (Math.abs(pos - left) <= SNAP) return left;
        if (Math.abs(pos - right) <= SNAP) return right;
        if (Math.abs(pos - center) <= SNAP) return center;
        return clamp(pos, size, screen);
    }

    /** Колёсико: масштаб ± шаг, 0.5…2.0, округление до 0.1. */
    public static double scaleStep(double scale, double wheel) {
        double v = scale + Math.signum(wheel) * SCALE_STEP;
        v = Math.round(v * 10) / 10.0;
        return Math.max(SCALE_MIN, Math.min(SCALE_MAX, v));
    }
}
