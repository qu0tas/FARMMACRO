package com.farmmacro.route;

import java.util.List;

/**
 * Симуляция автохода (WalkCore) без игры: простая кинематика (камера стоит на заданном yaw, движение — W/A/S/D
 * относительно него, разгон/инерция как у ходьбы), проверка, что маршрут из точек (ряды) проходится целиком без ложной паники
 * и без единого поворота камеры, а стена и снос дают «Застрял» и «Сошёл с маршрута».
 */
public final class WalkSim {

    record Result(int ticks, int reached, String panic, double maxSide, int pausedTicks) {}

    /** Тиков с нажатой клавишей движения и из них — по диагонали (две оси сразу), за последний run. */
    static int keyTicks, diagTicks;

    static Result run(PointRoute r, float yaw, int stuckTicks, boolean drift,
                      int wallAfter, int pushAt, int maxTicks) {
        WalkCore core = new WalkCore();
        WalkCore.Out out = new WalkCore.Out();
        keyTicks = 0; diagTicks = 0;
        core.start(r, 0, stuckTicks);
        RoutePoint p0 = r.points.get(0);
        double x = p0.x + 0.1, z = p0.z - 0.1, vx = 0, vz = 0;
        final float yaw0 = yaw;
        double maxSide = 0;
        int paused = 0, lastTarget = 0, reached = 0;
        for (int t = 0; t < maxTicks; t++) {
            core.tick(x, z, yaw, 0.3, true, stuckTicks, drift, 4.0, out);
            if (out.panic != null) return new Result(t, reached, out.panic, maxSide, paused);
            if (core.done()) return new Result(t, r.points.size(), null, maxSide, paused);
            if (core.target() != lastTarget) { reached += core.target() - lastTarget; lastTarget = core.target(); }
            if (yaw != yaw0) throw new IllegalStateException("камера повернулась");
            boolean moving = out.forward || out.back || out.left || out.right;
            double speed = moving ? (out.sneak ? WalkCore.SNEAK : out.sprint ? WalkCore.SPRINT : WalkCore.WALK) : 0;
            if (!moving && core.target() > 0) paused++;
            if (moving) { keyTicks++; if (out.forward ^ out.back && out.left ^ out.right) diagTicks++; }
            double[] dir = WalkCore.moveDir(out, yaw);
            double dx = dir[0] * speed, dz = dir[1] * speed;
            // инерция: скорость догоняет целевую за несколько тиков, как у ходьбы
            vx = vx * 0.55 + dx * 0.45; vz = vz * 0.55 + dz * 0.45;
            if (wallAfter >= 0 && t >= wallAfter) { vx = 0; vz = 0; }
            if (pushAt >= 0 && t == pushAt) x += 6;          // сносит вбок
            x += vx; z += vz;
            int ti = core.target();
            if (ti > 0) {
                RoutePoint a = r.points.get(ti - 1), b = r.points.get(ti);
                maxSide = Math.max(maxSide, RouteBuffer.distToSegmentXZ(x, z, a.x, a.z, b.x, b.z));
            }
        }
        return new Result(maxTicks, reached, "не дошёл за " + maxTicks + " тиков", maxSide, paused);
    }

    static PointRoute route(List<RoutePoint> pts) {
        PointRoute r = new PointRoute();
        r.points.addAll(pts);
        return r;
    }

    private static String res(Result r) { return r.panic == null ? "пройдена" : "ПАНИКА " + r.panic; }

    public static void main() {
        var rows = TestRoutes.rows(0, 0, 31, 31, 3, 64);
        boolean alongX = rows.get(0).z == rows.get(1).z;
        // yaw вдоль рядов: ряды по X → смотрим на −X (yaw 90) или +X (−90); по Z → +Z (0)
        float axisYaw = alongX ? 90f : 0f;
        Result ok = run(route(rows), axisYaw, 30, true, -1, -1, 20_000);
        System.out.printf("%nАвтоход по точкам: ряды 32×32 шаг 3 (%d точек), yaw %.0f° вдоль рядов: %s за %d тиков (%.0f с), "
                        + "макс. отклонение %.2f бл, диагональ %d из %d тиков%n",
                rows.size(), axisYaw, res(ok), ok.ticks, ok.ticks / 20.0, ok.maxSide, diagTicks, keyTicks);
        if (ok.panic != null || ok.reached != rows.size()) throw new IllegalStateException("маршрут не пройден: " + ok);
        if (ok.maxSide > 0.25) throw new IllegalStateException("вдоль оси ушёл вбок: " + ok);

        Result side = run(route(rows), axisYaw + 90f, 30, true, -1, -1, 20_000);
        System.out.printf("  yaw поперёк рядов (%.0f°, ряды — A/D): %s за %d тиков, макс. отклонение %.2f бл%n",
                axisYaw + 90f, res(side), side.ticks, side.maxSide);
        if (side.panic != null) throw new IllegalStateException("поперёк рядов паника: " + side);

        Result skew = run(route(rows), 37f, 30, true, -1, -1, 40_000);
        System.out.printf("  yaw 37° (не по оси): %s за %d тиков, макс. отклонение %.2f бл, диагональ %d из %d тиков%n",
                res(skew), skew.ticks, skew.maxSide, diagTicks, keyTicks);
        if (skew.panic != null) throw new IllegalStateException("yaw 37° дал панику: " + skew);

        for (RoutePoint p : rows) p.sprint = true;
        Result sprint = run(route(rows), axisYaw, 30, true, -1, -1, 20_000);
        System.out.printf("  со спринтом (спринт только при W): %s за %d тиков, макс. отклонение %.2f бл%n",
                res(sprint), sprint.ticks, sprint.maxSide);
        if (sprint.panic != null) throw new IllegalStateException("спринт дал панику: " + sprint);
        for (RoutePoint p : rows) p.sprint = false;

        Result wall = run(route(rows), axisYaw, 30, true, 100, -1, 20_000);
        System.out.printf("  стена на 100-м тике: %s (тик %d)%n", wall.panic, wall.ticks);
        if (wall.panic == null || !wall.panic.startsWith("Застрял") || wall.ticks > 100 + 30 + 5)
            throw new IllegalStateException("стена не дала «Застрял» вовремя: " + wall);

        Result push = run(route(rows), axisYaw, 30, true, -1, 150, 20_000);
        System.out.printf("  снос на 6 бл на 150-м тике: %s (тик %d)%n", push.panic, push.ticks);
        if (push.panic == null || !push.panic.startsWith("Сошёл")) throw new IllegalStateException("снос не дал «Сошёл»: " + push);

        var paused = new java.util.ArrayList<>(List.of(TestRoutes.pt(0, 64, 0, RoutePoint.ATTACK), TestRoutes.pt(9, 64, 0, RoutePoint.NONE)));
        paused.add(new RoutePoint(9.5, 64, 6.5));
        paused.get(1).pauseTicks = 40;
        Result pz = run(route(paused), 90f, 30, true, -1, -1, 5_000);
        System.out.printf("  пауза 40 т в точке 2: %s, стояли %d тиков%n", pz.panic == null ? "ок" : pz.panic, pz.pausedTicks);
        if (pz.panic != null || pz.pausedTicks < 40) throw new IllegalStateException("пауза не выдержана: " + pz);
    }
}
