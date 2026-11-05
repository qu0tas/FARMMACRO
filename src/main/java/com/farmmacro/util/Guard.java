package com.farmmacro.util;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Защита от исключений в коде мода, который вызывает игра каждый кадр/тик.
 * Исключение логируется один раз (со стеком), затем отключается только этот элемент,
 * а не роняется вся игра.
 */
public final class Guard {
    private Guard() {}

    /**
     * Фатальные ошибки JVM (OutOfMemoryError, InternalError…) не глотаем: после них состояние игры
     * недостоверно, и продолжать тик опаснее, чем упасть с понятным crash report.
     * StackOverflowError — исключение: стек уже размотан, это обычная ошибка в одном шаге.
     */
    static void rethrowFatal(Throwable t) {
        if (t instanceof VirtualMachineError fatal && !(t instanceof StackOverflowError)) throw fatal;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("Kaelix/Guard");
    private static final Set<String> DISABLED = ConcurrentHashMap.newKeySet();
    private static final Map<String, Failure> FAILURES = new ConcurrentHashMap<>();

    private static final class Failure {
        long count, lastLog;
        String type = "?";
    }

    private static void failure(String name, Throwable t) {
        Diagnostics.add(Diagnostics.Counter.GUARD_FAILURES);
        // Names are internal constants. Never include arbitrary names/messages in the copied report.
        String key = name != null && name.matches("[a-zA-Z0-9_/-]{1,80}") ? name : "other";
        Failure f;
        synchronized (FAILURES) {
            if (FAILURES.size() >= 127 && !FAILURES.containsKey(key)) key = "other";
            f = FAILURES.computeIfAbsent(key, k -> new Failure());
        }
        synchronized (f) {
            long now = System.nanoTime();
            f.count++;
            String type = t.getClass().getSimpleName();
            f.type = type.matches("[A-Za-z0-9_]{1,80}") ? type : "Throwable";
            if (f.count == 1) {
                f.lastLog = now;
                LOGGER.error("Ошибка в «{}» (далее ограничение журнала 30с)", key, t);
            } else if (now - f.lastLog >= 30_000_000_000L) {
                f.lastLog = now;
                LOGGER.error("Ошибка в «{}»: {}, всего {}", key, f.type, f.count);
            }
        }
    }

    public static String diagnosticSummary() {
        StringBuilder b = new StringBuilder();
        FAILURES.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .limit(128)
                .forEach(e -> {
                    var f = e.getValue();
                    synchronized (f) {
                        b.append(e.getKey())
                                .append(':')
                                .append(f.type)
                                .append('x')
                                .append(f.count)
                                .append(' ');
                    }
                });
        return b.isEmpty() ? "none" : b.toString();
    }

    /** HUD-элемент, который после первого исключения выключается до перезапуска игры. */
    public static HudElement hud(String name, HudElement element) {
        return (g, delta) -> {
            if (DISABLED.contains(name)) return;
            long start = System.nanoTime();
            try {
                element.extractRenderState(g, delta);
            } catch (Throwable t) {
                rethrowFatal(t);
                DISABLED.add(name);
                failure(name, t);
            } finally {
                StepProfiler.record(name, System.nanoTime() - start);
            }
        };
    }

    /**
     * Выполнить шаг, не пропуская исключение наружу.
     * @return true, если шаг прошёл без ошибок
     */
    public static boolean run(String name, Runnable step) {
        long start = System.nanoTime();
        try {
            step.run();
            return true;
        } catch (Throwable t) {
            rethrowFatal(t);
            failure(name, t);
            return false;
        } finally {
            StepProfiler.record(name, System.nanoTime() - start);
        }
    }

    /** Как {@link #run}, но после первой ошибки шаг отключается до перезапуска игры. */
    public static boolean runOrDisable(String name, Runnable step) {
        if (DISABLED.contains(name)) return false;
        long start = System.nanoTime();
        try {
            step.run();
            return true;
        } catch (Throwable t) {
            rethrowFatal(t);
            DISABLED.add(name);
            failure(name, t);
            return false;
        } finally {
            StepProfiler.record(name, System.nanoTime() - start);
        }
    }

    /** Для мест, где нельзя обернуть код в {@link #run} (миксины): ошибка не теряется молча, а попадает в журнал. */
    public static void report(String name, Throwable t) {
        rethrowFatal(t);
        failure(name, t);
    }

    public static boolean isDisabled(String name) {
        return DISABLED.contains(name);
    }

    /** Сколько всего ошибок поймано с запуска (для game-тестов и отчёта). */
    public static long failureCount() {
        long n = 0;
        for (Failure f : FAILURES.values())
            synchronized (f) {
                n += f.count;
            }
        return n;
    }
}
