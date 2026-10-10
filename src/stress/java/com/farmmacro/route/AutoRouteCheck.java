package com.farmmacro.route;

import java.util.List;
import java.util.Locale;

/**
 * v1.11 «Авто-маршрут» на синтетических фермах: классификация (линия, зигзаг, проходы тростника, 2 этажа, дыра,
 * спираль, несвязные участки) и проходимость построенного маршрута симуляцией с гравитацией (DropCheck.sim) без паники.
 */
public final class AutoRouteCheck {
    private static final byte FL = FarmGrid.FARMLAND, CROP = FarmGrid.CROP;
    private static final double FARM = 0.9375;

    private static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("AutoRouteCheck: " + what);
    }

    static FarmGrid grid() { return new FarmGrid(-6, 58, -6, 40, 14, 30); }

    /** Грядка с пшеницей: фермерская земля на y, урожай на y+1. */
    static void wheat(FarmGrid g, int x0, int x1, int z0, int z1, int y) {
        g.fill(x0, x1, y, z0, z1, FARM, FL).fill(x0, x1, y + 1, z0, z1, -1, CROP).fill(x0, x1, y - 1, z0, z1, 1, FarmGrid.NONE);
    }

    static AutoRoute.Params params() {
        AutoRoute.Params p = new AutoRoute.Params();
        p.endPause = 2;
        return p;
    }

    static int cases;

    static AutoRoute.Result run(String name, FarmGrid g, AutoRoute.Params p, int ax, int az, boolean expectLoop) {
        AutoRoute.Result r = AutoRoute.build(g, p, ax, az);
        var a = r.analysis;
        check(a.error == null, name + ": " + a.error);
        check(r.issues == 0, name + ": проверка рельефа — " + r.firstIssue);
        check(r.points.size() >= 2, name + ": мало точек");
        float yaw = a.main().alongX ? 90f : 0f;
        DropCheck.Sim s = DropCheck.sim(g, r.points, yaw, 60_000);
        check(s.panic() == null && s.done(), name + ": симуляция — " + s.panic());
        check(r.looped == expectLoop, name + ": возврат к старту " + r.looped + " " + r.warnings);
        System.out.printf(Locale.ROOT, "  %-26s %-70s точек %3d, пройден за %5d т%s%n", name, a.summary(), r.points.size(), s.ticks(),
                r.warnings.isEmpty() ? "" : " · " + String.join("; ", r.warnings));
        cases++;
        return r;
    }

    public static void main() {
        System.out.println("Авто-маршрут (v1.11):");
        // 1) линия: один ряд между стенами
        FarmGrid g = grid();
        wheat(g, 0, 19, 1, 1, 63);
        g.fill(-1, 20, 64, 0, 0, 1, FarmGrid.NONE).fill(-1, 20, 64, 2, 2, 1, FarmGrid.NONE);
        var r = run("линия", g, params(), 0, 1, true);
        check(r.analysis.main().type.equals("линейный"), "линия: тип " + r.analysis.main().type);

        // 2) зигзаг: поле 21×15, вода по z = 4 и 9 (по краям x — проход)
        g = grid();
        wheat(g, 0, 20, 0, 14, 63);
        for (int x = 1; x <= 19; x++) for (int z : new int[]{4, 9}) { g.set(x, 63, z, -1, FarmGrid.WATER); g.set(x, 64, z, -1, FarmGrid.NONE); }
        // низкие культуры: по умолчанию идём по воде (каналы z = 4 и 9), ряды дальше 1 блока — предупреждение
        r = run("пшеница: по воде", g, params(), 0, 0, true);
        var f = r.analysis.main();
        check(f.lowered && f.wetLanes && f.lanes.size() == 2 && f.uncovered == 9 && r.analysis.summary().contains("по воде"),
                "пшеница по воде: " + r.analysis.summary() + " " + f.uncovered);
        for (int[] l : f.lanes) check(l[0] == 4 || l[0] == 9, "пшеница по воде: ряд не по воде " + l[0]);
        check(r.warnings.stream().anyMatch(w -> w.contains("дальше 1 блока")) && r.hint != null, "пшеница по воде: нет предупреждения/совета");
        AutoRoute.Params pl = params(); pl.walk = AutoRoute.WALK_LEVEL;
        r = run("зигзаг (по грядкам)", g, pl, 0, 0, true);
        f = r.analysis.main();
        check(f.type.equals("зигзаг") && f.alongX && f.lanes.size() == 3 && f.cropPeriod == 5 && !f.lowered, "зигзаг: " + r.analysis.summary());
        AutoRoute.Params p2 = params(); p2.stepAuto = false; p2.step = 2; p2.walk = AutoRoute.WALK_LEVEL;
        r = run("зигзаг, шаг 2", g, p2, 20, 14, true);
        check(r.analysis.main().lanes.size() >= 6, "шаг 2: рядов " + r.analysis.main().lanes.size());
        AutoRoute.Params pz = params(); pz.axis = "z"; pz.walk = AutoRoute.WALK_LEVEL;
        r = run("зигзаг, ряды вдоль Z", g, pz, 0, 0, true);
        check(!r.analysis.main().alongX, "ось Z не применилась");

        // 3) тростник: песок, тростник / проход / тростник / вода; проходы по краям
        g = grid();
        for (int z = 0; z <= 11; z++) {
            g.fill(-1, 20, 63, z, z, 1, FarmGrid.SAND).fill(-1, 20, 62, z, z, 1, FarmGrid.NONE);
            if (z % 4 == 0 || z % 4 == 2) g.fill(0, 19, 64, z, z, -1, FarmGrid.CROP_TALL).fill(0, 19, 65, z, z, -1, FarmGrid.CROP_TALL);
            if (z % 4 == 3) for (int x = 0; x <= 19; x++) g.set(x, 63, z, -1, FarmGrid.WATER);
        }
        r = run("тростник (проходы)", g, params(), 0, 1, true);
        f = r.analysis.main();
        check(f.paths && f.atLevel && f.lanes.size() == 3 && f.laneStep == 4 && f.cropPeriod == 2, "тростник: " + r.analysis.summary());
        AutoRoute.Params ps = params(); ps.surface = AutoRoute.SURF_FARMLAND;
        check(AutoRoute.build(g, ps, 0, 1).analysis.error != null, "поверхность «фермерская земля» должна отсечь тростник");
        ps.surface = AutoRoute.SURF_SAND;
        check(AutoRoute.build(g, ps, 0, 1).analysis.error == null, "поверхность «песок» — тростник есть");

        // 3б) канал с водой на блок ниже пашни (как на Hypixel): урожай | вода | урожай урожай | вода | …, концы каналов
        //     соединены поперечным каналом x = 20, вокруг — стена
        g = grid();
        for (int z = 0; z <= 8; z++) {
            boolean water = z % 3 == 1;
            for (int xx = 0; xx <= 19; xx++) {
                g.set(xx, 61, z, 1, FarmGrid.NONE);
                if (water) { g.set(xx, 62, z, -1, FarmGrid.WATER); g.set(xx, 63, z, -1, FarmGrid.NONE); }
                else { g.set(xx, 62, z, 1, FarmGrid.NONE); g.set(xx, 63, z, FARM, FL); g.set(xx, 64, z, -1, CROP); }
            }
            g.set(20, 61, z, 1, FarmGrid.NONE);
            if (z >= 1 && z <= 7) g.set(20, 62, z, -1, FarmGrid.WATER);
            else g.fill(20, 20, 62, z, z, 1, FarmGrid.NONE).fill(20, 20, 63, z, z, 1, FarmGrid.NONE).fill(20, 20, 64, z, z, 1, FarmGrid.NONE);
        }
        for (int xx = -1; xx <= 21; xx++) for (int z : new int[]{-1, 9}) for (int y = 61; y <= 65; y++) g.set(xx, y, z, 1, FarmGrid.NONE);
        for (int z = -1; z <= 9; z++) for (int xx : new int[]{-1, 21}) for (int y = 61; y <= 65; y++) g.set(xx, y, z, 1, FarmGrid.NONE);
        r = run("каналы под пашней", g, params(), 0, 1, true);
        f = r.analysis.main();
        check(f.lowered && f.wetLanes && f.lanes.size() == 3 && f.uncovered == 0, "каналы: " + r.analysis.summary());
        for (RoutePoint q : r.points) check(Math.abs(q.y - 62) < 1e-6, "каналы: точка не в воде y=" + q.y);

        // 3в) арбузы «на уровне»: арбуз | проход | арбуз арбуз | проход | … — проходы между двумя рядами
        g = grid();
        g.fill(-2, 21, 63, -1, 9, 1, FarmGrid.NONE);
        for (int z = 0; z <= 8; z++) if (z % 3 != 1) g.fill(0, 19, 64, z, z, 1, FarmGrid.CROP_TALL);
        r = run("арбузы на уровне", g, params(), 0, 1, true);
        f = r.analysis.main();
        check(f.atLevel && !f.lowered && f.lanes.size() == 3 && f.uncovered == 0 && r.hint != null && r.hint.contains("45"),
                "арбузы: " + r.analysis.summary());
        for (RoutePoint q : r.points) check(Math.abs(q.y - 64) < 1e-6, "арбузы: точка не на уровне y=" + q.y);
        //     ряд-проход-ряд-проход-ряд: каждый проход достаёт два ряда — лишние не берём
        g = grid();
        g.fill(-2, 21, 63, -1, 9, 1, FarmGrid.NONE);
        for (int z = 0; z <= 8; z += 2) g.fill(0, 19, 64, z, z, 1, FarmGrid.CROP_TALL);
        r = run("арбузы через ряд", g, params(), 0, 1, true);
        check(r.analysis.main().lanes.size() == 3, "через ряд: проходов " + r.analysis.main().lanes.size() + " " + r.analysis.summary());

        // 4) два этажа: верхний x 0…15 (земля y = 67), нижний x −3…15 (y = 63), спуск с края x = 0
        g = grid();
        wheat(g, 0, 15, 0, 6, 67);
        wheat(g, -3, 15, 0, 6, 63);
        r = run("2 этажа", g, params(), 15, 0, false);
        check(r.analysis.floors.size() == 2 && r.analysis.summary().contains("Δy 4"), "2 этажа: " + r.analysis.summary());
        long drops = r.points.stream().filter(q -> q.drop).count();
        check(drops == 1 && r.points.stream().filter(q -> q.drop).allMatch(q -> Math.abs(q.dropDepth - 4) < 1e-6), "2 этажа: спуск " + drops);
        check(r.warnings.stream().anyMatch(w -> w.contains("вернуться к старту нельзя")), "2 этажа: нет предупреждения о возврате");

        // 5) дыра посередине
        g = grid();
        wheat(g, 0, 20, 0, 12, 63);
        for (int x = 8; x <= 12; x++) for (int z = 5; z <= 7; z++) for (int y = 58; y < 72; y++) g.set(x, y, z, -1, FarmGrid.NONE);
        r = run("дыра посередине", g, params(), 0, 0, true);
        check(r.analysis.main().type.equals("зигзаг"), "дыра: " + r.analysis.summary());
        for (RoutePoint q : r.points) check(!(q.x > 8 && q.x < 13 && q.z > 5 && q.z < 8), "точка в дыре");

        // 6) спираль: коридор шириной 1
        g = grid();
        int[][] seg = {{1, 0, 10}, {0, 1, 10}, {-1, 0, 10}, {0, -1, 8}, {1, 0, 8}, {0, 1, 6}, {-1, 0, 6}, {0, -1, 4}, {1, 0, 4}, {0, 1, 2}, {-1, 0, 2}};
        int x = 0, z = 0;
        wheat(g, 0, 0, 0, 0, 63);
        for (int[] s : seg) for (int k = 0; k < s[2]; k++) { x += s[0]; z += s[1]; wheat(g, x, x, z, z, 63); }
        AutoRoute.Params pn = params(); pn.returnToStart = false;
        r = run("спираль", g, pn, 0, 0, false);
        check(r.analysis.main().type.equals("спираль") && r.analysis.main().corridor != null, "спираль: " + r.analysis.summary());

        // 7) два несвязных участка
        g = grid();
        wheat(g, 0, 8, 0, 8, 63);
        wheat(g, 12, 20, 0, 8, 63);
        AutoRoute.Result c = AutoRoute.build(g, params(), 0, 0);
        check(c.analysis.main().type.equals("сложный") && c.analysis.main().parts == 2, "несвязные: " + c.analysis.summary());
        check(c.warnings.stream().anyMatch(w -> w.contains("не достать")), "несвязные: нет предупреждения");
        System.out.printf(Locale.ROOT, "  %-26s %s · %s%n", "несвязные участки", c.analysis.summary(), String.join("; ", c.warnings));

        // 8) A*: путь в обход стены
        g = grid();
        wheat(g, 0, 10, 0, 10, 63);
        g.fill(5, 5, 64, 0, 9, 1, FarmGrid.NONE).fill(5, 5, 65, 0, 9, 1, FarmGrid.NONE);
        var an = AutoRoute.analyze(g, params());
        List<int[]> path = AutoRoute.pathTo(g, an.main(), 0, 0, 10, 0);
        check(path != null && path.stream().anyMatch(q -> q[1] == 10), "A* не обошёл стену");
        check(AutoRoute.corners(path).size() <= 4, "A*: лишние повороты " + AutoRoute.corners(path).size());
        // большая ферма без предела размера: 960×960, каналы воды под пашней через 2 ряда урожая
        {
            int W = 960, D = 960;
            FarmGrid big = new FarmGrid(-1, 60, -1, W + 2, 6, D + 2);
            long t0 = System.nanoTime();
            for (int bz = 0; bz < D; bz++) {
                boolean water = bz % 3 == 1;
                for (int xx = 0; xx < W; xx++) {
                    big.set(xx, 61, bz, 1, FarmGrid.NONE);
                    if (water) big.set(xx, 62, bz, -1, FarmGrid.WATER);
                    else { big.set(xx, 62, bz, 1, FarmGrid.NONE); big.set(xx, 63, bz, FARM, FL); big.set(xx, 64, bz, -1, CROP); }
                }
            }
            for (int bz = 0; bz < D; bz++) { big.set(W, 61, bz, 1, FarmGrid.NONE); big.set(W, 62, bz, -1, FarmGrid.WATER); }
            long t1 = System.nanoTime();
            AutoRoute.Params bp = params(); bp.maxPoints = 1 << 20;
            AutoRoute.Result br = AutoRoute.build(big, bp, 0, 1);
            long t2 = System.nanoTime();
            check(br.analysis.error == null && br.issues == 0, "большая ферма: " + br.analysis.error + " " + br.firstIssue);
            var bf = br.analysis.main();
            check(bf.lowered && bf.lanes.size() == D / 3, "большая ферма: рядов " + bf.lanes.size() + " " + br.analysis.summary());
            System.out.printf(Locale.ROOT, "  %-26s %s, точек %d; снимок %d МБ, заполнение %.0f мс, разбор и маршрут %.0f мс%n", "ферма 960×960",
                    br.analysis.summary(), br.points.size(), big.cells() * 3 >> 20, (t1 - t0) / 1e6, (t2 - t1) / 1e6);
            check((t2 - t1) / 1e9 < 20, "большая ферма: слишком долго");
            cases++;
        }
        System.out.println("Авто-маршрут: " + (cases + 2) + " ферм — OK");
    }
}
