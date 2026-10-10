package com.farmmacro.route;

import com.google.gson.Gson;

/** Проверка смещения точки внутри блока без игры. Вызывается из routeStress. */
public final class OffsetCheck {
    private static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("смещение в блоке: " + what);
    }

    private static boolean near(double a, double b) { return Math.abs(a - b) < 1e-9; }

    public static void main() {
        RoutePoint p = new RoutePoint(10.5, 64, -3.5);
        p.setOffset(0.05, -0.2);
        check(near(p.x, 10.55) && near(p.z, -3.7) && near(p.centerX(), 10.5) && near(p.centerZ(), -3.5), "setOffset: " + p.x + " " + p.z);
        p.setOffset(0.9, -7);
        check(near(p.ox, 0.5) && near(p.oz, -0.5) && near(p.x, 11.0) && near(p.z, -4.0), "предел ±0.5");
        p.setOffset(0, 0);
        check(near(p.x, 10.5) && near(p.z, -3.5), "«В центр»");
        p.setOffset(0.1, 0.1);
        for (int i = 0; i < 100; i++) p.setOffset(p.ox + 0.01, p.oz);   // как стрелки по 0.01
        check(near(p.ox, 0.5) && near(p.x, 11.0), "серия сдвигов упирается в край, не уходит в соседний блок");
        RoutePoint q = p.withPos(20.5, 70, 0.5);
        check(near(q.x, 21.0) && near(q.z, 0.6) && near(q.y, 70) && near(q.ox, 0.5), "withPos (новая точка/вставка) сохраняет смещение");
        q.moveCenter(30.5, 71, 1.5);
        check(near(q.x, 31.0) && near(q.z, 1.6), "перетаскивание сохраняет смещение");
        RoutePoint c = q.copy();
        check(near(c.ox, q.ox) && near(c.oz, q.oz) && near(c.x, q.x), "copy() (снимок Ctrl+Z) копирует смещение");

        Gson gson = new Gson();
        RoutePoint r = gson.fromJson(gson.toJson(q), RoutePoint.class);
        check(near(r.ox, 0.5) && near(r.oz, 0.1) && near(r.x, 31.0), "запись/чтение файла");
        RoutePoint old = gson.fromJson("{\"x\":5.5,\"y\":64,\"z\":5.5}", RoutePoint.class);
        old.sanitize();
        check(near(old.ox, 0) && near(old.x, 5.5) && near(old.centerX(), 5.5), "старый файл без ox/oz — центр");
        RoutePoint bad = gson.fromJson("{\"x\":5.5,\"y\":64,\"z\":5.5,\"ox\":3,\"oz\":-0.25}", RoutePoint.class);
        bad.sanitize();
        check(near(bad.ox, 0.5) && near(bad.x, 3.0) && near(bad.oz, -0.25), "ручная правка: ox > 0.5 обрезается от того же центра");

        // стрелки относительно взгляда: yaw 0 — на юг (+Z), 90 — на запад (−X)
        double[] f0 = RoutePoint.axisStep(0, RoutePoint.FORWARD), r0 = RoutePoint.axisStep(0, RoutePoint.RIGHT);
        double[] f90 = RoutePoint.axisStep(90, RoutePoint.FORWARD), l90 = RoutePoint.axisStep(90, RoutePoint.LEFT);
        double[] b30 = RoutePoint.axisStep(30, RoutePoint.BACK);
        check(near(f0[0], 0) && near(f0[1], 1) && near(r0[0], -1) && near(r0[1], 0), "yaw 0: вперёд +Z, вправо −X");
        check(near(f90[0], -1) && near(f90[1], 0) && near(l90[0], 0) && near(l90[1], 1), "yaw 90: вперёд −X, влево +Z");
        check(near(b30[0], 0) && near(b30[1], -1), "yaw 30 → ближайшая ось Z, назад −Z");

        // ряды из точек со смещением
        var pts = java.util.List.of(TestRoutes.pt(0, 64, 0, RoutePoint.ATTACK), TestRoutes.pt(4, 64, 0, RoutePoint.NONE),
                TestRoutes.pt(4, 64, 2, RoutePoint.ATTACK), TestRoutes.pt(0, 64, 2, RoutePoint.NONE));
        for (RoutePoint s : pts) s.setOffset(0, 0.25);
        check(pts.stream().allMatch(s -> near(s.z - Math.floor(s.z), 0.75) && near(s.oz, 0.25)), "ряды: все точки смещены на Z +0.25");

        System.out.println("Смещение в блоке: ±0.5, центр, серии сдвигов, перенос/вставка/копия, файлы, стрелки по взгляду, ряды — OK");
    }
}
