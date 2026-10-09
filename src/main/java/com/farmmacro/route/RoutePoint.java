package com.farmmacro.route;

/**
 * Точка маршрута. Все параметры (кроме координат и паузы) действуют на отрезке ОТ этой точки до следующей.
 * Хранится в config/farmmacro_routes/*.json как есть (Gson).
 */
public class RoutePoint {
    public static final String NONE = "none", ATTACK = "attack", USE = "use";

    /** Центр верхней грани блока. */
    public double x, y, z;
    /** none | attack (держать ЛКМ) | use (держать ПКМ) — на отрезке до следующей точки. */
    public String  action = NONE;
    public boolean sneak, sprint;
    /** Один прыжок в начале отрезка. */
    public boolean jump;
    /** Слот хотбара 1–9, 0 — не менять. */
    public int     slot;
    /** Пауза в точке перед следующим отрезком, тиков. */
    public int     pauseTicks;
    /** Свой pitch на отрезке; null — pitch маршрута. */
    public Float   pitch;

    public RoutePoint() {}

    public RoutePoint(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }

    public RoutePoint copy() {
        RoutePoint p = new RoutePoint(x, y, z);
        p.action = action; p.sneak = sneak; p.sprint = sprint; p.jump = jump;
        p.slot = slot; p.pauseTicks = pauseTicks; p.pitch = pitch;
        return p;
    }

    /** Те же параметры отрезка, другие координаты (для вставки и «змейки»). */
    public RoutePoint withPos(double nx, double ny, double nz) {
        RoutePoint p = copy();
        p.x = nx; p.y = ny; p.z = nz;
        return p;
    }

    public boolean attack() { return ATTACK.equals(action); }
    public boolean use()    { return USE.equals(action); }

    /** Приводит значения к допустимым (ручная правка файла). */
    public void sanitize() {
        if (!NONE.equals(action) && !ATTACK.equals(action) && !USE.equals(action)) action = NONE;
        slot = Math.max(0, Math.min(9, slot));
        pauseTicks = Math.max(0, Math.min(20 * 60, pauseTicks));
        if (pitch != null && (!Float.isFinite(pitch) || pitch < -90 || pitch > 90)) pitch = null;
    }

    public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
}
