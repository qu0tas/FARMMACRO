package com.farmmacro.macro;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * v1.15: многослойные «человеческие» задержки рук — без зависимостей от игры (проверяется в routeStress, DelayCheck).
 * Идея слоёв — skyblock-autofisher (CC0); код свой. Задержка = (база × усталость) + отвлечение:
 * <ol>
 * <li><b>База</b> — усечённый гаусс (среднее ± отклонение в пределах min…max), своя у нажатия ЛКМ/ПКМ, отпускания и смены слота.</li>
 * <li><b>Усталость</b> — медленный множитель за сессию: рост до +fatiguePct % за fatigueRampMin минут
 *     (1 − e^(−t/ramp)) и плавный «дрейф» ± driftPct % (процесс Орнштейна — Уленбека с временем driftMin минут):
 *     соседние задержки похожи, через полчаса — другие.</li>
 * <li><b>Отвлечение</b> — редко (distractChance % событий) + distractMin…distractMax мс (чаще короткие).</li>
 * </ol>
 * В тики (50 мс) переводится со случайным округлением: 70 мс — то 1, то 2 тика, в среднем 1.4.
 */
public final class DelayModel {
    public enum Kind {
        PRESS("нажать", "↓"), RELEASE("отпустить", "↑"), SLOT("слот", "⇄");
        public final String label, mark;
        Kind(String label, String mark) { this.label = label; this.mark = mark; }
    }

    /** Настройки (из ModConfig). Миллисекунды, проценты, минуты. */
    public static final class Params {
        public final double[] mean = {95, 75, 170}, sd = {35, 30, 60}, min = {35, 25, 70}, max = {260, 220, 450};
        public double fatiguePct = 25, fatigueRampMin = 90, driftPct = 8, driftMin = 12;
        public double distractChance = 1.5, distractMinMs = 400, distractMaxMs = 2500;
    }

    /** Одна задержка: слои и итог. */
    public record Sample(Kind kind, String what, double baseMs, double fatigue, double distractMs, double totalMs, int ticks, long atMs) {
        public String line() {
            return String.format(Locale.ROOT, "%s %s %.0f мс = %.0f×%.2f%s → %d т", what, kind.mark, totalMs, baseMs, fatigue,
                    distractMs > 0 ? String.format(Locale.ROOT, " +%.0f отвл.", distractMs) : "", ticks);
        }
    }

    /** Сводка по виду: число, среднее, отклонение, min, max (мс), отвлечений. */
    public record Stats(int n, double mean, double sd, double min, double max, int distracts) {}

    public static final int HISTORY = 64;

    private final SplittableRandom rnd;
    private long sessionStartMs = -1, lastMs = -1;
    private double drift;               // ln(множителя дрейфа)
    private final ArrayDeque<Sample> history = new ArrayDeque<>();
    private int distracts;

    public DelayModel(long seed) { rnd = new SplittableRandom(seed); }

    /** Начать сессию заново (сброс статистики сессии в «Запуске»). */
    public synchronized void resetSession() { sessionStartMs = -1; lastMs = -1; drift = 0; history.clear(); distracts = 0; }

    /** Множитель усталости в момент now (без изменения состояния). */
    public synchronized double fatigue(long nowMs, Params p) {
        if (sessionStartMs < 0) return 1;
        return mult(nowMs - sessionStartMs, p, drift);
    }

    private static double mult(long nowMs, Params p, double drift) {
        double tMin = Math.max(0, nowMs) / 60_000.0;
        double ramp = 1 + p.fatiguePct / 100.0 * (1 - Math.exp(-tMin / Math.max(1e-3, p.fatigueRampMin)));
        return Math.max(0.5, Math.min(3.0, ramp * Math.exp(drift)));
    }

    /** Следующая задержка вида k (what — для журнала/оверлея: «ЛКМ», «ПКМ», «слот 3»). */
    public synchronized Sample next(Kind k, String what, long nowMs, Params p) {
        if (sessionStartMs < 0) { sessionStartMs = nowMs; lastMs = nowMs; drift = 0; }
        // дрейф: Орнштейн — Уленбек по прошедшему времени
        double dtMin = Math.max(0, nowMs - lastMs) / 60_000.0;
        lastMs = nowMs;
        double sigma = Math.log(1 + Math.max(0, p.driftPct) / 100.0);
        if (sigma > 0 && dtMin > 0) {
            double a = Math.exp(-dtMin / Math.max(1e-3, p.driftMin));
            drift = drift * a + sigma * Math.sqrt(1 - a * a) * rnd.nextGaussian();
        } else if (sigma == 0) drift = 0;
        int i = k.ordinal();
        double base = gauss(p.mean[i], p.sd[i], p.min[i], p.max[i]);
        double f = mult(nowMs - sessionStartMs, p, drift);
        double dist = 0;
        if (p.distractChance > 0 && rnd.nextDouble() * 100 < p.distractChance) {
            double u = rnd.nextDouble();
            dist = p.distractMinMs + (Math.max(p.distractMinMs, p.distractMaxMs) - p.distractMinMs) * u * u;   // чаще короткие
            distracts++;
        }
        double total = base * f + dist;
        double tk = total / 50.0;
        int ticks = (int) Math.floor(tk);
        if (rnd.nextDouble() < tk - ticks) ticks++;
        Sample s = new Sample(k, what, base, f, dist, total, ticks, nowMs);
        history.addLast(s);
        while (history.size() > HISTORY) history.removeFirst();
        return s;
    }

    private double gauss(double mean, double sd, double lo, double hi) {
        if (hi < lo) { double t = lo; lo = hi; hi = t; }
        if (!(sd > 0)) return Math.max(lo, Math.min(hi, mean));
        for (int i = 0; i < 64; i++) {
            double v = mean + sd * rnd.nextGaussian();
            if (v >= lo && v <= hi) return v;
        }
        return lo + rnd.nextDouble() * (hi - lo);
    }

    /** Последние n задержек (новые в конце). */
    public synchronized List<Sample> last(int n) {
        List<Sample> all = new ArrayList<>(history);
        return all.subList(Math.max(0, all.size() - n), all.size());
    }

    public synchronized int distracts() { return distracts; }

    /** Сводка по последним (до {@link #HISTORY}) задержкам вида k; k == null — все. */
    public synchronized Stats stats(Kind k) {
        int n = 0, d = 0;
        double s = 0, s2 = 0, mn = Double.MAX_VALUE, mx = 0;
        for (Sample x : history) {
            if (k != null && x.kind != k) continue;
            n++; s += x.totalMs; s2 += x.totalMs * x.totalMs;
            mn = Math.min(mn, x.totalMs); mx = Math.max(mx, x.totalMs);
            if (x.distractMs > 0) d++;
        }
        if (n == 0) return new Stats(0, 0, 0, 0, 0, 0);
        double m = s / n;
        return new Stats(n, m, Math.sqrt(Math.max(0, s2 / n - m * m)), mn, mx, d);
    }
}
