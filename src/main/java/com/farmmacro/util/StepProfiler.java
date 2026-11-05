package com.farmmacro.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Время шагов {@link Guard}: сколько раз вызван, сколько занял в сумме и максимум за один вызов.
 * Имена — внутренние константы вида {@code panic/effects}; не больше {@value #MAX_NAMES} имён, остальное
 * сливается в {@code other}. Только память, в отчёт попадают имена шагов и числа.
 */
@NullMarked
public final class StepProfiler {
    static final int MAX_NAMES = 128;
    private static final String OTHER = "other";
    private static final Map<String, Stat> STATS = new ConcurrentHashMap<>();

    private StepProfiler() {}

    /** Один шаг: имя, вызовы, сумма и максимум в наносекундах. */
    public record Row(String name, long count, long totalNanos, long maxNanos) {
        public double avgMillis() {
            return count == 0 ? 0 : totalNanos / 1e6 / count;
        }

        public double maxMillis() {
            return maxNanos / 1e6;
        }
    }

    private static final class Stat {
        final LongAdder count = new LongAdder();
        final LongAdder total = new LongAdder();
        final AtomicLong max = new AtomicLong();
    }

    static void record(@Nullable String name, long nanos) {
        if (name == null) name = OTHER;
        Stat s = STATS.get(name);
        if (s == null) s = STATS.computeIfAbsent(safeName(name), k -> new Stat());
        s.count.increment();
        s.total.add(nanos);
        s.max.accumulateAndGet(nanos, Math::max);
    }

    private static String safeName(String name) {
        if (!name.matches("[a-zA-Z0-9_/.-]{1,80}")) return OTHER;
        return STATS.size() >= MAX_NAMES ? OTHER : name;
    }

    /** Самые дорогие шаги по суммарному времени. */
    public static List<Row> top(int limit) {
        List<Row> rows = new ArrayList<>();
        STATS.forEach((name, s) -> rows.add(new Row(name, s.count.sum(), s.total.sum(), s.max.get())));
        rows.sort(Comparator.comparingLong(Row::totalNanos).reversed().thenComparing(Row::name));
        return rows.size() > limit ? List.copyOf(rows.subList(0, limit)) : rows;
    }

    /** Строка для отчёта/экрана: {@code panic/effects 0.012/1.3мс×1200}. */
    public static String summary(int limit) {
        List<Row> rows = top(limit);
        if (rows.isEmpty()) return "нет данных";
        StringBuilder b = new StringBuilder();
        for (Row r : rows) {
            if (!b.isEmpty()) b.append(" · ");
            b.append(
                    String.format(Locale.ROOT, "%s %.3f/%.2fms×%d", r.name(), r.avgMillis(), r.maxMillis(), r.count()));
        }
        return b.toString();
    }

    public static void reset() {
        STATS.clear();
    }
}
