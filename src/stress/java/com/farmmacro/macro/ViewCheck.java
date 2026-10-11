package com.farmmacro.macro;

import com.farmmacro.camera.FreelookMath;
import com.farmmacro.route.PointRoute;
import com.farmmacro.route.RoutePoint;
import com.farmmacro.route.RouteProjection;
import com.farmmacro.route.WalkCore;
import com.farmmacro.visual.LowRenderMath;

import java.util.Locale;
import java.util.Random;

/** v1.12: Low render (радиус), Freelook (углы), «Продолжить с места» (автоход с любого места дороги). */
public final class ViewCheck {
    private ViewCheck() {}

    private static void check(boolean ok, String what) { if (!ok) throw new IllegalStateException("ViewCheck: " + what); }

    public static void main() {
        // ── Low render: секция 16³ ──
        double px = 8.5, py = 70, pz = 8.5, r = 12;
        check(LowRenderMath.classify(0, 64, 0, 16, 80, 16, px, py, pz, r) == LowRenderMath.PARTIAL, "своя секция — пересекает");
        check(LowRenderMath.classify(32, 64, 0, 48, 80, 16, px, py, pz, r) == LowRenderMath.OUTSIDE, "через секцию — снаружи");
        check(LowRenderMath.classify(16, 64, 0, 32, 80, 16, px, py, pz, r) == LowRenderMath.PARTIAL, "соседняя — пересекает");
        check(LowRenderMath.classify(16, 64, 16, 32, 80, 32, px, py, pz, r) == LowRenderMath.PARTIAL, "по диагонали (угол в 10.6 бл) — пересекает");
        check(LowRenderMath.classify(8, 69, 8, 9, 71, 9, px, py, pz, r) == LowRenderMath.INSIDE, "сущность рядом — внутри");
        check(LowRenderMath.classify(0, 100, 0, 16, 116, 16, px, py, pz, r) == LowRenderMath.OUTSIDE, "высоко над — снаружи");
        // перебор: ни один блок в радиусе не отрезан, ни один дальше радиуса+диагональ секции не нарисован
        Random rnd = new Random(1);
        int cut = 0, far = 0;
        for (int i = 0; i < 200_000; i++) {
            double x = rnd.nextDouble() * 200 - 100, y = 60 + rnd.nextDouble() * 20, z = rnd.nextDouble() * 200 - 100;
            int sx = (int) Math.floor(x / 16) * 16, sy = (int) Math.floor(y / 16) * 16, sz = (int) Math.floor(z / 16) * 16;
            int k = LowRenderMath.classify(sx, sy, sz, sx + 16, sy + 16, sz + 16, px, py, pz, r);
            double d = Math.hypot(x - px, z - pz);
            if (d <= r && Math.abs(y - py) <= r && k == LowRenderMath.OUTSIDE) cut++;
            if (k != LowRenderMath.OUTSIDE && d > r + 16 * Math.sqrt(2) + 1e-9) far++;
        }
        check(cut == 0 && far == 0, "low render: отрезано в радиусе " + cut + ", нарисовано далеко " + far);

        // ── Freelook: углы ──
        float[] a = {135f, 0f};
        for (int i = 0; i < 1000; i++) a = FreelookMath.turn(a[0], a[1], 3, 2);
        check(Math.abs(a[0] - (135 + 450)) < 0.05 && a[1] == 90f, "freelook: yaw " + a[0] + " pitch " + a[1]);
        a = FreelookMath.turn(0, -89, 0, -100);
        check(a[1] == -90f, "freelook: pitch вниз ограничен");

        // ── «Продолжить с места» (N): игрок в случайном месте дороги → ближайший отрезок → дошёл до конца без паники ──
        PointRoute route = new PointRoute();
        route.points.addAll(com.farmmacro.route.TestRoutes.rows(0, 0, 19, 19, 3, 64));
        int n = route.points.size(), ok = 0, runs = 300, same = 0;
        Random rr = new Random(7);
        for (int k = 0; k < runs; k++) {
            int s0 = rr.nextInt(n - 1);
            RoutePoint a0 = route.points.get(s0), b0 = route.points.get(s0 + 1);
            double t0 = rr.nextDouble();
            double ex = b0.x - a0.x, ez = b0.z - a0.z, len = Math.hypot(ex, ez);
            double side = (rr.nextDouble() * 2 - 1) * 0.8;          // в стороне от линии до 0.8 бл
            double x = a0.x + ex * t0 - ez / len * side, z = a0.z + ez * t0 + ex / len * side;
            var hit = RouteProjection.nearest(route.points, x, 64, z);
            check(hit.dist() <= 0.81, "до дороги " + hit.dist());
            // другой отрезок — только если он не дальше своего (стык ряда или соседний ряд в 1 бл)
            check(hit.dist() <= Math.abs(side) + 1e-9, "отрезок " + (hit.seg() + 1) + " дальше своего " + (s0 + 1));
            if (hit.seg() == s0) same++;
            WalkCore core = new WalkCore();
            WalkCore.Out out = new WalkCore.Out();
            core.start(route.copy(), hit.target(), 30);
            check(core.target() == hit.target(), "первая цель — конец отрезка");
            double vx = 0, vz = 0;
            for (int t = 0; t < 60_000; t++) {
                core.tick(x, 64, z, true, 90f, 0.3, true, 30, true, 4.0, true, out);
                check(out.panic == null, "с места " + k + ": " + out.panic);
                check(core.target() >= hit.target(), "пошёл назад");
                if (core.done()) { ok++; break; }
                boolean moving = out.forward || out.back || out.left || out.right;
                double speed = moving ? (out.sprint ? WalkCore.SPRINT : WalkCore.WALK) : 0;
                double[] dir = WalkCore.moveDir(out, 90f);
                vx = vx * 0.55 + dir[0] * speed * 0.45; vz = vz * 0.55 + dir[1] * speed * 0.45;
                x += vx; z += vz;
            }
        }
        check(ok == runs, "дошли не со всех мест: " + ok + "/" + runs);
        check(same >= runs * 0.9, "свой отрезок только в " + same + "/" + runs);
        // этажи: такой же ряд на 4 бл ниже — выбирается свой этаж
        PointRoute two = new PointRoute();
        two.points.addAll(com.farmmacro.route.TestRoutes.rows(0, 0, 19, 19, 3, 64));
        int upper = two.points.size();
        two.points.addAll(com.farmmacro.route.TestRoutes.rows(0, 0, 19, 19, 3, 60));
        RoutePoint m = two.points.get(upper + 3);                  // середина ряда z=3 — над ним такой же ряд
        var low = RouteProjection.nearest(two.points, 9.5, 60, m.z);
        var high = RouteProjection.nearest(two.points, 9.5, 64, m.z);
        check(low.seg() >= upper && low.dy() == 0, "нижний этаж: отрезок " + (low.seg() + 1));
        check(high.seg() < upper - 1 && high.dy() == 0, "верхний этаж: отрезок " + (high.seg() + 1));
        check(RouteProjection.nearest(route.points, 40, 64, 40).dist() > 3, "далеко от дороги");
        System.out.printf(Locale.ROOT, "View (v1.12): low render 200000 блоков — в радиусе ничего не отрезано, дальше r+22.6 ничего не рисуется; "
                + "freelook — pitch ±90; «Продолжить с места» — %d случайных мест на дороге (до 0.8 бл в стороне): ближайший отрезок (свой в %d), вперёд, до конца без паники; этажи не путает — OK%n", ok, same);
    }
}
