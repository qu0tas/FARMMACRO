package com.farmmacro.route;

/**
 * v1.11 «Аномалия скорости» на симуляции автохода: «змейка» с ванильной инерцией (0.546) и с инерцией WalkSim (0.55)
 * проходится без паники, с остановками «Случайности» — тоже; толчок без пакета — паника, с пакетом — нет;
 * мелкий толчок ниже допуска — нет; упор в стену с ходьбы (0.215 < 0.25) — не аномалия, а «Застрял»; удар в стену
 * при допуске 0.1 без флага столкновения — аномалия, с флагом — снова «Застрял».
 */
public final class VelocitySim {

    record R(int ticks, String panic, double maxDiff) {}

    /**
     * @param keep      инерция симуляции
     * @param pushAt    тик толчка (−1 — нет), pushV — скорость толчка, бл/т
     * @param pushPacket толчок пришёл пакетом (оправдание на 4 тика, как PanicDetector.pushExcused)
     * @param wallAt    тик, с которого стена гасит скорость; wallFlag — игра сообщила о столкновении
     * @param stopsEvery внешняя остановка каждые N тиков на 20 тиков (0 — нет)
     */
    static R run(double keep, int pushAt, double pushV, boolean pushPacket, int wallAt, boolean wallFlag, int stopsEvery) {
        return run(keep, pushAt, pushV, pushPacket, wallAt, wallFlag, stopsEvery, 0.25);
    }

    static R run(double keep, int pushAt, double pushV, boolean pushPacket, int wallAt, boolean wallFlag, int stopsEvery, double limit) {
        var snake = SnakeBuilder.build(0, 0, 31, 31, 3, "auto", "attack", "none", (x, z) -> 64);
        boolean alongX = snake.get(0).z == snake.get(1).z;
        float yaw = alongX ? 90f : 0f;
        PointRoute r = WalkSim.route(snake);
        WalkCore core = new WalkCore();
        WalkCore.Out out = new WalkCore.Out();
        VelocityCheck vc = new VelocityCheck();
        core.start(r, 0, 30);
        vc.reset();
        RoutePoint p0 = r.points.get(0);
        double x = p0.x + 0.1, z = p0.z - 0.1, vx = 0, vz = 0, maxDiff = 0;
        int grace = 0;
        for (int t = 0; t < 30_000; t++) {
            boolean excuse = grace > 0 || (wallFlag && wallAt >= 0 && t > wallAt);
            if (grace > 0) grace--;
            String p = vc.observe(x, z, excuse, limit);
            maxDiff = Math.max(maxDiff, vc.lastDiff);
            if (p != null) return new R(t, p, maxDiff);
            if (stopsEvery > 0 && t > 0 && t % stopsEvery == 0) core.pauseExternal(20);
            core.tick(x, z, yaw, 0.3, true, 30, false, 4.0, out);
            if (out.panic != null) return new R(t, "WalkCore: " + out.panic, maxDiff);
            if (core.done()) return new R(t, null, maxDiff);
            vc.command(out, yaw);
            boolean moving = out.forward || out.back || out.left || out.right;
            double speed = moving ? (out.sneak ? WalkCore.SNEAK : out.sprint ? WalkCore.SPRINT : WalkCore.WALK) : 0;
            double[] dir = WalkCore.moveDir(out, yaw);
            vx = vx * keep + dir[0] * speed * (1 - keep);
            vz = vz * keep + dir[1] * speed * (1 - keep);
            if (t == pushAt) { vx += pushV; if (pushPacket) grace = 4; }
            if (wallAt >= 0 && t >= wallAt) { vx = 0; vz = 0; }
            x += vx; z += vz;
        }
        return new R(30_000, "не дошёл", maxDiff);
    }

    private static void ok(boolean c, String what, R r) {
        System.out.printf("  %-58s %s (тиков %d, макс. разница %.3f бл/т)%n", what,
                r.panic == null ? "без паники" : "ПАНИКА: " + r.panic, r.ticks, r.maxDiff);
        if (!c) throw new IllegalStateException("VelocitySim: " + what + " → " + r);
    }

    public static void main() {
        System.out.printf("%nАномалия скорости (v1.11, допуск 0.25 бл/т):%n");
        R a = run(VelocityCheck.KEEP, -1, 0, false, -1, false, 0);
        ok(a.panic == null, "змейка 32×32, ванильная инерция", a);
        R b = run(0.55, -1, 0, false, -1, false, 0);
        ok(b.panic == null, "змейка, инерция WalkSim 0.55", b);
        R c = run(VelocityCheck.KEEP, -1, 0, false, -1, false, 150);
        ok(c.panic == null, "змейка с остановками 20 т каждые 150 т", c);
        R d = run(VelocityCheck.KEEP, 400, 0.6, false, -1, false, 0);
        ok(d.panic != null && d.panic.startsWith("Аномалия скорости"), "толчок 0.6 бл/т без пакета", d);
        R e = run(VelocityCheck.KEEP, 400, 0.6, true, -1, false, 0);
        ok(e.panic == null, "толчок 0.6 бл/т пакетом (оправдан)", e);
        R f = run(VelocityCheck.KEEP, 400, 0.15, false, -1, false, 0);
        ok(f.panic == null, "толчок 0.15 бл/т (ниже допуска)", f);
        R g = run(VelocityCheck.KEEP, -1, 0, false, 300, false, 0);
        ok(g.panic != null && g.panic.contains("Застрял"), "стена с ходьбы: 0.215 < 0.25 — не аномалия, «Застрял»", g);
        R h = run(VelocityCheck.KEEP, -1, 0, false, 300, false, 0, 0.1);
        ok(h.panic != null && h.panic.startsWith("Аномалия"), "стена, допуск 0.1, без флага столкновения", h);
        R i = run(VelocityCheck.KEEP, -1, 0, false, 300, true, 0, 0.1);
        ok(i.panic != null && i.panic.contains("Застрял"), "стена, допуск 0.1, столкновение (оправдано)", i);
        if (a.maxDiff > 0.05) throw new IllegalStateException("VelocitySim: модель расходится с ходьбой на " + a.maxDiff);
    }
}
