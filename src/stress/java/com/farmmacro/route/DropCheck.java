package com.farmmacro.route;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Этажи и спуск вне игры: Terrain на сетке-заглушке (препятствие, обрыв, «нужен спуск», глубина, край на отрезке)
 * и симуляция автохода с высотой и гравитацией: двухэтажная ферма «влево по верхнему → упал → вправо по нижнему».
 */
public final class DropCheck {

    /** Сетка блоков: верх коллизии по (x, y, z), по умолчанию воздух. */
    static final class Grid implements Terrain.Cells {
        final Map<Long, Double> m = new HashMap<>();
        static long k(int x, int y, int z) { return ((long) (x + 1_000_000) << 40) | ((long) (y + 2048) << 24) | (z + 1_000_000L & 0xFFFFFF); }
        Grid set(int x, int y, int z, double top) { m.put(k(x, y, z), top); return this; }
        Grid fill(int x0, int x1, int y, int z0, int z1, double top) {
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) set(x, y, z, top);
            return this;
        }
        public double top(int x, int y, int z) { Double t = m.get(k(x, y, z)); return t == null ? -1 : t; }
    }

    /** Верхний этаж: пол y=69 (ноги 70), x 0…9; нижний: пол y=65 (ноги 66), x −3…9; над x = −1…−3 дыра. */
    static Grid farm() {
        return new Grid().fill(0, 9, 69, 0, 0, 1).fill(-3, 9, 65, 0, 0, 1);
    }

    static List<RoutePoint> route(boolean drop) {
        RoutePoint a = new RoutePoint(9.5, 70, 0.5), b = new RoutePoint(0.5, 70, 0.5), c = new RoutePoint(9.5, 66, 0.5);
        b.drop = drop;
        b.landPauseTicks = 10;
        return new java.util.ArrayList<>(List.of(a, b, c));
    }

    static boolean DEBUG;
    record Sim(int ticks, String panic, double fall, int landWait, boolean done) {}

    /** Простая кинематика: XZ — как в WalkSim, Y — гравитация MC (−0.08, ×0.98), опора — Terrain.floorY. */
    static Sim sim(Terrain.Cells g, List<RoutePoint> pts, float yaw, int maxTicks) { return sim(g, pts, yaw, maxTicks, null); }

    static Sim sim(Terrain.Cells g, List<RoutePoint> pts, float yaw, int maxTicks, com.farmmacro.macro.Humanizer human) {
        PointRoute r = new PointRoute();
        for (RoutePoint p : pts) r.points.add(p.copy());
        WalkCore core = new WalkCore();
        WalkCore.Out out = new WalkCore.Out();
        if (human != null) human.shapeRoute(r);
        core.start(r, 0, 30);
        if (human != null) core.setReach(human.reachRadii(r.points.size(), 0.3));
        var sh = human != null ? human.routeShaper() : null;
        double x = pts.get(0).x, z = pts.get(0).z, y = pts.get(0).y, vx = 0, vz = 0, vy = 0;
        boolean onGround = true;
        int landWait = 0;
        boolean landed = false;
        for (int t = 0; t < maxTicks; t++) {
            core.tick(x, y, z, onGround, yaw, 0.3, true, 30, true, 4.0, true, out);
            if (out.panic != null) return new Sim(t, out.panic, core.lastFall(), landWait, false);
            if (sh != null) {
                int ss = core.segment();
                sh.tick(out, x, z, core.target(), core.dropPhase(), ss >= 0 && r.points.get(ss).airHold);
                if (sh.idleNow) core.resetStuckWindow();
            }
            if (DEBUG) System.out.printf(Locale.ROOT, "t%d x%.2f y%.2f g%s tgt%d ph%d keys%s%s%s%s%n", t, x, y, onGround, core.target(), core.dropPhase(),
                    out.forward ? "W" : "", out.back ? "S" : "", out.left ? "A" : "", out.right ? "D" : "");
            if (core.done()) return new Sim(t, null, core.lastFall(), landWait, true);
            boolean moving = out.forward || out.back || out.left || out.right;
            if (core.dropPhase() == WalkCore.D_LAND) { landWait++; if (moving) throw new IllegalStateException("жмёт клавиши в паузе после приземления"); }
            if (!Double.isNaN(core.lastFall())) landed = true;
            double speed = moving ? (out.sneak ? WalkCore.SNEAK : out.sprint ? WalkCore.SPRINT : WalkCore.WALK) : 0;
            double[] dir = WalkCore.moveDir(out, yaw);
            double air = onGround ? 0.45 : 0.1;                  // в воздухе управление слабее
            vx = vx * (1 - air) + dir[0] * speed * air; vz = vz * (1 - air) + dir[1] * speed * air;
            double nx = x + vx, nz = z + vz;
            if (Terrain.blockedAt(g, nx, y, nz)) {
                // в воде у бортика до блока — выплываем (в игре автоход держит прыжок)
                double up = Terrain.floorY(g, nx, y + 1.0, nz, 2);
                boolean wet = g instanceof FarmGrid fg && fg.tag((int) Math.floor(x), (int) Math.floor(y + 0.01), (int) Math.floor(z)) == FarmGrid.WATER;
                if (wet && onGround && !Double.isNaN(up) && up > y && !Terrain.blockedAt(g, nx, up, nz)) { y = up; }
                else { vx = 0; vz = 0; nx = x; nz = z; }
            }
            x = nx; z = nz;
            double f = support(g, x, y, z);
            if (!Double.isNaN(f) && y - f < 1e-6 && vy <= 0) { y = f; vy = 0; onGround = true; }
            else {
                onGround = false;
                vy = (vy - 0.08) * 0.98;
                y += vy;
                if (!Double.isNaN(f) && y <= f) { y = f; vy = 0; onGround = true; }
                if (y < -64) return new Sim(t, "упал в бездну", Double.NaN, landWait, false);
            }
        }
        return new Sim(maxTicks, "не дошёл за " + maxTicks + " тиков", core.lastFall(), landWait, false);
    }

    /** Опора хитбокса 0.6×0.6: самая высокая под любым из углов (как в игре — не падаешь, пока край под ногой). */
    static double support(Terrain.Cells g, double x, double y, double z) {
        double best = Double.NaN;
        for (double ox : new double[]{-0.299, 0.299}) for (double oz : new double[]{-0.299, 0.299}) {
            double f = Terrain.floorY(g, x + ox, y, z + oz, 64);
            if (!Double.isNaN(f) && (Double.isNaN(best) || f > best)) best = f;
        }
        return best;
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("DropCheck: " + what);
    }

    public static void main() {
        Grid g = farm();
        List<RoutePoint> pts = route(false);

        // Terrain
        check(Terrain.check(g, pts, 0) == Terrain.Issue.OK, "верхний ряд должен быть OK, а " + Terrain.check(g, pts, 0));
        check(Terrain.check(g, pts, 1) == Terrain.Issue.NEED_DROP, "на нижний этаж без спуска — NEED_DROP");
        pts.get(1).drop = true;
        check(Terrain.check(g, pts, 1) == Terrain.Issue.OK, "со спуском — OK, а " + Terrain.check(g, pts, 1));
        double depth = Terrain.fillDepth(g, pts, 1);
        check(Math.abs(depth - 4) < 1e-6 && Math.abs(pts.get(1).dropDepth - 4) < 1e-6, "глубина спуска 4, а " + depth);
        // направление +z: там пол… нет — воздух сбоку и пол ниже: тоже обрыв; −x по ходу; +x — назад по полу (нет обрыва)
        pts.get(1).dropDir = "+x";
        check(Terrain.check(g, pts, 1) == Terrain.Issue.NO_EDGE, "спуск назад по полу — NO_EDGE");
        pts.get(1).dropDir = RoutePoint.DIR_AUTO;
        Grid wall = farm().set(5, 70, 0, 1);
        check(Terrain.check(wall, pts, 0) == Terrain.Issue.BLOCKED, "блок на пути — BLOCKED");
        Grid slab = farm().set(5, 70, 0, 0.5);
        check(Terrain.check(slab, pts, 0) == Terrain.Issue.OK, "плита на пути — шаг, не препятствие");
        Grid farmland = new Grid().fill(0, 9, 69, 0, 0, 0.9375);
        RoutePoint fa = new RoutePoint(0.5, 69.9375, 0.5), fb = new RoutePoint(9.5, 69.9375, 0.5);
        check(Terrain.check(farmland, List.of(fa, fb), 0) == Terrain.Issue.OK, "по грядкам — OK");
        Grid hole = farm().set(4, 69, 0, -1);
        hole.m.remove(Grid.k(4, 69, 0));
        check(Terrain.check(hole, pts, 0) == Terrain.Issue.CLIFF, "дыра в полу на ряду — CLIFF");
        // край на отрезке: уступ — верх x 0…4, ниже на 2 x 5…9
        Grid ledge = new Grid().fill(0, 4, 69, 0, 0, 1).fill(5, 9, 67, 0, 0, 1);
        List<RoutePoint> lp = List.of(new RoutePoint(0.5, 70, 0.5), new RoutePoint(9.5, 68, 0.5));
        double[] e = Terrain.edgeOnSegment(ledge, lp, 0);
        check(e != null && Math.abs(e[0] - 4.5) < 1e-6, "край уступа — блок x=4, а " + (e == null ? "null" : e[0]));
        check(Terrain.edgeOnSegment(g, route(false), 1) == null, "этажи друг под другом — края на линии нет");
        System.out.printf(Locale.ROOT, "%nTerrain: препятствие, плита, грядки, дыра, «нужен спуск», глубина %.1f, край уступа x=%.1f — OK%n", depth, e[0]);

        // симуляция: yaw 90 — смотрим на −X, ряд верхнего этажа — W, нижнего — S
        Sim ok = sim(g, route(true), 90f, 3000);
        System.out.printf(Locale.ROOT, "  спуск: %s за %d тиков, упал на %.2f бл, ждал после приземления %d т%n",
                ok.panic == null ? "пройдено" : "ПАНИКА " + ok.panic, ok.ticks, ok.fall, ok.landWait);
        check(ok.done && ok.panic == null, "двухэтажная ферма не пройдена: " + ok);
        check(Math.abs(ok.fall - 4) < 0.05, "падение 4 бл, а " + ok.fall);
        check(ok.landWait >= 9 && ok.landWait <= 11, "пауза после приземления 10 т, а " + ok.landWait);

        Sim air = sim(g, withAirHold(route(true)), 90f, 3000);
        check(air.done && air.panic == null, "спуск с «держать в полёте» не пройден: " + air);


        Sim noDrop = sim(g, route(false), 90f, 3000);

        System.out.printf(Locale.ROOT, "  без спуска: %s (тик %d)%n", noDrop.panic, noDrop.ticks);
        check(noDrop.panic != null && noDrop.panic.startsWith("Нет пути"), "без спуска ждали «Нет пути», а " + noDrop);

        List<RoutePoint> wrong = route(true);
        wrong.get(1).dropDepth = 8;
        Sim wf = sim(g, wrong, 90f, 3000);
        System.out.printf(Locale.ROOT, "  ждали 8 бл, упал на 4: %s%n", wf.panic);
        check(wf.panic != null && wf.panic.startsWith("Не тот этаж"), "ждали «Не тот этаж», а " + wf);

        List<RoutePoint> back = route(true);
        back.get(1).dropDir = "+x";
        Sim ne = sim(g, back, 90f, 3000);
        System.out.printf(Locale.ROOT, "  спуск назад по полу: %s%n", ne.panic);
        check(ne.panic != null && ne.panic.startsWith("Спуск у точки"), "ждали «не упал», а " + ne);

        // «змейка»-этажи в обратном порядке: два ряда сверху, спуск, те же ряды снизу
        Grid two = new Grid().fill(0, 9, 69, 0, 3, 1).fill(-3, 12, 65, 0, 3, 1);
        var top = SnakeBuilder.build(9, 0, 0, 3, 3, "x", "attack", "none", (x, z) -> 70);
        var all = new java.util.ArrayList<>(top);
        all.get(all.size() - 1).drop = true;
        for (int j = top.size() - 1; j >= 0; j--) { RoutePoint s = top.get(j); all.add(new RoutePoint(s.x, 66, s.z)); }
        int dropAt = top.size() - 1;
        Terrain.fillDepth(two, all, dropAt);
        Sim sn = sim(two, all, 90f, 20_000);
        System.out.printf(Locale.ROOT, "  «змейка» 2 этажа (%d точек): %s за %d тиков, упал на %.2f%n",
                all.size(), sn.panic == null ? "пройдена" : "ПАНИКА " + sn.panic, sn.ticks, sn.fall);
        check(sn.done && sn.panic == null, "«змейка» по этажам: " + sn);
        // то же со «Случайностью»: 100 сидов — без паники, падение то же, длина прохода разная
        var hs = new com.farmmacro.macro.HumanSettings();
        hs.enabled = true; hs.hesitateChance = 15; hs.landDelayMax = 12;
        int mn = Integer.MAX_VALUE, mx = 0;
        for (int i = 0; i < 100; i++) {
            Sim hr = sim(two, all, 90f, 20_000, new com.farmmacro.macro.Humanizer(hs, 500 + i));
            check(hr.done && hr.panic == null && Math.abs(hr.fall - 4) < 0.05, "«змейка» по этажам со случайностью, сид " + (500 + i) + ": " + hr);
            mn = Math.min(mn, hr.ticks); mx = Math.max(mx, hr.ticks);
        }
        check(mx > mn, "со случайностью длина не различается");
        System.out.printf(Locale.ROOT, "  со «Случайностью» × 100 сидов: без паники, %d…%d тиков%n", mn, mx);
        System.out.println("Этажи и спуск — OK");
    }

    private static List<RoutePoint> withAirHold(List<RoutePoint> p) { p.get(1).airHold = true; return p; }
}
