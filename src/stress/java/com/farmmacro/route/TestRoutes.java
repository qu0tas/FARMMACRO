package com.farmmacro.route;

import java.util.ArrayList;
import java.util.List;

/**
 * Маршруты из точек для проверок автохода (v1.11: «змейки» в моде больше нет — точки задаются прямо в тестах).
 * {@link #rows} — ряды вдоль X туда-обратно: точки на концах рядов, у начала ряда ЛКМ, у конца — ничего.
 */
public final class TestRoutes {
    private TestRoutes() {}

    /** Точка в центре блока (x, z) на высоте y с действием отрезка до следующей точки. */
    public static RoutePoint pt(int x, double y, int z, String action) {
        RoutePoint p = new RoutePoint(x + 0.5, y, z + 0.5);
        p.action = action;
        return p;
    }

    /**
     * Ряды вдоль X от (x0, z0) до (x1, z1): ряд через каждые {@code step} блоков по Z (последний — ровно z1),
     * чётные ряды x0 → x1, нечётные обратно.
     */
    public static List<RoutePoint> rows(int x0, int z0, int x1, int z1, int step, double y) {
        List<Integer> zs = new ArrayList<>();
        int dir = z1 >= z0 ? 1 : -1;
        for (int z = z0; dir > 0 ? z <= z1 : z >= z1; z += dir * step) zs.add(z);
        if (zs.get(zs.size() - 1) != z1) zs.add(z1);
        List<RoutePoint> out = new ArrayList<>();
        for (int r = 0; r < zs.size(); r++) {
            boolean fwd = r % 2 == 0;
            out.add(pt(fwd ? x0 : x1, y, zs.get(r), RoutePoint.ATTACK));
            out.add(pt(fwd ? x1 : x0, y, zs.get(r), RoutePoint.NONE));
        }
        return out;
    }
}
