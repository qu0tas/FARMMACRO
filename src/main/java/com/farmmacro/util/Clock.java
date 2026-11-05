package com.farmmacro.util;

import java.util.function.LongSupplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Часы для интервалов, таймеров и анимаций.
 *
 * <p>{@link #millis()} монотонны (на {@link System#nanoTime()}), поэтому перевод системного времени,
 * синхронизация NTP или переход на летнее время не обнуляют таймеры, подтверждения «Точно удалить?» и
 * лимит длительности запуска. Точка отсчёта выровнена по {@link System#currentTimeMillis()} при загрузке
 * класса — значения остаются сопоставимы с сохранёнными метками времени, пока системные часы не переводят.
 *
 * <p>{@link #wallMillis()} — настоящее время: им пишутся метки в файлах (createdAt, «когда»).
 * Модули паники, Humanizer и BanwaveWatch этим классом намеренно не тронуты.
 */
@NullMarked
public final class Clock {
    private static final long BASE_WALL = System.currentTimeMillis();
    private static final long BASE_NANO = System.nanoTime();
    private static volatile @Nullable LongSupplier testSource;

    private Clock() {}

    /** Монотонные миллисекунды (для разностей «сейчас − тогда»). */
    public static long millis() {
        LongSupplier test = testSource;
        if (test != null) return test.getAsLong();
        return BASE_WALL + (System.nanoTime() - BASE_NANO) / 1_000_000L;
    }

    /** Системное время для сохраняемых меток. */
    public static long wallMillis() {
        return System.currentTimeMillis();
    }

    /** Только для тестов: подменить источник {@link #millis()}; {@code null} — вернуть настоящий. */
    static void setTestSource(@Nullable LongSupplier source) {
        testSource = source;
    }
}
