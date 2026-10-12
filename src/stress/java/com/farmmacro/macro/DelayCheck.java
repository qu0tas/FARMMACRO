package com.farmmacro.macro;

import java.util.Locale;

/**
 * v1.15 «Задержки рук (3 слоя)»: проверки {@link DelayModel} и {@link ActionGate} без игры.
 * База — среднее и разброс в пределах, мин/макс соблюдены; усталость растёт со временем; дрейф медленный и в рамках;
 * отвлечения — с заданной частотой; округление в тики в среднем без сдвига; ворота — задержка ровно N тиков,
 * отмена до срока, несколько вызовов в одном тике не ускоряют.
 */
public final class DelayCheck {
    private DelayCheck() {}

    static int checks;
    static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("DelayCheck: " + what);
        checks++;
    }

    static DelayModel.Params quiet() {
        DelayModel.Params p = new DelayModel.Params();
        p.fatiguePct = 0; p.driftPct = 0; p.distractChance = 0;
        return p;
    }

    public static void main() {
        long t0 = System.nanoTime();
        StringBuilder rep = new StringBuilder();

        // ── слой 1: база ──
        DelayModel m = new DelayModel(1);
        DelayModel.Params p = quiet();
        int n = 20000;
        double s = 0, s2 = 0, mn = 1e9, mx = 0, ticks = 0;
        for (int i = 0; i < n; i++) {
            var x = m.next(DelayModel.Kind.PRESS, "ЛКМ", 1000L + i * 50, p);
            s += x.totalMs(); s2 += x.totalMs() * x.totalMs(); mn = Math.min(mn, x.totalMs()); mx = Math.max(mx, x.totalMs());
            ticks += x.ticks();
        }
        double mean = s / n, sd = Math.sqrt(s2 / n - mean * mean);
        // усечённый гаусс 95±35 в [35, 260]: среднее чуть выше 95, разброс чуть меньше 35
        check(Math.abs(mean - 96) < 3, "база: среднее " + mean);
        check(sd > 28 && sd < 36, "база: разброс " + sd);
        check(mn >= 35 && mx <= 260, "база: пределы " + mn + "–" + mx);
        check(Math.abs(ticks / n * 50 - mean) < 2.5, "округление в тики со сдвигом: " + ticks / n * 50 + " против " + mean);
        rep.append(String.format(Locale.ROOT, "база ЛКМ↓ %.1f±%.1f мс (%.0f–%.0f), в тиках %.1f мс; ", mean, sd, mn, mx, ticks / n * 50));

        // точное среднее без разброса: 75 мс = 1.5 т → поровну 1 и 2 тика
        DelayModel.Params fixed = quiet();
        fixed.sd[1] = 0;
        int one = 0, two = 0;
        for (int i = 0; i < 4000; i++) { int t = m.next(DelayModel.Kind.RELEASE, "ЛКМ", 1000L + i, fixed).ticks(); if (t == 1) one++; else if (t == 2) two++; }
        check(one + two == 4000 && Math.abs(one - two) < 250, "1.5 т → 1 и 2 поровну: " + one + "/" + two);

        // ── слой 2: усталость и дрейф ──
        DelayModel f = new DelayModel(2);
        DelayModel.Params fp = quiet();
        fp.fatiguePct = 25; fp.fatigueRampMin = 90;
        f.next(DelayModel.Kind.SLOT, "слот 1", 0, fp);
        double f0 = f.fatigue(0, fp), f30 = f.fatigue(30 * 60_000L, fp), f90 = f.fatigue(90 * 60_000L, fp), f600 = f.fatigue(600 * 60_000L, fp);
        check(Math.abs(f0 - 1) < 1e-9, "усталость в начале " + f0);
        check(f30 > 1.05 && f30 < f90 && f90 < f600, "усталость растёт: " + f30 + " " + f90 + " " + f600);
        check(Math.abs(f90 - (1 + 0.25 * (1 - Math.exp(-1)))) < 1e-6, "усталость за 90 мин " + f90);
        check(Math.abs(f600 - 1.25) < 0.01, "усталость к пределу " + f600);
        // средняя задержка через 3 часа длиннее, чем в начале
        DelayModel g = new DelayModel(3);
        double early = 0, late = 0;
        for (int i = 0; i < 3000; i++) early += g.next(DelayModel.Kind.PRESS, "ЛКМ", i * 100L, fp).totalMs();
        for (int i = 0; i < 3000; i++) late += g.next(DelayModel.Kind.PRESS, "ЛКМ", 180 * 60_000L + i * 100L, fp).totalMs();
        check(late / early > 1.17 && late / early < 1.30, "через 3 ч длиннее в " + late / early);
        rep.append(String.format(Locale.ROOT, "усталость ×%.2f/30 мин, ×%.2f/90, ×%.2f/10 ч, 3 ч: ×%.2f; ", f30, f90, f600, late / early));

        DelayModel d = new DelayModel(4);
        DelayModel.Params dp = quiet();
        dp.driftPct = 8; dp.driftMin = 12;
        double dmin = 9, dmax = 0, maxStep = 0, prev = Double.NaN;
        for (int i = 0; i < 6000; i++) {                       // 10 ч, событие раз в 6 с
            long now = i * 6000L;
            d.next(DelayModel.Kind.PRESS, "ЛКМ", now, dp);
            double fx = d.fatigue(now, dp);
            dmin = Math.min(dmin, fx); dmax = Math.max(dmax, fx);
            if (!Double.isNaN(prev)) maxStep = Math.max(maxStep, Math.abs(fx - prev));
            prev = fx;
        }
        check(dmax - dmin > 0.08, "дрейф есть: " + dmin + "–" + dmax);
        check(dmin > 0.6 && dmax < 1.45, "дрейф в рамках: " + dmin + "–" + dmax);
        check(maxStep < 0.06, "дрейф плавный: шаг " + maxStep);
        rep.append(String.format(Locale.ROOT, "дрейф ×%.2f…×%.2f, шаг ≤%.3f; ", dmin, dmax, maxStep));

        // ── слой 3: отвлечения ──
        DelayModel a = new DelayModel(5);
        DelayModel.Params ap = quiet();
        ap.distractChance = 1.5; ap.distractMinMs = 400; ap.distractMaxMs = 2500;
        int dn = 0; double dlo = 1e9, dhi = 0;
        for (int i = 0; i < 40000; i++) {
            var x = a.next(DelayModel.Kind.PRESS, "ЛКМ", i * 50L, ap);
            if (x.distractMs() > 0) { dn++; dlo = Math.min(dlo, x.distractMs()); dhi = Math.max(dhi, x.distractMs()); }
        }
        double rate = dn * 100.0 / 40000;
        check(Math.abs(rate - 1.5) < 0.25, "частота отвлечений " + rate + " %");
        check(dlo >= 400 && dhi <= 2500, "отвлечения в пределах " + dlo + "–" + dhi);
        check(a.distracts() == dn, "счётчик отвлечений");
        rep.append(String.format(Locale.ROOT, "отвлечений %.2f %% (%.0f–%.0f мс); ", rate, dlo, dhi));
        ap.distractChance = 0;
        DelayModel z = new DelayModel(6);
        boolean none = true;
        for (int i = 0; i < 5000; i++) none &= z.next(DelayModel.Kind.SLOT, "слот", i, ap).distractMs() == 0;
        check(none, "шанс 0 — без отвлечений");

        // история и сброс
        check(a.last(6).size() == 6 && a.stats(DelayModel.Kind.PRESS).n() == DelayModel.HISTORY, "история");
        a.resetSession();
        check(a.last(6).isEmpty() && a.distracts() == 0 && a.fatigue(1_000_000, ap) == 1, "сброс сессии");

        // ── ActionGate: кнопка ──
        ActionGate.Button b = new ActionGate.Button();
        b.reset(false);
        long tk = 100;
        check(b.update(true, tk, () -> 3) == false, "нажатие с задержкой 3: тик 0");
        check(b.update(true, tk, () -> 99) == false, "второй вызов в том же тике не ускоряет");
        check(b.update(true, ++tk, () -> 99) == false, "тик 1");
        check(b.update(true, ++tk, () -> 99) == false, "тик 2");
        check(b.update(true, ++tk, () -> 99) == true, "ровно через 3 тика — нажата");
        check(b.update(true, tk, () -> 99) == true, "держится");
        // отпустить с задержкой 2, но передумать до срока
        check(b.update(false, ++tk, () -> 2) == true, "отпускание ждёт");
        check(b.update(true, ++tk, () -> 7) == true, "передумал — остаётся нажатой, без новой задержки");
        check(b.left() == 0, "отмена: ждать нечего");
        check(b.update(false, ++tk, () -> 0) == false, "задержка 0 — сразу");
        b.reset(true);
        check(b.out(), "reset ставит сразу");
        // ── ActionGate: слот ──
        ActionGate.Slot sl = new ActionGate.Slot();
        long st = 0;
        check(sl.update(0, 3, st, () -> 2) == 0, "слот ждёт");
        check(sl.update(0, 3, ++st, () -> 99) == 0, "слот тик 1");
        check(sl.update(0, 3, ++st, () -> 99) == 3, "слот через 2 тика");
        check(sl.update(3, 3, ++st, () -> 99) == 3, "слот стоит");
        check(sl.update(3, 5, ++st, () -> 1) == 3 && sl.update(3, 6, st, () -> 0) == 6, "сменил желание — новая задержка");

        rep.append(String.format(Locale.ROOT, "ворота OK; %d проверок, %.0f мс", checks, (System.nanoTime() - t0) / 1e6));
        System.out.println("DelayCheck: " + rep);
    }
}
