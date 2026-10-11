package com.farmmacro.macro;

import com.farmmacro.camera.MouseTurn;

import java.util.Locale;
import java.util.SplittableRandom;

/**
 * v1.11.1 вне игры: гаусс (среднее, отклонение, пределы), «без повторов подряд», старые настройки — равномерно,
 * порядок на углах по весам (шаг назад/вбок не дальше cornerSlop), микропаузы без «Застрял», поворот «как мышь».
 */
public final class HumanizeGaussCheck {
    private static void check(boolean ok, String what) { if (!ok) throw new IllegalStateException("HumanizeGaussCheck: " + what); }

    public static void main() {
        // ── гаусс: среднее/отклонение и пределы ──
        HumanSettings hs = new HumanSettings();
        hs.enabled = true;
        Humanizer h = new Humanizer(hs, 5);
        int n = 200_000;
        double sum = 0, sq = 0, lo = 1e9, hi = -1e9;
        for (int i = 0; i < n; i++) {
            double v = h.gauss(10, 3, 0, 100);
            sum += v; sq += v * v; lo = Math.min(lo, v); hi = Math.max(hi, v);
        }
        double mean = sum / n, sd = Math.sqrt(sq / n - mean * mean);
        check(Math.abs(mean - 10) < 0.05 && Math.abs(sd - 3) < 0.05, String.format(Locale.ROOT, "гаусс 10±3: %.3f ± %.3f", mean, sd));
        check(lo >= 0 && hi <= 100, "гаусс вышел за пределы");
        double tlo = 1e9, thi = -1e9;
        for (int i = 0; i < n; i++) { double v = h.gauss(4, 5, 2, 6); tlo = Math.min(tlo, v); thi = Math.max(thi, v); }
        check(tlo >= 2 && thi <= 6, "усечённый гаусс вышел за 2…6");
        check(h.gauss(7, 0, 0, 5) == 5, "sd 0 — среднее в пределах");

        // ── старые настройки: равномерно, пределы как раньше ──
        HumanSettings old = HumanSettings.sanitize(new com.google.gson.Gson().fromJson("{\"enabled\":true,\"gapMax\":3,\"midStopMin\":10,\"midStopMax\":40}", HumanSettings.class));
        check(!old.dist(HumanSettings.Var.GAP).isGauss() && !old.cornerWeighted && old.microPauseChance == 0, "старый файл — равномерно, как раньше");
        Humanizer ho = new Humanizer(old, 9);
        int[] cnt = new int[4];
        for (int i = 0; i < 30_000; i++) { int v = ho.draw(HumanSettings.Var.GAP, 1, old.gapMax); check(v >= 1 && v <= 3, "равномерное вне 1…3"); cnt[v]++; }
        for (int v = 1; v <= 3; v++) check(Math.abs(cnt[v] / 30_000.0 - 1 / 3.0) < 0.03, "равномерное 1…3 неравномерно: " + cnt[v]);

        // ── гаусс в настройках + без повторов подряд ──
        HumanSettings g = new HumanSettings();
        g.enabled = true;
        for (HumanSettings.Var v : HumanSettings.Var.values()) g.gauss(v);
        HumanSettings.sanitize(g);
        String json = new com.google.gson.Gson().toJson(g);
        HumanSettings back = HumanSettings.sanitize(new com.google.gson.Gson().fromJson(json, HumanSettings.class));
        check(back.dist(HumanSettings.Var.STOP).isGauss() && back.dist(HumanSettings.Var.STOP).max == 70, "распределения не пережили json");
        Humanizer hg = new Humanizer(g, 11);
        StringBuilder rep = new StringBuilder();
        for (HumanSettings.Var v : HumanSettings.Var.values()) {
            HumanSettings.Dist d = g.dist(v);
            int prev = Integer.MIN_VALUE, same = 0, m = 20_000;
            for (int i = 0; i < m; i++) {
                int x = hg.draw(v, 0, 0);
                check(x >= Math.round(d.min) && x <= Math.round(d.max), v + ": " + x + " вне " + d.min + "…" + d.max);
                if (x == prev) same++;
                prev = x;
            }
            // три и больше вариантов — ни одного повтора подряд; два варианта — только редкие (не строгое чередование)
            if (Math.round(d.max) - Math.round(d.min) >= 2) check(same == 0, v + ": повторов подряд " + same);
            else check(same < m * 0.05, v + ": повторов подряд " + same);
            rep.append(v.name().toLowerCase(Locale.ROOT)).append(' ').append(same).append(' ');
        }
        // без правила — повторы есть
        g.noRepeat = false;
        Humanizer hn = new Humanizer(g, 11);
        int prev = -1, sameNo = 0;
        for (int i = 0; i < 20_000; i++) { int x = hn.draw(HumanSettings.Var.GAP, 0, 0); if (x == prev) sameNo++; prev = x; }
        check(sameNo > 1000, "без правила повторы должны быть: " + sameNo);
        System.out.printf(Locale.ROOT, "%nГаусс (v1.11.1): 10±3 → %.2f±%.2f, усечение 2…6 соблюдено; старые файлы — равномерно 1…3 (%d/%d/%d); "
                + "без повторов подряд (повторов на 20000): %s; без правила у зазора %d — OK%n", mean, sd, cnt[1], cnt[2], cnt[3], rep.toString().trim(), sameNo);

        // круги: длина неидеального круга не повторяет прошлую подряд (хвост 1…3 тика на последней точке)
        HumanSettings lt = new HumanSettings(); lt.noRepeat = true;
        Humanizer hl = new Humanizer(lt, 5);
        int prevLap = -1, lapSame = 0;
        java.util.Random lr = new java.util.Random(9);
        for (int i = 0; i < 10_000; i++) {
            int t = 800 + lr.nextInt(3);                     // нарочно узкий разброс — совпадения часты
            int tot = t + hl.lapTail(t);
            if (tot == prevLap) lapSame++;
            prevLap = tot;
        }
        check(lapSame == 0 && hl.lapTails > 1000, "круги одной длины подряд: " + lapSame + " (хвостов " + hl.lapTails + ")");
        lt.noRepeat = false;
        Humanizer hl2 = new Humanizer(lt, 5);
        check(hl2.lapTail(800) == 0 && hl2.lapTail(800) == 0, "без правила хвост не нужен");
        System.out.printf(Locale.ROOT, "Круги: 10000 с длиной 800…802 т — подряд одинаковых 0, удлинено на 1…3 т: %d — OK%n", hl.lapTails);

        // v1.11.2: «оглядеться»/взгляд на ходу возвращаются ровно в 0; «Камера точно» — смещений нет совсем
        {
            HumanSettings lk = new HumanSettings(); lk.enabled = true; lk.lookPitchDeg = 0.4; lk.stopLookDeg = 3;
            Humanizer hk = new Humanizer(lk, 77);
            for (int t = 0; t < 2000; t++) hk.look.tick(t % 400 < 120 ? 120 - t % 400 : 0, false);
            int backT = -1;
            for (int t = 0; t < 400; t++) { hk.look.tick(0, true); if (hk.look.atZero()) { backT = t; break; } }
            check(backT >= 0, "взгляд не вернулся ровно в 0");
            lk.cameraExact = true;
            Humanizer he2 = new Humanizer(lk, 78);
            for (int t = 0; t < 3000; t++) {
                float[] d = he2.look.tick(t % 300 < 100 ? 100 - t % 300 : 0, false);
                check(d[0] == 0 && d[1] == 0 && he2.look.atZero(), "«Камера точно»: камера сдвинулась на тике " + t);
            }
            check(he2.cameraJitter()[0] == 0 && he2.cameraJitter()[1] == 0, "«Камера точно»: пресет ±");
            System.out.printf(Locale.ROOT, "Камера: смещение возвращается ровно в 0 за %d т; «Камера точно» — 3000 тиков без сдвига — OK%n", backT);
        }

        // v1.11.2: старт у края блока (0.4 бл от линии) — поправка к линии не «угол»: шаг/перекрытие не уводят за линию
        {
            HumanSettings e = new HumanSettings();
            e.enabled = true; e.cornerWeighted = true; e.cornerChance = 100; e.weightOverlap = 40; e.weightGap = 0; e.weightStep = 60; e.stepMax = 4;
            e.overlapChance = 100; e.overlapMax = 4;
            HumanSettings.sanitize(e);
            double worstCross = 0, plainCross;
            HumanizerCheck.startSide = 0.4;
            HumanizerCheck.run(null, 20_000);
            plainCross = -HumanizerCheck.firstSegMin;
            for (int i = 0; i < 300; i++) {
                Humanizer he = new Humanizer(e, 7000 + i);
                he.beginLap();
                HumanizerCheck.Run r = HumanizerCheck.run(he, 60_000, true);
                check(r.panic() == null, "старт у края, сид " + (7000 + i) + ": " + r.panic());
                worstCross = Math.max(worstCross, -HumanizerCheck.firstSegMin);
            }
            HumanizerCheck.startSide = 0;
            check(worstCross <= Math.max(0, plainCross) + 0.05, String.format(Locale.ROOT,
                    "старт у края 0.40 бл: случайность увела за линию на %.2f бл (без случайности %.2f)", worstCross, plainCross));
            System.out.printf(Locale.ROOT, "Старт у края (0.40 бл от линии), шаг/перекрытие 100%%, 300 сидов: за линию максимум %.2f бл "
                    + "(без случайности %.2f) — OK%n", Math.max(0, worstCross), Math.max(0, plainCross));
        }

        // ── углы по весам: шаг назад/вбок, микропаузы — маршрут 20×20 × 200 сидов ──
        HumanSettings c = new HumanSettings();
        c.enabled = true;
        c.cornerWeighted = true; c.cornerChance = 100; c.weightOverlap = 30; c.weightGap = 20; c.weightStep = 50; c.stepMax = 4;
        c.microPauseChance = 60;
        c.gauss(HumanSettings.Var.STEP); c.gauss(HumanSettings.Var.OVERLAP); c.gauss(HumanSettings.Var.GAP);
        c.gauss(HumanSettings.Var.STOP); c.gauss(HumanSettings.Var.HESITATE); c.hesitateChance = 10;
        HumanSettings.sanitize(c);
        HumanizerCheck.Run plain = HumanizerCheck.run(null, 20_000);
        int steps = 0, micros = 0, ovs = 0, gaps = 0, tmin = Integer.MAX_VALUE, tmax = 0;
        double worst = 0;
        for (int i = 0; i < 200; i++) {
            Humanizer hh = new Humanizer(c, 3000 + i);
            hh.beginLap();
            HumanizerCheck.Run r = HumanizerCheck.run(hh, 60_000, true);
            check(r.panic() == null, "углы/микропаузы, сид " + (3000 + i) + ": " + r.panic() + " | клавиши: "
                    + r.keys().substring(Math.max(0, r.keys().length() - 240)));
            worst = Math.max(worst, r.maxOff());
            steps += HumanizerCheck.lastSteps; micros += HumanizerCheck.lastMicros; ovs += r.overlaps(); gaps += r.gaps();
            tmin = Math.min(tmin, r.ticks()); tmax = Math.max(tmax, r.ticks());
        }
        check(steps > 0 && micros > 0 && ovs > 0 && gaps > 0, "не было шагов/микропауз/перекрытий/зазоров");
        check(worst <= Math.max(plain.maxOff(), c.cornerSlop) + 0.05, String.format(Locale.ROOT, "шаг на углу ушёл на %.2f бл", worst));
        System.out.printf(Locale.ROOT, "Углы по весам + микропаузы 60%%: 200 сидов — без паники (окно «Застрял» 30 т), тиков %d…%d (без случайности %d), "
                + "шагов %d, перекрытий %d, зазоров %d, микропауз %d, макс. уход %.2f бл (допуск %.2f) — OK%n",
                tmin, tmax, plain.ticks(), steps, ovs, gaps, micros, worst, c.cornerSlop);

        // ── поворот «как мышь» ──
        SplittableRandom r = new SplittableRandom(1);
        double worstErr = 0, worstOv = 0;
        int round = 0, turns = 20_000;
        double[] sens = {0.0, 0.1, 0.25, 0.5, 0.75, 1.0};
        for (int i = 0; i < turns; i++) {
            double s = sens[i % sens.length];
            // старт как у игрока: либо ровный, либо с мышью
            float y0 = (float) (i % 3 == 0 ? Math.rint(r.nextDouble(-180, 180)) : r.nextDouble(-180, 180));
            float p0 = (float) (i % 3 == 0 ? Math.rint(r.nextDouble(-60, 60)) : r.nextDouble(-60, 60));
            float ty = (float) (Math.rint(r.nextDouble(-1800, 1800)) / 10), tp = (float) (Math.rint(r.nextDouble(-600, 600)) / 10);
            double dy = wrap(ty - y0), dp = tp - p0;
            MouseTurn mt = new MouseTurn(y0, p0, dy, dp, s, r.nextDouble(30, 600), 0.03, r);
            float y = y0, p = p0;
            double cy = 0;                                      // сколько повернули по yaw (без заворота через ±180)
            double len = Math.hypot(dy, dp), ux = len > 0 ? dy / len : 0, uz = len > 0 ? dp / len : 0, beyond = 0;
            double raw = mt.step / 0.15;
            for (int f = 0; f < 100_000 && !mt.finished(); f++) {
                long[] d = mt.frame(1 / 60.0 + r.nextDouble(0, 0.01));
                // как Entity.turn: float, ×0.15f
                float y1 = y + (float) (d[0] * raw) * 0.15f;
                cy += y1 - y; y = y1; p += (float) (d[1] * raw) * 0.15f;
                p = Math.max(-90f, Math.min(90f, p));
                beyond = Math.max(beyond, cy * ux + (p - p0) * uz);
            }
            beyond -= cy * ux + (p - p0) * uz;      // перелёт — дальше итоговой точки, потом доводка назад
            check(mt.finished(), "поворот не закончился");
            double err = Math.hypot(wrap(ty - y), tp - p);
            worstErr = Math.max(worstErr, err);
            if (len > 1) worstOv = Math.max(worstOv, beyond / len);
            if (isRound(y) && isRound(p)) round++;
            check(err <= 0.2, String.format(Locale.ROOT, "как мышь: итог %.3f° от пресета (чувств. %.2f)", err, s));
            check(len <= 1 || beyond <= len * 0.03 + 1e-3, String.format(Locale.ROOT, "перелёт %.2f%%", beyond / len * 100));
            check(!(isRound(y) && isRound(p)), String.format(Locale.ROOT, "ровные углы %.4f / %.4f", y, p));
        }
        System.out.printf(Locale.ROOT, "Поворот «как мышь»: %d поворотов, чувствительность 0…200%%: итог до %.3f° от пресета (допуск 0.2°), "
                + "перелёт до %.2f%% (допуск 3%%), ровных углов %d, шаг при 100%% %.4f° — OK%n",
                turns, worstErr, worstOv * 100, round, MouseTurn.stepDeg(0.5));
    }

    static double wrap(double d) { d %= 360; if (d >= 180) d -= 360; if (d < -180) d += 360; return d; }
    /** Кратно 0.1° (как пресет). */
    static boolean isRound(float a) { double t = a * 10.0; return Math.abs(t - Math.rint(t)) < 0.04; }
}
