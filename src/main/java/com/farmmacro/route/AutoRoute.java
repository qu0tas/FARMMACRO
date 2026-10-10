package com.farmmacro.route;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.function.IntPredicate;

/**
 * v1.11 «Авто-маршрут» по геометрии фермы. Без зависимостей от игры (проверяется в routeStress).
 * <ol>
 * <li>{@link #analyze}: этажи (где можно стоять: опора + место для тела, не в воде/огне, не на урожае), урожай по этажам,
 *     ориентация рядов (по числу смен «урожай/нет» вдоль осей), проходы, ряды, тип фермы.</li>
 * <li>{@link #build}: точки по рядам «змейкой», переходы между рядами и обход — A* по сетке проходимости,
 *     спуск на этаж ниже ({@code drop}), возврат к старту.</li>
 * </ol>
 */
public final class AutoRoute {
    private AutoRoute() {}

    public static final String SURF_AUTO = "auto", SURF_FARMLAND = "farmland", SURF_SAND = "sand", SURF_SOUL = "soul_sand";
    public static final List<String> SURFACES = List.of(SURF_AUTO, SURF_FARMLAND, SURF_SAND, SURF_SOUL);
    /** Где идти: авто (по культуре), ниже грядок (канал с водой), на уровне культур. */
    public static final String WALK_AUTO = "auto", WALK_LOW = "low", WALK_LEVEL = "level";
    public static final List<String> WALKS = List.of(WALK_AUTO, WALK_LOW, WALK_LEVEL);
    /** Ноги в канале «ниже грядок»: от 2.2 до 0.45 блока ниже блока урожая. На уровне — ±0.45. */
    static final double LOW_MIN = 2.2, LOW_MAX = 0.45, LEVEL_TOL = 0.45;
    /** Поиск канала у урожая (колонок в стороны) — грядка шириной до 8 между каналами. */
    static final int LOW_RADIUS = 4;
    /** Из воды на бортик (и с бортика в воду) — до блока. */
    static final double WATER_CLIMB = 1.0;
    /** Шаг рядов «авто» на сплошном поле без проходов. */
    public static final int AUTO_FIELD_STEP = 3;

    /** Параметры построения. */
    public static final class Params {
        public String axis = SnakeBuilder.AXIS_AUTO;
        public boolean stepAuto = true;
        public int step = 3;
        public String surface = SURF_AUTO;
        public String walk = WALK_AUTO;
        public boolean returnToStart = true;
        public int endPause = 0;
        public String rowAction = RoutePoint.ATTACK, turnAction = RoutePoint.NONE;
        public int maxPoints = 4096;
    }

    /** Этаж: маски по колонкам области (индекс (x − x0)·d + (z − z0)). */
    public static final class Floor {
        public final boolean[] walk, crop, wet;
        public final double[] feet;
        public final int[] base;          // нижний блок урожая в колонке (тростник/кактус — основание)
        public int lowCrops, tallCrops;
        public double cropLevel = Double.NaN;
        public boolean low;               // режим «ниже грядок» (низкие культуры), иначе «на уровне»
        public boolean lowered, wetLanes, atLevel;   // проходы найдены: ниже грядок (по воде) / на уровне урожая
        public int uncovered;             // рядов урожая дальше 1 блока от прохода
        public double level;
        public int walkCount, cropCount;
        public int bx0 = Integer.MAX_VALUE, bx1 = Integer.MIN_VALUE, bz0 = Integer.MAX_VALUE, bz1 = Integer.MIN_VALUE;
        // результат разбора
        public boolean alongX;
        public List<int[]> lanes = new ArrayList<>();   // {линия, от, до} — мировые координаты
        public String type = "—";
        public boolean paths;            // ряды по проходам (а не по самому урожаю)
        public int laneStep, passWidth, cropPeriod, parts;
        public List<int[]> corridor;     // для «спирали»/коридора: клетки пути {x, z}

        /** Число линий рядов (кусок вокруг дыры — та же линия). */
        public int lines() { return (int) lanes.stream().mapToInt(l -> l[0]).distinct().count(); }

        Floor(int n) {
            walk = new boolean[n]; crop = new boolean[n]; wet = new boolean[n]; feet = new double[n]; base = new int[n];
            Arrays.fill(feet, Double.NaN); Arrays.fill(base, Integer.MAX_VALUE);
        }
    }

    public static final class Analysis {
        public final FarmGrid g;
        public final List<Floor> floors = new ArrayList<>();   // сверху вниз, только с урожаем
        public int climb;
        public String error;
        Analysis(FarmGrid g) { this.g = g; }

        public Floor main() { return floors.isEmpty() ? null : floors.get(0); }

        /** «зигзаг, ряды вдоль X, шаг 3, 2 этажа, Δy 4». */
        public String summary() {
            if (error != null) return error;
            Floor f = main();
            StringBuilder b = new StringBuilder(f.type);
            if (!f.lanes.isEmpty() || f.corridor == null) b.append(", ряды вдоль ").append(f.alongX ? "X" : "Z");
            if (f.lines() > 1) b.append(", шаг ").append(f.laneStep);
            if (f.paths && f.passWidth > 0) b.append(", проходы ").append(f.passWidth).append(" бл");
            if (f.cropPeriod > 1) b.append(", урожай через ").append(f.cropPeriod);
            if (f.lowered) b.append(f.wetLanes ? ", по воде под грядками" : ", ниже грядок");
            else if (f.atLevel) b.append(", на уровне урожая");
            b.append(", рядов ").append(f.corridor != null ? 1 : f.lines());
            if (floors.size() > 1) {
                b.append(", ").append(floors.size()).append(floors.size() < 5 ? " этажа" : " этажей");
                double dy = floors.get(0).level - floors.get(1).level;
                b.append(", Δy ").append(fmt(dy));
            }
            if (f.parts > 1) b.append(", участков ").append(f.parts);
            if (climb > 0) b.append(", лестниц/лиан ").append(climb).append(" (не используются)");
            return b.toString();
        }
    }

    static String fmt(double v) {
        return Math.abs(v - Math.rint(v)) < 0.01 ? String.valueOf((int) Math.rint(v)) : String.format(Locale.ROOT, "%.2f", v);
    }

    // ── Анализ ───────────────────────────────────────────────────────────────

    private static boolean surfaceOk(FarmGrid g, int x, int y, int z, String surface) {
        if (surface == null || SURF_AUTO.equals(surface)) return true;
        int yy = y - 1;
        while (yy >= g.y0 && FarmGrid.isCrop(g.tag(x, yy, z))) yy--;    // тростник/кактус столбом
        byte t = g.tag(x, yy, z);
        return switch (surface) {
            case SURF_FARMLAND -> t == FarmGrid.FARMLAND;
            case SURF_SAND -> t == FarmGrid.SAND;
            case SURF_SOUL -> t == FarmGrid.SOUL;
            default -> true;
        };
    }

    public static Analysis analyze(FarmGrid g, Params p) {
        Analysis a = new Analysis(g);
        int n = g.w * g.d;
        // 1) где можно стоять: по колонкам, все высоты
        List<double[]> stands = new ArrayList<>();          // {col, feet, в воде}
        for (int i = 0; i < g.w; i++) for (int k = 0; k < g.d; k++) {
            int x = g.x0 + i, z = g.z0 + k, col = i * g.d + k;
            for (int y = g.y0 + g.h - 1; y >= g.y0; y--) {
                byte tg = g.tag(x, y, z);
                if (tg == FarmGrid.CLIMB) a.climb++;
                double t = g.top(x, y, z);
                if (t < 0 || FarmGrid.isCrop(tg) || tg == FarmGrid.DANGER) continue;
                double feet = y + t;
                int fy = (int) Math.floor(feet + 0.01);
                byte at = g.tag(x, fy, z);
                if (at == FarmGrid.DANGER) continue;
                // вода по колено/пояс (канал у грядок) — по дну идут; глубже — плыть, не идти
                boolean inWater = at == FarmGrid.WATER;
                if (inWater && g.tag(x, fy + 1, z) == FarmGrid.WATER) continue;
                if (Terrain.blockedAt(g, x + 0.5, feet, z + 0.5)) continue;
                stands.add(new double[]{col, feet, inWater ? 1 : 0});
            }
        }
        if (stands.isEmpty()) { a.error = "в области негде стоять"; return a; }
        // 2) этажи: высоты ног по убыванию, разрыв больше 1 блока — новый этаж
        double[] fs = stands.stream().mapToDouble(s -> s[1]).sorted().toArray();
        List<double[]> clusters = new ArrayList<>();        // {верх, низ}
        double hi = fs[fs.length - 1], lo = hi;
        for (int i = fs.length - 2; i >= -1; i--) {
            if (i < 0 || lo - fs[i] > 1.0) { clusters.add(new double[]{hi, lo}); if (i >= 0) { hi = lo = fs[i]; } }
            else lo = fs[i];
        }
        List<Floor> all = new ArrayList<>();
        for (int c = 0; c < clusters.size(); c++) all.add(new Floor(n));
        for (double[] s : stands) {
            int col = (int) s[0];
            for (int c = 0; c < clusters.size(); c++) {
                double[] cl = clusters.get(c);
                if (s[1] <= cl[0] + 1e-9 && s[1] >= cl[1] - 1e-9) {
                    Floor f = all.get(c);
                    if (!f.walk[col] || s[1] > f.feet[col]) {
                        if (!f.walk[col]) f.walkCount++;
                        f.walk[col] = true; f.feet[col] = s[1]; f.wet[col] = s[2] > 0;
                    }
                    break;
                }
            }
        }
        for (Floor f : all) {
            double[] v = new double[f.walkCount];
            int j = 0;
            for (int i = 0; i < n; i++) if (f.walk[i]) v[j++] = f.feet[i];
            Arrays.sort(v);
            f.level = v.length == 0 ? Double.NaN : v[v.length / 2];
        }
        // 3) урожай → этаж. Низкие культуры (пшеница, морковь, картофель, нарост, грибы) — к этажу канала ниже грядок
        //    (обычно вода на блок ниже пашни) в пределах 4 колонок; культуры «на уровне» (арбуз, тыква, тростник, цветы,
        //    кактус, какао) — к этажу, где ноги на уровне блока урожая. Не нашлось — самый высокий этаж с ногами
        //    не выше блока урожая (+0.2) и не ниже его на 3 (верх забора / стены над грядками — не этаж урожая).
        for (int i = 0; i < g.w; i++) for (int k = 0; k < g.d; k++) {
            int x = g.x0 + i, z = g.z0 + k, col = i * g.d + k;
            for (int y = g.y0; y < g.y0 + g.h; y++) {
                byte tg = g.tag(x, y, z);
                if (!FarmGrid.isCrop(tg) || !surfaceOk(g, x, y, z, p.surface)) continue;
                int by = y;
                while (by - 1 >= g.y0 && FarmGrid.isCrop(g.tag(x, by - 1, z))) by--;     // основание столба
                boolean lowKind = WALK_LOW.equals(p.walk) || (!WALK_LEVEL.equals(p.walk) && tg == FarmGrid.CROP);
                Floor pick = lowKind ? near(g, all, x, z, by - LOW_MIN, by - LOW_MAX, LOW_RADIUS)
                        : near(g, all, x, z, by - LEVEL_TOL, by + LEVEL_TOL, 1);
                if (pick == null) for (Floor f : all) {
                    if (Double.isNaN(f.level) || f.level > y + 0.2 || f.level < y - 3) continue;
                    pick = f;
                    break;
                }
                if (pick == null) continue;
                Floor f = pick;
                if (!f.crop[col]) {
                    f.crop[col] = true; f.cropCount++;
                    if (lowKind) f.lowCrops++; else f.tallCrops++;
                    f.bx0 = Math.min(f.bx0, x); f.bx1 = Math.max(f.bx1, x);
                    f.bz0 = Math.min(f.bz0, z); f.bz1 = Math.max(f.bz1, z);
                }
                f.base[col] = Math.min(f.base[col], by);
            }
        }
        for (Floor f : all) {
            if (f.cropCount == 0) continue;
            f.low = f.lowCrops >= f.tallCrops;
            int[] v = new int[f.cropCount];
            int j = 0;
            for (int c = 0; c < n; c++) if (f.crop[c]) v[j++] = f.base[c];
            Arrays.sort(v);
            f.cropLevel = v[v.length / 2];
        }
        for (Floor f : all) if (f.cropCount > 0 && f.walkCount >= 2) a.floors.add(f);
        if (a.floors.isEmpty()) {
            a.error = SURF_AUTO.equals(p.surface) ? "урожая в области не найдено" : "урожая на выбранной поверхности не найдено";
            return a;
        }
        for (Floor f : a.floors) classify(g, f, p);
        return a;
    }

    /** Этаж с клеткой, где ноги в [lo, hi], ближайшей к колонке (кольцами до r); при равенстве — выше. */
    private static Floor near(FarmGrid g, List<Floor> all, int x, int z, double lo, double hi, int r) {
        for (int d = 0; d <= r; d++) {
            Floor best = null;
            for (int dx = -d; dx <= d; dx++) for (int dz = -d; dz <= d; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != d || !g.inXZ(x + dx, z + dz)) continue;
                int c = col(g, x + dx, z + dz);
                for (Floor f : all) {
                    if (!f.walk[c] || f.feet[c] < lo - 1e-9 || f.feet[c] > hi + 1e-9) continue;
                    if (best == null || f.level > best.level) best = f;
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    static int col(FarmGrid g, int x, int z) { return (x - g.x0) * g.d + (z - g.z0); }

    static boolean walkable(FarmGrid g, Floor f, int x, int z) { return g.inXZ(x, z) && f.walk[col(g, x, z)]; }

    /** Можно ли шагнуть из колонки a в соседнюю b на этом этаже (ступенька ≤ 0.6 вверх и вниз). */
    static boolean step(FarmGrid g, Floor f, int ax, int az, int bx, int bz) {
        if (!walkable(g, f, ax, az) || !walkable(g, f, bx, bz)) return false;
        int ca = col(g, ax, az), cb = col(g, bx, bz);
        double fa = f.feet[ca], fb = f.feet[cb];
        // канал с водой: из воды на бортик до 1 блока выплывают (автоход держит прыжок), с бортика в воду — шаг вниз
        double up = f.wet[ca] ? WATER_CLIMB : Terrain.STEP, down = f.wet[cb] ? WATER_CLIMB : Terrain.FLOOR_GAP;
        return fb - fa <= up + 1e-9 && fa - fb <= down + 1e-9;
    }

    private static boolean cropAt(FarmGrid g, Floor f, int x, int z) { return g.inXZ(x, z) && f.crop[col(g, x, z)]; }

    /** Координата клетки на линии: alongX — линия по z, движение по x. */
    private static int cx(boolean alongX, int line, int s) { return alongX ? s : line; }
    private static int cz(boolean alongX, int line, int s) { return alongX ? line : s; }

    /** Вода годится только как канал «ниже грядок»; иначе по воде не ходим (как раньше). */
    private static void classify(FarmGrid g, Floor f, Params p) {
        if (!f.low) stripWet(f);
        classify0(g, f, p);
        if (f.low && !f.lowered) {
            stripWet(f);
            f.lanes.clear(); f.corridor = null; f.paths = false; f.uncovered = 0;
            classify0(g, f, p);
        }
    }

    private static void stripWet(Floor f) {
        for (int c = 0; c < f.walk.length; c++) if (f.wet[c] && f.walk[c]) { f.walk[c] = false; f.wet[c] = false; f.walkCount--; }
    }

    private static void classify0(FarmGrid g, Floor f, Params p) {
        // ориентация: вдоль рядов урожай меняется редко
        int tx = 0, tz = 0;
        for (int x = f.bx0; x <= f.bx1; x++) for (int z = f.bz0; z <= f.bz1; z++) {
            boolean c = cropAt(g, f, x, z);
            if (x < f.bx1 && c != cropAt(g, f, x + 1, z)) tx++;
            if (z < f.bz1 && c != cropAt(g, f, x, z + 1)) tz++;
        }
        int ex = f.bx1 - f.bx0 + 1, ez = f.bz1 - f.bz0 + 1;
        if (SnakeBuilder.AXIS_X.equals(p.axis)) f.alongX = true;
        else if (SnakeBuilder.AXIS_Z.equals(p.axis)) f.alongX = false;
        else if (tx < tz * 0.7) f.alongX = true;
        else if (tz < tx * 0.7) f.alongX = false;
        else f.alongX = ex >= ez;

        // одиночный коридор (спираль / извилистый проход): у каждой клетки ≤ 2 соседа
        List<int[]> cor = corridor(g, f);
        if (cor != null) {
            f.corridor = cor;
            if (f.low) {
                int lowN = 0, wetN = 0;
                for (int[] q : cor) {
                    int c = col(g, q[0], q[1]);
                    double ft = f.feet[c];
                    if (ft <= f.cropLevel - LOW_MAX + 1e-9 && ft >= f.cropLevel - LOW_MIN - 1e-9) lowN++;
                    if (f.wet[c]) wetN++;
                }
                f.lowered = lowN * 2 >= cor.size();
                f.wetLanes = wetN * 2 >= cor.size();
            }
            int left = 0, right = 0;
            for (int i = 1; i + 1 < cor.size(); i++) {
                int dx1 = cor.get(i)[0] - cor.get(i - 1)[0], dz1 = cor.get(i)[1] - cor.get(i - 1)[1];
                int dx2 = cor.get(i + 1)[0] - cor.get(i)[0], dz2 = cor.get(i + 1)[1] - cor.get(i)[1];
                int cr = dx1 * dz2 - dz1 * dx2;
                if (cr > 0) right++; else if (cr < 0) left++;
            }
            int turns = left + right;
            f.type = turns == 0 ? "линейный" : turns >= 4 && Math.max(left, right) >= turns * 0.8 ? "спираль" : "зигзаг";
            f.parts = 1;
            return;
        }

        boolean ax = f.alongX;
        int a0 = ax ? f.bx0 : f.bz0, a1 = ax ? f.bx1 : f.bz1;          // вдоль ряда
        int c0 = ax ? f.bz0 : f.bx0, c1 = ax ? f.bz1 : f.bx1;          // поперёк
        int lineMin = Math.max(c0 - 1, ax ? g.z0 : g.x0), lineMax = Math.min(c1 + 1, (ax ? g.z0 + g.d : g.x0 + g.w) - 1);
        int ext = a1 - a0 + 1;
        int nl = lineMax - lineMin + 1;
        List<List<int[]>> runs = new ArrayList<>();
        boolean[] ok = new boolean[nl], path = new boolean[nl], cropLine = new boolean[nl], good = new boolean[nl], wetL = new boolean[nl];
        double cl0 = f.cropLevel;
        for (int li = 0; li < nl; li++) {
            int line = lineMin + li;
            // куски ходьбы вдоль линии в пределах урожая (дыра посередине — два куска)
            List<int[]> rs = new ArrayList<>();
            int s = a0, walkLen = 0;
            while (s <= a1) {
                if (!walkable(g, f, cx(ax, line, s), cz(ax, line, s))) { s++; continue; }
                int e = s;
                while (e + 1 <= a1 && step(g, f, cx(ax, line, e), cz(ax, line, e), cx(ax, line, e + 1), cz(ax, line, e + 1))
                        && step(g, f, cx(ax, line, e + 1), cz(ax, line, e + 1), cx(ax, line, e), cz(ax, line, e))) e++;
                if (e > s) { rs.add(new int[]{line, s, e}); walkLen += e - s + 1; }
                s = e + 1;
            }
            runs.add(rs);
            int crops = 0, walkNoCrop = 0, goodN = 0, wetN = 0;
            for (int t = a0; t <= a1; t++) {
                int x = cx(ax, line, t), z = cz(ax, line, t);
                if (cropAt(g, f, x, z)) crops++;
                else if (walkable(g, f, x, z)) {
                    walkNoCrop++;
                    double ft = f.feet[col(g, x, z)];
                    // проход нужного вида: ниже грядок (низкие культуры) или на уровне урожая (арбуз, тростник, цветы)
                    if (f.low ? ft <= cl0 - LOW_MAX + 1e-9 && ft >= cl0 - LOW_MIN - 1e-9 : Math.abs(ft - cl0) <= LEVEL_TOL + 1e-9) goodN++;
                    if (f.wet[col(g, x, z)]) wetN++;
                }
            }
            int need = Math.max(2, (int) Math.ceil(0.6 * ext));
            ok[li] = walkLen >= need;
            cropLine[li] = crops >= Math.max(1, ext / 2);
            path[li] = ok[li] && walkNoCrop >= need;
            good[li] = path[li] && goodN >= need;
            wetL[li] = good[li] && wetN * 2 >= goodN;
        }
        // период рядов урожая
        List<Integer> cl = new ArrayList<>();
        for (int li = 0; li < nl; li++) if (cropLine[li] && (li == 0 || !cropLine[li - 1])) cl.add(li);
        f.cropPeriod = median(diffs(cl));

        // низкие культуры: идём только по каналам ниже грядок (обычно вода на блок ниже пашни) — по каждому
        List<int[]> lowGroups = f.low ? groups(good) : List.of();
        f.lanes.clear();
        if (!lowGroups.isEmpty()) {
            f.paths = f.lowered = true;
            List<Integer> widths = new ArrayList<>();
            int wet = 0;
            for (int[] gr : lowGroups) {
                int mid = (gr[0] + gr[1]) / 2;
                f.lanes.addAll(runs.get(mid));
                if (wetL[mid]) wet++;
                widths.add(gr[1] - gr[0] + 1);
            }
            f.wetLanes = wet * 2 >= lowGroups.size();
            f.passWidth = median(widths);
            f.uncovered = uncovered(cropLine, lowGroups);
            finishLanes(g, f);
            return;
        }
        // культуры «на уровне»: проходы только на уровне урожая (если есть)
        if (!f.low) {
            boolean any = false;
            for (int li = 0; li < nl; li++) any |= good[li];
            if (any) { System.arraycopy(good, 0, path, 0, nl); f.atLevel = true; }
        }

        // режим «проходы»: каждая линия урожая не дальше 2 от прохода
        List<int[]> pathGroups = groups(path);
        boolean covered = !pathGroups.isEmpty();
        if (covered) for (int li = 0; li < nl && covered; li++) {
            if (!cropLine[li]) continue;
            boolean near = false;
            for (int[] gr : pathGroups) if (li >= gr[0] - 2 && li <= gr[1] + 2) { near = true; break; }
            covered = near;
        }
        boolean hasCropOnly = false;
        for (int li = 0; li < nl; li++) if (cropLine[li] && !path[li]) hasCropOnly = true;
        f.lanes.clear();
        if (covered && hasCropOnly) {
            f.paths = true;
            List<Integer> widths = new ArrayList<>();
            // «на уровне» идём между двумя рядами и ломаем оба: лишний проход (оба соседних ряда уже покрыты) не нужен
            if (f.atLevel) pathGroups = cover(cropLine, pathGroups);
            f.uncovered = uncovered(cropLine, pathGroups);
            for (int[] gr : pathGroups) {
                f.lanes.addAll(runs.get((gr[0] + gr[1]) / 2));
                widths.add(gr[1] - gr[0] + 1);
            }
            f.passWidth = median(widths);
        } else {
            int stepN = !p.stepAuto ? Math.max(1, p.step) : f.cropPeriod >= 2 ? f.cropPeriod : AUTO_FIELD_STEP;
            for (int[] gr : groups(ok)) {
                int gw = gr[1] - gr[0] + 1;
                if (p.stepAuto && gw <= stepN) { f.lanes.addAll(runs.get((gr[0] + gr[1]) / 2)); continue; }
                int off = ((gw - 1) % stepN) / 2;
                for (int li = gr[0] + off; li <= gr[1]; li += stepN) f.lanes.addAll(runs.get(li));
            }
            List<Integer> widths = new ArrayList<>();
            for (int[] gr : groups(ok)) widths.add(gr[1] - gr[0] + 1);
            f.passWidth = median(widths);
        }
        finishLanes(g, f);
    }

    /** Рядов урожая дальше 1 линии от ближайшего прохода. */
    private static int uncovered(boolean[] cropLine, List<int[]> groups) {
        int n = 0;
        for (int li = 0; li < cropLine.length; li++) {
            if (!cropLine[li]) continue;
            boolean near = false;
            for (int[] gr : groups) if (li >= gr[0] - 1 && li <= gr[1] + 1) { near = true; break; }
            if (!near) n++;
        }
        return n;
    }

    /** Жадное покрытие: слева направо берём самый правый проход, который ещё достаёт первый непокрытый ряд. */
    private static List<int[]> cover(boolean[] cropLine, List<int[]> groups) {
        List<int[]> out = new ArrayList<>();
        int li = 0;
        while (li < cropLine.length) {
            if (!cropLine[li]) { li++; continue; }
            int[] best = null;
            for (int[] gr : groups) if (gr[0] - 1 <= li && gr[1] + 1 >= li) best = gr;   // группы по возрастанию
            if (best == null) { li++; continue; }
            if (out.isEmpty() || out.get(out.size() - 1) != best) out.add(best);
            li = best[1] + 2;
        }
        return out.isEmpty() ? groups : out;
    }

    private static void finishLanes(FarmGrid g, Floor f) {
        boolean ax = f.alongX;
        f.lanes.removeIf(l -> l[2] < l[1]);
        List<Integer> pos = new ArrayList<>();
        for (int[] l : f.lanes) if (pos.isEmpty() || pos.get(pos.size() - 1) != l[0]) pos.add(l[0]);
        f.laneStep = Math.max(1, median(diffs(pos)));
        // связность рядов
        int[] comp = components(g, f);
        java.util.Set<Integer> cs = new java.util.HashSet<>();
        for (int[] l : f.lanes) cs.add(comp[col(g, cx(ax, l[0], l[1]), cz(ax, l[0], l[1]))]);
        f.parts = Math.max(1, cs.size());
        f.type = f.parts > 1 ? "сложный" : f.lines() <= 1 ? "линейный" : "зигзаг";
    }

    private static List<Integer> diffs(List<Integer> v) {
        List<Integer> d = new ArrayList<>();
        for (int i = 1; i < v.size(); i++) d.add(v.get(i) - v.get(i - 1));
        return d;
    }

    private static int median(List<Integer> v) {
        if (v.isEmpty()) return 0;
        List<Integer> s = new ArrayList<>(v);
        s.sort(null);
        return s.get(s.size() / 2);
    }

    /** Подряд идущие true: {от, до}. */
    private static List<int[]> groups(boolean[] m) {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < m.length; i++) {
            if (!m[i]) continue;
            int j = i;
            while (j + 1 < m.length && m[j + 1]) j++;
            out.add(new int[]{i, j});
            i = j;
        }
        return out;
    }

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Компоненты связности по шагам этажа. */
    static int[] components(FarmGrid g, Floor f) {
        int n = g.w * g.d;
        int[] comp = new int[n];
        Arrays.fill(comp, -1);
        int id = 0;
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int s = 0; s < n; s++) {
            if (!f.walk[s] || comp[s] >= 0) continue;
            comp[s] = id; q.add(s);
            while (!q.isEmpty()) {
                int c = q.poll(), x = g.x0 + c / g.d, z = g.z0 + c % g.d;
                for (int[] d : DIRS) {
                    int nx = x + d[0], nz = z + d[1];
                    if (!step(g, f, x, z, nx, nz)) continue;
                    int nc = col(g, nx, nz);
                    if (comp[nc] < 0) { comp[nc] = id; q.add(nc); }
                }
            }
            id++;
        }
        return comp;
    }

    /** Если ходить можно только по одному коридору шириной 1 — клетки по порядку, иначе null. */
    static List<int[]> corridor(FarmGrid g, Floor f) {
        int x0 = Math.max(g.x0, f.bx0 - 2), x1 = Math.min(g.x0 + g.w - 1, f.bx1 + 2);
        int z0 = Math.max(g.z0, f.bz0 - 2), z1 = Math.min(g.z0 + g.d - 1, f.bz1 + 2);
        List<int[]> cells = new ArrayList<>();
        int[] end = null;
        int ends = 0;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            if (!walkable(g, f, x, z)) continue;
            int nb = 0;
            for (int[] d : DIRS) { int nx = x + d[0], nz = z + d[1]; if (nx >= x0 && nx <= x1 && nz >= z0 && nz <= z1 && step(g, f, x, z, nx, nz)) nb++; }
            if (nb > 2) return null;
            if (nb <= 1) { ends++; if (end == null) end = new int[]{x, z}; }
            cells.add(new int[]{x, z});
        }
        if (cells.size() < 6 || ends > 2) return null;
        if (end == null) end = cells.get(0);     // кольцо
        List<int[]> out = new ArrayList<>();
        boolean[] seen = new boolean[g.w * g.d];
        int[] cur = end;
        while (cur != null) {
            out.add(cur);
            seen[col(g, cur[0], cur[1])] = true;
            int[] nxt = null;
            for (int[] d : DIRS) {
                int nx = cur[0] + d[0], nz = cur[1] + d[1];
                if (nx < x0 || nx > x1 || nz < z0 || nz > z1 || !step(g, f, cur[0], cur[1], nx, nz) || seen[col(g, nx, nz)]) continue;
                nxt = new int[]{nx, nz};
                break;
            }
            cur = nxt;
        }
        return out.size() == cells.size() ? out : null;   // несвязные куски — не коридор
    }

    // ── A* (Дейкстра с штрафом за поворот) ───────────────────────────────────

    /**
     * Кратчайший путь по шагам этажа от (sx, sz) до первой клетки, где goal(col) = true.
     * Штраф 0.5 за поворот — меньше лишних углов. @return клетки {x, z} от старта до цели или null.
     */
    // один разбор за раз (авто-маршрут строится в одном фоновом потоке), synchronized build — на всякий случай
    private static double[] wDist;
    private static int[] wPrev, wGen;
    private static int gen;

    static List<int[]> path(FarmGrid g, Floor f, int sx, int sz, IntPredicate goal, int hx, int hz) {
        if (!walkable(g, f, sx, sz)) return null;
        int n = g.w * g.d;
        // рабочие массивы — одни на все вызовы (на больших фермах путь ищется сотни раз): метка поколения вместо заливки
        if (wDist == null || wDist.length < n * 4) { wDist = new double[n * 4]; wPrev = new int[n * 4]; wGen = new int[n * 4]; gen = 0; }
        if (++gen == Integer.MAX_VALUE) { Arrays.fill(wGen, 0); gen = 1; }
        final double[] dist = wDist; final int[] prev = wPrev, seen = wGen; final int gn = gen;
        PriorityQueue<double[]> pq = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        int s = col(g, sx, sz);
        boolean heur = hx != Integer.MIN_VALUE;
        for (int dir = 0; dir < 4; dir++) { dist[s * 4 + dir] = 0; prev[s * 4 + dir] = -1; seen[s * 4 + dir] = gn; pq.add(new double[]{heur ? Math.abs(sx - hx) + Math.abs(sz - hz) : 0, s * 4 + dir}); }
        int found = -1;
        while (!pq.isEmpty()) {
            double[] e = pq.poll();
            int st = (int) e[1], c = st / 4, dir = st % 4;
            int x = g.x0 + c / g.d, z = g.z0 + c % g.d;
            double dd = dist[st];
            if (e[0] - (heur ? Math.abs(x - hx) + Math.abs(z - hz) : 0) > dd + 1e-9) continue;
            if (goal.test(c)) { found = st; break; }
            for (int nd = 0; nd < 4; nd++) {
                int nx = x + DIRS[nd][0], nz = z + DIRS[nd][1];
                if (!step(g, f, x, z, nx, nz)) continue;
                int ns = col(g, nx, nz) * 4 + nd;
                double cost = dd + 1 + (nd != dir && c != s ? 0.5 : 0);
                if (seen[ns] != gn || cost < dist[ns] - 1e-9) {
                    seen[ns] = gn;
                    dist[ns] = cost; prev[ns] = st;
                    pq.add(new double[]{cost + (heur ? Math.abs(nx - hx) + Math.abs(nz - hz) : 0), ns});
                }
            }
        }
        if (found < 0) return null;
        ArrayDeque<int[]> out = new ArrayDeque<>();
        for (int st = found; st >= 0; st = prev[st]) {
            int c = st / 4;
            int[] cell = {g.x0 + c / g.d, g.z0 + c % g.d};
            if (out.isEmpty() || out.peekFirst()[0] != cell[0] || out.peekFirst()[1] != cell[1]) out.addFirst(cell);
        }
        return new ArrayList<>(out);
    }

    static List<int[]> pathTo(FarmGrid g, Floor f, int sx, int sz, int tx, int tz) {
        if (!walkable(g, f, tx, tz)) return null;
        int tc = col(g, tx, tz);
        return path(g, f, sx, sz, c -> c == tc, tx, tz);
    }

    /** Углы пути и клетки по обе стороны ступеньки (смена высоты ног: бортик → вода), без первой. */
    static List<int[]> corners(FarmGrid g, Floor f, List<int[]> cells) {
        List<int[]> out = new ArrayList<>();
        for (int i = 1; i < cells.size(); i++) {
            int[] a = cells.get(i - 1), b = cells.get(i);
            boolean keep = i + 1 == cells.size();
            if (!keep) {
                int[] c = cells.get(i + 1);
                keep = b[0] - a[0] != c[0] - b[0] || b[1] - a[1] != c[1] - b[1]
                        || Double.compare(f.feet[col(g, a[0], a[1])], f.feet[col(g, b[0], b[1])]) != 0
                        || Double.compare(f.feet[col(g, b[0], b[1])], f.feet[col(g, c[0], c[1])]) != 0;
            }
            if (keep) out.add(b);
        }
        return out;
    }

    /** Клетки прямой (fx, fz) → (tx, tz) включительно. */
    static List<int[]> line(int fx, int fz, int tx, int tz) {
        List<int[]> out = new ArrayList<>();
        int sx = Integer.signum(tx - fx), sz = Integer.signum(tz - fz);
        for (int x = fx, z = fz; ; x += sx, z += sz) {
            out.add(new int[]{x, z});
            if (x == tx && z == tz) break;
        }
        return out;
    }

    /** Только углы пути (и последняя клетка), без первой. */
    static List<int[]> corners(List<int[]> cells) {
        List<int[]> out = new ArrayList<>();
        for (int i = 1; i < cells.size(); i++) {
            if (i + 1 < cells.size()) {
                int[] a = cells.get(i - 1), b = cells.get(i), c = cells.get(i + 1);
                if (b[0] - a[0] == c[0] - b[0] && b[1] - a[1] == c[1] - b[1]) continue;
            }
            out.add(cells.get(i));
        }
        return out;
    }

    // ── Построение ───────────────────────────────────────────────────────────

    public static final class Result {
        public final List<RoutePoint> points = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();
        public Analysis analysis;
        public int issues;
        public String firstIssue;
        public boolean looped;
        /** Совет в чат (как поставить камеру). */
        public String hint;
    }

    private static RoutePoint pt(FarmGrid g, Floor f, int x, int z, String action) {
        RoutePoint p = new RoutePoint(x + 0.5, f.feet[col(g, x, z)], z + 0.5);
        p.action = action;
        return p;
    }

    /** Дойти по этажу до клетки (переход): точки с действием перехода. false — не дойти. */
    private static boolean walkTo(FarmGrid g, Floor f, int[] cur, int tx, int tz, List<RoutePoint> out, String action) {
        if (cur[0] == tx && cur[1] == tz) return true;
        List<int[]> cells = pathTo(g, f, cur[0], cur[1], tx, tz);
        if (cells == null) return false;
        if (!out.isEmpty()) out.get(out.size() - 1).action = action;
        for (int[] c : corners(g, f, cells)) out.add(pt(g, f, c[0], c[1], action));
        cur[0] = tx; cur[1] = tz;
        return true;
    }

    /**
     * Построить маршрут. ax/az — угол, с которого начинать (первый клик). Высоты точек — ноги на этаже.
     */
    public static synchronized Result build(FarmGrid g, Params p, int ax, int az) {
        Result r = new Result();
        Analysis a = analyze(g, p);
        r.analysis = a;
        if (a.error != null) return r;
        List<RoutePoint> out = r.points;
        int[] cur = null;
        for (int fi = 0; fi < a.floors.size(); fi++) {
            Floor f = a.floors.get(fi);
            int entryX = cur != null ? cur[0] : ax, entryZ = cur != null ? cur[1] : az;
            if (f.corridor != null) {
                List<int[]> c = f.corridor;
                int[] s = c.get(0), e = c.get(c.size() - 1);
                if (Math.abs(e[0] - entryX) + Math.abs(e[1] - entryZ) < Math.abs(s[0] - entryX) + Math.abs(s[1] - entryZ)) {
                    c = new ArrayList<>(c);
                    java.util.Collections.reverse(c);
                }
                int[] st = c.get(0);
                if (cur == null) { out.add(pt(g, f, st[0], st[1], p.rowAction)); cur = new int[]{st[0], st[1]}; }
                else if (!walkTo(g, f, cur, st[0], st[1], out, p.turnAction)) { r.warnings.add("этаж " + (fi + 1) + ": до начала коридора не дойти"); break; }
                out.get(out.size() - 1).action = p.rowAction;
                for (int[] k : corners(g, f, c)) out.add(pt(g, f, k[0], k[1], p.rowAction));
                int[] last = c.get(c.size() - 1);
                cur = new int[]{last[0], last[1]};
                out.get(out.size() - 1).pauseTicks = p.endPause;
            } else {
                List<int[]> lanes = new ArrayList<>(f.lanes);
                if (lanes.isEmpty()) { r.warnings.add("этаж " + (fi + 1) + ": рядов не найдено"); continue; }
                boolean ax2 = f.alongX;
                int[] first = lanes.get(0), lastL = lanes.get(lanes.size() - 1);
                int dFirst = Math.min(dist(ax2, first, first[1], entryX, entryZ), dist(ax2, first, first[2], entryX, entryZ));
                int dLast = Math.min(dist(ax2, lastL, lastL[1], entryX, entryZ), dist(ax2, lastL, lastL[2], entryX, entryZ));
                if (dLast < dFirst) java.util.Collections.reverse(lanes);
                int skipped = 0;
                for (int li = 0; li < lanes.size(); ) {
                    // куски одной линии — по близости к текущей позиции
                    int lj = li;
                    while (lj < lanes.size() && lanes.get(lj)[0] == lanes.get(li)[0]) lj++;
                    List<int[]> line = new ArrayList<>(lanes.subList(li, lj));
                    li = lj;
                    while (!line.isEmpty()) {
                        int px = cur != null ? cur[0] : entryX, pz = cur != null ? cur[1] : entryZ;
                        int bi = 0;
                        for (int k = 1; k < line.size(); k++) {
                            int[] q = line.get(k), b0 = line.get(bi);
                            if (Math.min(dist(ax2, q, q[1], px, pz), dist(ax2, q, q[2], px, pz))
                                    < Math.min(dist(ax2, b0, b0[1], px, pz), dist(ax2, b0, b0[2], px, pz))) bi = k;
                        }
                        int[] l = line.remove(bi);
                        boolean fwd = dist(ax2, l, l[1], px, pz) <= dist(ax2, l, l[2], px, pz);
                        int from = fwd ? l[1] : l[2], to = fwd ? l[2] : l[1];
                        int fx = cx(ax2, l[0], from), fz = cz(ax2, l[0], from), tx = cx(ax2, l[0], to), tz = cz(ax2, l[0], to);
                        if (cur == null) { out.add(pt(g, f, fx, fz, p.rowAction)); cur = new int[]{fx, fz}; }
                        else if (!walkTo(g, f, cur, fx, fz, out, p.turnAction)) { skipped++; continue; }
                        out.get(out.size() - 1).action = p.rowAction;
                        if (from != to) {
                            for (int[] k : corners(g, f, line(fx, fz, tx, tz))) out.add(pt(g, f, k[0], k[1], p.rowAction));
                            out.get(out.size() - 1).action = p.turnAction;
                            cur[0] = tx; cur[1] = tz;
                        }
                        out.get(out.size() - 1).pauseTicks = p.endPause;
                    }
                    if (out.size() > p.maxPoints) break;
                }
                if (skipped > 0) r.warnings.add("этаж " + (fi + 1) + ": рядов не достать " + skipped + " (нет прохода)");
                if (f.uncovered > 0) r.warnings.add("этаж " + (fi + 1) + ": рядов урожая дальше 1 блока от прохода — "
                        + f.uncovered + " (с прохода их не сломать, проверь превью)");
            }
            if (cur == null) continue;
            // спуск на следующий этаж
            if (fi + 1 < a.floors.size()) {
                Floor nf = a.floors.get(fi + 1);
                double myFeet = f.feet[col(g, cur[0], cur[1])];
                List<int[]> cells = path(g, f, cur[0], cur[1], c -> dropEdge(g, f, nf, c, null) >= 0, Integer.MIN_VALUE, 0);
                if (cells == null) { r.warnings.add("с этажа " + (fi + 1) + " нет спуска на этаж " + (fi + 2) + " — дальше не строю"); break; }
                int[] e = cells.get(cells.size() - 1);
                int ec = col(g, e[0], e[1]);
                int[] land = new int[2];
                int dd = dropEdge(g, f, nf, ec, land);
                if (!out.isEmpty()) out.get(out.size() - 1).action = p.turnAction;
                for (int[] k : corners(g, f, cells)) out.add(pt(g, f, k[0], k[1], p.turnAction));
                RoutePoint dp = out.get(out.size() - 1);
                dp.drop = true;
                dp.action = RoutePoint.NONE;
                dp.dropDir = new String[]{"+x", "-x", "+z", "-z"}[dd];
                RoutePoint lp = pt(g, nf, land[0], land[1], RoutePoint.NONE);
                out.add(lp);
                Terrain.fillDepth(g, out, out.size() - 2);
                cur = new int[]{land[0], land[1]};
                if (Double.isNaN(myFeet)) break;
            }
        }
        if (out.isEmpty()) { r.warnings.add("рядов не найдено"); return r; }
        Floor mf = a.main();
        if (mf.lowered) r.hint = "идёшь " + (mf.wetLanes ? "по воде" : "по каналу") + " ниже грядок: yaw вдоль канала, pitch чуть вверх — на урожай по бокам";
        else if (mf.atLevel) r.hint = "ход на уровне урожая: поверни yaw на ~45° к рядам — автоход пойдёт по диагонали (W+A / W+D) "
                + "и будет ломать сразу по два блока";
        // возврат к старту
        if (p.returnToStart && out.size() > 1) {
            RoutePoint s = out.get(0);
            int sx = (int) Math.floor(s.x), sz = (int) Math.floor(s.z);
            Floor sf = a.floors.get(0), lf = floorOf(a, out.get(out.size() - 1).y);
            if (lf != sf) r.warnings.add("вернуться к старту нельзя: старт на этаже выше (лестницы не используются)");
            else if (cur != null && !(cur[0] == sx && cur[1] == sz)) {
                int before = out.size();
                if (walkTo(g, sf, cur, sx, sz, out, p.turnAction)) {
                    r.looped = true;
                    if (out.size() > before) out.get(out.size() - 1).action = RoutePoint.NONE;
                } else r.warnings.add("вернуться к старту нельзя: нет прохода");
            } else r.looped = true;
        }
        if (out.size() > p.maxPoints) {
            r.warnings.add("слишком много точек: " + out.size() + " > " + p.maxPoints);
        }
        out.get(out.size() - 1).action = RoutePoint.NONE;
        // проверка рельефа — как в редакторе (красная лента)
        for (int i = 0; i + 1 < out.size(); i++) {
            Terrain.Issue is = Terrain.check(g, out, i);
            if (is == Terrain.Issue.OK) continue;
            r.issues++;
            if (r.firstIssue == null) r.firstIssue = "отрезок " + (i + 1) + "→" + (i + 2) + ": " + is.text;
        }
        return r;
    }

    private static Floor floorOf(Analysis a, double y) {
        Floor best = null;
        for (Floor f : a.floors) if (best == null || Math.abs(f.level - y) < Math.abs(best.level - y)) best = f;
        return best;
    }

    private static int dist(boolean alongX, int[] lane, int s, int x, int z) {
        return Math.abs(cx(alongX, lane[0], s) - x) + Math.abs(cz(alongX, lane[0], s) - z);
    }

    /**
     * Клетка c этажа f — край для спуска на этаж nf? @return индекс направления (+x, −x, +z, −z) или −1;
     * land — куда приземлимся (колонка на nf).
     */
    static int dropEdge(FarmGrid g, Floor f, Floor nf, int c, int[] land) {
        if (!f.walk[c]) return -1;
        int x = g.x0 + c / g.d, z = g.z0 + c % g.d;
        double feet = f.feet[c];
        for (int d = 0; d < 4; d++) {
            int nx = x + DIRS[d][0], nz = z + DIRS[d][1];
            if (!g.inXZ(nx, nz)) continue;
            int nc = col(g, nx, nz);
            if (f.walk[nc] || !nf.walk[nc]) continue;
            double lf = nf.feet[nc];
            if (lf > feet - Terrain.FLOOR_GAP - 1e-9) continue;
            if (Terrain.blockedAt(g, nx + 0.5, feet, nz + 0.5)) continue;
            double fy = Terrain.floorY(g, nx + 0.5, feet, nz + 0.5, 64);
            if (Double.isNaN(fy) || Math.abs(fy - lf) > 0.6) continue;     // упадём не на тот этаж
            if (land != null) { land[0] = nx; land[1] = nz; }
            return d;
        }
        return -1;
    }
}
