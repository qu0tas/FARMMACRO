package com.farmmacro.panic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Системный вывод звука (javax.sound) в ОДНОМ фоновом потоке с очередью.
 *
 *  • Главный поток только кладёт задание в очередь ({@link #submit}) — без I/O и декодирования.
 *  • Частота дискретизации всегда целая (тон меняется ресэмплингом в {@link SoundDecoder#render}).
 *  • Линия закрывается в finally, есть таймаут на запись и на доигрывание (без line.drain()).
 *  • Любая ошибка/Throwable → {@link #isBroken()} = true, задание и дальнейшие звуки идут через движок игры.
 *  • Если поток завис дольше таймаута, следующий submit тоже помечает системный звук сломанным.
 */
final class SystemAudioPlayer {
    private SystemAudioPlayer() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("FarmMacro/Sound");

    /** fallback вызывается из потока проигрывателя, он сам должен перекинуть работу в главный поток. */
    record Job(SoundDecoder.Clip clip, float volume, float pitch, String id, Runnable fallback) {}

    private static final BlockingQueue<Job> QUEUE = new ArrayBlockingQueue<>(4);
    private static final Object LOCK = new Object();
    private static Thread thread;

    private static volatile boolean broken;
    /** Когда текущее задание обязано закончиться (0 — ничего не играет). */
    private static volatile long busyDeadlineMs;

    static boolean isBroken() { return broken; }

    /** @return false — задание не принято, играй через движок игры. */
    static boolean submit(Job job) {
        if (broken) return false;
        long deadline = busyDeadlineMs;
        if (deadline != 0 && System.currentTimeMillis() > deadline + 3000) {
            markBroken("поток проигрывателя завис", null);
            return false;
        }
        ensureThread();
        return QUEUE.offer(job);
    }

    private static void ensureThread() {
        synchronized (LOCK) {
            if (thread != null && thread.isAlive()) return;
            thread = new Thread(SystemAudioPlayer::loop, "farmmacro-sound-player");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY);
            thread.start();
        }
    }

    private static void loop() {
        while (true) {
            Job job;
            try {
                job = QUEUE.take();
            } catch (InterruptedException e) {
                return;
            }
            if (broken) { runFallback(job); continue; }
            try {
                playBlocking(job);
            } catch (Throwable t) {
                markBroken("ошибка при выводе " + job.id(), t);
                runFallback(job);
            } finally {
                busyDeadlineMs = 0;
            }
        }
    }

    private static void runFallback(Job job) {
        try { job.fallback().run(); } catch (Throwable t) { LOGGER.error("Запасной звук не сработал", t); }
    }

    private static void markBroken(String why, Throwable t) {
        if (broken) return;
        broken = true;
        if (t != null) LOGGER.warn("Системный звук отключён ({}), дальше играю через Minecraft", why, t);
        else LOGGER.warn("Системный звук отключён ({}), дальше играю через Minecraft", why);
        // Всё, что ждёт в очереди, отдаём движку игры.
        Job j;
        while ((j = QUEUE.poll()) != null) runFallback(j);
    }

    private static void playBlocking(Job job) throws Exception {
        SoundDecoder.Clip clip = job.clip();
        byte[] data = SoundDecoder.render(clip, job.volume(), job.pitch());
        int rate = clip.sampleRate();                 // целая частота
        int frameSize = clip.channels() * 2;
        long frames = data.length / frameSize;
        long durationMs = frames * 1000L / rate;
        busyDeadlineMs = System.currentTimeMillis() + durationMs + 2500;

        AudioFormat fmt = new AudioFormat(rate, 16, clip.channels(), true, false);
        SourceDataLine line = null;
        try {
            line = AudioSystem.getSourceDataLine(fmt);
            int bufBytes = Math.max(frameSize, (rate / 5) * frameSize);   // ~200 мс
            line.open(fmt, bufBytes);
            line.start();
            int chunk = Math.max(frameSize, bufBytes / 2 / frameSize * frameSize);
            int off = 0;
            while (off < data.length) {
                if (System.currentTimeMillis() > busyDeadlineMs)
                    throw new java.util.concurrent.TimeoutException("запись в линию не успела");
                int n = line.write(data, off, Math.min(chunk, data.length - off));
                if (n <= 0) Thread.sleep(5);
                off += Math.max(0, n);
            }
            // доигрываем без drain() (на некоторых драйверах он вечный)
            while (line.getLongFramePosition() < frames) {
                if (System.currentTimeMillis() > busyDeadlineMs) {
                    LOGGER.debug("Таймаут доигрывания {}, обрываю", job.id());
                    break;
                }
                Thread.sleep(10);
            }
        } finally {
            if (line != null) {
                try { line.stop(); line.flush(); } catch (Throwable ignored) {}
                try { line.close(); } catch (Throwable ignored) {}
            }
        }
    }
}
