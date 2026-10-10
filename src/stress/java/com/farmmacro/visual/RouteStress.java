package com.farmmacro.visual;

import com.farmmacro.macro.MacroFrame;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Оценка упрощения маршрута на 70 000 кадров (≈58 минут игры) вне игры.
 * Запуск: ./gradlew routeStress
 */
public final class RouteStress {

    public static void main(String[] args) {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 70_000;
        report("Ферма-«змейка» (ряды 96 бл, ЛКМ на рядах, дрожание ±3 мм)", farm(n), 48);
        report("Хаотичная прогулка (поворот каждые 1–3 с, прыжки) — худший случай", wander(n), 48);
        snake();
        com.farmmacro.route.WalkSim.main();
        com.farmmacro.camera.CameraBindingCheck.main();
        com.farmmacro.macro.HoldCheck.main();
        com.farmmacro.gui.NumberInputCheck.main();
        com.farmmacro.route.OffsetCheck.main();
    }

    /** «Змейка» по точкам: проверка рядов и геометрии маршрута по точкам. */
    private static void snake() {
        var pts = com.farmmacro.route.SnakeBuilder.build(0, 0, 95, 95, 3, "auto", "attack", "none", (x, z) -> 64);
        int rows = 0;
        for (int i = 0; i + 1 < pts.size(); i += 2) {
            var a = pts.get(i); var b = pts.get(i + 1);
            if (a.z != b.z || !a.attack()) throw new IllegalStateException("ряд " + rows + " не по X или без ЛКМ");
            rows++;
        }
        var last = pts.get(pts.size() - 1);
        if (last.z != 95.5 || !"none".equals(last.action)) throw new IllegalStateException("последний ряд не на краю B: " + last.z);
        var edge = com.farmmacro.route.SnakeBuilder.build(0, 0, 9, 3, 1, "z", "use", "none", (x, z) -> 64);
        if (edge.get(0).x != 0.5 || edge.get(1).z != 3.5) throw new IllegalStateException("ось z не соблюдена");
        var one = com.farmmacro.route.SnakeBuilder.build(5, 5, 5, 5, 3, "auto", "attack", "none", (x, z) -> 64);
        if (one.size() != 1) throw new IllegalStateException("1×1 должен дать одну точку: " + one.size());
        long t0 = System.nanoTime();
        var big = com.farmmacro.route.SnakeBuilder.build(0, 0, 2047, 4095, 1, "auto", "attack", "none", (x, z) -> 64);
        double msSnake = (System.nanoTime() - t0) / 1e6;
        t0 = System.nanoTime();
        RoutePath p = RoutePath.fromPoints(pts, 4.0, 4.0);
        double msPath = (System.nanoTime() - t0) / 1e6;
        System.out.printf("%n«Змейка» 96×96, шаг 3: рядов %d, точек %d → отрезков ленты %d, стрелок %d, %.2f мс%n",
                rows, pts.size(), p.size - 1, p.arrows, msPath);
        System.out.printf("«Змейка» 2048×4096, шаг 1: точек %d за %.1f мс (в редакторе предел %d)%n",
                big.size(), msSnake, com.farmmacro.route.RouteBuffer.MAX_POINTS);
    }

    private static void report(String name, List<MacroFrame> frames, int radius) {
        // прогрев
        for (int i = 0; i < 3; i++) RoutePath.build(frames, 0.25, 0.08, 4.0, 4.0, true);
        long t0 = System.nanoTime();
        RoutePath p = RoutePath.build(frames, 0.25, 0.08, 4.0, 4.0, true);
        double msFull = (System.nanoTime() - t0) / 1e6;
        t0 = System.nanoTime();
        RoutePath live = RoutePath.build(frames, 0.25, 0.08, 4.0, 4.0, false);
        double msLive = (System.nanoTime() - t0) / 1e6;

        int segs = Math.max(0, p.size - 1);
        // вершины: лента 4 (обычный проход) + 8 (сквозь стены, оба обхода); стрелка 8 + 16; отметка 4 + 8
        long vAll = segs * 12L + p.arrows * 24L + p.marks * 12L;
        // в радиусе от точки посередине маршрута
        float cx = p.x[p.size / 2], cz = p.z[p.size / 2];
        float r2 = radius * radius;
        int sIn = 0, aIn = 0, mIn = 0;
        for (int i = 1; i < p.size; i++) {
            float mx = (p.x[i] + p.x[i - 1]) / 2 - cx, mz = (p.z[i] + p.z[i - 1]) / 2 - cz;
            if (mx * mx + mz * mz <= r2) sIn++;
        }
        for (int i = 0; i < p.arrows; i++) { float dx = p.ax[i] - cx, dz = p.az[i] - cz; if (dx * dx + dz * dz <= r2) aIn++; }
        for (int i = 0; i < p.marks; i++) { float dx = p.mx[i] - cx, dz = p.mz[i] - cz; if (dx * dx + dz * dz <= r2) mIn++; }
        long vFrame = sIn * 12L + aIn * 24L + mIn * 12L;

        System.out.printf("%n%s%n", name);
        System.out.printf("  кадров %d → точек %d (запись в реальном времени, без RDP: %d)%n", frames.size(), p.size, live.size);
        System.out.printf("  стрелок %d, отметок прыжка/приседания %d%n", p.arrows, p.marks);
        System.out.printf("  упрощение: %.1f мс полное, %.1f мс без RDP%n", msFull, msLive);
        System.out.printf("  вершин на весь маршрут: %d; в радиусе %d бл за кадр: %d (отрезков %d, стрелок %d)%n",
                vAll, radius, vFrame, sIn, aIn);
    }

    private static MacroFrame f(double x, double y, double z, boolean attack, boolean jump) {
        return new MacroFrame(x, y, z, 0, 0, true, false, false, false, jump, false, false, attack, false, 0);
    }

    private static List<MacroFrame> farm(int n) {
        Random r = new Random(1);
        List<MacroFrame> out = new ArrayList<>(n);
        double x = 0, z = 0, speed = 0.216; int dir = 1; double rowLen = 96; double along = 0; int side = 0;
        for (int i = 0; i < n; i++) {
            boolean turning = side > 0;
            if (turning) { x += speed; side--; }
            else {
                z += dir * speed; along += speed;
                if (along >= rowLen) { along = 0; dir = -dir; side = (int) Math.round(3 / speed); }
            }
            out.add(f(x + (r.nextDouble() - 0.5) * 0.006, 64, z + (r.nextDouble() - 0.5) * 0.006, !turning, false));
        }
        return out;
    }

    private static List<MacroFrame> wander(int n) {
        Random r = new Random(2);
        List<MacroFrame> out = new ArrayList<>(n);
        double x = 0, y = 64, z = 0, yaw = 0; int nextTurn = 20;
        for (int i = 0; i < n; i++) {
            if (--nextTurn <= 0) { yaw += (r.nextDouble() - 0.5) * Math.PI; nextTurn = 20 + r.nextInt(40); }
            yaw += (r.nextDouble() - 0.5) * 0.02;
            x += Math.cos(yaw) * 0.216; z += Math.sin(yaw) * 0.216;
            boolean jump = r.nextInt(200) == 0;
            out.add(f(x, y, z, r.nextInt(3) == 0, jump));
        }
        return out;
    }
}
