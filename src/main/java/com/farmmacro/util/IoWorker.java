package com.farmmacro.util;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Один фоновый поток для записи файлов, чтобы тик и кадр не ждали диск.
 *
 * <p>Задачи с одинаковым ключом схлопываются: если конфиг сохраняют 30 раз за секунду (ползунок), на диск
 * попадёт последнее состояние. Ошибка ввода-вывода повторяется с паузами {@link #BACKOFF_MS}; если за это
 * время пришла более новая задача с тем же ключом, старая бросается. {@link #flush(long)} дожидается очереди
 * (при выходе из игры и перед чтением того же файла).
 */
@NullMarked
public final class IoWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger("Kaelix/IO");
    static final long[] BACKOFF_MS = {100, 500, 2000};

    /** Запись, которая может бросить {@link IOException}. */
    @FunctionalInterface
    public interface IoTask {
        void run() throws IOException;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long ms) throws InterruptedException;
    }

    private static final Object LOCK = new Object();
    private static final Map<String, IoTask> PENDING = new LinkedHashMap<>();
    private static int inFlight;
    private static @Nullable Thread thread;
    private static final AtomicLong DONE = new AtomicLong(), RETRIES = new AtomicLong(), FAILED = new AtomicLong();
    private static volatile String lastError = "нет";

    private IoWorker() {}

    /** Поставить запись в очередь; более старая запись с тем же ключом, ещё не начатая, заменяется. */
    public static void submitLatest(String key, IoTask task) {
        synchronized (LOCK) {
            PENDING.remove(key);
            PENDING.put(key, task);
            if (thread == null) {
                Thread t = new Thread(IoWorker::loop, "Kaelix-IO");
                t.setDaemon(true);
                t.start();
                thread = t;
            }
            LOCK.notifyAll();
        }
    }

    /** Дождаться пустой очереди. @return false — не успели за {@code timeoutMs}. */
    public static boolean flush(long timeoutMs) {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        synchronized (LOCK) {
            while (!PENDING.isEmpty() || inFlight > 0) {
                long left = (deadline - System.nanoTime()) / 1_000_000L;
                if (left <= 0) return false;
                try {
                    LOCK.wait(left);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return true;
    }

    /** Для Diagnostics: только числа и имя класса последней ошибки. */
    public static String technicalState() {
        int queued;
        synchronized (LOCK) {
            queued = PENDING.size() + inFlight;
        }
        return "queue=" + queued + " done=" + DONE.get() + " retries=" + RETRIES.get() + " failed=" + FAILED.get()
                + " last=" + lastError;
    }

    private static void loop() {
        while (true) {
            String key;
            IoTask task;
            synchronized (LOCK) {
                while (PENDING.isEmpty()) {
                    try {
                        LOCK.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                Iterator<Map.Entry<String, IoTask>> it = PENDING.entrySet().iterator();
                Map.Entry<String, IoTask> next = it.next();
                it.remove();
                key = next.getKey();
                task = next.getValue();
                inFlight++;
            }
            try {
                runWithRetry(key, task, Thread::sleep);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } finally {
                synchronized (LOCK) {
                    inFlight--;
                    LOCK.notifyAll();
                }
            }
        }
    }

    /** @return true — записано; false — бросили (все попытки неудачны или пришла более новая задача). */
    static boolean runWithRetry(String key, IoTask task, Sleeper sleeper) throws InterruptedException {
        for (int attempt = 0; ; attempt++) {
            try {
                task.run();
                DONE.incrementAndGet();
                return true;
            } catch (IOException | RuntimeException e) {
                lastError = e.getClass().getSimpleName();
                if (attempt >= BACKOFF_MS.length) {
                    FAILED.incrementAndGet();
                    LOGGER.error("Не удалось записать «{}» после {} попыток", key, attempt + 1, e);
                    return false;
                }
                RETRIES.incrementAndGet();
                LOGGER.warn("Запись «{}» не удалась ({}), повтор через {} мс", key, lastError, BACKOFF_MS[attempt]);
                sleeper.sleep(BACKOFF_MS[attempt]);
                synchronized (LOCK) {
                    if (PENDING.containsKey(key)) return false; // новее уже в очереди — она и запишет
                }
            }
        }
    }
}
