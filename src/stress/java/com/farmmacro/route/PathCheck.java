package com.farmmacro.route;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * v1.15: проверка «Возврата на маршрут» без игры — A* ({@link PathFinder}) и ходьба по пути ({@link ReturnCore})
 * на сетке-заглушке: лестница, яма, вода, лава, тупик, предел, спуск 3/4, плиты, грядки, забор, этажи, порядок,
 * сглаживание, «сбили с пути» → новый путь, камера по ходу и в конце — ровно к камере маршрута.
 */
public final class PathCheck {

    /** Сетка: верх коллизии и опасность по клеткам; по умолчанию — воздух. */
    static final class Grid implements PathFinder.World {
        final Map<Long, Double> top = new HashMap<>();
        final Map<Long, Integer> hz = new HashMap<>();
        static long k(int x, int y, int z) { return ((long) (x + 1_000_000) << 40) | ((long) (y + 2048) << 24) | (z + 1_000_000L & 0xFFFFFF); }
        public double top(int x, int y, int z) { Double t = top.get(k(x, y, z)); return t == null ? -1 : t; }
        public int hazard(int x, int y, int z) { Integer h = hz.get(k(x, y, z)); return h == null ? 0 : h; }
        Grid set(int x, int y, int z, double t) { top.put(k(x, y, z), t); return this; }
        Grid clearCell(int x, int y, int z) { top.remove(k(x, y, z)); hz.remove(k(x, y, z)); return this; }
        Grid haz(int x, int y, int z, int h) { hz.put(k(x, y, z), h); return this; }
        /** Пол из блоков (верх на y + 1) в прямоугольнике. */
        Grid floor(int x0, int z0, int x1, int z1, int y) {
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) set(x, y, z, 1.0);
            return this;
        }
        Grid wall(int x0, int z0, int x1, int z1, int y0, int y1, double t) {
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = y0; y <= y1; y++) set(x, y, z, y == y1 ? t : 1.0);
            return this;
        }
    }

    static int checks;
    static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("PathCheck: " + what);
        checks++;
    }

    static PathFinder.Result find(Grid g, double x, double y, double z, List<PathFinder.Goal> goals, double max) {
        PathFinder.Params p = new PathFinder.Params();
        p.maxDist = max;
        return new PathFinder(g, p).find(x, y, z, true, goals);
    }

    // ── симуляция: XZ — инерция как у ходьбы, Y — гравитация MC, шаг 0.6, прыжок 0.42; камера — до 9°/тик ──
    record Sim(boolean done, int ticks, double x, double y, double z, float yaw, float pitch, int replans, String why,
               boolean touchedHazard, double maxTurnPerTick) {}

    static Sim walk(Grid g, double x, double y, double z, float yaw, float pitch, List<PathFinder.Goal> goals,
                    float endYaw, float endPitch, int pushAt, double pushX, int maxTicks) {
        PathFinder.Result r = find(g, x, y, z, goals, 64);
        if (!r.ok) return new Sim(false, 0, x, y, z, yaw, pitch, 0, r.reason, false, 0);
        ReturnCore rc = new ReturnCore();
        rc.start(r.points, x, z, endYaw, endPitch);
        WalkCore.Out out = new WalkCore.Out();
        double vx = 0, vz = 0, vy = 0;
        boolean onGround = true, hc = false, hazard = false;
        int replans = 0;
        double maxTurn = 0;
        for (int t = 0; t < maxTicks; t++) {
            ReturnCore.Phase ph = rc.tick(x, y, z, onGround, yaw, pitch, hc, out);
            if (ph == ReturnCore.Phase.DONE) return new Sim(true, t, x, y, z, yaw, pitch, replans, null, hazard, maxTurn);
            if (ph == ReturnCore.Phase.REPLAN) {
                if (++replans > 5) return new Sim(false, t, x, y, z, yaw, pitch, replans, "replans: " + rc.reason, hazard, maxTurn);
                PathFinder.Result r2 = find(g, x, y, z, goals, 64);
                if (!r2.ok) return new Sim(false, t, x, y, z, yaw, pitch, replans, r2.reason, hazard, maxTurn);
                rc.start(r2.points, x, z, endYaw, endPitch);
                continue;
            }
            // камера: как мышь — не быстрее 9°/тик (180°/с)
            float dy = WalkCore.wrap(rc.wantYaw - yaw), dp = rc.wantPitch - pitch;
            double d = Math.hypot(dy, dp);
            if (d > 0) { double k = Math.min(1, 9 / d); yaw += (float) (dy * k); pitch += (float) (dp * k); maxTurn = Math.max(maxTurn, d * k); }
            boolean moving = out.forward || out.back || out.left || out.right;
            double speed = moving ? WalkCore.WALK : 0;
            double[] dir = WalkCore.moveDir(out, yaw);
            double air = onGround ? 0.45 : 0.1;
            vx = vx * (1 - air) + dir[0] * speed * air; vz = vz * (1 - air) + dir[1] * speed * air;
            if (out.jump && onGround) { vy = 0.42; onGround = false; }
            if (t == pushAt) { x += pushX; vx = 0; vz = 0; }
            double nx = x + vx, nz = z + vz;
            hc = false;
            if (blocked(g, nx, y, nz)) {
                double up = support(g, nx, y + Terrain.STEP, nz);    // ступенька до 0.6 — шагнуть
                if (onGround && !Double.isNaN(up) && up > y && up <= y + Terrain.STEP + 1e-9 && !blocked(g, nx, up, nz)) { y = up; x = nx; z = nz; }
                else { hc = true; vx = 0; vz = 0; }
            } else { x = nx; z = nz; }
            double f = support(g, x, Math.max(y, y + vy), z);
            if (vy <= 0 && !Double.isNaN(f) && y - f < 1e-6 && y - f > -1e-6) { y = f; vy = 0; onGround = true; }
            else {
                onGround = false;
                y += vy;
                vy = (vy - 0.08) * 0.98;
                f = support(g, x, y + 0.5, z);
                if (!Double.isNaN(f) && y <= f && vy <= 0) { y = f; vy = 0; onGround = true; }
                if (y < -64) return new Sim(false, t, x, y, z, yaw, pitch, replans, "упал в бездну", hazard, maxTurn);
            }
            int cx = (int) Math.floor(x), cz = (int) Math.floor(z);
            for (int cy = (int) Math.floor(y + 0.01); cy <= (int) Math.floor(y + 1.7); cy++) if (g.hazard(cx, cy, cz) != 0) hazard = true;
        }
        return new Sim(false, maxTicks, x, y, z, yaw, pitch, replans, "не дошёл за " + maxTicks + " т", hazard, maxTurn);
    }

    /** Тело 0.6×1.8 упирается в коллизию выше шага (по 4 углам). */
    static boolean blocked(Grid g, double x, double y, double z) {
        for (double ox : new double[]{-0.299, 0.299}) for (double oz : new double[]{-0.299, 0.299})
            if (Terrain.blockedAt(g, x + ox, y, z + oz)) return true;
        return false;
    }

    static double support(Grid g, double x, double y, double z) {
        double best = Double.NaN;
        for (double ox : new double[]{-0.299, 0.299}) for (double oz : new double[]{-0.299, 0.299}) {
            double f = Terrain.floorY(g, x + ox, y, z + oz, 64);
            if (!Double.isNaN(f) && (Double.isNaN(best) || f > best)) best = f;
        }
        return best;
    }

    static List<PathFinder.Goal> at(double x, double y, double z) { return PathFinder.pointGoal(x, y, z, 0); }

    static double minY(PathFinder.Result r) { double m = 1e9; for (double[] n : r.nodes) m = Math.min(m, n[1]); return m; }

    static String s(Sim s) {
        return String.format(Locale.ROOT, "%s за %d т, (%.1f %.1f %.1f) yaw %.2f", s.done ? "дошёл" : "НЕТ (" + s.why + ")", s.ticks, s.x, s.y, s.z, s.yaw);
    }

    public static void main() {
        long t0 = System.nanoTime();
        StringBuilder rep = new StringBuilder();

        // 1. Лестница: 5 ступенек по 1 блоку вверх (прыжки), потом площадка
        Grid stairs = new Grid().floor(-2, -2, 12, 2, 63);
        for (int i = 0; i < 5; i++) stairs.wall(4 + i, -2, 12, 2, 64 + i, 64 + i, 1.0);
        PathFinder.Result st = find(stairs, 0.5, 64, 0.5, at(10.5, 69, 0.5), 64);
        check(st.ok, "лестница: " + st);
        long jumps = st.points.stream().filter(p -> p.jump).count();
        check(jumps == 5, "лестница: прыжков " + jumps + " (ждали 5)");
        Sim ss = walk(stairs, 0.5, 64, 0.5, 0, 0, at(10.5, 69, 0.5), -90f, 5f, -1, 0, 1200);
        check(ss.done && Math.abs(ss.y - 69) < 0.01, "лестница: симуляция " + s(ss));
        rep.append(String.format(Locale.ROOT, "лестница 5 бл — %d точек, %d прыжков, %s; ", st.points.size(), jumps, s(ss)));

        // 2. Яма 2 блока глубиной поперёк, обход сбоку — в яму не лезет (из неё не выбраться)
        Grid pit = new Grid().floor(-2, -6, 14, 6, 63);
        for (int x = 5; x <= 7; x++) for (int z = -6; z <= 3; z++) { pit.clearCell(x, 63, z); pit.clearCell(x, 62, z); pit.set(x, 61, z, 1.0); }
        PathFinder.Result pr = find(pit, 0.5, 64, 0.5, at(12.5, 64, 0.5), 64);
        check(pr.ok && minY(pr) >= 64 - 1e-9, "яма: путь " + pr + " мин. y " + minY(pr));
        Sim ps = walk(pit, 0.5, 64, 0.5, 0, 0, at(12.5, 64, 0.5), 0f, 0f, -1, 0, 1500);
        check(ps.done && Math.abs(ps.y - 64) < 0.01, "яма: симуляция " + s(ps));
        // из ямы: стоим на дне (y 62), выход — ступенька 1 бл с одной стороны
        Grid pit2 = new Grid().floor(-4, -4, 8, 4, 63);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) { pit2.clearCell(x, 63, z); pit2.clearCell(x, 62, z); pit2.set(x, 61, z, 1.0); }
        pit2.set(1, 62, 0, 1.0);                                         // ступенька внутри ямы
        PathFinder.Result p2 = find(pit2, -0.5, 62, 0.5, at(6.5, 64, 0.5), 64);
        check(p2.ok, "из ямы: " + p2);
        Sim p2s = walk(pit2, -0.5, 62, 0.5, 0, 0, at(6.5, 64, 0.5), 0f, 0f, -1, 0, 1500);
        check(p2s.done && Math.abs(p2s.y - 64) < 0.01, "из ямы: симуляция " + s(p2s));
        rep.append("яма — обход без спуска, из ямы по ступеньке; ");

        // 3. Вода: река поперёк, мост в 1 блок дальше по z — идёт по мосту, в воду не наступает
        Grid river = new Grid().floor(-2, -10, 14, 10, 63);
        for (int x = 5; x <= 7; x++) for (int z = -10; z <= 10; z++) { river.clearCell(x, 63, z); river.set(x, 62, z, 1.0); river.haz(x, 63, z, PathFinder.H_WATER); }
        for (int x = 5; x <= 7; x++) { river.set(x, 63, 7, 1.0); river.hz.remove(Grid.k(x, 63, 7)); }
        PathFinder.Result rv = find(river, 0.5, 64, 0.5, at(12.5, 64, 0.5), 64);
        check(rv.ok, "вода: " + rv);
        boolean viaBridge = rv.nodes.stream().anyMatch(n -> n[0] >= 5 && n[0] <= 8 && Math.floor(n[2]) == 7);
        check(viaBridge && minY(rv) >= 64 - 1e-9, "вода: не по мосту");
        Sim rs = walk(river, 0.5, 64, 0.5, 0, 0, at(12.5, 64, 0.5), 0f, 0f, -1, 0, 2000);
        check(rs.done && !rs.touchedHazard, "вода: симуляция " + s(rs) + " в воде: " + rs.touchedHazard);
        // лава — так же
        Grid lava = new Grid().floor(-2, -10, 14, 10, 63);
        for (int x = 5; x <= 6; x++) for (int z = -10; z <= 4; z++) { lava.clearCell(x, 63, z); lava.set(x, 62, z, 1.0); lava.haz(x, 63, z, PathFinder.H_LAVA); }
        PathFinder.Result lv = find(lava, 0.5, 64, 0.5, at(12.5, 64, 0.5), 64);
        check(lv.ok && lv.nodes.stream().allMatch(n -> !(n[0] >= 5 && n[0] < 7 && n[2] <= 5)), "лава: путь через лаву " + lv);
        Sim ls = walk(lava, 0.5, 64, 0.5, 0, 0, at(12.5, 64, 0.5), 0f, 0f, -1, 0, 2000);
        check(ls.done && !ls.touchedHazard, "лава: симуляция " + s(ls));
        rep.append("вода — по мосту, лава — в обход; ");

        // 4. Тупик: цель в загоне из стен 3 бл
        Grid pen = new Grid().floor(-10, -10, 10, 10, 63);
        pen.wall(4, -3, 8, -3, 64, 66, 1.0).wall(4, 3, 8, 3, 64, 66, 1.0).wall(4, -3, 4, 3, 64, 66, 1.0).wall(8, -3, 8, 3, 64, 66, 1.0);
        PathFinder.Result dead = find(pen, 0.5, 64, 0.5, at(6.5, 64, 0.5), 64);
        check(!dead.ok && dead.reason.contains("не найден"), "тупик: " + dead);
        rep.append(String.format(Locale.ROOT, "тупик — «%s» за %.1f мс (%d клеток); ", dead.reason, dead.micros / 1000.0, dead.expanded));

        // 5. Предел: 80 бл при пределе 64 — «далеко»; 60 — ок
        Grid open = new Grid().floor(-4, -4, 90, 4, 63);
        PathFinder.Result far = find(open, 0.5, 64, 0.5, at(80.5, 64, 0.5), 64);
        check(!far.ok && far.reason.contains("предела"), "предел: " + far);
        PathFinder.Result near = find(open, 0.5, 64, 0.5, at(60.5, 64, 0.5), 64);
        check(near.ok && near.points.size() == 1, "предел 60 бл: " + near + " (прямая — одна точка)");
        PathFinder.Result small = find(open, 0.5, 64, 0.5, at(20.5, 64, 0.5), 16);
        check(!small.ok, "предел 16: " + small);
        rep.append("предел 64: 80 бл — нет, 60 — одна прямая; ");

        // 6. Спуск: 3 блока — можно, 4 — нет (ищет лестницу сбоку)
        Grid cliff = new Grid().floor(-2, -6, 3, 6, 66).floor(4, -6, 12, 6, 63);
        PathFinder.Result c3 = find(cliff, 0.5, 67, 0.5, at(10.5, 64, 0.5), 64);
        check(c3.ok && c3.nodes.stream().anyMatch(n -> n[3] == PathFinder.Move.DROP.ordinal()), "спуск 3: " + c3);
        Sim c3s = walk(cliff, 0.5, 67, 0.5, -90, 0, at(10.5, 64, 0.5), -90f, 0f, -1, 0, 800);
        check(c3s.done && Math.abs(c3s.y - 64) < 0.01, "спуск 3: симуляция " + s(c3s));
        Grid cliff4 = new Grid().floor(-2, -6, 3, 6, 67).floor(4, -6, 12, 6, 63);
        PathFinder.Result c4 = find(cliff4, 0.5, 68, 0.5, at(10.5, 64, 0.5), 64);
        check(!c4.ok, "спуск 4: путь есть, а не должен " + c4);
        for (int i = 0; i < 3; i++) cliff4.wall(4 + i, 6, 4 + i, 6, 64, 66 - i, 1.0);   // ступени вниз по 1 бл у z = 6
        PathFinder.Result c4b = find(cliff4, 0.5, 68, 0.5, at(10.5, 64, 0.5), 64);
        double maxDrop = 0;
        for (int i = 1; c4b.ok && i < c4b.nodes.size(); i++) maxDrop = Math.max(maxDrop, c4b.nodes.get(i - 1)[1] - c4b.nodes.get(i)[1]);
        check(c4b.ok && maxDrop <= 3 + 1e-9, "спуск 4 по ступеням: " + c4b + " макс. спуск " + maxDrop);
        Sim c4s = walk(cliff4, 0.5, 68, 0.5, -90, 0, at(10.5, 64, 0.5), -90f, 0f, -1, 0, 1500);
        check(c4s.done && Math.abs(c4s.y - 64) < 0.01, "спуск по ступеням: симуляция " + s(c4s));
        rep.append("спуск 3 бл — да, 4 — нет (по ступеням — да); ");

        // 7. Плиты (0.5) — без прыжка; забор (1.5) — не перелезть, обход через калитку-проём; грядки — не спрыгивать
        Grid slab = new Grid().floor(-2, -2, 12, 2, 63);
        slab.wall(4, -2, 12, 2, 64, 64, 0.5).wall(6, -2, 12, 2, 64, 64, 1.0);
        PathFinder.Result sl = find(slab, 0.5, 64, 0.5, at(10.5, 65, 0.5), 64);
        check(sl.ok && sl.points.stream().noneMatch(p -> p.jump), "плиты: прыжок не нужен " + sl);
        Sim sls = walk(slab, 0.5, 64, 0.5, -90, 0, at(10.5, 65, 0.5), -90f, 0f, -1, 0, 800);
        check(sls.done && Math.abs(sls.y - 65) < 0.01, "плиты: симуляция " + s(sls));
        Grid fence = new Grid().floor(-2, -8, 12, 8, 63);
        fence.wall(5, -8, 5, 8, 64, 64, 1.5);
        fence.clearCell(5, 64, -6);                                       // проём в заборе
        PathFinder.Result fe = find(fence, 0.5, 64, 0.5, at(10.5, 64, 0.5), 64);
        check(fe.ok && fe.nodes.stream().anyMatch(n -> Math.floor(n[0]) == 5 && Math.floor(n[2]) == -6), "забор: не через проём " + fe);
        Sim fes = walk(fence, 0.5, 64, 0.5, -90, 0, at(10.5, 64, 0.5), -90f, 0f, -1, 0, 1500);
        check(fes.done, "забор: симуляция " + s(fes));
        Grid farm = new Grid().floor(-2, -8, 14, 8, 63);
        farm.wall(-2, -8, 3, 8, 64, 64, 1.0);                             // помост y 65
        for (int x = 4; x <= 14; x++) for (int z = -8; z <= 8; z++) farm.set(x, 63, z, 0.9375);   // грядки 63.9375
        for (int z = -8; z <= 8; z++) farm.set(4, 63, z, 1.0);            // край грядки — бортик
        PathFinder.Result fa = find(farm, 0.5, 65, 0.5, at(10.5, 63.9375, 0.5), 64);
        boolean jumpedOnFarmland = false;
        for (int i = 1; i < fa.nodes.size(); i++) {
            double[] a = fa.nodes.get(i - 1), b = fa.nodes.get(i);
            if (a[1] - b[1] > 0.5 && Math.abs(b[1] - Math.floor(b[1]) - 0.9375) < 0.01) jumpedOnFarmland = true;
        }
        check(fa.ok && !jumpedOnFarmland, "грядки: спрыгнул на грядку " + fa);
        rep.append("плиты без прыжка, забор — через проём, на грядку не спрыгивает; ");

        // 8. Этажи и ход: маршрут-ряды на двух этажах (y 64 и 69), игрок на нижнем — цель на нижнем
        Grid two = new Grid().floor(-2, -2, 20, 12, 63).floor(-2, -2, 20, 12, 68);
        for (int x = -2; x <= 20; x++) for (int z = -2; z <= 12; z++) two.clearCell(x, 68, z);
        two.floor(-2, 6, 20, 12, 68);                                     // верхний этаж над z 6…12
        List<RoutePoint> rows = new ArrayList<>();
        rows.add(new RoutePoint(0.5, 64, 0.5)); rows.add(new RoutePoint(18.5, 64, 0.5));
        rows.add(new RoutePoint(18.5, 64, 3.5)); rows.add(new RoutePoint(0.5, 64, 3.5));
        rows.add(new RoutePoint(0.5, 69, 8.5)); rows.add(new RoutePoint(18.5, 69, 8.5));
        PathFinder.Result fl = find(two, 9.5, 64, 7.5, PathFinder.routeGoals(rows, 64, -1, 0), 64);
        check(fl.ok && Math.abs(fl.goal.y - 64) < 0.01, "этажи: цель не на моём этаже " + fl + " y " + (fl.goal == null ? "-" : fl.goal.y));
        // порядок: был на отрезке 1→2 у x 9 — возвращаемся туда же, а не на ближайший 3→4
        PathFinder.Result ord = find(two, 9.5, 64, 2.5, PathFinder.routeGoals(rows, 64, 0, 0.5), 64);
        check(ord.ok && ord.goal.seg == 0 && Math.abs(ord.goal.x - 9.5) < 1.6, "порядок: цель " + ord + " x " + (ord.goal == null ? "-" : ord.goal.x));
        PathFinder.Result any = find(two, 9.5, 64, 2.6, PathFinder.routeGoals(rows, 64, -1, 0), 64);
        check(any.ok, "без порядка: " + any);
        rep.append(String.format(Locale.ROOT, "этаж — свой, порядок — отрезок %d у x %.1f; ", ord.goal.seg + 1, ord.goal.x));

        // 9. Сглаживание: открытое поле по диагонали — одна прямая; поле со столбом — 2–3 точки, все прямые проходимы
        Grid field = new Grid().floor(-20, -20, 40, 40, 63);
        PathFinder.Result fd = find(field, 0.5, 64, 0.5, at(25.5, 64, 18.5), 64);
        check(fd.ok && fd.points.size() == 1, "сглаживание: в поле " + fd.points.size() + " точек (ждали 1), узлов " + fd.nodes.size());
        field.wall(8, 3, 14, 9, 64, 65, 1.0);
        PathFinder.Result fd2 = find(field, 0.5, 64, 0.5, at(25.5, 64, 18.5), 64);
        PathFinder pf = new PathFinder(field, new PathFinder.Params());
        boolean allClear = fd2.ok;
        double[] prev = {0.5, 64, 0.5};
        for (RoutePoint p : fd2.points) { double[] q = {p.x, p.y, p.z}; if (!pf.lineClear(prev, q)) allClear = false; prev = q; }
        check(fd2.ok && fd2.points.size() <= 4 && allClear, "сглаживание со стеной: " + fd2.points.size() + " точек, прямые " + allClear);
        Sim fds = walk(field, 0.5, 64, 0.5, 0, 0, at(25.5, 64, 18.5), 12.5f, 3.2f, -1, 0, 1500);
        check(fds.done, "сглаживание: симуляция " + s(fds));
        rep.append(String.format(Locale.ROOT, "сглаживание: поле — 1 точка из %d узлов, со стеной — %d; ", fd.nodes.size(), fd2.points.size()));

        // 10. Сбили с пути: толчок на 3 бл вбок посреди пути — новый путь с текущего места, дошёл
        Grid lane = new Grid().floor(-2, -8, 40, 8, 63);
        Sim push = walk(lane, 0.5, 64, 0.5, -90, 0, at(35.5, 64, 0.5), -90f, 0f, 40, 0, 2000);
        Sim push2 = walkPushZ(lane);
        check(push.done && push2.done && push2.replans >= 1, "толчок: " + s(push2) + " перепланов " + push2.replans);
        rep.append("толчок 3 бл → новый путь (" + push2.replans + "), дошёл; ");

        // 11. Камера: по ходу поворачивается (не быстрее 9°/т), в конце — ровно к камере маршрута (≤ 0.6°)
        Sim cam = walk(field, 0.5, 64, 0.5, 170, 30, at(25.5, 64, 18.5), -37.3f, 4.1f, -1, 0, 1500);
        double endErr = ReturnCore.angle(cam.yaw, cam.pitch, -37.3f, 4.1f);
        check(cam.done && endErr <= ReturnCore.END_TOL_DEG && cam.maxTurnPerTick <= 9.0001, "камера: " + s(cam) + " ошибка " + endErr);
        rep.append(String.format(Locale.ROOT, "камера: конец %.2f° от маршрута; ", endErr));

        // 12. Скорость: поле 129×129 без цели внутри (загон) — сколько мс
        Grid big = new Grid().floor(-70, -70, 70, 70, 63);
        big.wall(30, -3, 36, -3, 64, 66, 1.0).wall(30, 3, 36, 3, 64, 66, 1.0).wall(30, -3, 30, 3, 64, 66, 1.0).wall(36, -3, 36, 3, 64, 66, 1.0);
        PathFinder.Result bg = find(big, 0.5, 64, 0.5, at(33.5, 64, 0.5), 64);
        check(!bg.ok, "загон в большом поле: " + bg);
        rep.append(String.format(Locale.ROOT, "худший случай (поле 145², цель в загоне): %d клеток за %.0f мс", bg.expanded, bg.micros / 1000.0));

        System.out.printf(Locale.ROOT, "PathCheck (v1.15 возврат на маршрут): %d проверок — OK (%.0f мс)%n  %s%n",
                checks, (System.nanoTime() - t0) / 1e6, rep);
    }

    /** Толчок вбок по Z на 3 бл на 40-м тике. */
    static Sim walkPushZ(Grid g) {
        List<PathFinder.Goal> goals = at(35.5, 64, 0.5);
        PathFinder.Result r = find(g, 0.5, 64, 0.5, goals, 64);
        ReturnCore rc = new ReturnCore();
        rc.start(r.points, 0.5, 0.5, -90f, 0f);
        WalkCore.Out out = new WalkCore.Out();
        double x = 0.5, y = 64, z = 0.5, vx = 0, vz = 0;
        float yaw = -90, pitch = 0;
        int replans = 0;
        for (int t = 0; t < 2000; t++) {
            ReturnCore.Phase ph = rc.tick(x, y, z, true, yaw, pitch, false, out);
            if (ph == ReturnCore.Phase.DONE) return new Sim(true, t, x, y, z, yaw, pitch, replans, null, false, 0);
            if (ph == ReturnCore.Phase.REPLAN) {
                replans++;
                PathFinder.Result r2 = find(g, x, y, z, goals, 64);
                if (!r2.ok) return new Sim(false, t, x, y, z, yaw, pitch, replans, r2.reason, false, 0);
                rc.start(r2.points, x, z, -90f, 0f);
                continue;
            }
            float dy = WalkCore.wrap(rc.wantYaw - yaw), dp = rc.wantPitch - pitch;
            double d = Math.hypot(dy, dp);
            if (d > 0) { double k = Math.min(1, 9 / d); yaw += (float) (dy * k); pitch += (float) (dp * k); }
            boolean moving = out.forward || out.back || out.left || out.right;
            double[] dir = WalkCore.moveDir(out, yaw);
            vx = vx * 0.55 + dir[0] * (moving ? WalkCore.WALK : 0) * 0.45; vz = vz * 0.55 + dir[1] * (moving ? WalkCore.WALK : 0) * 0.45;
            if (t == 40) z += 3;
            x += vx; z += vz;
        }
        return new Sim(false, 2000, x, y, z, yaw, pitch, replans, "не дошёл", false, 0);
    }
}
