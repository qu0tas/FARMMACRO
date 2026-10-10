package com.farmmacro.route;

/**
 * Точка маршрута. Все параметры (кроме координат и паузы) действуют на отрезке ОТ этой точки до следующей.
 * Хранится в config/farmmacro_routes/*.json как есть (Gson).
 */
public class RoutePoint {
    public static final String NONE = "none", ATTACK = "attack", USE = "use";

    /** Куда идти: центр верхней грани блока + смещение (ox, oz). */
    public double x, y, z;
    /** Смещение внутри блока по X/Z, −0.5…+0.5 (0 — центр). x = центр + ox, z = центр + oz. */
    public double ox, oz;
    public static final double OFFSET_MAX = 0.5;
    /** none | attack (держать ЛКМ) | use (держать ПКМ) — на отрезке до следующей точки. */
    public String  action = NONE;
    public boolean sneak, sprint;
    /** Один прыжок в начале отрезка. */
    public boolean jump;
    /** Слот хотбара 1–9, 0 — не менять. */
    public int     slot;
    /** Пауза в точке перед следующим отрезком, тиков. */
    public int     pauseTicks;
    /** «Зажим мыши: по точкам» в настройках маршрута — держать выбранную кнопку на отрезке от этой точки. */
    public boolean hold;

    // ── «Спуск» (v5): из этой точки упасть на этаж ниже ──
    public static final String DIR_AUTO = "auto";
    public static final java.util.List<String> DROP_DIRS = java.util.List.of(DIR_AUTO, "+x", "-x", "+z", "-z");
    /** Точка-спуск: дойти, шагнуть дальше по {@link #dropDir} до обрыва, упасть, приземлиться, потом к следующей. */
    public boolean drop;
    /** auto — по ходу (от предыдущей точки к этой), иначе +x / −x / +z / −z. */
    public String  dropDir = DIR_AUTO;
    /** Пауза после приземления, тиков. */
    public int     landPauseTicks;
    /** В полёте держать клавишу направления (иначе отпустить — падать почти вертикально). */
    public boolean airHold;
    /** Ожидаемая высота падения, блоков (считает редактор по миру; 0 — неизвестно, не проверяется). */
    public double  dropDepth;

    public RoutePoint() {}

    public RoutePoint(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }

    public RoutePoint copy() {
        RoutePoint p = new RoutePoint(x, y, z);
        p.action = action; p.sneak = sneak; p.sprint = sprint; p.jump = jump;
        p.slot = slot; p.pauseTicks = pauseTicks; p.hold = hold;
        p.ox = ox; p.oz = oz;
        p.drop = drop; p.dropDir = dropDir; p.landPauseTicks = landPauseTicks; p.airHold = airHold; p.dropDepth = dropDepth;
        return p;
    }

    /** Те же параметры отрезка (и смещение) в другом блоке: nx/nz — центр нового блока. */
    public RoutePoint withPos(double nx, double ny, double nz) {
        RoutePoint p = copy();
        p.moveCenter(nx, ny, nz);
        return p;
    }

    public double centerX() { return x - ox; }
    public double centerZ() { return z - oz; }

    /** Перенести в другой блок (cx/cz — центр), смещение сохраняется. */
    public void moveCenter(double cx, double ny, double cz) {
        x = cx + ox; y = ny; z = cz + oz;
    }

    public static final int FORWARD = 0, BACK = 1, LEFT = 2, RIGHT = 3;

    /** Единичный сдвиг по X/Z для направления относительно взгляда yaw («вперёд» — ближайшая ось X/Z). */
    public static double[] axisStep(float yaw, int dir) {
        double r = Math.toRadians(yaw);
        double fx = -Math.sin(r), fz = Math.cos(r);
        if (Math.abs(fx) >= Math.abs(fz)) { fx = Math.signum(fx); fz = 0; } else { fz = Math.signum(fz); fx = 0; }
        double rx = -fz, rz = fx;                  // вправо от взгляда
        return switch (dir) {
            case FORWARD -> new double[]{fx, fz};
            case BACK -> new double[]{-fx, -fz};
            case RIGHT -> new double[]{rx, rz};
            default -> new double[]{-rx, -rz};
        };
    }

    public static double clampOffset(double v) {
        return Double.isFinite(v) ? Math.max(-OFFSET_MAX, Math.min(OFFSET_MAX, Math.round(v * 1e6) / 1e6)) : 0;
    }

    /** Задать смещение внутри блока (обрезается до ±0.5). */
    public void setOffset(double nox, double noz) {
        double cx = centerX(), cz = centerZ();
        ox = clampOffset(nox); oz = clampOffset(noz);
        x = cx + ox; z = cz + oz;
    }

    public boolean attack() { return ATTACK.equals(action); }
    public boolean use()    { return USE.equals(action); }

    /**
     * Направление спуска (единичный вектор X/Z): ручное или «по ходу» — от prev к этой точке,
     * если prev нет или совпадает — от этой к next. {0, 0} — не определить.
     */
    public double[] dropVector(double prevX, double prevZ, boolean hasPrev, RoutePoint next) {
        switch (dropDir == null ? DIR_AUTO : dropDir) {
            case "+x": return new double[]{1, 0};
            case "-x": return new double[]{-1, 0};
            case "+z": return new double[]{0, 1};
            case "-z": return new double[]{0, -1};
            default: break;
        }
        double dx = hasPrev ? x - prevX : 0, dz = hasPrev ? z - prevZ : 0, n = Math.hypot(dx, dz);
        if (n < 1e-6 && next != null) { dx = next.x - x; dz = next.z - z; n = Math.hypot(dx, dz); }
        return n < 1e-6 ? new double[]{0, 0} : new double[]{dx / n, dz / n};
    }

    /** Приводит значения к допустимым (ручная правка файла). */
    public void sanitize() {
        if (!NONE.equals(action) && !ATTACK.equals(action) && !USE.equals(action)) action = NONE;
        slot = Math.max(0, Math.min(9, slot));
        pauseTicks = Math.max(0, Math.min(20 * 60, pauseTicks));
        landPauseTicks = Math.max(0, Math.min(20 * 60, landPauseTicks));
        if (dropDir == null || !DROP_DIRS.contains(dropDir)) dropDir = DIR_AUTO;
        if (!Double.isFinite(dropDepth) || dropDepth < 0) dropDepth = 0;
        dropDepth = Math.min(dropDepth, 384);
        if (!Double.isFinite(ox) || !Double.isFinite(oz) || Math.abs(ox) > OFFSET_MAX || Math.abs(oz) > OFFSET_MAX) {
            double cx = Double.isFinite(ox) ? x - ox : x, cz = Double.isFinite(oz) ? z - oz : z;
            ox = clampOffset(ox); oz = clampOffset(oz);
            x = cx + ox; z = cz + oz;
        }
    }

    public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
}
