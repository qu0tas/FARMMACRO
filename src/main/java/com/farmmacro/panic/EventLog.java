package com.farmmacro.panic;

import com.farmmacro.config.ModConfig;
import com.farmmacro.macro.MacroManager;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * v1.11 «Журнал событий»: {@code config/farmmacro/logs/events-ГГГГ-ММ-ДД.log}.
 * Строка: {@code время | мс эпохи | тип | данные | pos x y z | круг N}. Пишет флаги, паники, старт/стоп, круги, остановки,
 * совпадения чата, очки подозрительности, пакеты ниже эпсилона.
 *
 * Потоки: {@link #log} зовётся из главного потока — строка собирается сразу (позиция, круг) и кладётся
 * в {@link ConcurrentLinkedQueue}; один фоновый поток {@code farmmacro-event-log} забирает пачками и пишет
 * ({@link EventLogWriter}, ротация по 5 МБ). Тик игры диск не трогает. Очередь ограничена — при переполнении
 * строки отбрасываются со счётчиком.
 */
public final class EventLog {
    private EventLog() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("FarmMacro/EventLog");
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int QUEUE_MAX = 20_000;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static final ConcurrentLinkedQueue<String[]> QUEUE = new ConcurrentLinkedQueue<>();  // {день, строка}
    private static final AtomicInteger SIZE = new AtomicInteger();
    private static final AtomicInteger DROPPED = new AtomicInteger();
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static volatile Thread writer;

    /** Записать событие (главный поток). type — короткое слово заглавными: FLAG, PANIC, START, STOP, LAP… */
    public static void log(String type, String data) {
        if (!ModConfig.INSTANCE.eventLogEnabled) return;
        try {
            LocalDateTime now = LocalDateTime.now();
            long ms = System.currentTimeMillis();
            String pos = "-";
            String lap = "-";
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null)
                pos = String.format(Locale.ROOT, "%.2f %.2f %.2f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
            if (MacroManager.INSTANCE.isPlaying()) lap = Integer.toString(MacroManager.INSTANCE.getLoopsDone() + 1);
            String line = TIME.format(now) + " | " + ms + " | " + type + " | " + clean(data) + " | pos " + pos + " | круг " + lap;
            if (SIZE.get() >= QUEUE_MAX) { DROPPED.incrementAndGet(); return; }
            QUEUE.add(new String[]{now.toLocalDate().toString(), line});
            SIZE.incrementAndGet();
            ensureWriter();
            Thread w = writer;
            if (w != null) LockSupport.unpark(w);
        } catch (Throwable t) {
            LOGGER.debug("Журнал событий: не записал {}", type, t);
        }
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\n', ' ').replace('\r', ' ').replace('|', '/');
    }

    private static void ensureWriter() {
        if (!STARTED.compareAndSet(false, true)) return;
        Thread t = new Thread(EventLog::run, "farmmacro-event-log");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        writer = t;
        t.start();
        Runtime.getRuntime().addShutdownHook(new Thread(EventLog::drain, "farmmacro-event-log-flush"));
    }

    private static void run() {
        while (true) {
            try {
                drain();
                LockSupport.parkNanos(500_000_000L);       // будит log(); иначе раз в полсекунды
            } catch (Throwable t) {
                LOGGER.warn("Журнал событий: ошибка записи", t);
                LockSupport.parkNanos(5_000_000_000L);
            }
        }
    }

    private static final Object WRITE_LOCK = new Object();

    /** Забрать всё из очереди и записать (поток-писатель или выход из игры). */
    private static void drain() {
        synchronized (WRITE_LOCK) {
            EventLogWriter w = new EventLogWriter(ModConfig.modDir().resolve("logs"), MAX_BYTES);
            List<String> batch = new ArrayList<>();
            String day = null;
            String[] e;
            while ((e = QUEUE.poll()) != null) {
                SIZE.decrementAndGet();
                if (day != null && !day.equals(e[0])) { write(w, batch, day); batch.clear(); }
                day = e[0];
                batch.add(e[1]);
            }
            int dropped = DROPPED.getAndSet(0);
            if (dropped > 0 && day != null) batch.add("! пропущено строк (очередь переполнена): " + dropped);
            if (day != null) write(w, batch, day);
        }
    }

    private static void write(EventLogWriter w, List<String> batch, String day) {
        try {
            w.write(batch, LocalDate.parse(day));
        } catch (Exception ex) {
            LOGGER.warn("Журнал событий: не удалось записать {} строк", batch.size(), ex);
        }
    }
}
