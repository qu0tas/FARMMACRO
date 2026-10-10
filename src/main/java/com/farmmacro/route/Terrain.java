package com.farmmacro.route;

import java.util.List;

/**
 * «Мод видит блоки»: проходимость, препятствия и обрывы по коллизиям блоков. Без зависимостей от игры —
 * мир подаётся через {@link Cells} (в игре — {@link TerrainLevel}, в проверках — сетка-заглушка).
 * Высоты — как у ног игрока: верх коллизии блока под ногами (грядка 15/16, плита 0.5, забор 1.5).
 */
public final class Terrain {
    private Terrain() {}

    /** Верх коллизии блока (x, y, z) в долях блока (0…1.5) или −1, если коллизии нет (воздух, трава, пшеница). */
    @FunctionalInterface
    public interface Cells { double top(int x, int y, int z); }

    /** На сколько игрок поднимается шагом без прыжка. */
    public static final double STEP = 0.6;
    /** Ниже этого — «другой этаж» (падение, а не ступенька). */
    public static final double FLOOR_GAP = 0.6;

    public enum Issue {
        OK(null),
        BLOCKED("препятствие (блоки на пути)"),
        CLIFF("обрыв на пути — упадёт раньше времени"),
        NEED_DROP("следующая точка ниже — отметь «Спуск» (Ctrl+D)"),
        NO_EDGE("спуск: по направлению нет обрыва");
        public final String text;
        Issue(String t) { text = t; }
    }

    private static int fl(double v) { return (int) Math.floor(v); }

    /** Верх ближайшей опоры не выше {@code fromY} (+0.01) в колонке (x, z), не глубже maxDepth блоков; NaN — нет. */
    public static double floorY(Cells w, double x, double fromY, double z, int maxDepth) {
        int cx = fl(x), cz = fl(z), cy = fl(fromY + 0.01);
        for (int i = 0; i <= maxDepth + 1; i++, cy--) {
            double t = w.top(cx, cy, cz);
            if (t >= 0 && cy + t <= fromY + 0.01) return cy + t;
        }
        return Double.NaN;
    }

    /** Упирается ли игрок (ноги на высоте y) в блок в этой колонке: коллизия выше шага на уровне ног, тела или головы. */
    public static boolean blockedAt(Cells w, double x, double y, double z) {
        int cx = fl(x), cz = fl(z);
        for (double dy : new double[]{0.2, 1.0, 1.7}) {
            int cy = fl(y + dy);
            double t = w.top(cx, cy, cz);
            if (t >= 0 && cy + t > y + STEP) return true;
        }
        return false;
    }

    /**
     * Глубина спуска из точки p по направлению (dx, dz): идём от p шагом 0.1 до 2.5 бл, пока опора не окажется ниже
     * p.y больше чем на FLOOR_GAP. @return блоков падения или NaN (нет обрыва, стена или бездна глубже 64).
     */
    public static double dropDepth(Cells w, RoutePoint p, double dx, double dz) {
        if (dx == 0 && dz == 0) return Double.NaN;
        for (double s = 0.1; s <= 2.5 + 1e-9; s += 0.1) {
            double x = p.x + dx * s, z = p.z + dz * s;
            if (blockedAt(w, x, p.y, z)) return Double.NaN;
            double f = floorY(w, x, p.y, z, 64);
            if (Double.isNaN(f)) return Double.NaN;
            if (f < p.y - FLOOR_GAP) return p.y - f;
        }
        return Double.NaN;
    }

    /** Направление спуска точки i маршрута. */
    public static double[] dropDir(List<RoutePoint> pts, int i) {
        RoutePoint p = pts.get(i), prev = i > 0 ? pts.get(i - 1) : null, next = i + 1 < pts.size() ? pts.get(i + 1) : null;
        return p.dropVector(prev != null ? prev.x : 0, prev != null ? prev.z : 0, prev != null, next);
    }

    /** Посчитать dropDepth у точки-спуска i (0, если не нашли обрыв). @return глубина или NaN */
    public static double fillDepth(Cells w, List<RoutePoint> pts, int i) {
        RoutePoint p = pts.get(i);
        double[] d = dropDir(pts, i);
        double depth = dropDepth(w, p, d[0], d[1]);
        p.dropDepth = Double.isNaN(depth) ? 0 : Math.round(depth * 1000) / 1000.0;
        return depth;
    }

    /** Что не так с отрезком i → i+1. */
    public static Issue check(Cells w, List<RoutePoint> pts, int i) {
        RoutePoint a = pts.get(i), b = pts.get(i + 1);
        if (a.drop) {
            double[] d = dropDir(pts, i);
            return Double.isNaN(dropDepth(w, a, d[0], d[1])) ? Issue.NO_EDGE : Issue.OK;
        }
        if (a.y - b.y > FLOOR_GAP) return Issue.NEED_DROP;
        double dx = b.x - a.x, dz = b.z - a.z, len = Math.hypot(dx, dz);
        int n = (int) Math.min(2000, Math.ceil(len / 0.1));
        for (int k = 1; k < n; k++) {
            double t = (double) k / n, x = a.x + dx * t, z = a.z + dz * t;
            double y = a.y + Math.max(0, b.y - a.y) * t;      // подъём — постепенно (ступеньки/прыжок)
            if (blockedAt(w, x, y, z)) return Issue.BLOCKED;
            double f = floorY(w, x, y, z, 3);
            if (Double.isNaN(f) || f < Math.min(y, b.y) - FLOOR_GAP) return Issue.CLIFF;
        }
        return Issue.OK;
    }

    /**
     * Где вставить спуск на отрезке i → i+1 (следующая точка ниже): последняя позиция на верхнем этаже перед обрывом
     * по линии отрезка — центр её блока на высоте a.y. null — по линии обрыва нет (этажи друг под другом: отметь
     * точку i как спуск и задай направление).
     */
    public static double[] edgeOnSegment(Cells w, List<RoutePoint> pts, int i) {
        RoutePoint a = pts.get(i), b = pts.get(i + 1);
        double dx = b.x - a.x, dz = b.z - a.z, len = Math.hypot(dx, dz);
        int n = (int) Math.min(2000, Math.ceil(len / 0.1));
        double lastX = a.x, lastZ = a.z;
        for (int k = 1; k <= n; k++) {
            double t = (double) k / n, x = a.x + dx * t, z = a.z + dz * t;
            if (blockedAt(w, x, a.y, z)) return null;
            double f = floorY(w, x, a.y, z, 64);
            if (Double.isNaN(f) || f < a.y - FLOOR_GAP) {
                return new double[]{Math.floor(lastX) + 0.5, a.y, Math.floor(lastZ) + 0.5};
            }
            lastX = x; lastZ = z;
        }
        return null;
    }
}
