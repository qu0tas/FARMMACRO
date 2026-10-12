package com.farmmacro.route;

import java.util.List;
import java.util.Locale;

/**
 * v1.15 «Возврат на маршрут»: идти по найденному пути ({@link PathFinder}) — без зависимостей от игры
 * (проверяется симуляцией в routeStress, PathCheck).
 * Ходит тем же {@link WalkCore} (W/A/S/D по текущему yaw), но, в отличие от маршрута, камера поворачивается по ходу
 * (как человек): {@link #wantYaw} — на точку впереди, pitch — сразу к взгляду маршрута. Разворот больше
 * {@link #TURN_STOP_DEG} — сначала повернуться на месте. В конце — повернуться ровно к камере маршрута и только потом
 * продолжить маршрут. Сбили с пути, упал, застрял — {@link Phase#REPLAN} (новый путь с текущего места).
 */
public final class ReturnCore {
    public enum Phase { WALK, TURN, DONE, REPLAN }

    /** Разворот больше — стоим и поворачиваемся. */
    public static final double TURN_STOP_DEG = 70;
    /** Готово: камера ближе этого к камере маршрута. */
    public static final double END_TOL_DEG = 0.6;
    /** Сбили с пути: дальше от линии пути. */
    public static final double OFF_PATH = 1.6;
    /** Окно «застрял» (тиков). */
    public static final int STUCK_TICKS = 30;
    /** Точка «смотреть на»: не ближе этого впереди по пути. */
    public static final double LOOK_AHEAD = 2.0;
    public static final double REACH = 0.35;

    private final WalkCore core = new WalkCore();
    private PointRoute route;
    private float endYaw, endPitch;
    private Phase phase = Phase.DONE;
    public String reason;
    /** Куда смотреть (yaw, pitch) — ReturnWalker поворачивает «как мышь». */
    public float wantYaw, wantPitch;
    /** В этот тик стоим ради поворота. */
    public boolean turningInPlace;
    private int lowTicks;
    private double startX, startZ;

    /** Путь pts от места (sx, sz); в конце повернуться к (endYaw, endPitch). */
    public void start(List<RoutePoint> pts, double sx, double sz, float endYaw, float endPitch) {
        startX = sx; startZ = sz;
        start(pts, endYaw, endPitch);
    }

    public void start(List<RoutePoint> pts, float endYaw, float endPitch) {
        route = new PointRoute();
        for (RoutePoint p : pts) route.points.add(p.copy());
        this.endYaw = endYaw; this.endPitch = endPitch;
        core.start(route, 0, STUCK_TICKS);
        phase = route.points.isEmpty() ? Phase.TURN : Phase.WALK;
        reason = null; lowTicks = 0; lastX = Double.NaN; needFly = false;
        wantYaw = endYaw; wantPitch = endPitch;
    }

    public WalkCore core() { return core; }
    public Phase phase() { return phase; }
    public List<RoutePoint> points() { return route == null ? List.of() : route.points; }
    public int target() { return core.target(); }
    public float endYaw() { return endYaw; }
    public float endPitch() { return endPitch; }

    /** Остаток пути по прямым, блоков. */
    public double remaining(double x, double z) {
        if (route == null || route.points.isEmpty() || phase != Phase.WALK) return 0;
        int t = core.target();
        RoutePoint p = route.points.get(t);
        double d = Math.hypot(p.x - x, p.z - z);
        for (int i = t; i + 1 < route.points.size(); i++)
            d += Math.hypot(route.points.get(i + 1).x - route.points.get(i).x, route.points.get(i + 1).z - route.points.get(i).z);
        return d;
    }

    /**
     * Один тик. horizontalCollision — упёрся в стену (для прыжка на ступеньку).
     * @return фаза после тика; клавиши — в out
     */
    public Phase tick(double x, double y, double z, boolean onGround, float yaw, float pitch, boolean horizontalCollision,
                      WalkCore.Out out) {
        return tick(x, y, z, onGround, false, yaw, pitch, horizontalCollision, out);
    }

    /** v1.15.1: путь просит взлететь (следующая точка — «летим», а игрок не летит) — ReturnWalker дважды жмёт прыжок. */
    public boolean needFly;
    /** Скорость по XZ прошлого тика (для торможения в полёте). */
    private double lastX = Double.NaN, lastZ, speed;
    /** Полёт: сход с линии допускается больше (инерция в воздухе). */
    public static final double OFF_PATH_FLY = 2.5;

    /**
     * Тик с полётом. flying — игрок сейчас летит (способность): прыжок — вверх, присед — вниз; точки «летим»
     * (RoutePoint.fly) — по высоте тоже; на вертикальном отрезке (та же клетка) — только вверх/вниз.
     */
    public Phase tick(double x, double y, double z, boolean onGround, boolean flying, float yaw, float pitch,
                      boolean horizontalCollision, WalkCore.Out out) {
        needFly = false;
        speed = Double.isNaN(lastX) ? 0 : Math.hypot(x - lastX, z - lastZ);
        lastX = x; lastZ = z;
        out.forward = out.back = out.left = out.right = out.jump = out.sprint = out.sneak = out.attack = out.use = false;
        out.slot = -1; out.panic = null;
        turningInPlace = false;
        if (phase == Phase.DONE || phase == Phase.REPLAN) return phase;
        if (phase == Phase.TURN) {
            wantYaw = endYaw; wantPitch = endPitch;
            if (angle(yaw, pitch, endYaw, endPitch) <= END_TOL_DEG) phase = Phase.DONE;
            return phase;
        }
        if (!core.done()) {
            RoutePoint ft = route.points.get(core.target());
            if (ft.fly && !flying) {                            // взлететь: стоим, ReturnWalker делает двойной прыжок
                if (speed > 0.02) { core.resetStuckWindow(); return phase; }   // сначала погасить скорость — в воздухе инерция
                needFly = true;
                core.resetStuckWindow();
                return phase;
            }
            if (flying) {
                double dyErr = ft.y - y, hd = Math.hypot(ft.x - x, ft.z - z);
                int ti = core.target();
                boolean vertical = ti > 0 ? Math.hypot(route.points.get(ti - 1).x - ft.x, route.points.get(ti - 1).z - ft.z) < 0.05 : hd < 0.45;
                // вертикальный отрезок — только вверх/вниз; горизонтальный, а мы ниже его высоты — сначала подняться
                if ((vertical && Math.abs(dyErr) > 0.35) || (!vertical && dyErr > 0.5)) {
                    out.jump = dyErr > 0; out.sneak = dyErr < 0;
                    if (hd > 0.25 && speed < 0.06) core.resteer(x, z, yaw, out);   // снесло — мягко к клетке
                    out.jump = dyErr > 0; out.sneak = dyErr < 0;
                    core.resetStuckWindow();
                    return phase;
                }
            }
        }
        core.tick(x, y, z, onGround, yaw, REACH, true, STUCK_TICKS, !flying, OFF_PATH, false, out);
        out.attack = false; out.use = false; out.slot = -1; out.sneak = false; out.sprint = false;
        if (out.panic != null) {
            String p = out.panic;
            out.panic = null;
            out.forward = out.back = out.left = out.right = out.jump = false;
            return replan(p.startsWith("Застрял") ? "застрял" : "сбили с пути");
        }
        if (core.done()) {
            out.forward = out.back = out.left = out.right = out.jump = false;
            if (flying && !onGround) { out.sneak = true; return phase; }   // сесть: полёт сам выключится на земле
            phase = Phase.TURN;
            wantYaw = endYaw; wantPitch = endPitch;
            if (angle(yaw, pitch, endYaw, endPitch) <= END_TOL_DEG) phase = Phase.DONE;
            return phase;
        }
        int t = core.target();
        RoutePoint tp = route.points.get(t);
        // сбили с линии пути (в т. ч. на первом отрезке — от места старта; WalkCore там идёт прямо к точке)
        double ax = t > 0 ? route.points.get(t - 1).x : startX, az = t > 0 ? route.points.get(t - 1).z : startZ;
        if (RouteBuffer.distToSegmentXZ(x, z, ax, az, tp.x, tp.z) > (flying ? OFF_PATH_FLY : OFF_PATH)) return replan("сбили с пути");
        // упал ниже пути (столкнули с края) — новый путь
        double expY = Math.min(tp.y, t > 0 ? route.points.get(t - 1).y : tp.y);
        if (onGround && !flying && y < expY - 1.2) { if (++lowTicks >= 3) return replan("упал с пути"); }
        else lowTicks = 0;
        if (flying) {
            // по высоте — к точке; перед точкой с поворотом — тормозить (в воздухе инерция больше)
            double dyErr = tp.y - y;
            out.jump = dyErr > 0.15; out.sneak = dyErr < -0.25;
            double hd = Math.hypot(tp.x - x, tp.z - z);
            // скорость к точке: не больше 0.1 + 0.12·расстояние (в воздухе инерция ×0.91 за тик); быстрее — тормозить обратными
            double want = 0.1 + 0.12 * hd;
            if (speed > want + 0.03) {
                boolean f = out.forward, b = out.back, l = out.left, r = out.right;
                out.forward = b; out.back = f; out.left = r; out.right = l;
            } else if (speed > want) out.forward = out.back = out.left = out.right = false;
        }
        // взгляд: точка впереди по пути, не ближе LOOK_AHEAD
        double[] look = lookPoint(x, z, t);
        if (Math.hypot(look[0] - x, look[1] - z) > 0.3) wantYaw = WalkCore.yawTo(x, z, look[0], look[1]);
        wantPitch = endPitch;
        double err = Math.abs(WalkCore.wrap(wantYaw - yaw));
        if (err > TURN_STOP_DEG && (onGround || flying)) {
            out.forward = out.back = out.left = out.right = false;
            if (!flying) out.jump = false;
            turningInPlace = true;
            core.resetStuckWindow();
            return phase;
        }
        // упёрся в ступеньку, а точка выше — прыжок (как в воде у бортика)
        boolean moving = out.forward || out.back || out.left || out.right;
        if (!flying && !out.jump && moving && horizontalCollision && onGround && tp.y > y + 0.4) out.jump = true;
        return phase;
    }

    private double[] lookPoint(double x, double z, int t) {
        List<RoutePoint> p = route.points;
        double need = LOOK_AHEAD;
        double ax = x, az = z;
        for (int i = t; i < p.size(); i++) {
            double bx = p.get(i).x, bz = p.get(i).z, d = Math.hypot(bx - ax, bz - az);
            if (d >= need || i == p.size() - 1) {
                if (d < 1e-6) return new double[]{bx, bz};
                double k = Math.min(1, need / d);
                return new double[]{ax + (bx - ax) * k, az + (bz - az) * k};
            }
            need -= d; ax = bx; az = bz;
        }
        RoutePoint l = p.get(p.size() - 1);
        return new double[]{l.x, l.z};
    }

    private Phase replan(String why) {
        reason = why;
        phase = Phase.REPLAN;
        return phase;
    }

    public static double angle(float yaw, float pitch, float y2, float p2) {
        return Math.hypot(WalkCore.wrap(y2 - yaw), p2 - pitch);
    }

    @Override public String toString() {
        return String.format(Locale.ROOT, "%s, точка %d/%d", phase, core.target() + 1, route == null ? 0 : route.points.size());
    }
}
