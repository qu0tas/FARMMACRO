package com.farmmacro.panic;

import java.io.InputStream;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Проверка декодера вне игры: каждый встроенный звук декодируется N раз (по умолчанию 1000)
 * тем же кодом, что в моде (SoundDecoder). Проверяется длительность, одинаковость результата,
 * отсутствие роста кучи и прямой (нативной) памяти NIO, а также ресэмплинг тона.
 *
 * Запуск: ./gradlew soundStress   (или -Piterations=200)
 */
public final class SoundDecodeStress {

    /** Ожидаемая длительность (ffprobe по файлам в ресурсах), секунды. */
    private static final Map<String, Double> EXPECTED = new LinkedHashMap<>();
    static {
        EXPECTED.put("siren", 1.600);
        EXPECTED.put("klaxon", 1.320);
        EXPECTED.put("alarm", 1.460);
        EXPECTED.put("alert", 1.4796);
        EXPECTED.put("done", 1.150);
    }

    public static void main(String[] args) throws Exception {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 1000;
        int failures = 0;

        Map<String, byte[]> raw = new LinkedHashMap<>();
        for (String name : EXPECTED.keySet()) {
            try (InputStream in = SoundDecodeStress.class.getResourceAsStream("/assets/farmmacro/sounds/panic/" + name + ".ogg")) {
                if (in == null) throw new IllegalStateException("нет ресурса " + name);
                raw.put(name, in.readAllBytes());
            }
        }

        // прогрев (JIT, классы javax.sound)
        for (byte[] b : raw.values()) SoundDecoder.decodeOgg(b);
        long heap0 = usedHeapAfterGc();
        long direct0 = directBytes();
        int threads0 = Thread.activeCount();

        long t0 = System.nanoTime();
        for (Map.Entry<String, byte[]> e : raw.entrySet()) {
            String name = e.getKey();
            SoundDecoder.Clip first = null;
            long ts = System.nanoTime();
            for (int i = 0; i < n; i++) {
                SoundDecoder.Clip c = SoundDecoder.decode(e.getValue(), name + ".ogg");
                if (first == null) first = c;
                else if (c.samples().length != first.samples().length || c.sampleRate() != first.sampleRate()
                        || c.channels() != first.channels() || (i % 97 == 0 && !Arrays.equals(c.samples(), first.samples()))) {
                    System.out.println("FAIL " + name + ": итерация " + i + " отличается от первой");
                    failures++;
                    break;
                }
            }
            double ms = (System.nanoTime() - ts) / 1e6 / n;
            double dur = first.seconds();
            double exp = EXPECTED.get(name);
            boolean ok = Math.abs(dur - exp) <= 0.02;
            if (!ok) failures++;
            System.out.printf("%s %-7s %d Гц %d кан. %.3f с (ожидалось %.3f) · %.2f мс/декод%n",
                    ok ? "OK  " : "FAIL", name, first.sampleRate(), first.channels(), dur, exp, ms);

            // ресэмплинг: частота та же (целая), длина ≈ / pitch
            for (float pitch : new float[]{0.5f, 0.75f, 1f, 1.5f, 2f}) {
                byte[] pcm = SoundDecoder.render(first, 0.8f, pitch);
                double outSec = pcm.length / 2.0 / first.channels() / first.sampleRate();
                if (Math.abs(outSec - dur / pitch) > 0.01) {
                    System.out.printf("FAIL %s pitch %.2f: %.3f с вместо %.3f%n", name, pitch, outSec, dur / pitch);
                    failures++;
                }
            }
        }
        double total = (System.nanoTime() - t0) / 1e9;

        long heap1 = usedHeapAfterGc();
        long direct1 = directBytes();
        int threads1 = Thread.activeCount();
        long heapGrowth = heap1 - heap0;
        System.out.printf("Всего %d декодов за %.1f с. Куча после GC: %+d КБ, прямая память NIO: %+d Б, потоков: %+d%n",
                n * raw.size(), total, heapGrowth / 1024, direct1 - direct0, threads1 - threads0);
        if (heapGrowth > 4L * 1024 * 1024) { System.out.println("FAIL: куча выросла больше 4 МБ"); failures++; }
        if (direct1 != direct0) { System.out.println("FAIL: изменилась прямая память"); failures++; }

        // пустые/битые данные — исключение, а не краш
        for (byte[] bad : new byte[][]{new byte[0], new byte[]{'O', 'g', 'g', 'S', 1, 2, 3}, "garbage".getBytes()}) {
            try {
                SoundDecoder.decodeOgg(bad);
                System.out.println("FAIL: битый ogg декодировался"); failures++;
            } catch (Exception expected) { /* так и надо */ }
        }

        System.out.println(failures == 0 ? "ИТОГ: OK" : "ИТОГ: ошибок " + failures);
        System.exit(failures == 0 ? 0 : 1);
    }

    private static long usedHeapAfterGc() throws InterruptedException {
        for (int i = 0; i < 3; i++) { System.gc(); Thread.sleep(100); }
        Runtime r = Runtime.getRuntime();
        return r.totalMemory() - r.freeMemory();
    }

    private static long directBytes() {
        long sum = 0;
        for (BufferPoolMXBean b : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) sum += b.getMemoryUsed();
        return sum;
    }
}
