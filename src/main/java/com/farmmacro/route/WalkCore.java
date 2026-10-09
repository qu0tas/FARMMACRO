package com.farmmacro.route;

import java.util.List;
import java.util.Locale;

/**
 * Логика автохода без зависимостей от игры (проверяется симуляцией в routeStress).
 * На входе — позиция и yaw игрока, на выходе — что нажать, куда смотреть и не пора ли паниковать.
 * Детерминированно: одинаковый вход — одинаковый выход, никакой случайности.
 */
public final class WalkCore {

    /** Нажимать «вперёд», только если камера смотрит на точку точнее этого. */
    public static final float ALIGN_DEG = 35f;

    /** Ожидаемая скорость, блоков за тик (ходьба / спринт / присед) — для детектора «застрял». */
    public static final double WALK = 0.215, SPRINT = 0.28, SNEAK = 0.065;

    public static final class Out {
        public float yaw, pitch;
        public boolean forward, sprint, sneak, attack, use, jump;
        /** Слот 0–8 или −1 — не трогать. */
        public int slot = -1;
        /** Не null — паника с этой причиной. */
        public String panic;
    }

    private List<RoutePoint> pts;
    private float routePitch;
    private int target, seg = -1, pauseLeft, jumpTicks;
    private boolean done;

    // окно застревания: позиции за последние n тиков, пока нажат «вперёд»
    private double[] hx = new double[0], hz = new double[0], he = new double[0];
    private int hn, head;
    private double expectSum;

    public void start(PointRoute route, int index, int stuckTicks) {
        pts = route.points;
        routePitch = route.pitch;
        target = Math.max(0, Math.min(pts.size() - 1, index));
        seg = target - 1;
        pauseLeft = 0; jumpTicks = 0; done = false;
        resetStuck(stuckTicks);
    }

    public boolean done()   { return done; }
    public int target()     { return target; }
    public int segment()    { return seg; }
    public List<RoutePoint> points() { return pts; }

    private float pitchAt(int i) {
        RoutePoint p = i >= 0 && i < pts.size() ? pts.get(i) : null;
        return p != null && p.pitch != null ? p.pitch : routePitch;
    }

    /**
     * Один тик.
     * @param stuck/drift — включены ли детекторы (и паника вообще)
     */
    public Out tick(double x, double z, float curYaw, double reachRadius,
                    boolean stuck, int stuckTicks, boolean drift, double driftMax, Out out) {
        out.forward = out.sprint = out.sneak = out.attack = out.use = out.jump = false;
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

        out.yaw = yawTo(x, z, t.x, t.z);
        out.pitch = pitchAt(seg >= 0 ? seg : target);
        boolean pausing = pauseLeft > 0;
        if (pausing) pauseLeft--;
        float err = Math.abs(wrap(out.yaw - curYaw));
        out.forward = !pausing && err < ALIGN_DEG;
        out.sneak = s != null && s.sneak;
        out.sprint = out.forward && s != null && s.sprint && !out.sneak;
        out.attack = s != null && s.attack();
        out.use = s != null && s.use();
        out.jump = jumpTicks > 0 && !pausing;
        if (out.jump) jumpTicks--;
        if (s != null && s.slot > 0) out.slot = s.slot - 1;

        if (stuck) {
            if (out.forward && !out.jump) {
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
