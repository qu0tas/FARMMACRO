package com.farmmacro.route;

import java.util.List;
import java.util.Locale;

/**
 * Логика автохода без зависимостей от игры (проверяется симуляцией в routeStress).
 * На входе — позиция и yaw игрока, на выходе — какие клавиши нажать и не пора ли паниковать.
 * Камеру автоход НЕ поворачивает (с 1.6.0): yaw/pitch задаёт игрок или пресет камеры.
 * Направление к цели раскладывается по осям камеры и превращается в W/A/S/D (8 направлений):
 * при yaw вдоль ряда «змейки» это чистые W/S/A/D.
 * Детерминированно: одинаковый вход — одинаковый выход, никакой случайности.
 */
public final class WalkCore {

    /** Клавиша оси нажимается, если проекция желаемого направления на ось больше sin 22.5° (сектора по 45°). */
    public static final double AXIS_MIN = 0.383;
    /** Поправка к линии отрезка: блоков вбок → доля направления (0.2 бл вбок уже даёт A/D). */
    public static final double LINE_GAIN = 2.0;

    /** Ожидаемая скорость, блоков за тик (ходьба / спринт / присед) — для детектора «застрял». */
    public static final double WALK = 0.215, SPRINT = 0.28, SNEAK = 0.065;

    public static final class Out {
        public boolean forward, back, left, right, sprint, sneak, attack, use, jump;
        /** Слот 0–8 или −1 — не трогать. */
        public int slot = -1;
        /** Не null — паника с этой причиной. */
        public String panic;
    }

    private List<RoutePoint> pts;
    private int target, seg = -1, pauseLeft, jumpTicks;
    private boolean done, pausingNow;

    // окно застревания: позиции за последние n тиков, пока нажат «вперёд»
    private double[] hx = new double[0], hz = new double[0], he = new double[0];
    private int hn, head;
    private double expectSum;

    public void start(PointRoute route, int index, int stuckTicks) {
        pts = route.points;
        target = Math.max(0, Math.min(pts.size() - 1, index));
        seg = target - 1;
        pauseLeft = 0; jumpTicks = 0; done = false;
        resetStuck(stuckTicks);
    }

    public boolean done()   { return done; }
    public int target()     { return target; }
    public int segment()    { return seg; }
    public List<RoutePoint> points() { return pts; }

    /**
     * Один тик.
     * @param stuck/drift — включены ли детекторы (и паника вообще)
     */
    public Out tick(double x, double z, float curYaw, double reachRadius,
                    boolean stuck, int stuckTicks, boolean drift, double driftMax, Out out) {
        out.forward = out.back = out.left = out.right = false;
        out.sprint = out.sneak = out.attack = out.use = out.jump = false;
        out.slot = -1; out.panic = null;
        if (done || pts.isEmpty()) { done = true; return out; }

        RoutePoint t = pts.get(target);
        if (pauseLeft == 0 && reached(x, z, t, reachRadius)) {
            RoutePoint q = pts.get(target);
            seg = target;
            pauseLeft = q.pauseTicks;
            jumpTicks = q.jump ? 2 : 0;
            resetStuck(stuckTicks);
            target++;
            if (target >= pts.size()) { done = true; target = pts.size() - 1; return out; }
            t = pts.get(target);
        }
        RoutePoint s = seg >= 0 ? pts.get(seg) : null;

        boolean pausing = pauseLeft > 0;
        pausingNow = pausing;
        if (pausing) pauseLeft--;
        if (!pausing) steer(x, z, t, curYaw, out);
        boolean moving = out.forward || out.back || out.left || out.right;
        out.sneak = s != null && s.sneak;
        out.sprint = out.forward && !out.back && s != null && s.sprint && !out.sneak;
        out.attack = s != null && s.attack();
        out.use = s != null && s.use();
        out.jump = jumpTicks > 0 && !pausing;
        if (out.jump) jumpTicks--;
        if (s != null && s.slot > 0) out.slot = s.slot - 1;

        if (stuck) {
            if (moving && !out.jump) {
                double exp = out.sneak ? SNEAK : out.sprint ? SPRINT : WALK;
                if (push(x, z, exp, stuckTicks)) {
                    double moved = Math.hypot(x - hx[head], z - hz[head]);
                    if (expectSum >= 1.0 && moved < Math.min(0.3, expectSum * 0.15)) {
                        out.panic = String.format(Locale.ROOT,
                                "Застрял: к точке %d за %.1f с должен был пройти %.1f бл, прошёл %.2f",
                                target + 1, stuckTicks / 20.0, expectSum, moved);
                        return out;
                    }
                }
            } else {
                resetStuck(stuckTicks);
            }
        }
        if (drift && target > 0) {
            RoutePoint a = pts.get(target - 1);
            double d = RouteBuffer.distToSegmentXZ(x, z, a.x, a.z, t.x, t.z);
            if (d > driftMax) out.panic = String.format(Locale.ROOT,
                    "Сошёл с маршрута на %.1f бл (отрезок %d → %d)", d, target, target + 1);
        }
        return out;
    }

    /** В радиусе по XZ или уже проскочили точку по ходу отрезка (не дальше 0.6 бл вбок). */
    private boolean reached(double x, double z, RoutePoint t, double r) {
        double dx = x - t.x, dz = z - t.z;
        if (dx * dx + dz * dz <= r * r) return true;
        if (target == 0) return false;
        RoutePoint a = pts.get(target - 1);
        double vx = t.x - a.x, vz = t.z - a.z, len = Math.hypot(vx, vz);
        if (len < 1e-6) return true;
        vx /= len; vz /= len;
        return dx * vx + dz * vz > 0 && Math.abs(dx * vz - dz * vx) < 0.6;
    }

    /** Прогресс на отрезке к target: 0…1 (для раскраски «пройдено»). */
    public double segmentFraction(double x, double z) {
        if (target == 0 || pts.isEmpty()) return 0;
        RoutePoint a = pts.get(target - 1), b = pts.get(target);
        double vx = b.x - a.x, vz = b.z - a.z, vv = vx * vx + vz * vz;
        return vv < 1e-9 ? 1 : Math.max(0, Math.min(1, ((x - a.x) * vx + (z - a.z) * vz) / vv));
    }

    /**
     * Желаемое направление: на первом подходе — прямо к точке, на отрезке — вдоль отрезка с поправкой к его линии
     * (не дальше 45° от отрезка). Раскладка по осям камеры (yaw Minecraft: 0 — на +Z, 90 — на −X):
     * вперёд f = (−sin, cos), влево l = (cos, sin).
     */
    private void steer(double x, double z, RoutePoint t, float yaw, Out out) {
        double dx, dz;
        RoutePoint a = target > 0 ? pts.get(target - 1) : null;
        double ux = a == null ? 0 : t.x - a.x, uz = a == null ? 0 : t.z - a.z, len = Math.hypot(ux, uz);
        if (a == null || len < 1e-6) {
            dx = t.x - x; dz = t.z - z;
        } else {
            ux /= len; uz /= len;
            // смещение от линии вдоль нормали n = (uz, −ux); поправка — против нормали, не больше 45°
            double side = (x - a.x) * uz - (z - a.z) * ux;
            double k = Math.max(-1, Math.min(1, side * LINE_GAIN));
            dx = ux - k * uz; dz = uz + k * ux;
        }
        double n = Math.hypot(dx, dz);
        if (n < 1e-9) return;
        double r = Math.toRadians(yaw), sin = Math.sin(r), cos = Math.cos(r);
        double fwd = (-sin * dx + cos * dz) / n;
        double lft = (cos * dx + sin * dz) / n;
        out.forward = fwd > AXIS_MIN;
        out.back = fwd < -AXIS_MIN;
        out.left = lft > AXIS_MIN;
        out.right = lft < -AXIS_MIN;
    }

    /**
     * Пересчитать W/A/S/D после того, как в этом тике камеру поставили в пресет (точка смены камеры):
     * направление то же, оси уже новые. Остальное (действие, пауза, прыжок) не меняется.
     */
    public void resteer(double x, double z, float yaw, Out out) {
        if (done || pts.isEmpty() || pausingNow) return;
        boolean wasForward = out.forward;
        out.forward = out.back = out.left = out.right = false;
        steer(x, z, pts.get(target), yaw, out);
        if (!out.forward || out.back) out.sprint = false;
        else if (!wasForward) {
            RoutePoint s = seg >= 0 ? pts.get(seg) : null;
            out.sprint = s != null && s.sprint && !out.sneak;
        }
    }

    /** Куда сдвинется игрок за тик при этих клавишах и yaw: единичный вектор (x, z) или (0, 0). */
    public static double[] moveDir(Out o, float yaw) {
        double f = (o.forward ? 1 : 0) - (o.back ? 1 : 0), l = (o.left ? 1 : 0) - (o.right ? 1 : 0);
        double r = Math.toRadians(yaw), sin = Math.sin(r), cos = Math.cos(r);
        double x = -sin * f + cos * l, z = cos * f + sin * l, n = Math.hypot(x, z);
        return n < 1e-9 ? new double[]{0, 0} : new double[]{x / n, z / n};
    }

    /** Yaw Minecraft (0 — на +Z, 90 — на −X) от (x, z) к (tx, tz). */
    public static float yawTo(double x, double z, double tx, double tz) {
        return (float) Math.toDegrees(Math.atan2(tz - z, tx - x)) - 90f;
    }

    public static float wrap(float d) {
        d %= 360f;
        if (d >= 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    private void resetStuck(int n) {
        if (hx.length != n + 1) { hx = new double[n + 1]; hz = new double[n + 1]; he = new double[n + 1]; }
        hn = 0; head = 0; expectSum = 0;
    }

    /** @return true, когда окно заполнено (head — позиция n тиков назад) */
    private boolean push(double x, double z, double e, int n) {
        if (hx.length != n + 1) resetStuck(n);
        int cap = n + 1;
        if (hn < cap) {
            int i = (head + hn) % cap;
            hx[i] = x; hz[i] = z; he[i] = e;
            if (hn > 0) expectSum += e;
            return ++hn == cap;
        }
        int next = (head + 1) % cap;
        expectSum -= he[next];
        hx[head] = x; hz[head] = z; he[head] = e;
        head = next;
        expectSum += e;
        return true;
    }
}
