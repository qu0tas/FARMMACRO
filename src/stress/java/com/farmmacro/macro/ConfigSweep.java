package com.farmmacro.macro;

import com.farmmacro.route.PointRoute;
import com.farmmacro.route.RoutePoint;
import com.farmmacro.route.TestRoutes;
import com.farmmacro.route.WalkCore;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Подбор «Случайности» вне игры: ./gradlew configSweep [-Pcandidates=N -Plaps=N].
 * Случайный поиск по настройкам HumanSettings на нескольких фермах (кинематика как в WalkSim/HumanizerCheck):
 * жёсткие условия — ни одной паники («Застрял» окно 30 т, «Сошёл» 4 бл), уход от линии ≤ 0.45 бл, нет одинаковых
 * неидеальных кругов; оценка — разброс длины кругов (CV), доля идеальных, частота остановок в минутах, потеря скорости.
 * Лучшие кандидаты перепроверяются на длинных сессиях, при окне застревания 20 т и при yaw поперёк рядов.
 */
public final class ConfigSweep {

    record Farm(String name, PointRoute route, float yaw, boolean sprint) {}

    record Lap(int ticks, boolean perfect, String panic, double maxOff, int stood, int stops, int afks, long keyHash) {}

    record Stats(HumanSettings h, double score, boolean ok, String why, double cv, double perfectShare, double overhead,
                 double stopsPer10min, double afkPerHour, double stoodShare, double maxOff, int dupLaps, int laps,
                 double cornerVar, double maxPerfectRun, String worstFarm) {}

    static Farm farm(String name, int w, int d, int step, boolean pauses, boolean sprint, float yaw) {
        PointRoute r = new PointRoute();
        r.points.addAll(TestRoutes.rows(0, 0, w - 1, d - 1, step, 64));
        if (pauses) for (int i = 1; i < r.points.size(); i += 2) r.points.get(i).pauseTicks = 8;   // конец ряда — пауза
        if (sprint) for (RoutePoint p : r.points) p.sprint = true;
        return new Farm(name, r, yaw, sprint);
    }

    /** Один круг; human == null — без случайности. */
    static Lap lap(Farm f, Humanizer human, boolean perfect, int stuck, int maxTicks) {
        PointRoute r = f.route.copy();
        WalkCore core = new WalkCore();
        WalkCore.Out out = new WalkCore.Out();
        Humanizer shapeH = perfect ? null : human;
        if (shapeH != null) shapeH.shapeRoute(r);
        core.start(r, 0, stuck);
        if (shapeH != null) core.setReach(shapeH.reachRadii(r.points.size(), 0.3));
        Humanizer.RouteShaper sh = shapeH != null ? shapeH.routeShaper(r.points.size()) : null;
        RoutePoint p0 = r.points.get(0);
        double x = p0.x, z = p0.z, vx = 0, vz = 0, maxOff = 0;
        long hash = 1469598103934665603L;
        int stood = 0;
        for (int t = 0; t < maxTicks; t++) {
            if (sh != null) core.pauseExternal(sh.stopNow(core.target(), core.dropPhase(), true, core.pausing()));
            core.tick(x, 64, z, true, f.yaw, 0.3, true, stuck, true, 4.0, true, out);
            if (out.panic != null) return new Lap(t, perfect, out.panic, maxOff, stood, 0, 0, hash);
            if (core.done()) return new Lap(t, perfect, null, maxOff, stood, sh == null ? 0 : sh.stops, sh == null ? 0 : sh.afkStops, hash);
            if (core.stoppedNow()) stood++;
            if (sh != null) { sh.tick(out, x, z, core.target(), core.dropPhase(), false); if (sh.idleNow) core.resetStuckWindow(); }
            int m = (out.forward ? 1 : 0) | (out.back ? 2 : 0) | (out.left ? 4 : 0) | (out.right ? 8 : 0) | (out.sprint ? 16 : 0);
            hash = (hash ^ m) * 1099511628211L;
            boolean moving = (m & 15) != 0;
            double speed = moving ? (out.sprint ? WalkCore.SPRINT : WalkCore.WALK) : 0;
            double[] dir = WalkCore.moveDir(out, f.yaw);
            vx = vx * 0.55 + dir[0] * speed * 0.45; vz = vz * 0.55 + dir[1] * speed * 0.45;
            x += vx; z += vz;
            int ti = core.target();
            double d = Double.MAX_VALUE;
            for (int k = Math.max(1, ti - 1); k <= Math.min(r.points.size() - 1, ti + 1); k++) {
                RoutePoint a = r.points.get(k - 1), b = r.points.get(k);
                d = Math.min(d, HumanizerCheck.seg(x, z, a.x, a.z, b.x, b.z));
            }
            maxOff = Math.max(maxOff, d);
        }
        return new Lap(maxTicks, perfect, "не дошёл", maxOff, stood, 0, 0, hash);
    }

    static final Map<String, Integer> PLAIN = new ConcurrentHashMap<>();

    static int plain(Farm f) {
        return PLAIN.computeIfAbsent(f.name, k -> lap(f, null, true, 30, 200_000).ticks);
    }

    /** Сессия из laps кругов на ферме; одна случайность на всю сессию, как в игре (beginLap перед кругом). */
    static Stats session(HumanSettings hs, List<Farm> farms, int laps, long seed, int stuck) {
        double sumCv = 0, perfect = 0, overhead = 0, stopsPerMin = 0, afkPerHour = 0, stoodShare = 0, maxOff = 0, corner = 0;
        int dup = 0, total = 0, maxRun = 0;
        String fail = null, worst = "";
        for (Farm f : farms) {
            int base = plain(f);
            Humanizer h = new Humanizer(hs, seed ^ f.name.hashCode());
            double[] t = new double[laps];
            Set<Long> seen = new HashSet<>();
            long ticks = 0, stood = 0;
            int stops = 0, afks = 0, perf = 0, run = 0;
            double nonPerfSum = 0; int nonPerf = 0;
            for (int i = 0; i < laps; i++) {
                boolean p = h.beginLap();
                Lap l = lap(f, h, p, stuck, base * 4 + 30_000);
                if (l.panic != null) { fail = f.name + ": " + l.panic; break; }
                t[i] = l.ticks; ticks += l.ticks; stood += l.stood; stops += l.stops; afks += l.afks;
                if (l.maxOff > maxOff) { maxOff = l.maxOff; worst = f.name; }
                if (p) { perf++; run++; maxRun = Math.max(maxRun, run); } else {
                    run = 0;
                    if (!seen.add(l.keyHash)) dup++;
                    nonPerfSum += l.ticks; nonPerf++;
                }
            }
            if (fail != null) break;
            double mean = ticks / (double) laps, var = 0;
            for (double v : t) var += (v - mean) * (v - mean);
            sumCv += Math.sqrt(var / laps) / mean;
            perfect += perf / (double) laps;
            overhead += mean / base - 1;
            double minutes = ticks / 1200.0;
            stopsPerMin += stops / minutes * 10;
            afkPerHour += afks / minutes * 60;
            stoodShare += stood / (double) ticks;
            total += laps;
        }
        int n = farms.size();
        if (fail != null) return new Stats(hs, 1e9, false, fail, 0, 0, 0, 0, 0, 0, maxOff, dup, total, 0, maxRun, worst);
        double cv = sumCv / n, ps = perfect / n, oh = overhead / n, s10 = stopsPerMin / n, aph = afkPerHour / n, ss = stoodShare / n;
        corner = (hs.overlapChance + hs.gapChance) / 100.0;
        String why = null;
        if (maxOff > 0.45) why = String.format(Locale.ROOT, "уход %.2f > 0.45", maxOff);
        else if (dup > 0) why = "одинаковые круги: " + dup;
        double score = score(MODE_BALANCE, cv, ps, s10, aph, oh, corner, maxOff, maxRun);
        return new Stats(hs, why == null ? score : 1e6 + score, why == null, why, cv, ps, oh, s10, aph, ss, maxOff, dup, total, corner, maxRun, worst);
    }

    static final int MODE_BALANCE = 0, MODE_SAFE = 1, MODE_FAST = 2;
    static final String[] MODE_NAMES = {"Баланс", "Безопасность", "Эффективность"};

    /**
     * Оценка «человечности» (меньше — лучше). Цели (docs/ANALYSIS.md): разброс длины кругов, доля идеальных,
     * остановки в минутах реального времени, потеря скорости, «смазанность» углов, запас до соседнего ряда.
     */
    static double score(int mode, double cv, double ps, double s10, double aph, double oh, double corner, double maxOff, double maxRun) {
        double cvLo = 0.04, cvHi = 0.09, psLo = 0.06, psHi = 0.14, sLo = 1.0, sHi = 3.0, aLo = 1.0, aHi = 4.0, ohMax = 0.08, ohW = 25;
        if (mode == MODE_SAFE) { cvLo = 0.06; cvHi = 0.12; psLo = 0.04; psHi = 0.10; sLo = 2.0; sHi = 4.0; aLo = 2.0; aHi = 6.0; ohMax = 0.15; ohW = 15; }
        if (mode == MODE_FAST) { cvLo = 0.03; cvHi = 0.07; psLo = 0.08; psHi = 0.18; sLo = 0.5; sHi = 1.5; aLo = 0.5; aHi = 2.0; ohMax = 0.03; ohW = 60; }
        double score = 0;
        score += 40 * band(cv, cvLo, cvHi);
        score += 30 * band(ps, psLo, psHi);
        score += 20 * band(s10, sLo, sHi) / 3;
        score += 15 * band(aph, aLo, aHi) / 4;
        score += ohW * Math.max(0, oh - ohMax) * 10;
        score += 10 * band(corner, 0.35, 0.75);
        score += 10 * Math.max(0, maxOff - (mode == MODE_SAFE ? 0.33 : 0.38)) * 10;
        score += 5 * Math.max(0, maxRun - 6);
        score += oh + 0.5 * maxOff;                      // при равенстве — быстрее и дальше от соседнего ряда
        return score;
    }

    static double score(int mode, Stats s) {
        double v = score(mode, s.cv, s.perfectShare, s.stopsPer10min, s.afkPerHour, s.overhead, s.cornerVar, s.maxOff, s.maxPerfectRun);
        return s.ok ? v : 1e6 + v;
    }

    /** Мутация вокруг хорошего кандидата (локальный поиск). */
    static HumanSettings mutate(HumanSettings b, SplittableRandom r) {
        HumanSettings h = b.copy();
        int n = 1 + r.nextInt(4);
        for (int i = 0; i < n; i++) {
            switch (r.nextInt(16)) {
                case 0 -> h.reachJitter = round(clamp(h.reachJitter + r.nextDouble(-0.05, 0.05), 0, 0.3), 0.01);
                case 1 -> h.cornerSlop = round(clamp(h.cornerSlop + r.nextDouble(-0.05, 0.05), 0.2, 0.5), 0.01);
                case 2 -> h.overlapChance = (int) clamp(h.overlapChance + r.nextInt(-10, 11), 0, 70);
                case 3 -> h.overlapMax = (int) clamp(h.overlapMax + r.nextInt(-1, 2), 1, 5);
                case 4 -> h.gapChance = (int) clamp(h.gapChance + r.nextInt(-10, 11), 0, 50);
                case 5 -> h.gapMax = (int) clamp(h.gapMax + r.nextInt(-1, 2), 1, 4);
                case 6 -> h.hesitateChance = (int) clamp(h.hesitateChance + r.nextInt(-4, 5), 0, 20);
                case 7 -> h.hesitateMax = (int) clamp(h.hesitateMax + r.nextInt(-4, 5), 2, 20);
                case 8 -> h.pauseJitterPct = (int) clamp(h.pauseJitterPct + r.nextInt(-8, 9), 0, 50);
                case 9 -> h.startDelayMax = (int) clamp(h.startDelayMax + r.nextInt(-10, 11), 0, 60);
                case 10 -> h.perfectLapChance = (int) clamp(h.perfectLapChance + r.nextInt(-3, 4), 0, 20);
                case 11 -> h.perfectStreakChance = (int) clamp(h.perfectStreakChance + r.nextInt(-10, 11), 0, 60);
                case 12 -> h.streakMax = (int) clamp(h.streakMax + r.nextInt(-1, 2), 2, 6);
                case 13 -> h.midStopChance = (int) clamp(h.midStopChance + r.nextInt(-4, 5), 0, 30);
                case 14 -> { h.midStopMin = (int) clamp(h.midStopMin + r.nextInt(-8, 9), 5, 40); h.midStopMax = (int) clamp(h.midStopMax + r.nextInt(-20, 21), h.midStopMin, 140); }
                default -> { h.afkChance = (int) clamp(h.afkChance + r.nextInt(-1, 2), 0, 8); h.afkMax = (int) clamp(h.afkMax + r.nextInt(-150, 151), h.afkMin, 1200); }
            }
        }
        return HumanSettings.sanitize(h);
    }

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    /** 0 внутри [lo, hi], иначе относительное расстояние до границы. */
    static double band(double v, double lo, double hi) {
        if (v < lo) return (lo - v) / Math.max(1e-9, lo);
        if (v > hi) return (v - hi) / Math.max(1e-9, hi);
        return 0;
    }

    static HumanSettings random(SplittableRandom r) {
        HumanSettings h = ConfigSweepPresets.base();
        h.reachJitter = round(r.nextDouble(0, 0.30), 0.01);
        h.cornerSlop = round(r.nextDouble(0.20, 0.50), 0.01);
        h.overlapChance = r.nextInt(0, 71); h.overlapMax = r.nextInt(1, 6);
        h.gapChance = r.nextInt(0, 51); h.gapMax = r.nextInt(1, 5);
        h.hesitateChance = r.nextInt(0, 21); h.hesitateMax = r.nextInt(2, 21);
        h.pauseJitterPct = r.nextInt(0, 51); h.pauseJitterTicks = r.nextInt(0, 7);
        h.startDelayMax = r.nextInt(0, 61); h.sprintDelayMax = r.nextInt(0, 7); h.jumpDelayMax = r.nextInt(0, 5);
        h.perfectLapChance = r.nextInt(0, 21); h.perfectStreakChance = r.nextInt(0, 61);
        h.streakMin = 2; h.streakMax = r.nextInt(2, 7);
        h.midStopChance = r.nextInt(0, 31); h.midStopMin = r.nextInt(5, 41); h.midStopMax = h.midStopMin + r.nextInt(0, 101);
        h.afkChance = r.nextInt(0, 9); h.afkMin = r.nextInt(60, 301); h.afkMax = h.afkMin + r.nextInt(0, 901);
        return HumanSettings.sanitize(h);
    }

    static double round(double v, double s) { return Math.round(v / s) * s; }

    static String describe(HumanSettings h) {
        return String.format(Locale.ROOT, "reach+%.2f slop %.2f | overlap %d%%/%d gap %d%%/%d hes %d%%/%d | pause ±%d%%/±%d start %d spr %d jmp %d | "
                        + "perfect %d%% streak %d%% %d-%d | stop %d%% %d-%d | afk %d%% %d-%d",
                h.reachJitter, h.cornerSlop, h.overlapChance, h.overlapMax, h.gapChance, h.gapMax, h.hesitateChance, h.hesitateMax,
                h.pauseJitterPct, h.pauseJitterTicks, h.startDelayMax, h.sprintDelayMax, h.jumpDelayMax,
                h.perfectLapChance, h.perfectStreakChance, h.streakMin, h.streakMax, h.midStopChance, h.midStopMin, h.midStopMax,
                h.afkChance, h.afkMin, h.afkMax);
    }

    static String row(Stats s) {
        return String.format(Locale.ROOT, "score %.2f | CV %.1f%% идеальных %.1f%% (макс. серия %.0f) потеря %.1f%% встать %.1f/10мин протупить %.1f/ч стоим %.1f%% уход %.2f | %s",
                s.score, s.cv * 100, s.perfectShare * 100, s.maxPerfectRun, s.overhead * 100, s.stopsPer10min, s.afkPerHour, s.stoodShare * 100, s.maxOff,
                (s.ok ? "OK" : s.why) + (s.maxOff > 0.4 ? " [макс. уход: " + s.worstFarm + "]" : ""));
    }

    public static void main(String[] args) throws Exception {
        int candidates = Integer.getInteger("candidates", 3000);
        int laps = Integer.getInteger("laps", 40);
        long t0 = System.currentTimeMillis();
        List<Farm> farms = List.of(
                farm("20x20 шаг 3, паузы на концах", 20, 20, 3, true, false, 90f),
                farm("40x40 шаг 3, спринт", 40, 40, 3, false, true, 90f),
                farm("30x12 шаг 2, без пауз", 30, 12, 2, false, false, 90f));
        for (Farm f : farms) System.out.printf(Locale.ROOT, "Ферма %-30s точек %3d, круг без случайности %d т (%.1f с)%n",
                f.name, f.route.points.size(), plain(f), plain(f) / 20.0);

        // 1. случайный поиск (параллельно)
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors());
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        List<Future<Stats>> fs = new ArrayList<>();
        AtomicInteger done = new AtomicInteger();
        for (int i = 0; i < candidates; i++) {
            final int k = i;
            fs.add(ex.submit(() -> {
                HumanSettings h = random(new SplittableRandom(9_000_000L + k));
                Stats s = session(h, farms, laps, 77_000L + k, 30);
                int d = done.incrementAndGet();
                if (d % 500 == 0) System.out.printf(Locale.ROOT, "  … %d/%d кандидатов, %.0f с%n", d, candidates, (System.currentTimeMillis() - t0) / 1000.0);
                return s;
            }));
        }
        List<Stats> all = new ArrayList<>();
        for (var f : fs) all.add(f.get());
        long okN = all.stream().filter(Stats::ok).count();
        Map<String, Long> fails = new TreeMap<>();
        for (Stats s : all) if (!s.ok) fails.merge(s.why.replaceAll("[0-9.]+", "N").replaceAll(": .*", ""), 1L, Long::sum);
        System.out.printf(Locale.ROOT, "%nСлучайный поиск: кандидатов %d, прошли жёсткие условия %d (%.1f%%). Причины отсева: %s (%.1f мин)%n",
                all.size(), okN, okN * 100.0 / all.size(), fails, (System.currentTimeMillis() - t0) / 60000.0);

        System.out.println("\nВлияние параметров (доля отсева по уходу от линии > 0.45):");
        sens(all, "cornerSlop", s -> s.h.cornerSlop, new double[]{0.2, 0.3, 0.35, 0.4, 0.45, 0.51});
        sens(all, "reachJitter", s -> s.h.reachJitter, new double[]{0, 0.06, 0.12, 0.18, 0.24, 0.31});
        sens(all, "overlapMax", s -> s.h.overlapMax, new double[]{1, 2, 3, 4, 5, 6});
        sens(all, "gapMax", s -> s.h.gapMax, new double[]{1, 2, 3, 4, 5});

        Map<String, HumanSettings> named = ConfigSweepPresets.named();
        Map<String, Future<Stats>> nf = new LinkedHashMap<>();
        for (var e : named.entrySet()) nf.put(e.getKey(), ex.submit(() -> session(e.getValue(), farms, laps, 4242, 30)));
        Map<String, Stats> namedStats = new LinkedHashMap<>();
        for (var e : nf.entrySet()) namedStats.put(e.getKey(), e.getValue().get());

        int refine = Integer.getInteger("refine", 40);
        List<HumanSettings> finals = new ArrayList<>();
        List<String> finalNames = new ArrayList<>();
        for (int mode = 0; mode < 3; mode++) {
            final int md = mode;
            List<Stats> ranked = new ArrayList<>(all);
            ranked.sort(Comparator.comparingDouble(s -> score(md, s)));
            System.out.printf("%n══ Режим «%s» ══%nТекущие наборы:%n", MODE_NAMES[mode]);
            for (var e : namedStats.entrySet()) System.out.printf(Locale.ROOT, "  %-22s %.2f | %s%n", e.getKey(), score(md, e.getValue()), row(e.getValue()));
            System.out.println("Топ-5 случайного поиска:");
            for (int i = 0; i < 5; i++) System.out.printf(Locale.ROOT, "  #%d %.2f | %s%n     %s%n", i + 1, score(md, ranked.get(i)), row(ranked.get(i)), describe(ranked.get(i).h));

            // локальный поиск: мутации вокруг топ-8, вдвое больше кругов, лучший — дальше
            List<Stats> pool = new ArrayList<>(ranked.subList(0, 8));
            for (int gen = 0; gen < 3; gen++) {
                List<Future<Stats>> mf = new ArrayList<>();
                for (int pi = 0; pi < pool.size(); pi++) for (int k = 0; k < refine / 8 + 1; k++) {
                    final HumanSettings parent = pool.get(pi).h; final long sd = 31L * gen + 1000L * pi + k + 7L * md;
                    mf.add(ex.submit(() -> session(mutate(parent, new SplittableRandom(sd * 7919)), farms, laps * 2, 5150 + sd, 30)));
                }
                for (Stats ps0 : new ArrayList<>(pool)) mf.add(CompletableFuture.completedFuture(ps0));
                List<Stats> next = new ArrayList<>();
                for (var f : mf) next.add(f.get());
                next.sort(Comparator.comparingDouble(s -> score(md, s)));
                pool = new ArrayList<>(next.subList(0, 8));
                System.out.printf(Locale.ROOT, "  поколение %d: лучший %.2f | %s (%.1f мин)%n", gen + 1, score(md, pool.get(0)), row(pool.get(0)),
                        (System.currentTimeMillis() - t0) / 60000.0);
            }
            for (int i = 0; i < 2; i++) { finals.add(pool.get(i).h); finalNames.add(MODE_NAMES[mode] + " #" + (i + 1)); }
        }

        // перепроверка: длинная сессия, окно застревания 20 т, yaw поперёк рядов, 3 сида
        System.out.println("\n══ Перепроверка: " + Integer.getInteger("vlaps", 300) + " кругов × 4 фермы (+ yaw поперёк рядов), окно «Застрял» 20 т, 3 сида ══");
        List<Farm> hard = new ArrayList<>(farms);
        hard.add(farm("20x20 yaw поперёк (A/D)", 20, 20, 3, true, false, 0f));
        for (var e : named.entrySet()) { finals.add(e.getValue()); finalNames.add(e.getKey()); }
        List<Future<String>> vf = new ArrayList<>();
        for (int i = 0; i < finals.size(); i++) {
            final HumanSettings h = finals.get(i); final String nm = finalNames.get(i);
            vf.add(ex.submit(() -> {
                StringBuilder sb = new StringBuilder("\n" + nm + ": " + describe(h) + "\n");
                for (long sd : new long[]{1, 2, 3}) {
                    Stats s = session(h, hard, Integer.getInteger("vlaps", 300), sd * 1_000_003L, 20);
                    sb.append(String.format(Locale.ROOT, "  сид %d: %s%n", sd, row(s)));
                }
                return sb.toString();
            }));
        }
        for (var f : vf) System.out.print(f.get());
        ex.shutdown();
        System.out.printf(Locale.ROOT, "%nВсего %.1f мин%n", (System.currentTimeMillis() - t0) / 60000.0);
    }

    interface Get { double v(Stats s); }

    static void sens(List<Stats> all, String name, Get g, double[] edges) {
        StringBuilder sb = new StringBuilder("  " + name + ": ");
        for (int i = 0; i + 1 < edges.length; i++) {
            int n = 0, bad = 0;
            for (Stats s : all) {
                double v = g.v(s);
                if (v >= edges[i] && v < edges[i + 1]) { n++; if (s.why != null && s.why.startsWith("уход")) bad++; }
            }
            sb.append(String.format(Locale.ROOT, "[%.2f–%.2f) %d%% из %d; ", edges[i], edges[i + 1], n == 0 ? 0 : bad * 100 / n, n));
        }
        System.out.println(sb);
    }
}
