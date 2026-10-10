package com.farmmacro.macro;

import com.farmmacro.route.PointRoute;
import com.farmmacro.route.RoutePoint;
import com.farmmacro.route.TestRoutes;
import com.farmmacro.route.WalkCore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * «Случайность» вне игры: повторяемость по сиду, пределы значений, выкл = как раньше, и 200 проходов рядов из точек 20×20
 * с разными сидами (кинематика как в WalkSim): все точки, без ложной паники, смазанный угол не дальше допуска,
 * длина прохода в тиках разная.
 */
public final class HumanizerCheck {

    private static void check(boolean ok, String what) { if (!ok) throw new IllegalStateException("HumanizerCheck: " + what); }

    record Run(int ticks, String panic, double maxOff, String keys, int overlaps, int gaps) {}

    /** Тиков стояли по остановкам «Случайности» (WalkCore.pauseExternal) за последний run. */
    static int stoodTicks, stopsDone;

    static PointRoute rowsRoute() {
        PointRoute r = new PointRoute();
        r.points.addAll(TestRoutes.rows(0, 0, 19, 19, 3, 64));
        for (int i = 3; i < r.points.size(); i += 4) r.points.get(i).pauseTicks = 10;
        return r;
    }

    /** Один проход; human == null — без случайности. */
    static Run run(Humanizer human, int maxTicks) { return run(human, maxTicks, false); }

    /** withStops — как в игре: shaper.stopNow до WalkCore.tick (остановки посреди пути, заминки). */
    static Run run(Humanizer human, int maxTicks, boolean withStops) {
        stoodTicks = 0; stopsDone = 0;
        PointRoute r = rowsRoute().copy();
        WalkCore core = new WalkCore();
        WalkCore.Out out = new WalkCore.Out();
        if (human != null) human.shapeRoute(r);
        core.start(r, 0, 30);
        if (human != null) core.setReach(human.reachRadii(r.points.size(), 0.3));
        Humanizer.RouteShaper sh = human != null ? (withStops ? human.routeShaper(r.points.size()) : human.routeShaper()) : null;
        float yaw = 90f;
        RoutePoint p0 = r.points.get(0);
        double x = p0.x, z = p0.z, vx = 0, vz = 0, maxOff = 0;
        StringBuilder keys = new StringBuilder();
        for (int t = 0; t < maxTicks; t++) {
            if (withStops && sh != null) {
                int n = sh.stopNow(core.target(), core.dropPhase(), true, core.pausing());
                if (n > 0) stopsDone++;
                core.pauseExternal(n);
            }
            core.tick(x, 64, z, true, yaw, 0.3, true, 30, true, 4.0, true, out);
            if (core.stoppedNow()) {
                stoodTicks++;
                if (out.forward || out.back || out.left || out.right) return new Run(t, "на остановке нажата клавиша", 0, "", 0, 0);
            }
            if (out.panic != null) return new Run(t, out.panic, maxOff, keys.toString(), 0, 0);
            if (core.done()) return new Run(t, null, maxOff, keys.toString(), sh == null ? 0 : sh.overlaps, sh == null ? 0 : sh.gaps);
            if (sh != null) { sh.tick(out, x, z, core.target(), core.dropPhase(), false); if (sh.idleNow) core.resetStuckWindow(); }
            keys.append(out.forward ? 'W' : '.').append(out.back ? 'S' : '.').append(out.left ? 'A' : '.').append(out.right ? 'D' : '.');
            boolean moving = out.forward || out.back || out.left || out.right;
            double speed = moving ? (out.sprint ? WalkCore.SPRINT : WalkCore.WALK) : 0;
            double[] dir = WalkCore.moveDir(out, yaw);
            vx = vx * 0.55 + dir[0] * speed * 0.45; vz = vz * 0.55 + dir[1] * speed * 0.45;
            x += vx; z += vz;
            // отклонение от ломаной маршрута (ближайший из соседних отрезков) — «смазанный угол»
            int ti = core.target();
            double d = Double.MAX_VALUE;
            for (int k = Math.max(1, ti - 1); k <= Math.min(r.points.size() - 1, ti + 1); k++) {
                RoutePoint a = r.points.get(k - 1), b = r.points.get(k);
                d = Math.min(d, seg(x, z, a.x, a.z, b.x, b.z));
            }
            maxOff = Math.max(maxOff, d);
        }
        return new Run(maxTicks, "не дошёл", maxOff, keys.toString(), 0, 0);
    }

    static double seg(double x, double z, double ax, double az, double bx, double bz) {
        double vx = bx - ax, vz = bz - az, vv = vx * vx + vz * vz;
        double t = vv < 1e-12 ? 0 : Math.max(0, Math.min(1, ((x - ax) * vx + (z - az) * vz) / vv));
        return Math.hypot(x - ax - vx * t, z - az - vz * t);
    }

    public static void main() {
        // выключено / старые файлы
        HumanSettings def = new HumanSettings();
        check(!def.enabled, "по умолчанию выключено");
        check(Humanizer.create(def, 123) == null, "выключено — нет Humanizer");
        check(Humanizer.create(null, 123) == null, "нет настроек — нет Humanizer");
        MacroSettings old = MacroSettings.fromFile(new MacroSettings() {{ human = null; }}, null);
        check(old.human != null && !old.human.enabled, "файл без human — выключено");

        // пределы
        HumanSettings bad = new HumanSettings();
        bad.overlapChance = 500; bad.gapMax = -3; bad.reachJitter = Double.NaN; bad.landDelayMin = 9; bad.landDelayMax = 2; bad.camNoiseDeg = 1e9;
        HumanSettings.sanitize(bad);
        check(bad.overlapChance == 100 && bad.gapMax == 1 && bad.reachJitter == 0.12 && bad.landDelayMax == 9 && bad.camNoiseDeg == 10, "sanitize");

        HumanSettings hs = new HumanSettings();
        hs.enabled = true;
        hs.hesitateChance = 15;
        Humanizer h = new Humanizer(hs, 42);
        for (int i = 0; i < 10_000; i++) {
            int v = h.range(-3, 5);
            check(v >= -3 && v <= 5, "range");
        }
        double[] rr = h.reachRadii(1000, 0.3);
        for (double v : rr) check(v >= 0.3 - 1e-9 && v <= Math.max(0.3, hs.cornerSlop * 1.4) + 1e-9, "радиус вне пределов: " + v);

        // без случайности — как раньше (тот же путь, что и до 1.9.0)
        Run plain = run(null, 20_000), plain2 = run(null, 20_000);
        check(plain.panic == null && plain.keys.equals(plain2.keys), "без случайности должно быть одинаково");

        // повторяемость по сиду
        Run a = run(new Humanizer(hs, 777), 20_000), b = run(new Humanizer(hs, 777), 20_000), c = run(new Humanizer(hs, 778), 20_000);
        check(a.keys.equals(b.keys), "один сид — один проход");
        check(!a.keys.equals(c.keys), "разные сиды — разные проходы");
        check(!a.keys.equals(plain.keys), "со случайностью проход должен отличаться от обычного");

        // 200 проходов
        int n = 200, min = Integer.MAX_VALUE, max = 0, ov = 0, gp = 0;
        long sum = 0;
        double worst = 0;
        java.util.Set<String> distinct = new java.util.HashSet<>();
        for (int i = 0; i < n; i++) {
            Run r = run(new Humanizer(hs, 1000 + i), 40_000);
            check(r.panic == null, "сид " + (1000 + i) + ": " + r.panic);
            min = Math.min(min, r.ticks); max = Math.max(max, r.ticks); sum += r.ticks;
            worst = Math.max(worst, r.maxOff);
            ov += r.overlaps; gp += r.gaps;
            distinct.add(r.keys);
        }
        check(worst <= Math.max(plain.maxOff, hs.cornerSlop) + 0.05, String.format(Locale.ROOT, "смазанный угол ушёл на %.2f бл", worst));
        check(max > min, "длина проходов не различается");
        check(distinct.size() == n, "есть одинаковые проходы: " + distinct.size() + " из " + n);
        System.out.printf(Locale.ROOT, "%nСлучайность: ряды из точек 20×20 (%d точек) × %d сидов — без паники; тиков min %d / сред. %.0f / max %d "
                        + "(без случайности %d); макс. уход от линии %.2f бл (допуск %.2f); перекрытий %d, зазоров %d — OK%n",
                rowsRoute().points.size(), n, min, sum / (double) n, max, plain.ticks, worst, hs.cornerSlop, ov, gp);

        // запись: повтор/пропуск стоячих кадров, ЛКМ позже
        Humanizer.RecShaper rs = new Humanizer(hs, 5).recShaper();
        int adv = 0, frames = 1000;
        for (int i = 0; i < frames; i++) adv += rs.advance(true, true);
        check(rs.repeats > 0 && rs.skips > 0, "стоячие кадры: нет повторов/пропусков");
        check(Math.abs(adv - frames) < frames * 0.1, "стоячие кадры: сдвиг слишком большой " + adv);
        Humanizer.RecShaper rs2 = new Humanizer(hs, 5).recShaper();
        for (int i = 0; i < 100; i++) check(rs2.advance(false, true) == 1, "движение — кадры не трогаем");
        List<Boolean> lag = new ArrayList<>();
        Humanizer.RecShaper rs3 = new Humanizer(hs, 9).recShaper();
        for (int i = 0; i < 20; i++) lag.add(rs3.attack(i >= 5));
        int firstOn = lag.indexOf(true);
        check(firstOn >= 5 && firstOn <= 5 + hs.actionDelayMax, "ЛКМ позже на 0…" + hs.actionDelayMax + ", а с " + firstOn);
        // ── v1.10: круги, остановки, взгляд ──
        HumanSettings ls = new HumanSettings();
        ls.enabled = true;
        Humanizer lh = new Humanizer(ls, 31337);
        int lapsN = 20_000, perfect = 0, streak = 0, maxStreak = 0, streaks2 = 0;
        for (int i = 0; i < lapsN; i++) {
            boolean pf = lh.beginLap();
            if (pf) { perfect++; streak++; if (streak == 2) streaks2++; maxStreak = Math.max(maxStreak, streak); }
            else streak = 0;
        }
        double frac = perfect / (double) lapsN;
        check(frac > 0.06 && frac < 0.25, String.format(Locale.ROOT, "идеальных кругов %.1f %%", frac * 100));
        check(streaks2 > 50 && maxStreak >= ls.streakMax, "серии идеальных: " + streaks2 + ", макс. " + maxStreak);
        check(lh.laps == lapsN && lh.perfectLaps == perfect, "счётчики кругов");
        Humanizer.ACTIVE = lh;
        lh.beginLap();
        check((Humanizer.lap() == null) == lh.lapPerfect(), "lap(): идеальный круг — без формы");
        Humanizer.ACTIVE = null;
        // идеальный круг = проход без случайности (те же клавиши, что и plain)
        HumanSettings allPerfect = new HumanSettings();
        allPerfect.enabled = true; allPerfect.perfectLapChance = 100;
        Humanizer ap = new Humanizer(allPerfect, 5);
        check(ap.beginLap(), "100 % — идеальный");
        check(ap.cameraJitter()[0] == 0 && ap.cameraJitter()[1] == 0, "идеальный круг — пресет камеры точно");
        System.out.printf(Locale.ROOT, "  круги: %d — идеальных %.1f %% (шанс %d %%, серии %d %% по %d…%d), серий ≥2: %d, самая длинная %d — OK%n",
                lapsN, frac * 100, ls.perfectLapChance, ls.perfectStreakChance, ls.streakMin, ls.streakMax, streaks2, maxStreak);

        // остановки посреди пути и «протупить» — без «Застрял» (окно 30 т, стоим до 200 т)
        HumanSettings st = new HumanSettings();
        st.enabled = true; st.midStopChance = 100; st.midStopMin = 40; st.midStopMax = 80;
        st.afkChance = 100; st.afkMin = 100; st.afkMax = 200; st.hesitateChance = 30; st.hesitateMax = 60;
        int minStood = Integer.MAX_VALUE, maxStood = 0, okRuns = 0;
        for (int i = 0; i < 100; i++) {
            Run r = run(new Humanizer(st, 5000 + i), 60_000, true);
            check(r.panic == null, "остановки, сид " + (5000 + i) + ": " + r.panic);
            check(stopsDone >= 2, "остановки, сид " + (5000 + i) + ": встали " + stopsDone + " раз");
            check(stoodTicks >= st.midStopMin + st.afkMin, "стояли мало: " + stoodTicks);
            minStood = Math.min(minStood, stoodTicks); maxStood = Math.max(maxStood, stoodTicks);
            okRuns++;
        }
        HumanSettings noStops = new HumanSettings();
        noStops.enabled = true; noStops.midStopChance = 0; noStops.afkChance = 0; noStops.hesitateChance = 0;
        Run ns = run(new Humanizer(noStops, 1), 40_000, true);
        check(ns.panic == null && stoodTicks == 0, "без остановок — не стоим");
        System.out.printf(Locale.ROOT, "  остановки: %d проходов с «встать» 40…80 т + «протупить» 100…200 т + заминки до 60 т — "
                + "без паники «Застрял» (окно 30 т), стояли %d…%d т — OK%n", okRuns, minStood, maxStood);

        // запись: остановка только на стоячем кадре
        Humanizer.RecShaper rst = new Humanizer(st, 77).recShaper(500);
        int idx = 0, held = 0;
        for (int tk = 0; tk < 5000 && idx < 500; tk++) {
            boolean idle = idx % 50 < 20;                  // стоячие участки по 20 кадров через каждые 50
            int step = rst.advance(idle, idle, idx);
            if (rst.stopping()) { held++; check(idle, "запись: встали на ходу, кадр " + idx); }
            idx += step;
        }
        check(rst.stops == 1 && rst.afkStops == 1 && held >= st.midStopMin + st.afkMin - 2, "запись: остановки " + rst.stops + "/" + rst.afkStops + ", стояли " + held);

        // взгляд: на остановке гуляет и возвращается, на ходу — только pitch в пределах
        Humanizer.Look look = new Humanizer(ls, 3).look;
        double maxY = 0, yawSum = 0, pitchSum = 0;
        for (int i = 120; i >= 1; i--) {
            float[] d = look.tick(i, false); yawSum += d[0]; pitchSum += d[1];
            maxY = Math.max(maxY, Math.abs(look.yawOff()));
        }
        check(maxY > 0.2 && maxY <= ls.stopLookDeg + 1e-6, "оглядеться: yaw " + maxY);
        for (int i = 0; i < 6; i++) { float[] d = look.tick(0, false); yawSum += d[0]; pitchSum += d[1]; }
        check(Math.abs(look.yawOff()) < 0.1 && Math.abs(yawSum - look.yawOff()) < 1e-3, "после остановки yaw вернулся: " + look.yawOff());
        double backYaw = Math.abs(look.yawOff());
        double maxP = 0;
        for (int i = 0; i < 2000; i++) {
            float[] d = look.tick(0, false); yawSum += d[0];
            check(d[0] == 0 || Math.abs(look.yawOff()) < 0.1, "на ходу yaw не гуляет");
            maxP = Math.max(maxP, Math.abs(look.pitchOff()));
        }
        check(maxP > 0.05 && maxP <= Math.max(ls.lookPitchDeg, 1.0) + 1e-6, "pitch на ходу: " + maxP);
        for (int i = 0; i < 200; i++) look.tick(0, true);
        check(Math.abs(look.pitchOff()) < 0.01 && Math.abs(look.yawOff()) < 1e-3, "идеальный круг — взгляд в 0");
        System.out.printf(Locale.ROOT, "  взгляд: на остановке yaw до %.2f° (допуск %.1f°), вернулся до %.3f°; на ходу pitch до %.2f° (допуск %.1f°), yaw 0 — OK%n",
                maxY, ls.stopLookDeg, backYaw, maxP, ls.lookPitchDeg);

        // чат
        check("ник".equals(com.farmmacro.panic.ChatMatch.hit("эй Vadim123 ты тут?", "vadim123", true, "")), "чат: ник");
        check(com.farmmacro.panic.ChatMatch.hit("привет всем", "vadim123", true, "макро, бот") == null, "чат: мимо");
        check(com.farmmacro.panic.ChatMatch.hit("это БОТ?", "vadim123", false, "макро, бот") != null, "чат: слово");
        check(com.farmmacro.panic.ChatMatch.hit("ab joined", "ab", true, "") == null, "чат: короткий ник не ищем");

        System.out.printf(Locale.ROOT, "  запись: 1000 стоячих кадров → %d (повторов %d, пропусков %d), ЛКМ с кадра %d вместо 5 — OK%n",
                adv, rs.repeats, rs.skips, firstOn);
    }
}
