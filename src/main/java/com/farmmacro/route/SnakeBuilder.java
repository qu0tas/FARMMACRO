package com.farmmacro.route;

import java.util.ArrayList;
import java.util.List;

/**
 * «Змейка» по прямоугольнику: ряды с шагом {@code step} блоков, через один в обратную сторону.
 * Начинается в углу A и идёт к углу B. На ряд — две точки (начало и конец): у начала действие ряда,
 * у конца — действие перехода на следующий ряд. Если ширина не делится на шаг, последний ряд кладётся по краю B.
 * Без зависимостей от игры (проверяется в routeStress).
 */
public final class SnakeBuilder {
    private SnakeBuilder() {}

    /** Высота верхней грани в колонке (x, z) блоков. */
    @FunctionalInterface
    public interface Surface { double y(int x, int z); }

    public static final String AXIS_AUTO = "auto", AXIS_X = "x", AXIS_Z = "z";

    /** Вдоль какой оси идут ряды (auto — вдоль длинной стороны, при равных — вдоль X). */
    public static boolean rowsAlongX(int ax, int az, int bx, int bz, String axis) {
        if (AXIS_X.equals(axis)) return true;
        if (AXIS_Z.equals(axis)) return false;
        return Math.abs(bx - ax) >= Math.abs(bz - az);
    }

    /** Позиции рядов от a к b с шагом step (последний — ровно b). */
    static int[] rows(int a, int b, int step) {
        int len = Math.abs(b - a), dir = b >= a ? 1 : -1;
        int n = len / step + 1;
        boolean extra = len % step != 0;
        int[] out = new int[n + (extra ? 1 : 0)];
        for (int i = 0; i < n; i++) out[i] = a + dir * i * step;
        if (extra) out[n] = b;
        return out;
    }

    public static List<RoutePoint> build(int ax, int az, int bx, int bz, int step, String axis,
                                         String rowAction, String turnAction, Surface surface) {
        step = Math.max(1, step);
        boolean alongX = rowsAlongX(ax, az, bx, bz, axis);
        int[] rs = alongX ? rows(az, bz, step) : rows(ax, bx, step);
        int s0 = alongX ? ax : az, s1 = alongX ? bx : bz;
        List<RoutePoint> out = new ArrayList<>(rs.length * 2);
        for (int r = 0; r < rs.length; r++) {
            boolean fwd = r % 2 == 0;
            int from = fwd ? s0 : s1, to = fwd ? s1 : s0;
            int rowPos = rs[r];
            RoutePoint a = point(alongX, from, rowPos, surface);
            a.action = rowAction;
            RoutePoint b = point(alongX, to, rowPos, surface);
            b.action = r == rs.length - 1 ? RoutePoint.NONE : turnAction;
            out.add(a);
            if (from != to) out.add(b);
            else a.action = r == rs.length - 1 ? RoutePoint.NONE : turnAction;    // ряд из одного блока
        }
        return out;
    }

    private static RoutePoint point(boolean alongX, int along, int rowPos, Surface s) {
        int x = alongX ? along : rowPos, z = alongX ? rowPos : along;
        return new RoutePoint(x + 0.5, s.y(x, z), z + 0.5);
    }
}
