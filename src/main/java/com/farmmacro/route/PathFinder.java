package com.farmmacro.route;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * v1.15 «Возврат на маршрут»: свой A* по блокам — без зависимостей от игры (проверяется в routeStress, PathCheck).
 * Идеи (только идеи, код свой): Baritone (LGPL-3) — стоимость движений, A* по клеткам, перепланирование;
 * HELM-MC (LGPL-3) — «сбили с пути → перестроить с текущего места».
 * <ul>
 * <li>Узел — колонка (x, z) и высота ног (верх коллизии опоры: плита 0.5, грядка 15/16, блок 1.0, забор 1.5 — на забор не встать).</li>
 * <li>Ходы: 8 направлений; шаг вверх до {@link Terrain#STEP} без прыжка, до {@link #JUMP_MAX} — с прыжком; вниз до
 *     {@link #DROP_MAX} блоков; по диагонали — только по ровному, если обе соседние клетки проходимы (углы не режем).</li>
 * <li>Вода, лава, огонь, магма, кактус, паутина, ягоды, рыхлый снег, роза визера — обходить (в клетке тела и под ногами);
 *     спрыгнуть на грядку нельзя (вытопчет); рядом с лавой — дороже.</li>
 * <li>Цель — набор точек на линиях маршрута (см. {@link #routeGoals}); у каждой своя добавка к стоимости
 *     (другой этаж, пропуск/повтор участка, подход против хода). Поиск — до «стока»: цель + добавка.</li>
 * <li>Путь сглаживается: прямые участки по ровному склеиваются, углы срезаются там, где проходит тело 0.6 бл.</li>
 * <li>v1.16.2: лестницы, лианы, строительные леса — ход {@link Move#CLIMB} вверх/вниз по колонке; тонкие пластины у края
 *     клетки (открытая дверь, люк, лестница) — «стенка» только на этой грани ({@link World#edges}); закрытая деревянная
 *     дверь/калитка ({@link #H_DOOR}) — проходима, но дороже (ReturnWalker откроет её ПКМ).</li>
 * </ul>
 */
public final class PathFinder {

    /** Мир для поиска: верх коллизии (как {@link Terrain.Cells}) и «опасность» клетки. */
    public interface World extends Terrain.Cells {
        /** {@link #H_NONE}, {@link #H_WATER}, {@link #H_LAVA}, {@link #H_DANGER}, {@link #H_CLIMB}, {@link #H_DOOR}. */
        int hazard(int x, int y, int z);
        /** v1.16.2: тонкие пластины у граней клетки (биты {@link #E_PX}…{@link #E_NZ}) — через эту грань не пройти. */
        default int edges(int x, int y, int z) { return 0; }
        /** Exact shape veto when available; coarse/cache worlds remain conservative through top/edges. */
        default boolean bodyClear(double x,double y,double z,double radius,double height) { return true; }
    }

    /** Опасность клетки; H_CLIMB — по ней лезут (лестница, лиана, леса), H_DOOR — закрытая дверь/калитка, открыть ПКМ. */
    public static final int H_NONE = 0, H_WATER = 1, H_LAVA = 2, H_DANGER = 3, H_CLIMB = 4, H_DOOR = 5;
    /** Грани клетки: +x, −x, +z, −z. */
    public static final int E_PX = 1, E_NX = 2, E_PZ = 4, E_NZ = 8;
    /** v1.16.2 цена: лезть вверх / вниз за блок, через дверь (открыть). */
    public static final double C_CLIMB_UP = 1.3, C_CLIMB_DOWN = 1.1, C_DOOR = 2.5;

    /** Прыжком — до 1.25 бл (блок, грядка на блоке; забор 1.5 — нет). */
    public static final double JUMP_MAX = 1.25;
    /** Спуск без урона — до 3 блоков. */
    public static final double DROP_MAX = 3.0;
    /** Рост игрока (тело 1.8). */
    public static final double BODY = 1.8;
    /** Стоимость: шаг, прыжок, спуск (+ за блок), рядом с лавой. */
    public static final double C_WALK = 1.0, C_DIAG = Math.sqrt(2), C_JUMP = 1.5, C_DROP = 1.0, C_DROP_BLOCK = 0.4, C_LAVA_NEAR = 3.0;
    /** Добавки к цели: другой этаж, подход против хода (макс.). */
    public static final double PEN_FLOOR = 24, PEN_DIR = 4;
    /** Добавки за порядок: пропустить участок маршрута (за блок) / пройти его ещё раз (за блок). */
    public static final double PEN_SKIP = 1.0, PEN_REDO = 0.3;
    /** Своя высота цели и узла: совпадают, если ближе этого. */
    public static final double LEVEL_TOL = 0.65;

    public enum Move { START, WALK, JUMP, DROP, FLY, CLIMB }

    /** Цель: точка на маршруте (x, y, z), отрезок seg → seg+1 и доля t; join — с какой точки продолжать; pen — добавка. */
    public static final class Goal {
        public final double x, y, z, t, pen, dirX, dirZ;
        public final int seg, join;
        public Goal(double x, double y, double z, int seg, double t, int join, double pen, double dirX, double dirZ) {
            this.x = x; this.y = y; this.z = z; this.seg = seg; this.t = t; this.join = join; this.pen = pen;
            this.dirX = dirX; this.dirZ = dirZ;
        }
    }

    public static final class Params {
        /** Предел: цель дальше по горизонтали — «далеко»; поиск — в квадрате ±(maxDist + 8). */
        public double maxDist = 64;
        /** Сколько узлов раскрыть, прежде чем сдаться. */
        public int maxNodes = 150_000;
        public java.util.function.BooleanSupplier cancelled=()->false;
        /** v1.15.1: можно лететь (полёт от предмета/способности): ходы в воздухе на 26 соседей. */
        public boolean fly;
        /** Полёт: не выше этого над самой высокой из (старт, цели), блоков. */
        public int flyCeil = 12;
        /** Игрок уже летит (старт — в воздухе, без поиска опоры). */
        public boolean flyingNow;
    }

    /** v1.15.1 цена полёта: блок по горизонтали, вверх, вниз, взлёт (с земли в воздух). */
    public static final double C_FLY = 1.15, C_FLY_UP = 1.4, C_FLY_DOWN = 1.05, C_TAKEOFF = 3.0;

    /** Итог. ok — путь есть: points (без стартовой клетки, последняя — точка цели), goal, cost; иначе reason. */
    public static final class Result {
        public boolean ok;
        public java.util.Map<CachedPathWorld.Cell,Short> cachedCells = java.util.Map.of();
        public String reason;
        public Goal goal;
        public double cost;
        public int expanded;
        public long micros;
        /** Сырые узлы (x, z, ноги, ход) — для проверок и отрисовки. */
        public final List<double[]> nodes = new ArrayList<>();
        /** Сглаженный путь для автохода. */
        public final List<RoutePoint> points = new ArrayList<>();
        @Override public String toString() {
            return ok ? String.format(Locale.ROOT, "путь: %d точек, %d узлов, цена %.1f, к отрезку %d (%.0f%%)",
                    points.size(), nodes.size(), cost, goal.seg + 1, goal.t * 100) : "нет пути: " + reason;
        }
    }

    // ── цели ─────────────────────────────────────────────────────────────────

    /** Одна цель — точка (старт маршрута, кадр записи, точка остановки). */
    public static List<Goal> pointGoal(double x, double y, double z, int join) {
        return List.of(new Goal(x, y, z, Math.max(0, join - 1), 1, join, 0, 0, 0));
    }

    /**
     * Цели на линиях маршрута: каждые 0.5 бл по отрезкам (кроме отрезков-спусков и нулевой длины).
     * @param myY      высота ног игрока: цели на другом этаже (|Δy| > 1.5) — дороже на {@link #PEN_FLOOR}
     * @param prefSeg  где игрок был на маршруте (−1 — неважно, «Продолжить с места»): пропуск дальше — {@link #PEN_SKIP}
     *                 за блок пути по маршруту, возврат назад — {@link #PEN_REDO}
     */
    public static List<Goal> routeGoals(List<RoutePoint> pts, double myY, int prefSeg, double prefT) {
        List<Goal> out = new ArrayList<>();
        if (pts == null || pts.isEmpty()) return out;
        if (pts.size() == 1) { RoutePoint p = pts.get(0); return new ArrayList<>(pointGoal(p.x, p.y, p.z, 0)); }
        int n = pts.size() - 1;
        double[] at = new double[n + 1];                      // путь по маршруту до начала отрезка i
        for (int i = 0; i < n; i++) at[i + 1] = at[i] + Math.hypot(pts.get(i + 1).x - pts.get(i).x, pts.get(i + 1).z - pts.get(i).z);
        double prefPos = prefSeg < 0 ? Double.NaN : at[Math.min(n - 1, prefSeg)] + Math.max(0, Math.min(1, prefT))
                * (at[Math.min(n, prefSeg + 1)] - at[Math.min(n - 1, prefSeg)]);
        for (int i = 0; i < n; i++) {
            RoutePoint a = pts.get(i), b = pts.get(i + 1);
            if (a.drop) continue;
            double dx = b.x - a.x, dz = b.z - a.z, len = Math.hypot(dx, dz);
            if (len < 1e-6) continue;
            int k = Math.max(1, (int) Math.ceil(len / 0.5));
            for (int s = 0; s <= k; s++) {
                double t = (double) s / k;
                if (s == k && i + 1 < n) continue;            // конец отрезка = начало следующего
                double x = a.x + dx * t, z = a.z + dz * t, y = a.y + (b.y - a.y) * t;
                double pen = Math.abs(y - myY) > 1.5 ? PEN_FLOOR : 0;
                if (!Double.isNaN(prefPos)) {
                    double d = at[i] + len * t - prefPos;
                    pen += d >= 0 ? d * PEN_SKIP : -d * PEN_REDO;
                }
                out.add(new Goal(x, y, z, i, t, i + 1, pen, dx / len, dz / len));
                if (out.size() >= 6000) return out;
            }
        }
        return out;
    }

    // ── поиск ────────────────────────────────────────────────────────────────

    private final World w;
    private final Params prm;
    // кэш мира: верх коллизии ×1024 (−1 — нет) и опасность
    private final CellCache cache = new CellCache();

    public PathFinder(World world, Params params) { this.w = world; this.prm = params == null ? new Params() : params; }

    private double top(int x, int y, int z) { return cache.top(x, y, z); }
    private int hazard(int x, int y, int z) { return cache.hazard(x, y, z); }
    private int edges(int x, int y, int z) { return cache.edges(x, y, z); }
    private boolean climbable(int x, int y, int z) { return cache.hazard(x, y, z) == H_CLIMB; }
    private static boolean bad(int h) { return h == H_WATER || h == H_LAVA || h == H_DANGER; }

    private static int fl(double v) { return (int) Math.floor(v); }

    /** Верх опоры в колонке не выше fromY и не ниже minY; NaN — нет. floorCell[0] — клетка опоры. */
    private double floor(int x, int z, double fromY, double minY, int[] floorCell) {
        int cy = fl(fromY + 0.01);
        for (int i = 0; cy - i >= fl(minY) - 2 && i < 12; i++) {
            int y = cy - i;
            double t = top(x, y, z);
            if (t >= 0 && y + t <= fromY + 0.01) {
                if (y + t < minY - 0.01) return Double.NaN;
                if (floorCell != null) floorCell[0] = y;
                return y + t;
            }
        }
        return Double.NaN;
    }

    /** Нет коллизии в колонке на высотах (lo, hi). */
    private boolean clear(int x, int z, double lo, double hi) {
        for (int cy = fl(lo) - 1; cy <= fl(hi - 1e-6); cy++) {
            double t = top(x, cy, z);
            if (t >= 0 && cy + t > lo + 0.01 && cy < hi - 0.01 && !climbable(x, cy, z)) return false;   // сквозь леса — можно
        }
        return true;
    }

    /** v1.16.2: из (x, z) в соседнюю ортогональную (x+dx, z+dz) мешает пластина на общей грани на высотах (lo, hi). */
    private boolean edgeBlocked(int x, int z, int dx, int dz, double lo, double hi) {
        int from = dx > 0 ? E_PX : dx < 0 ? E_NX : dz > 0 ? E_PZ : E_NZ;
        int to = dx > 0 ? E_NX : dx < 0 ? E_PX : dz > 0 ? E_NZ : E_PZ;
        for (int cy = fl(lo + 0.01); cy <= fl(hi - 0.01); cy++)
            if ((edges(x, cy, z) & from) != 0 || (edges(x + dx, cy, z + dz) & to) != 0) return true;
        return false;
    }

    /** Ход (x, z) → (x2, z2) (сосед, в т. ч. по диагонали) упирается в пластину. */
    private boolean crossBlocked(int x, int z, int x2, int z2, double lo, double hi) {
        int dx = x2 - x, dz = z2 - z;
        if (dx == 0 && dz == 0) return false;
        if (dx == 0 || dz == 0) return edgeBlocked(x, z, dx, dz, lo, hi);
        // по диагонали тело задевает все четыре грани у общего угла — любая пластина мешает
        return edgeBlocked(x, z, dx, 0, lo, hi) || edgeBlocked(x, z, 0, dz, lo, hi)
                || edgeBlocked(x + dx, z, 0, dz, lo, hi) || edgeBlocked(x, z + dz, dx, 0, lo, hi);
    }

    /** В клетках тела — закрытая дверь/калитка. */
    private boolean doorAt(int x, int z, double lo, double hi) {
        for (int cy = fl(lo + 0.01); cy <= fl(hi - 0.01); cy++) if (hazard(x, cy, z) == H_DOOR) return true;
        return false;
    }

    /** Нет воды/лавы/опасного в клетках тела на (lo, hi). */
    private boolean safe(int x, int z, double lo, double hi) {
        for (int cy = fl(lo + 0.01); cy <= fl(hi - 0.01); cy++) if (bad(hazard(x, cy, z))) return false;
        return true;
    }

    private final int[] fc = new int[1];

    /**
     * Где можно встать в колонке (x, z), если идём с высоты y0: опора от y0 + JUMP_MAX до y0 − DROP_MAX, тело свободно,
     * без опасностей; NaN — нельзя.
     */
    double stand(int x, int z, double y0) {
        double f = floor(x, z, y0 + JUMP_MAX, y0 - DROP_MAX, fc);
        if (Double.isNaN(f)) return Double.NaN;
        if (!clear(x, z, f, f + BODY)) return Double.NaN;
        if (!safe(x, z, f, f + BODY)) return Double.NaN;
        int h = hazard(x, fc[0], z);
        if (h == H_DANGER || h == H_LAVA) return Double.NaN;  // магма, костёр, кактус под ногами
        return f;
    }

    private boolean farmland(int x, int z, double f) {
        double t = f - Math.floor(f - 1e-6);
        return Math.abs(t - 0.9375) < 0.002;
    }

    private boolean lavaNear(int x, int z, double f) {
        int y = fl(f + 0.01);
        return hazard(x + 1, y, z) == H_LAVA || hazard(x - 1, y, z) == H_LAVA || hazard(x, y, z + 1) == H_LAVA
                || hazard(x, y, z - 1) == H_LAVA || hazard(x, y - 1, z) == H_LAVA;
    }

    // узлы
    private int n;
    private int[] nx = new int[1024], nz = new int[1024], par = new int[1024];
    private double[] ny = new double[1024], g = new double[1024];
    private byte[] mv = new byte[1024];
    private boolean[] closed = new boolean[1024];
    private final LongIntMap index = new LongIntMap();
    // куча: f и номер (узел ≥ 0, сток — −(k+1))
    private double[] hf = new double[1024];
    private int[] hi = new int[1024];
    private int hn;
    private final List<Goal> sinkGoal = new ArrayList<>();
    private final List<Integer> sinkNode = new ArrayList<>();

    private int sx, sz, R;
    private double flyLo, flyHi;
    private double[] hgrid;     // октильное расстояние до ближайшей колонки цели (эвристика)
    private int side;
    private java.util.Map<Long, List<Goal>> goalsAt;

    private static long key(int x, int z, double feet) {
        long y = Math.round(feet * 64) + (1 << 20);
        return ((long) (x & 0xFFFFF) << 42) | ((long) (z & 0xFFFFF) << 22) | (y & 0x3FFFFF);
    }
    private static long col(int x, int z) { return ((long) x << 32) ^ (z & 0xFFFFFFFFL); }

    private int node(int x, int z, double feet, int parent, double cost, Move m) {
        long k = key(x, z, feet);
        int i = index.get(k);
        if (i >= 0) {
            if (closed[i] || cost >= g[i] - 1e-9) return -1;
            g[i] = cost; par[i] = parent; mv[i] = (byte) m.ordinal();
            return i;
        }
        if (n == nx.length) grow();
        i = n++;
        nx[i] = x; nz[i] = z; ny[i] = feet; g[i] = cost; par[i] = parent; mv[i] = (byte) m.ordinal(); closed[i] = false;
        index.put(k, i);
        return i;
    }

    private void grow() {
        int c = nx.length * 2;
        nx = Arrays.copyOf(nx, c); nz = Arrays.copyOf(nz, c); par = Arrays.copyOf(par, c);
        ny = Arrays.copyOf(ny, c); g = Arrays.copyOf(g, c); mv = Arrays.copyOf(mv, c); closed = Arrays.copyOf(closed, c);
    }

    private void push(double f, int id) {
        if (hn == hf.length) { hf = Arrays.copyOf(hf, hn * 2); hi = Arrays.copyOf(hi, hn * 2); }
        int i = hn++;
        while (i > 0) {
            int p = (i - 1) >> 1;
            if (hf[p] <= f) break;
            hf[i] = hf[p]; hi[i] = hi[p]; i = p;
        }
        hf[i] = f; hi[i] = id;
    }

    private int pop() {
        int top = hi[0];
        double lf = hf[--hn]; int li = hi[hn];
        int i = 0;
        while (true) {
            int c = 2 * i + 1;
            if (c >= hn) break;
            if (c + 1 < hn && hf[c + 1] < hf[c]) c++;
            if (hf[c] >= lf) break;
            hf[i] = hf[c]; hi[i] = hi[c]; i = c;
        }
        if (hn > 0) { hf[i] = lf; hi[i] = li; }
        return top;
    }

    private double h(int x, int z) {
        int ix = x - sx + R, iz = z - sz + R;
        if (ix < 0 || iz < 0 || ix >= side || iz >= side) return 0;
        return hgrid[ix * side + iz];
    }

    /**
     * Найти путь от ног игрока (px, py, pz) к лучшей из целей.
     * @param onGround стоит ли игрок (нет — опора ищется под ним)
     */
    public Result find(double px, double py, double pz, boolean onGround, List<Goal> goals) {
        long t0 = System.nanoTime();
        Result r = new Result();
        sx = fl(px); sz = fl(pz);
        R = (int) Math.ceil(prm.maxDist) + 8;
        // цели в пределе
        goalsAt = new java.util.HashMap<>();
        double nearest = Double.MAX_VALUE;
        for (Goal gl : goals) {
            double d = Math.hypot(gl.x - px, gl.z - pz);
            nearest = Math.min(nearest, d);
            if (d > prm.maxDist) continue;
            goalsAt.computeIfAbsent(col(fl(gl.x), fl(gl.z)), k -> new ArrayList<>()).add(gl);
        }
        if (goalsAt.isEmpty()) {
            r.reason = goals.isEmpty() ? "маршрут пуст"
                    : String.format(Locale.ROOT, "до маршрута %.0f бл — дальше предела %.0f бл", nearest, prm.maxDist);
            r.micros = (System.nanoTime() - t0) / 1000;
            return r;
        }
        buildHeuristic();
        // старт: ноги игрока; если в воздухе — опора под ним
        double y0 = py;
        flyLo = py; flyHi = py;
        for (Goal gl : goals) { flyLo = Math.min(flyLo, gl.y); flyHi = Math.max(flyHi, gl.y); }
        flyLo -= 6; flyHi += prm.flyCeil;
        if (prm.fly && prm.flyingNow && !onGround) {
            y0 = fl(py + 0.01);                                     // летит: клетка ног, опору не ищем
        } else if (!onGround && (climbable(sx, fl(py + 0.01), sz) || climbable(sx, fl(py + 0.01) - 1, sz))) {
            y0 = climbable(sx, fl(py + 0.01), sz) ? fl(py + 0.01) : fl(py + 0.01) - 1;   // v1.16.2: висит на лестнице
        } else if (!onGround) {
            double f = floor(sx, sz, py, py - 64, null);
            if (!Double.isNaN(f)) y0 = f;
        }
        n = 0; hn = 0; index.clear(); sinkGoal.clear(); sinkNode.clear();
        int s = node(sx, sz, y0, -1, 0, Move.START);
        push(h(sx, sz), s);
        int expanded = 0;
        while (hn > 0) {
            if (Thread.currentThread().isInterrupted() || prm.cancelled.getAsBoolean()) { r.reason="поиск отменён"; return r; }
            int id = pop();
            if (id < 0) {                                           // сток — лучшая цель найдена
                int k = -id - 1;
                finish(r, sinkNode.get(k), sinkGoal.get(k), px, pz);
                r.expanded = expanded;
                r.micros = (System.nanoTime() - t0) / 1000;
                return r;
            }
            if (closed[id]) continue;
            closed[id] = true;
            if (++expanded > prm.maxNodes) { r.reason = "путь слишком сложный (раскрыто " + prm.maxNodes + " клеток)"; break; }
            int x = nx[id], z = nz[id];
            double y = ny[id];
            List<Goal> here = goalsAt.get(col(x, z));
            if (here != null) {
                double ax = 0, az = 0;
                if (par[id] >= 0) { ax = x - nx[par[id]]; az = z - nz[par[id]]; double l = Math.hypot(ax, az); if (l > 0) { ax /= l; az /= l; } }
                for (Goal gl : here) {
                    if (Math.abs(gl.y - y) > LEVEL_TOL) continue;
                    double cos = ax * gl.dirX + az * gl.dirZ;          // 1 — по ходу, −1 — навстречу
                    double pen = gl.pen + (gl.dirX == 0 && gl.dirZ == 0 || par[id] < 0 ? 0 : PEN_DIR * (1 - cos) / 2);
                    sinkGoal.add(gl); sinkNode.add(id);
                    push(g[id] + pen, -sinkGoal.size());
                }
            }
            expand(id, x, z, y);
            expandClimb(id, x, z, y);
            if (prm.fly) expandFly(id, x, z, y);
        }
        if (r.reason == null) r.reason = "путь не найден (тупик: вокруг стены, вода, лава или обрыв > 3 бл)";
        r.expanded = expanded;
        r.micros = (System.nanoTime() - t0) / 1000;
        return r;
    }

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private void expand(int id, int x, int z, double y) {
        for (int[] d : DIRS) {
            int x2 = x + d[0], z2 = z + d[1];
            if (Math.abs(x2 - sx) > R || Math.abs(z2 - sz) > R) continue;
            double f = stand(x2, z2, y);
            if (Double.isNaN(f)) continue;
            double dy = f - y;
            boolean diag = d[0] != 0 && d[1] != 0;
            if (crossBlocked(x, z, x2, z2, Math.max(y, f), Math.max(y, f) + BODY)) continue;   // пластина на грани (переходим на верхней высоте)
            boolean door = doorAt(x2, z2, f, f + BODY);
            if (diag && (door || doorAt(x, z, y, y + BODY))) continue;   // в дверь — только прямо
            Move m;
            double cost;
            if (dy > Terrain.STEP + 1e-9) {
                if (diag) continue;
                if (!clear(x, z, y, f + BODY)) continue;              // голова при прыжке
                m = Move.JUMP; cost = C_WALK + C_JUMP;
            } else if (dy < -Terrain.STEP - 1e-9) {
                if (diag) continue;
                if (!clear(x2, z2, f, y + BODY)) continue;             // шахта падения
                if (!safe(x2, z2, f, y + BODY)) continue;
                if (dy < -0.5 && farmland(x2, z2, f)) continue;        // спрыгнуть на грядку — вытопчет
                m = Move.DROP; cost = C_WALK + C_DROP + C_DROP_BLOCK * -dy;
            } else {
                if (dy < -0.5 && farmland(x2, z2, f)) continue;
                if (diag) {
                    // по диагонали — только если обе соседние клетки проходимы на том же уровне (углы не режем)
                    double a = stand(x2, z, y), b = stand(x, z2, y);
                    if (Double.isNaN(a) || Double.isNaN(b) || Math.abs(a - y) > Terrain.STEP || Math.abs(b - y) > Terrain.STEP) continue;
                    if (Math.abs(a - f) > Terrain.STEP || Math.abs(b - f) > Terrain.STEP) continue;
                }
                m = Move.WALK; cost = diag ? C_DIAG : C_WALK;
            }
            if (m == Move.WALK && !door && !w.bodyClear(x2+.5,f+.001,z2+.5,.30,BODY-.002)) continue;
            if (m == Move.WALK) for(int[] side: new int[][]{{1,0},{-1,0},{0,1},{0,-1}})
                if(!clear(x2+side[0],z2+side[1],f,f+BODY)) cost+=.12;
            if (lavaNear(x2, z2, f)) cost += C_LAVA_NEAR;
            if (door) cost += C_DOOR;
            double ng = g[id] + cost;
            int c = node(x2, z2, f, id, ng, m);
            if (c >= 0) push(ng + h(x2, z2), c);
        }
    }

    /**
     * v1.16.2 лестницы/лианы/леса: вверх — если клетка ног «лезть» и выше тоже (или наверху можно встать — леса);
     * вниз — в клетку «лезть» без коллизии под ногами (с верха лесов не спускаемся — там нужен присед);
     * сбоку — шагнуть в колонку лестницы на высоте ног или на блок ниже (с края стены на лестницу).
     */
    private void expandClimb(int id, int x, int z, double y) {
        int cy = fl(y + 0.01);
        if (climbable(x, cy, z)) {
            int y2 = cy + 1;
            double up = floor(x, z, y2 + 0.01, y2 - 0.01, null);
            if ((climbable(x, y2, z) || (!Double.isNaN(up) && Math.abs(up - y2) < 1e-6))
                    && clear(x, z, y2, y2 + BODY) && safe(x, z, y2, y2 + BODY)) {
                double ng = g[id] + C_CLIMB_UP * (y2 - y);
                int c = node(x, z, y2, id, ng, Move.CLIMB);
                if (c >= 0) push(ng + h(x, z), c);
            }
        }
        if (Math.abs(y - Math.rint(y)) < 1e-6) {
            int yd = (int) Math.rint(y) - 1;
            if (climbable(x, yd, z) && top(x, yd, z) < 0 && safe(x, z, yd, yd + 1)) {
                double ng = g[id] + C_CLIMB_DOWN;
                int c = node(x, z, yd, id, ng, Move.CLIMB);
                if (c >= 0) push(ng + h(x, z), c);
            }
        }
        for (int k = 0; k < 4; k++) {
            int x2 = x + DIRS[k][0], z2 = z + DIRS[k][1];
            if (Math.abs(x2 - sx) > R || Math.abs(z2 - sz) > R) continue;
            for (int ye = cy; ye >= cy - 1; ye--) {
                if (ye > y + 0.01 || ye < y - 1.01) continue;
                if (!climbable(x2, ye, z2) || top(x2, ye, z2) >= 0) continue;
                if (!clear(x2, z2, ye, y + BODY) || !safe(x2, z2, ye, ye + BODY)) continue;
                if (crossBlocked(x, z, x2, z2, y, y + BODY)) continue;
                double ng = g[id] + C_WALK + 0.5 + (y - ye) * C_DROP_BLOCK;
                int c = node(x2, z2, ye, id, ng, Move.CLIMB);
                if (c >= 0) push(ng + h(x2, z2), c);
                break;
            }
        }
    }

    private static final int[][] DIRS3 = buildDirs3();
    private static int[][] buildDirs3() {
        List<int[]> l = new ArrayList<>();
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            if ((dx != 0 || dy != 0 || dz != 0) && !(dy != 0 && (dx != 0 || dz != 0))) l.add(new int[]{dx, dy, dz});
        return l.toArray(new int[0][]);
    }

    /** Тело (ноги fy, целая высота) свободно и без опасностей в колонке. */
    private boolean air(int x, int z, int fy) {
        return clear(x, z, fy, fy + BODY) && safe(x, z, fy, fy + BODY);
    }

    /**
     * v1.15.1 «Полёт»: ходы в воздухе — 8 по горизонтали (без среза углов) и вверх/вниз на 1, ноги на целой высоте.
     * Клетка, где под ногами опора ровно на этой высоте, — обычное «стоя» (тот же ключ узла): так путь садится.
     */
    private void expandFly(int id, int x, int z, double y) {
        int fy = (int) Math.ceil(y - 1e-6);
        boolean fromAir = mv[id] == Move.FLY.ordinal() || (par[id] < 0 && prm.flyingNow);
        if (!fromAir && fy != y && !air(x, z, fy)) return;          // со ступени/плиты вверх не взлететь — потолок
        for (int[] d : DIRS3) {
            int x2 = x + d[0], z2 = z + d[2], y2 = fy + d[1];
            if (Math.abs(x2 - sx) > R || Math.abs(z2 - sz) > R) continue;
            if (y2 < flyLo || y2 > flyHi) continue;
            if (!air(x2, z2, y2)) continue;
            boolean diag = d[0] != 0 && d[2] != 0;
            if (diag && (!air(x2, z, y2) || !air(x, z2, y2))) continue;
            if (crossBlocked(x, z, x2, z2, y2, y2 + BODY)) continue;
            if (d[1] > 0 && !air(x, z, y2)) continue;
            double cost = d[1] > 0 ? C_FLY_UP : d[1] < 0 ? C_FLY_DOWN : diag ? C_FLY * Math.sqrt(2) : C_FLY;
            if (!fromAir) cost += C_TAKEOFF;
            // на опору ровно на этой высоте — это уже «стоя» (садимся)
            double f = floor(x2, z2, y2 + 0.01, y2 - 0.01, null);
            Move m = !Double.isNaN(f) && Math.abs(f - y2) < 1e-6 ? Move.WALK : Move.FLY;
            if (m == Move.WALK && !fromAir && d[1] == 0) continue;     // по земле — обычные ходы
            double ng = g[id] + cost;
            int c = node(x2, z2, y2, id, ng, Move.FLY);              // и посадка — «летим» (ReturnCore снижается сам)
            if (c >= 0) push(ng + h(x2, z2), c);
        }
    }

    /** Октильное расстояние до ближайшей колонки цели (двухпроходный chamfer по сетке ±R) — не больше реальной цены. */
    private void buildHeuristic() {
        side = 2 * R + 1;
        if (hgrid == null || hgrid.length != side * side) hgrid = new double[side * side];
        Arrays.fill(hgrid, 1e18);
        for (long k : goalsAt.keySet()) {
            int gx = (int) (k >> 32), gz = (int) k;
            int ix = gx - sx + R, iz = gz - sz + R;
            if (ix >= 0 && iz >= 0 && ix < side && iz < side) hgrid[ix * side + iz] = 0;
        }
        double d = Math.sqrt(2);
        for (int i = 0; i < side; i++) for (int j = 0; j < side; j++) {
            double v = hgrid[i * side + j];
            if (i > 0) { v = Math.min(v, hgrid[(i - 1) * side + j] + 1); if (j > 0) v = Math.min(v, hgrid[(i - 1) * side + j - 1] + d); if (j + 1 < side) v = Math.min(v, hgrid[(i - 1) * side + j + 1] + d); }
            if (j > 0) v = Math.min(v, hgrid[i * side + j - 1] + 1);
            hgrid[i * side + j] = v;
        }
        for (int i = side - 1; i >= 0; i--) for (int j = side - 1; j >= 0; j--) {
            double v = hgrid[i * side + j];
            if (i + 1 < side) { v = Math.min(v, hgrid[(i + 1) * side + j] + 1); if (j > 0) v = Math.min(v, hgrid[(i + 1) * side + j - 1] + d); if (j + 1 < side) v = Math.min(v, hgrid[(i + 1) * side + j + 1] + d); }
            if (j + 1 < side) v = Math.min(v, hgrid[i * side + j + 1] + 1);
            hgrid[i * side + j] = v;
        }
        // цели вне сетки быть не может (maxDist < R), но на всякий случай — ничего не завышаем
    }

    // ── сборка пути ──────────────────────────────────────────────────────────

    private void finish(Result r, int last, Goal goal, double px, double pz) {
        List<Integer> chain = new ArrayList<>();
        for (int i = last; i >= 0; i = par[i]) chain.add(i);
        java.util.Collections.reverse(chain);
        for (int i : chain) r.nodes.add(new double[]{nx[i] + 0.5, ny[i], nz[i] + 0.5, mv[i]});
        r.ok = true;
        r.goal = goal;
        r.cost = g[last];
        smooth(r, px, pz);
    }

    /**
     * Сгладить: от узла i — самый дальний j, до которого все ходы — по ровному и прямая проходима телом (±0.3 бл);
     * ход с прыжком/спуском — отдельной точкой. У точки перед прыжком — «прыжок».
     */
    private void smooth(Result r, double px, double pz) {
        List<double[]> nd = r.nodes;
        int k = nd.size() - 1;
        double[] from = {px, nd.get(0)[1], pz};
        int i = 0;
        List<double[]> keep = new ArrayList<>();
        while (i < k) {
            int j = i + 1;
            if (nd.get(j)[3] == Move.FLY.ordinal()) {
                // полёт: склеить только шаги в одну сторону (dx, dy, dz одинаковы)
                double[] a0 = nd.get(i), b0 = nd.get(j);
                double ddx = b0[0] - a0[0], ddy = b0[1] - a0[1], ddz = b0[2] - a0[2];
                while (j + 1 <= k && nd.get(j + 1)[3] == Move.FLY.ordinal()) {
                    double[] c0 = nd.get(j), c1 = nd.get(j + 1);
                    if (Math.abs(c1[0] - c0[0] - ddx) > 1e-6 || Math.abs(c1[1] - c0[1] - ddy) > 1e-6 || Math.abs(c1[2] - c0[2] - ddz) > 1e-6) break;
                    j++;
                }
            } else if (nd.get(j)[3] == Move.CLIMB.ordinal()) {
                // лестница: склеить шаги по той же колонке в ту же сторону
                double[] a0 = nd.get(i), b0 = nd.get(j);
                boolean sameCol = Math.abs(b0[0] - a0[0]) < 1e-6 && Math.abs(b0[2] - a0[2]) < 1e-6;
                double sgn = Math.signum(b0[1] - a0[1]);
                while (sameCol && j + 1 <= k && nd.get(j + 1)[3] == Move.CLIMB.ordinal()) {
                    double[] c0 = nd.get(j), c1 = nd.get(j + 1);
                    if (Math.abs(c1[0] - c0[0]) > 1e-6 || Math.abs(c1[2] - c0[2]) > 1e-6 || Math.signum(c1[1] - c0[1]) != sgn) break;
                    j++;
                }
            } else if (nd.get(j)[3] == Move.WALK.ordinal()) {
                double[] a = i == 0 ? from : nd.get(i);
                while (j + 1 <= k && nd.get(j + 1)[3] == Move.WALK.ordinal() && lineClear(a, j + 1 == k ? goalPos(r) : nd.get(j + 1))) j++;
            }
            keep.add(j == k ? goalPos(r) : nd.get(j));
            i = j;
        }
        if (k == 0) keep.add(goalPos(r));
        for (int q = 0; q < keep.size(); q++) {
            double[] p = keep.get(q);
            RoutePoint rp = new RoutePoint(p[0], p[1], p[2]);
            rp.fly = p[3] == Move.FLY.ordinal();
            rp.climb = p[3] == Move.CLIMB.ordinal();
            if (q + 1 < keep.size()) {
                double[] nxt = keep.get(q + 1);
                if (nxt[1] - p[1] > Terrain.STEP + 1e-9 && nxt[3] != Move.FLY.ordinal() && nxt[3] != Move.CLIMB.ordinal()) rp.jump = true;   // следующий ход — вверх с прыжком
            }
            r.points.add(rp);
        }
    }

    private static double[] goalPos(Result r) {
        double[] last = r.nodes.get(r.nodes.size() - 1);
        return new double[]{r.goal.x, last[1], r.goal.z, last[3]};
    }

    /** Прямая a → b проходима телом шириной 0.6 по одному уровню (±STEP), без опасностей и обрывов. */
    boolean lineClear(double[] a, double[] b) {
        double dx = b[0] - a[0], dz = b[2] - a[2], len = Math.hypot(dx, dz);
        if (len < 1e-6) return true;
        double ux = dx / len, uz = dz / len;
        int steps = (int) Math.ceil(len / 0.2);
        double lo = Math.min(a[1], b[1]), hiY = Math.max(a[1], b[1]);
        double[] offs = {-0.31, 0, 0.31};
        int[] px = new int[3], pz = new int[3];
        int ax = fl(a[0]), az = fl(a[2]), bx = fl(b[0]), bz = fl(b[2]);
        for (int s = 0; s <= steps; s++) {
            double t = (double) s / steps, x = a[0] + dx * t, z = a[2] + dz * t, y = a[1] + (b[1] - a[1]) * t;
            if(!w.bodyClear(x,y+.001,z,.30,BODY-.002))return false;
            for (int o = 0; o < 3; o++) {
                double off = offs[o];
                double qx = x - uz * off, qz = z + ux * off;
                int cx = fl(qx), cz = fl(qz);
                double f = stand(cx, cz, y);
                if (Double.isNaN(f) || f < lo - Terrain.STEP || f > hiY + Terrain.STEP) return false;
                if (Math.abs(f - y) > Terrain.STEP) return false;
                // v1.16.2: через пластину на грани не режем; дверь — только узлом пути (через её центр)
                if (s > 0 && (cx != px[o] || cz != pz[o])) {
                    if (Math.abs(cx - px[o]) > 1 || Math.abs(cz - pz[o]) > 1) return false;
                    if (crossBlocked(px[o], pz[o], cx, cz, lo, hiY + BODY)) return false;
                }
                if (!(cx == ax && cz == az) && !(cx == bx && cz == bz) && doorAt(cx, cz, f, f + BODY)) return false;
                px[o] = cx; pz[o] = cz;
            }
        }
        return true;
    }

    // ── кэш клеток ───────────────────────────────────────────────────────────

    private final class CellCache {
        private long[] keys = new long[1 << 14];
        private int[] vals = new int[1 << 14];
        private int size;
        private static final long EMPTY = Long.MIN_VALUE;
        CellCache() { Arrays.fill(keys, EMPTY); }

        private int get(int x, int y, int z) {
            long k = ((long) (x & 0x3FFFFF) << 42) | ((long) (y & 0xFFFFF) << 22) | (z & 0x3FFFFF);
            int mask = keys.length - 1, i = (int) (mix(k) & mask);
            while (keys[i] != EMPTY) { if (keys[i] == k) return vals[i]; i = (i + 1) & mask; }
            double t = w.top(x, y, z);
            int hz = w.hazard(x, y, z) & 15;
            int ed = w.edges(x, y, z) & 15;
            int v = ((t < 0 ? 0 : (int) Math.round(t * 1024) + 1) << 8) | (ed << 4) | hz;
            if (size * 2 >= keys.length) { rehash(); return get(x, y, z); }
            keys[i] = k; vals[i] = v; size++;
            return v;
        }
        double top(int x, int y, int z) { int v = get(x, y, z) >>> 8; return v == 0 ? -1 : (v - 1) / 1024.0; }
        int hazard(int x, int y, int z) { return get(x, y, z) & 15; }
        int edges(int x, int y, int z) { return (get(x, y, z) >>> 4) & 15; }
        private void rehash() {
            long[] ok = keys; int[] ov = vals;
            keys = new long[ok.length * 2]; vals = new int[ok.length * 2]; Arrays.fill(keys, EMPTY); size = 0;
            int mask = keys.length - 1;
            for (int j = 0; j < ok.length; j++) if (ok[j] != EMPTY) {
                int i = (int) (mix(ok[j]) & mask);
                while (keys[i] != EMPTY) i = (i + 1) & mask;
                keys[i] = ok[j]; vals[i] = ov[j]; size++;
            }
        }
    }

    private static long mix(long k) { k ^= k >>> 33; k *= 0xff51afd7ed558ccdL; k ^= k >>> 33; return k; }

    /** long → int (−1 — нет), открытая адресация. */
    private static final class LongIntMap {
        private long[] keys = new long[1 << 12];
        private int[] vals = new int[1 << 12];
        private boolean[] used = new boolean[1 << 12];
        private int size;
        void clear() { Arrays.fill(used, false); size = 0; }
        int get(long k) {
            int mask = keys.length - 1, i = (int) (mix(k) & mask);
            while (used[i]) { if (keys[i] == k) return vals[i]; i = (i + 1) & mask; }
            return -1;
        }
        void put(long k, int v) {
            if (size * 2 >= keys.length) rehash();
            int mask = keys.length - 1, i = (int) (mix(k) & mask);
            while (used[i]) { if (keys[i] == k) { vals[i] = v; return; } i = (i + 1) & mask; }
            used[i] = true; keys[i] = k; vals[i] = v; size++;
        }
        private void rehash() {
            long[] ok = keys; int[] ov = vals; boolean[] ou = used;
            keys = new long[ok.length * 2]; vals = new int[ok.length * 2]; used = new boolean[ok.length * 2]; size = 0;
            for (int j = 0; j < ok.length; j++) if (ou[j]) put(ok[j], ov[j]);
        }
    }
}
