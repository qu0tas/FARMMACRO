package com.farmmacro.panic;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * v1.11 «Подозрительность» — без зависимостей от игры (stress-тест {@code SuspicionCheck}).
 *
 * Каждое мелкое событие ниже порогов детекторов (сдвиг меньше эпсилона, лаг-откат, слабый толчок, игрок за радиусом,
 * слово в чате без ника, надпись над хотбаром) даёт очки с весом. Очки затухают экспоненциально:
 * вклад события = вес · 0.5^(возраст / полураспад). Сумма ≥ лимита — паника со списком событий.
 * Так ловится косвенное «внешнее наблюдение»: по одному событию ничего не значит, а серия за минуту — уже да.
 * Только главный поток (кроме {@link #lastScore} — volatile, читает кто угодно).
 */
public final class Suspicion {

    /** Событие: тип (для группировки), подробность (последняя — в тексте), вес, время. */
    public record Ev(String type, String detail, double weight, long ms) {}

    public static final int MAX_EVENTS = 512;

    private final ArrayDeque<Ev> events = new ArrayDeque<>();
    /** Последняя посчитанная сумма — для HUD. */
    public volatile double lastScore;

    public void reset() { events.clear(); lastScore = 0; }

    public int size() { return events.size(); }

    /** Добавить событие. @return сумма очков сразу после него */
    public double add(String type, String detail, double weight, long nowMs, double halfLifeSec) {
        if (!(weight > 0)) return score(nowMs, halfLifeSec);
        events.addLast(new Ev(type, detail, weight, nowMs));
        while (events.size() > MAX_EVENTS) events.removeFirst();
        return score(nowMs, halfLifeSec);
    }

    /** Текущая сумма; заодно выкидывает события, от которых осталось меньше 0.1 % веса (10 полураспадов). */
    public double score(long nowMs, double halfLifeSec) {
        double hl = Math.max(0.001, halfLifeSec) * 1000.0, sum = 0;
        for (Iterator<Ev> it = events.iterator(); it.hasNext(); ) {
            Ev e = it.next();
            double age = Math.max(0, nowMs - e.ms());
            if (age > hl * 10) { it.remove(); continue; }
            sum += e.weight() * Math.pow(0.5, age / hl);
        }
        lastScore = sum;
        return sum;
    }

    /** Текст паники, если сумма ≥ лимита, иначе null. */
    public String reasonIfOver(long nowMs, double halfLifeSec, double limit) {
        double s = score(nowMs, halfLifeSec);
        if (s < limit || events.isEmpty()) return null;
        return String.format(Locale.ROOT, "Много мелких аномалий (%.1f ≥ %.0f): ", s, limit) + summary(nowMs, halfLifeSec);
    }

    /** «лаг-откат ×3 (последнее: 0.08 бл), толчок ×1 (…)» — по убыванию вклада. */
    public String summary(long nowMs, double halfLifeSec) {
        double hl = Math.max(0.001, halfLifeSec) * 1000.0;
        Map<String, double[]> sum = new LinkedHashMap<>();
        Map<String, String> last = new LinkedHashMap<>();
        for (Ev e : events) {
            double[] a = sum.computeIfAbsent(e.type(), k -> new double[2]);
            a[0] += e.weight() * Math.pow(0.5, Math.max(0, nowMs - e.ms()) / hl);
            a[1]++;
            if (e.detail() != null && !e.detail().isEmpty()) last.put(e.type(), e.detail());
        }
        StringBuilder b = new StringBuilder();
        sum.entrySet().stream().sorted((x, y) -> Double.compare(y.getValue()[0], x.getValue()[0])).forEach(en -> {
            if (b.length() > 0) b.append(", ");
            b.append(en.getKey()).append(" ×").append((int) en.getValue()[1]);
            String d = last.get(en.getKey());
            if (d != null) b.append(" (").append(d).append(')');
        });
        return b.toString();
    }
}
