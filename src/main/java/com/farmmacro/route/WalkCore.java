package com.farmmacro.route;

import java.util.List;
import java.util.Locale;

/**
 * Логика автохода без зависимостей от игры (проверяется симуляцией в routeStress).
 * На входе — позиция и yaw игрока, на выходе — какие клавиши нажать и не пора ли паниковать.
 * Камеру автоход НЕ поворачивает (с 1.6.0): yaw/pitch задаёт игрок или пресет камеры.
 * Направление к цели раскладывается по осям камеры и превращается в W/A/S/D (8 направлений):
 * при yaw вдоль ряда (пресет культуры) это чистые W/S/A/D.
 * Детерминированно: одинаковый вход — одинаковый выход, никакой случайности.
 * Спуск (точка с {@code drop}): дойти до точки → шагать по направлению спуска до обрыва → полёт → приземление →
 * пауза {@code landPauseTicks} → следующий отрезок начинается с места приземления. Этажи сверяются по Y
 * (флаг floor): упал не туда, не упал, точка на другом этаже без спуска — паника.
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

    // ── спуск ──
    public static final int D_NONE = 0, D_EDGE = 1, D_AIR = 2, D_LAND = 3;
    /** Сколько тиков можно идти к краю (не упав) и сколько лететь. */
    public static final int DROP_EDGE_MAX = 40, DROP_AIR_MAX = 200;
    /** «Не тот этаж»: допуск по высоте падения; «нет пути»: Δy до точки и сколько тиков подряд. */
    public static final double FLOOR_TOL = 0.6, OTHER_FLOOR_DY = 1.6;
    public static final int OTHER_FLOOR_TICKS = 3;
    private int dropPhase, dropTicks, landLeft, floorTicks;
    private double dropDx, dropDz, dropFromY;
    /** Начало текущего отрезка — место приземления после спуска (вместо предыдущей точки). */
    private boolean hasStart;
    private double startX, startZ;
    /** Радиус достижения по точкам («Случайность»); null — общий из настроек. */
    private double[] reach;
    public void setReach(double[] r) { reach = r; }

    /** Внешняя остановка («Случайность»: встать посреди пути, заминка): тиков без клавиш, «Застрял» не считается. */
    private int extPause;
    private boolean extNow;
    public void pauseExternal(int ticks) { if (ticks > 0 && !done) extPause = Math.max(extPause, ticks); }
    /** Сколько ещё стоять по внешней остановке. */
    public int externalPause() { return extPause; }
    /** В этот тик стояли по внешней остановке. */
    public boolean stoppedNow() { return extNow; }
    /** Окно «Застрял» — заново (игрок стоял не по вине препятствия: «Случайность» отпустила клавиши). */
    public void resetStuckWindow() { resetStuck(hx.length - 1); }

    /** Стоим на паузе точки / после приземления (в этот тик). */
    public boolean pausing() { return pausingNow; }

    /** Последнее событие спуска (для симуляции/логов): высота падения последнего приземления. */
    private double lastFall = Double.NaN;

    // окно застревания: позиции за последние n тиков, пока нажат «вперёд»
    private double[] hx = new double[0], hz = new double[0], he = new double[0];
    private int hn, head;
    private double expectSum;

    public void start(PointRoute route, int index, int stuckTicks) {
        pts = route.points;
        target = Math.max(0, Math.min(pts.size() - 1, index));
        seg = target - 1;
        pauseLeft = 0; jumpTicks = 0; done = false;
        dropPhase = D_NONE; dropTicks = 0; landLeft = 0; floorTicks = 0; hasStart = false; lastFall = Double.NaN;
        reach = null;
        extPause = 0; extNow = false;
        resetStuck(stuckTicks);
    }

    public int dropPhase()   { return dropPhase; }
    public double lastFall() { return lastFall; }

    public boolean done()   { return done; }
    public int target()     { return target; }
    public int segment()    { return seg; }
    public List<RoutePoint> points() { return pts; }

    /** Старая сигнатура без высоты (Y неизвестен: этажи не проверяются, спуск — по отрыву от земли не отслеживается). */
    public Out tick(double x, double z, float curYaw, double reachRadius,
                    boolean stuck, int stuckTicks, boolean drift, double driftMax, Out out) {
        return tick(x, Double.NaN, z, true, curYaw, reachRadius, stuck, stuckTicks, drift, driftMax, false, out);
    }

    /**
     * Один тик.
     * @param y        высота ног игрока (NaN — неизвестна: этажи и спуск по высоте не проверяются)
     * @param onGround стоит ли игрок на земле
     * @param stuck/drift/floor — включены ли детекторы (и паника вообще); floor — «Не тот этаж» / «Нет пути»
     */
    public Out tick(double x, double y, double z, boolean onGround, float curYaw, double reachRadius,
                    boolean stuck, int stuckTicks, boolean drift, double driftMax, boolean floor, Out out) {
        out.forward = out.back = out.left = out.right = false;
        out.sprint = out.sneak = out.attack = out.use = out.jump = false;
        out.slot = -1; out.panic = null;
        if (done || pts.isEmpty()) { done = true; return out; }
        boolean knowY = !Double.isNaN(y);

        RoutePoint t = pts.get(target);
        double rr = reach != null && target < reach.length ? reach[target] : reachRadius;
        if (pauseLeft == 0 && dropPhase == D_NONE && reached(x, y, z, t, rr, floor && knowY)) {
            RoutePoint q = pts.get(target);
            seg = target;
            pauseLeft = q.pauseTicks;
            jumpTicks = q.jump ? 2 : 0;
            resetStuck(stuckTicks);
            floorTicks = 0;
            if (q.drop && target + 1 < pts.size()) {
                double px = hasStart ? startX : target > 0 ? pts.get(target - 1).x : x;
                double pz = hasStart ? startZ : target > 0 ? pts.get(target - 1).z : z;
                double[] d = q.dropVector(px, pz, hasStart || target > 0, pts.get(target + 1));
                dropDx = d[0]; dropDz = d[1];
                dropPhase = D_EDGE; dropTicks = 0; landLeft = 0;
                dropFromY = knowY ? y : q.y;
            }
            hasStart = false;
            target++;
            if (target >= pts.size()) { done = true; target = pts.size() - 1; dropPhase = D_NONE; return out; }
            t = pts.get(target);
        }
        RoutePoint s = seg >= 0 ? pts.get(seg) : null;

        boolean ext = extPause > 0 && dropPhase == D_NONE;
        boolean pausing;
        if (ext) { extPause--; pausing = true; }
        else { pausing = pauseLeft > 0; if (pausing) pauseLeft--; }
        extNow = ext;
        boolean dropping = !pausing && dropPhase != D_NONE;
        pausingNow = pausing || (dropping && dropPhase == D_LAND);
        if (dropping) {
            String p = tickDrop(x, y, z, onGround, curYaw, s, floor && knowY, out);
            if (p != null) { out.panic = p; return out; }
        } else if (!pausing) {
            steer(x, z, t, curYaw, out);
        }
        boolean moving = out.forward || out.back || out.left || out.right;
        boolean inDrop = dropPhase == D_EDGE || dropPhase == D_AIR;
        out.sneak = s != null && s.sneak && !inDrop;           // присед не даёт упасть с края
        out.sprint = out.forward && !out.back && s != null && s.sprint && !out.sneak && !inDrop;
        out.attack = s != null && s.attack();
        out.use = s != null && s.use();
        out.jump = jumpTicks > 0 && !pausing && dropPhase == D_NONE;
        if (out.jump) jumpTicks--;
        if (s != null && s.slot > 0) out.slot = s.slot - 1;

        // точка на другом этаже, а спуска нет: стоим над ней и не можем попасть
        // (дошли по XZ или проскочили, а по высоте не тот этаж; в полёте — ждём приземления)
        if (floor && knowY && dropPhase == D_NONE && !pausing) {
            if (otherFloor && onGround) {
                if (++floorTicks >= OTHER_FLOOR_TICKS) {
                    out.panic = String.format(Locale.ROOT, "Нет пути: точка %d на другом этаже (Δy %.1f бл)%s",
                            target + 1, t.y - y, target > 0 ? " — отметь «Спуск» у точки " + target : "");
                    return out;
                }
            } else floorTicks = 0;
        }

        if (stuck) {
            if (moving && !out.jump && dropPhase == D_NONE && onGround) {
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
            } else if (dropPhase != D_NONE || !moving) {
                resetStuck(stuckTicks);
            }
        }
        if (drift && target > 0 && dropPhase == D_NONE) {
            double ax = segStartX(), az = segStartZ();
            double d = RouteBuffer.distToSegmentXZ(x, z, ax, az, t.x, t.z);
            if (d > driftMax) out.panic = String.format(Locale.ROOT,
                    "Сошёл с маршрута на %.1f бл (отрезок %d → %d)", d, target, target + 1);
        }
        return out;
    }

    /** Фазы спуска. @return причина паники или null */
    private String tickDrop(double x, double y, double z, boolean onGround, float yaw, RoutePoint q, boolean floor, Out out) {
        int n = seg + 1;                                       // номер точки-спуска для сообщений
        boolean knowY = !Double.isNaN(y);
        switch (dropPhase) {
            case D_EDGE -> {
                keysFor(dropDx, dropDz, yaw, out);
                dropTicks++;
                boolean falling = knowY ? !onGround && y < dropFromY - 0.2 : !onGround;
                if (falling) { dropPhase = D_AIR; dropTicks = 0; }
                else if (dropTicks > DROP_EDGE_MAX) {
                    if (floor) return String.format(Locale.ROOT, "Спуск у точки %d: не упал за %.1f с (нет обрыва по направлению?)",
                            n, DROP_EDGE_MAX / 20.0);
                    land(x, z, q);                             // без проверки этажей — просто идём дальше
                }
            }
            case D_AIR -> {
                dropTicks++;
                if (!onGround && q != null && q.airHold) keysFor(dropDx, dropDz, yaw, out);
                if (onGround) {
                    double fell = knowY ? dropFromY - y : Double.NaN;
                    lastFall = fell;
                    if (floor && !Double.isNaN(fell)) {
                        if (fell < FLOOR_GAP_MIN)
                            return String.format(Locale.ROOT, "Не тот этаж: после спуска у точки %d упал всего на %.1f бл", n, fell);
                        if (q != null && q.dropDepth > 0 && Math.abs(fell - q.dropDepth) > FLOOR_TOL)
                            return String.format(Locale.ROOT, "Не тот этаж: упал на %.1f бл, ждали %.1f (точка %d)", fell, q.dropDepth, n);
                    }
                    land(x, z, q);
                } else if (dropTicks > DROP_AIR_MAX && floor) {
                    return String.format(Locale.ROOT, "Падение после точки %d дольше %.0f с", n, DROP_AIR_MAX / 20.0);
                }
            }
            case D_LAND -> {
                if (landLeft > 0) landLeft--;
                if (landLeft <= 0) dropPhase = D_NONE;
            }
            default -> dropPhase = D_NONE;
        }
        return null;
    }

    /** Ниже этого падение после спуска не считается (наступил на ступеньку, а не упал). */
    public static final double FLOOR_GAP_MIN = 0.6;

    private void land(double x, double z, RoutePoint q) {
        hasStart = true; startX = x; startZ = z;
        landLeft = q != null ? q.landPauseTicks : 0;
        dropPhase = landLeft > 0 ? D_LAND : D_NONE;
        dropTicks = 0;
    }

    private double segStartX() { return hasStart ? startX : pts.get(target - 1).x; }
    private double segStartZ() { return hasStart ? startZ : pts.get(target - 1).z; }

    /** Последний reached(): по XZ дошли, но точка на другом этаже. */
    private boolean otherFloor;

    /** В радиусе по XZ или уже проскочили точку по ходу отрезка (не дальше 0.6 бл вбок); с floor — и на том же этаже. */
    private boolean reached(double x, double y, double z, RoutePoint t, double r, boolean floor) {
        otherFloor = false;
        boolean xz = reachedXZ(x, z, t, r);
        if (xz && floor && Math.abs(y - t.y) > OTHER_FLOOR_DY) { otherFloor = true; return false; }
        return xz;
    }

    private boolean reachedXZ(double x, double z, RoutePoint t, double r) {
        double dx = x - t.x, dz = z - t.z;
        if (dx * dx + dz * dz <= r * r) return true;
        if (target == 0) return false;
        double ax = segStartX(), az = segStartZ();
        double vx = t.x - ax, vz = t.z - az, len = Math.hypot(vx, vz);
        if (len < 1e-6) return true;
        vx /= len; vz /= len;
        return dx * vx + dz * vz > 0 && Math.abs(dx * vz - dz * vx) < 0.6;
    }

    /** Прогресс на отрезке к target: 0…1 (для раскраски «пройдено»). */
    public double segmentFraction(double x, double z) {
        if (target == 0 || pts.isEmpty()) return 0;
        if (dropPhase != D_NONE) return 0;
        RoutePoint b = pts.get(target);
        double ax = segStartX(), az = segStartZ();
        double vx = b.x - ax, vz = b.z - az, vv = vx * vx + vz * vz;
        return vv < 1e-9 ? 1 : Math.max(0, Math.min(1, ((x - ax) * vx + (z - az) * vz) / vv));
    }

    /**
     * Желаемое направление: на первом подходе — прямо к точке, на отрезке — вдоль отрезка с поправкой к его линии
     * (не дальше 45° от отрезка). Раскладка по осям камеры (yaw Minecraft: 0 — на +Z, 90 — на −X):
     * вперёд f = (−sin, cos), влево l = (cos, sin).
     */
    private void steer(double x, double z, RoutePoint t, float yaw, Out out) {
        double dx, dz;
        boolean hasA = target > 0;
        double ax = hasA ? segStartX() : 0, az = hasA ? segStartZ() : 0;
        double ux = hasA ? t.x - ax : 0, uz = hasA ? t.z - az : 0, len = Math.hypot(ux, uz);
        if (!hasA || len < 1e-6) {
            dx = t.x - x; dz = t.z - z;
        } else {
            ux /= len; uz /= len;
            // смещение от линии вдоль нормали n = (uz, −ux); поправка — против нормали, не больше 45°
            double side = (x - ax) * uz - (z - az) * ux;
            double k = Math.max(-1, Math.min(1, side * LINE_GAIN));
            dx = ux - k * uz; dz = uz + k * ux;
        }
        keysFor(dx, dz, yaw, out);
    }

    /** Направление (dx, dz) в мире → W/A/S/D по осям камеры (8 направлений). */
    private static void keysFor(double dx, double dz, float yaw, Out out) {
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
        boolean hadKeys = out.forward || out.back || out.left || out.right;
        out.forward = out.back = out.left = out.right = false;
        if (dropPhase == D_EDGE || (dropPhase == D_AIR && hadKeys)) { keysFor(dropDx, dropDz, yaw, out); out.sprint = false; return; }
        if (dropPhase != D_NONE) return;
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
