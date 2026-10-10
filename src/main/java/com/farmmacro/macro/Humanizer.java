package com.farmmacro.macro;

import com.farmmacro.route.PointRoute;
import com.farmmacro.route.RoutePoint;
import com.farmmacro.route.WalkCore;

import java.util.SplittableRandom;

/**
 * «Случайность» одного прохода: из {@link HumanSettings} и сида делает проход немного другим — без зависимостей от игры
 * (проверяется в routeStress, HumanizerCheck). Один генератор SplittableRandom на проход: одинаковый сид → одинаковый
 * проход, разные сиды → разные. Выключено ({@code enabled = false}) — ничего не меняет (как в 1.8.0).
 *  • маршрут: разброс пауз и радиуса достижения, задержка после приземления (на копии маршрута) и {@link RouteShaper}
 *    — смазанные смены клавиш на углах (перекрытие/зазор, не дальше cornerSlop от угла), заминки, прыжок и спринт
 *    позже, отпускание клавиши у края спуска, пауза на старте;
 *  • запись: {@link RecShaper} — повтор/пропуск стоячих кадров, ЛКМ/ПКМ позже на 0…N тиков, плавный шум камеры;
 *  • камера: разброс привязки пресетов ({@link #cameraJitter}); зажим мыши — задержка старта + 0…N;
 *  • круги (v1.10): {@link #beginLap} — шанс «идеального» круга (точно как есть) и серии таких кругов подряд;
 *    остановки посреди маршрута и редкое «протупить» (WalkCore.pauseExternal / повтор стоячего кадра записи);
 *    {@link Look} — лёгкий шум pitch на ходу и «оглядеться» на остановках (возвращается до движения).
 */
public final class Humanizer {
    /** Humanizer текущего прохода (null — выкл или ничего не играет). Ставит MacroManager. */
    public static volatile Humanizer ACTIVE;

    public final HumanSettings s;
    public final long seed;
    private final SplittableRandom rnd;

    public Humanizer(HumanSettings settings, long seed) {
        this.s = HumanSettings.sanitize(settings == null ? new HumanSettings() : settings.copy());
        this.seed = seed;
        this.rnd = new SplittableRandom(seed);
    }

    /** null — случайность выключена. seedNow — сид, если в настройках 0 (обычно время старта). */
    public static Humanizer create(HumanSettings h, long seedNow) {
        if (h == null || !h.enabled) return null;
        return new Humanizer(h, h.seed != 0 ? h.seed : seedNow);
    }

    // ── круги ────────────────────────────────────────────────────────────────
    private volatile boolean lapPerfect;
    private int perfectLeft;
    /** Для HUD/проверок: кругов всего, из них идеальных; остановок посреди маршрута, «протупил». */
    public volatile int laps, perfectLaps, midStops, afks;
    /** Сколько ещё стоять (ставят источники, для HUD). */
    public volatile int stopLeftTicks;
    /** «Оглядеться»: один на весь запуск (смещения камеры копятся между кругами и возвращаются в 0). */
    public final Look look = new Look();

    /**
     * Начало круга (MacroManager перед каждым проходом). Идеальный круг — без случайности, остановок и шума камеры:
     * шанс perfectLapChance, и с шансом perfectStreakChance — серия streakMin…streakMax идеальных подряд.
     * @return true — этот круг идеальный
     */
    public boolean beginLap() {
        laps++;
        boolean p;
        if (perfectLeft > 0) { perfectLeft--; p = true; }
        else if (chance(s.perfectLapChance)) {
            p = true;
            if (chance(s.perfectStreakChance)) perfectLeft = range(s.streakMin, s.streakMax) - 1;
        } else p = false;
        if (p) perfectLaps++;
        lapPerfect = p;
        stopLeftTicks = 0;
        return p;
    }

    public boolean lapPerfect() { return lapPerfect; }
    /** Сколько ещё идеальных кругов в серии после текущего. */
    public int perfectLeft() { return perfectLeft; }

    /** Humanizer для «формы» текущего круга: null — случайность выкл или круг идеальный. */
    public static Humanizer lap() {
        Humanizer h = ACTIVE;
        return h != null && !h.lapPerfect ? h : null;
    }

    /** Строка для HUD. */
    public String status() {
        int st = stopLeftTicks;
        if (st > 0) return String.format(java.util.Locale.ROOT, "стоит %.1f с", st / 20.0);
        if (lapPerfect) return "идеальный круг" + (perfectLeft > 0 ? " (+" + perfectLeft + ")" : "");
        return "круг " + laps;
    }

    // ── генератор ──
    /** Равномерно lo…hi включительно. */
    public int range(int lo, int hi) { return hi <= lo ? lo : lo + rnd.nextInt(hi - lo + 1); }
    public double uni(double lo, double hi) { return hi <= lo ? lo : lo + rnd.nextDouble() * (hi - lo); }
    public boolean chance(int pct) { return pct > 0 && (pct >= 100 || rnd.nextInt(100) < pct); }

    // ── маршрут: на копии для одного прохода ──

    /** Паузы ± разброс, после спусков — задержка приземления. Меняет точки копии маршрута. */
    public void shapeRoute(PointRoute r) {
        for (RoutePoint p : r.points) {
            if (p.pauseTicks > 0) {
                int d = (int) Math.round(p.pauseTicks * s.pauseJitterPct / 100.0) + s.pauseJitterTicks;
                p.pauseTicks = Math.max(0, p.pauseTicks + range(-d, d));
            }
            if (p.drop) p.landPauseTicks = Math.max(0, p.landPauseTicks + range(s.landDelayMin, s.landDelayMax));
        }
    }

    /**
     * Радиус достижения каждой точки: base … base + reachJitter (не больше 1.4 × cornerSlop) — поворот раньше,
     * срезая угол внутрь (уход от линии ≈ радиус / √2). Меньше base не бывает: позже поворачивать — значит
     * проскочить угол с инерцией; «позже» делает перекрытие клавиш, у которого есть свой предел cornerSlop.
     */
    public double[] reachRadii(int n, double base) {
        double[] out = new double[n];
        double hi = Math.max(base, Math.min(base + s.reachJitter, s.cornerSlop * 1.4));
        for (int i = 0; i < n; i++) out[i] = uni(base, hi);
        return out;
    }

    /** Зажим мыши: задержка старта + 0…holdDelayJitter. */
    public HoldSettings shapeHold(HoldSettings h) {
        if (h == null) return null;
        HoldSettings c = h.copy();
        c.delayTicks = Math.min(HoldSettings.DELAY_MAX, c.delayTicks + range(0, s.holdDelayJitter));
        return c;
    }

    /** Разброс установки пресета (привязка камеры): {dyaw, dpitch}. */
    public float[] cameraJitter() {
        double d = lapPerfect ? 0 : s.bindJitterDeg;
        return d <= 0 ? new float[]{0, 0} : new float[]{(float) uni(-d, d), (float) uni(-d, d)};
    }

    // ── маршрут: клавиши ─────────────────────────────────────────────────────

    /**
     * Смазывает клавиши автохода (поверх {@link WalkCore.Out}): WalkCore решает «что», shaper — «когда».
     * Перекрытие старой клавиши не уводит дальше cornerSlop от места, где сменилось направление.
     */
    public final class RouteShaper {
        private int prevMask = -1, prevTarget = -1, prevDrop = WalkCore.D_NONE;
        private int overlapMask, overlapLeft, gapLeft;
        private double cornerX, cornerZ;
        private boolean prevSprintWant, prevJumpWant;
        private int sprintDelay, jumpDelay, jumpHold;
        private boolean overlapAnyDist;
        /** Позиция прошлого тика — скорость для оценки инерции (сколько ещё пронесёт после отпускания). */
        private double lastX = Double.NaN, lastZ = Double.NaN;
        /** Сколько пронесёт по инерции после отпускания: ~1.2 × скорость за тик (ходьба: ~0.26 бл). */
        public static final double CARRY = 1.2;
        /** Для проверок: сколько раз было перекрытие / зазор / заминка / остановка / «протупил». */
        public int overlaps, gaps, hesitations, stops, afkStops;
        /**
         * В этот тик shaper отпустил все клавиши движения, хотя WalkCore хотел идти (зазор, пауза на старте).
         * Тогда окно «Застрял» надо сбросить (WalkCore.resetStuckWindow): он считает по своему решению, а игрок стоял.
         */
        public boolean idleNow;
        /** Заминка, ждущая следующего тика (её ставит {@link #stopNow} через WalkCore.pauseExternal). */
        private int pendingStop;
        /** Остановки этого круга: к какой точке идём, через сколько тиков на отрезке, сколько стоять (−1 — нет). */
        private int midTarget = -1, midDelay, midLen, afkTarget = -1, afkDelay, afkLen;
        private int ticksOnTarget, lastStopTarget = -1;

        /** Начало прохода: пауза 0…startDelayMax; n — точек в маршруте (для остановок посреди пути). */
        public void start(int n) {
            prevMask = -1; prevTarget = -1; prevDrop = WalkCore.D_NONE;
            overlapLeft = 0; gapLeft = range(0, s.startDelayMax);
            prevSprintWant = prevJumpWant = false; sprintDelay = jumpDelay = jumpHold = 0;
            pendingStop = 0; ticksOnTarget = 0; lastStopTarget = -1;
            midTarget = afkTarget = -1;
            if (n >= 2 && chance(s.midStopChance)) {
                midTarget = range(1, n - 1); midDelay = range(4, 30); midLen = range(s.midStopMin, s.midStopMax);
            }
            if (n >= 2 && chance(s.afkChance)) {
                afkTarget = range(1, n - 1); afkDelay = range(4, 30); afkLen = range(s.afkMin, s.afkMax);
            }
        }

        public void start() { start(0); }

        /** Есть ли в этом круге запланированная остановка / «протупить» (для проверок). */
        public boolean plannedStop() { return midTarget >= 0; }
        public boolean plannedAfk()  { return afkTarget >= 0; }

        /**
         * Каждый тик ДО WalkCore.tick: пора ли встать. Встаём только на земле, не в спуске и не на паузе точки.
         * @return сколько тиков стоять (0 — идём) — передать в WalkCore.pauseExternal
         */
        public int stopNow(int target, int dropPhase, boolean onGround, boolean pausing) {
            if (target != lastStopTarget) { lastStopTarget = target; ticksOnTarget = 0; } else ticksOnTarget++;
            if (dropPhase != WalkCore.D_NONE || !onGround || pausing || target <= 0) return 0;
            if (pendingStop > 0) { int n = pendingStop; pendingStop = 0; return n; }
            if (afkTarget >= 0 && (target > afkTarget || target == afkTarget && ticksOnTarget >= afkDelay)) {
                afkTarget = -1; afkStops++; afks++;
                return afkLen;
            }
            if (midTarget >= 0 && (target > midTarget || target == midTarget && ticksOnTarget >= midDelay)) {
                midTarget = -1; stops++; midStops++;
                return midLen;
            }
            return 0;
        }

        private int mask(WalkCore.Out o) {
            return (o.forward ? 1 : 0) | (o.back ? 2 : 0) | (o.left ? 4 : 0) | (o.right ? 8 : 0);
        }

        private void setMask(WalkCore.Out o, int m) {
            o.forward = (m & 1) != 0; o.back = (m & 2) != 0; o.left = (m & 4) != 0; o.right = (m & 8) != 0;
            // вперёд и назад (влево и вправо) одновременно не жмём — побеждает новая клавиша
        }

        /**
         * @param out    решение WalkCore этого тика (меняется на месте)
         * @param airHold у точки-спуска «в полёте держать»
         */
        public void tick(WalkCore.Out out, double x, double z, int target, int dropPhase, boolean airHold) {
            int want = mask(out);
            double v = Double.isNaN(lastX) ? 0 : Math.hypot(x - lastX, z - lastZ);
            lastX = x; lastZ = z;
            boolean dropping = dropPhase == WalkCore.D_EDGE || dropPhase == WalkCore.D_AIR;

            // новая цель → иногда заминка
            // (заминка — через WalkCore.pauseExternal со следующего тика: долгая не даёт «Застрял»)
            if (prevTarget >= 0 && target != prevTarget && !dropping && chance(s.hesitateChance)) {
                pendingStop = Math.max(pendingStop, range(1, s.hesitateMax));
                hesitations++;
            }
            // смена направления на земле → перекрытие или зазор
            if (prevMask > 0 && want > 0 && want != prevMask && !dropping && prevDrop == WalkCore.D_NONE) {
                int old = prevMask & ~want;
                if (old != 0 && chance(s.overlapChance)) {
                    overlapMask = old; overlapLeft = range(1, s.overlapMax); overlapAnyDist = false;
                    cornerX = x; cornerZ = z;
                    overlaps++;
                } else if (chance(s.gapChance)) {
                    gapLeft = Math.max(gapLeft, range(1, s.gapMax));
                    gaps++;
                }
            }
            // оторвался от края спуска → клавиша ещё 0…edgeReleaseMax тиков (если в полёте не держим)
            if (prevDrop == WalkCore.D_EDGE && dropPhase == WalkCore.D_AIR && !airHold && prevMask > 0) {
                overlapMask = prevMask; overlapLeft = range(0, s.edgeReleaseMax); overlapAnyDist = true;
            }
            if (dropPhase == WalkCore.D_LAND) overlapLeft = 0;

            int outMask = want;
            idleNow = false;
            if (gapLeft > 0 && !dropping) {
                gapLeft--;
                outMask = 0;
                out.sprint = false;
                idleNow = want != 0;
            } else if (overlapLeft > 0) {
                // держим, только если с инерцией не уйдём от угла дальше cornerSlop
                if (overlapAnyDist || Math.hypot(x - cornerX, z - cornerZ) + v * CARRY <= s.cornerSlop) {
                    int add = overlapMask;
                    if ((outMask & 3) != 0) add &= ~3;            // уже есть вперёд/назад — не жмём противоположную
                    if ((outMask & 12) != 0) add &= ~12;
                    outMask |= add;
                    overlapLeft--;
                } else overlapLeft = 0;
            }
            setMask(out, outMask);

            // спринт — позже
            boolean sprintWant = out.sprint;
            if (sprintWant && !prevSprintWant) sprintDelay = range(0, s.sprintDelayMax);
            prevSprintWant = sprintWant;
            if (sprintDelay > 0) { sprintDelay--; out.sprint = false; }
            if (!out.forward) out.sprint = false;

            // прыжок — позже (WalkCore даёт 2 тика прыжка)
            boolean jumpWant = out.jump;
            if (jumpWant && !prevJumpWant) { jumpDelay = range(0, s.jumpDelayMax); jumpHold = 2; }
            prevJumpWant = jumpWant;
            if (jumpDelay > 0) { jumpDelay--; out.jump = false; }
            else if (jumpHold > 0) { jumpHold--; out.jump = true; }
            else out.jump = false;

            prevMask = want;
            prevTarget = target;
            prevDrop = dropPhase;
        }
    }

    public RouteShaper routeShaper() { return routeShaper(0); }
    /** n — точек в маршруте (0 — без остановок посреди пути). */
    public RouteShaper routeShaper(int n) { RouteShaper r = new RouteShaper(); r.start(n); return r; }

    // ── запись ───────────────────────────────────────────────────────────────

    /** Запись: повтор/пропуск стоячих кадров, ЛКМ/ПКМ позже, плавный шум камеры. */
    public final class RecShaper {
        private boolean outAttack, outUse, wantAttack, wantUse;
        private int attackDelay, useDelay;
        private double noiseYaw, noisePitch, goalYaw, goalPitch;
        private int noiseLeft;
        private int repeatsInRow;
        public int repeats, skips, stops, afkStops;
        /** Остановки этого круга: с какого кадра искать стоячий кадр (−1 — нет), сколько стоять. */
        private int midFrom = -1, midLen, afkFrom = -1, afkLen, stopLeft;

        RecShaper(int length) {
            if (length >= 2 && chance(s.midStopChance)) { midFrom = range(0, length - 1); midLen = range(s.midStopMin, s.midStopMax); }
            if (length >= 2 && chance(s.afkChance)) { afkFrom = range(0, length - 1); afkLen = range(s.afkMin, s.afkMax); }
        }

        /** Стоим (остановка/«протупить»): ЛКМ/ПКМ и зажим отпущены. */
        public boolean stopping() { return stopLeft > 0; }
        public int stopLeft() { return stopLeft; }

        /**
         * С остановками: на стоячем кадре не раньше запланированного — стоим (кадр повторяется) N тиков.
         * Встаём только на стоячих кадрах: запись идёт «вслепую», остановка на ходу сбила бы позицию.
         */
        public int advance(boolean idle, boolean nextIdle, int index) {
            if (stopLeft > 0) { stopLeft--; return stopLeft > 0 ? 0 : 1; }
            if (idle && afkFrom >= 0 && index >= afkFrom) { afkFrom = -1; afkStops++; afks++; stopLeft = afkLen; return 0; }
            if (idle && midFrom >= 0 && index >= midFrom) { midFrom = -1; stops++; midStops++; stopLeft = midLen; return 0; }
            return advance(idle, nextIdle);
        }

        /**
         * Сколько кадров сдвинуться после этого тика: 0 — повторить кадр, 1 — как обычно, 2 — пропустить следующий.
         * @param idle этот кадр стоячий (позиция как у предыдущего, клавиш движения нет)
         * @param nextIdle следующий тоже стоячий
         */
        public int advance(boolean idle, boolean nextIdle) {
            if (!idle || s.idleStretchPct <= 0) { repeatsInRow = 0; return 1; }
            int roll = rnd.nextInt(200);                       // pct/2 — повтор, pct/2 — пропуск
            if (roll < s.idleStretchPct && repeatsInRow < 3) { repeatsInRow++; repeats++; return 0; }
            repeatsInRow = 0;
            if (roll >= 200 - s.idleStretchPct && nextIdle) { skips++; return 2; }
            return 1;
        }

        public boolean attack(boolean want) {
            if (want != wantAttack) { wantAttack = want; attackDelay = range(0, s.actionDelayMax); }
            if (attackDelay > 0) attackDelay--; else outAttack = wantAttack;
            return outAttack;
        }

        public boolean use(boolean want) {
            if (want != wantUse) { wantUse = want; useDelay = range(0, s.actionDelayMax); }
            if (useDelay > 0) useDelay--; else outUse = wantUse;
            return outUse;
        }

        /** Плавный шум камеры: {dyaw, dpitch}, новая цель каждые 20…40 тиков, догоняем по 10 % за тик. */
        public float[] camNoise() {
            double d = s.camNoiseDeg;
            if (d <= 0) return new float[]{0, 0};
            if (--noiseLeft <= 0) { goalYaw = uni(-d, d); goalPitch = uni(-d, d) * 0.6; noiseLeft = range(20, 40); }
            noiseYaw += (goalYaw - noiseYaw) * 0.1;
            noisePitch += (goalPitch - noisePitch) * 0.1;
            return new float[]{(float) noiseYaw, (float) noisePitch};
        }
    }

    public RecShaper recShaper() { return new RecShaper(0); }
    /** length — кадров в записи (0 — без остановок). */
    public RecShaper recShaper(int length) { return new RecShaper(length); }

    // ── камера: «оглядеться» ─────────────────────────────────────────────────

    /**
     * Смещения камеры от «своего» взгляда: на ходу — плавный шум pitch ± lookPitchDeg (yaw не трогаем — от него
     * зависит направление W/A/S/D); на остановке — yaw и pitch гуляют ± stopLookDeg, за 14 тиков до конца
     * остановки возвращаются. Отдаёт приращение за тик (его применяют к игроку и сообщают детектору поворота).
     */
    public final class Look {
        private double yawOff, pitchOff, goalYaw, goalPitch;
        private int left;
        private boolean wasStop;

        /** @param stopLeft сколько ещё стоять (0 — идём); perfect — идеальный круг: всё плавно в 0. @return {dYaw, dPitch} */
        public float[] tick(int stopLeft, boolean perfect) {
            double rate;
            boolean stop = stopLeft > 0 && s.stopLookDeg > 0 && !perfect;
            if (stop != wasStop) { left = 0; wasStop = stop; }
            if (stop) {
                if (stopLeft <= 14) { goalYaw = 0; goalPitch = 0; }
                else if (--left <= 0) {
                    double d = s.stopLookDeg;
                    goalYaw = uni(-d, d); goalPitch = uni(-d, d) * 0.6; left = range(15, 45);
                }
                rate = stopLeft <= 14 ? 0.3 : 0.12;
            } else {
                goalYaw = 0;
                if (perfect || s.lookPitchDeg <= 0) goalPitch = 0;
                else if (--left <= 0) { goalPitch = uni(-s.lookPitchDeg, s.lookPitchDeg); left = range(25, 60); }
                rate = 0.08;
            }
            double ny = yawOff + (goalYaw - yawOff) * (stop ? rate : 0.35);
            double np = pitchOff + (goalPitch - pitchOff) * rate;
            if (Math.abs(ny) < 1e-4 && goalYaw == 0) ny = 0;
            float[] d = {(float) (ny - yawOff), (float) (np - pitchOff)};
            yawOff = ny; pitchOff = np;
            return d;
        }

        public double yawOff() { return yawOff; }
        public double pitchOff() { return pitchOff; }
    }
}
