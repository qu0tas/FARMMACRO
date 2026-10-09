package com.farmmacro.route;

import java.util.List;

/**
 * Симуляция автохода (WalkCore) без игры: простая кинематика (поворот с ограничением скорости,
 * разгон/инерция как у ходьбы), проверка, что «змейка» проходится целиком без ложной паники,
 * а стена и снос дают «Застрял» и «Сошёл с маршрута».
 */
public final class WalkSim {

    record Result(int ticks, int reached, String panic, double maxSide, int pausedTicks) {}

    static Result run(PointRoute r, double turnDegPerSec, int stuckTicks, boolean drift,
                      int wallAfter, int pushAt, int maxTicks) {
        WalkCore core = new WalkCore();
        WalkCore.Out out = new WalkCore.Out();
        core.start(r, 0, stuckTicks);
        RoutePoint p0 = r.points.get(0);
        double x = p0.x + 0.1, z = p0.z - 0.1, vx = 0, vz = 0;
        float yaw = 37;
        double maxSide = 0;
        int paused = 0, lastTarget = 0, reached = 0;
        for (int t = 0; t < maxTicks; t++) {
            core.tick(x, z, yaw, 0.3, true, stuckTicks, drift, 4.0, out);
            if (out.panic != null) return new Result(t, reached, out.panic, maxSide, paused);
            if (core.done()) return new Result(t, r.points.size(), null, maxSide, paused);
            if (core.target() != lastTarget) { reached += core.target() - lastTarget; lastTarget = core.target(); }
            // поворот: как SmoothTurn за тик (20 кадров/с упрощённо)
            float err = WalkCore.wrap(out.yaw - yaw);
            float step = (float) (turnDegPerSec / 20.0 * Math.max(0.2, Math.min(1, Math.abs(err) / 12.0)));
            yaw += Math.abs(err) <= step ? err : Math.signum(err) * step;
            double speed = out.forward ? (out.sneak ? WalkCore.SNEAK : out.sprint ? WalkCore.SPRINT : WalkCore.WALK) : 0;
            if (!out.forward && core.target() > 0) paused++;
            double rad = Math.toRadians(yaw);
            double dx = -Math.sin(rad) * speed, dz = Math.cos(rad) * speed;
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
        r.pitch = 30;
        return r;
    }

    public static void main() {
        var snake = SnakeBuilder.build(0, 0, 31, 31, 3, "auto", "attack", "none", (x, z) -> 64);
        Result ok = run(route(snake), 180, 30, true, -1, -1, 20_000);
        System.out.printf("%nАвтоход «змейка» 32×32 шаг 3 (%d точек), поворот 180°/с: %s за %d тиков (%.0f с), макс. отклонение %.2f бл%n",
                snake.size(), ok.panic == null ? "пройдена" : "ПАНИКА " + ok.panic, ok.ticks, ok.ticks / 20.0, ok.maxSide);
        if (ok.panic != null || ok.reached != snake.size()) throw new IllegalStateException("змейка не пройдена: " + ok);

        Result slow = run(route(snake), 45, 30, true, -1, -1, 40_000);
        System.out.printf("  поворот 45°/с: %s за %d тиков, макс. отклонение %.2f бл%n",
                slow.panic == null ? "пройдена" : "ПАНИКА " + slow.panic, slow.ticks, slow.maxSide);
        if (slow.panic != null) throw new IllegalStateException("медленный поворот дал панику: " + slow);

        for (RoutePoint p : snake) p.sprint = true;
        Result sprint = run(route(snake), 180, 30, true, -1, -1, 20_000);
        System.out.printf("  со спринтом: %s за %d тиков, макс. отклонение %.2f бл%n",
                sprint.panic == null ? "пройдена" : "ПАНИКА " + sprint.panic, sprint.ticks, sprint.maxSide);
        if (sprint.panic != null) throw new IllegalStateException("спринт дал панику: " + sprint);
        for (RoutePoint p : snake) p.sprint = false;

        Result wall = run(route(snake), 180, 30, true, 100, -1, 20_000);
        System.out.printf("  стена на 100-м тике: %s (тик %d)%n", wall.panic, wall.ticks);
        if (wall.panic == null || !wall.panic.startsWith("Застрял") || wall.ticks > 100 + 30 + 5)
            throw new IllegalStateException("стена не дала «Застрял» вовремя: " + wall);

        Result push = run(route(snake), 180, 30, true, -1, 150, 20_000);
        System.out.printf("  снос на 6 бл на 150-м тике: %s (тик %d)%n", push.panic, push.ticks);
        if (push.panic == null || !push.panic.startsWith("Сошёл")) throw new IllegalStateException("снос не дал «Сошёл»: " + push);

        var paused = SnakeBuilder.build(0, 0, 9, 0, 3, "x", "attack", "none", (x, z) -> 64);
        paused.add(new RoutePoint(9.5, 64, 6.5));
        paused.get(1).pauseTicks = 40;
        Result pz = run(route(paused), 180, 30, true, -1, -1, 5_000);
        System.out.printf("  пауза 40 т в точке 2: %s, стояли %d тиков%n", pz.panic == null ? "ок" : pz.panic, pz.pausedTicks);
        if (pz.panic != null || pz.pausedTicks < 40) throw new IllegalStateException("пауза не выдержана: " + pz);
    }
}
