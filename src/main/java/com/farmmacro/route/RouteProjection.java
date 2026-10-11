package com.farmmacro.route;

import java.util.List;

/**
 * v1.12 «Продолжить с места»: куда на маршруте встал игрок. Ищет ближайший отрезок (точка i → i+1)
 * по горизонтали; высота сильно штрафуется, чтобы на многоэтажной ферме не перепутать этаж.
 * Чистая математика — без Minecraft (проверяется в stress-тесте).
 */
public final class RouteProjection {
    private RouteProjection() {}

    /** Вес разницы по высоте: 1 блок по Y «стоит» как 3 блока по горизонтали. */
    public static final double Y_WEIGHT = 3.0;

    /** Результат: отрезок seg (от точки seg к seg+1), доля t 0…1, расстояние по горизонтали и по высоте. */
    public record Hit(int seg, double t, double dist, double dy) {
        /** Индекс точки, к которой идти дальше (конец отрезка). */
        public int target() { return seg + 1; }
    }

    /** null — меньше двух точек. y = NaN — высота не учитывается. */
    public static Hit nearest(List<RoutePoint> pts, double x, double y, double z) {
        if (pts == null || pts.size() < 2) return null;
        Hit best = null;
        double bestCost = Double.MAX_VALUE;
        for (int i = 0; i + 1 < pts.size(); i++) {
            RoutePoint a = pts.get(i), b = pts.get(i + 1);
            double ex = b.x - a.x, ez = b.z - a.z, len2 = ex * ex + ez * ez;
            double t = len2 < 1e-9 ? 0 : ((x - a.x) * ex + (z - a.z) * ez) / len2;
            t = Math.max(0, Math.min(1, t));
            double px = a.x + ex * t, pz = a.z + ez * t;
            double d = Math.hypot(x - px, z - pz);
            // высота: в пределах отрезка (спуск — от верха до низа) — без штрафа
            double dy = 0;
            if (!Double.isNaN(y)) {
                double lo = Math.min(a.y, b.y), hi = Math.max(a.y, b.y);
                dy = y < lo - 0.6 ? lo - y : y > hi + 0.6 ? y - hi : 0;
            }
            double cost = Math.hypot(d, dy * Y_WEIGHT);
            // при равенстве (стоим ровно в точке-стыке) — более поздний отрезок: туда и идём дальше
            if (cost <= bestCost + 1e-9) { bestCost = cost; best = new Hit(i, t, d, dy); }
        }
        return best;
    }
}
