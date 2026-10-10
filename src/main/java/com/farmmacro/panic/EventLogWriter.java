package com.farmmacro.panic;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.List;

/**
 * v1.11: запись журнала событий в файл — без зависимостей от игры (stress-тест {@code SuspicionCheck}).
 * Файл {@code events-ГГГГ-ММ-ДД.log}; когда он дорастает до {@code maxBytes}, переименовывается в
 * {@code events-ГГГГ-ММ-ДД.N.log} (первый свободный N), и запись идёт в новый. Только поток-писатель.
 */
public final class EventLogWriter {
    private final Path dir;
    private final long maxBytes;

    public EventLogWriter(Path dir, long maxBytes) {
        this.dir = dir;
        this.maxBytes = Math.max(1024, maxBytes);
    }

    public Path fileFor(LocalDate day) { return dir.resolve("events-" + day + ".log"); }

    /** Дописать строки (каждая — без перевода строки). */
    public void write(List<String> lines, LocalDate day) throws IOException {
        if (lines.isEmpty()) return;
        Files.createDirectories(dir);
        Path f = fileFor(day);
        int i = 0;
        boolean full = false;
        while (i < lines.size()) {
            long size = Files.exists(f) ? Files.size(f) : 0;
            if (full || size >= maxBytes) { if (size > 0) rotate(f, day); size = 0; full = false; }
            try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                for (; i < lines.size(); i++) {
                    byte[] b = (lines.get(i) + "\n").getBytes(StandardCharsets.UTF_8);
                    if (size > 0 && size + b.length > maxBytes) { full = true; break; }   // остальное — в новый файл
                    w.write(lines.get(i));
                    w.write('\n');
                    size += b.length;
                }
            }
        }
    }

    private void rotate(Path f, LocalDate day) throws IOException {
        for (int n = 1; n < 10_000; n++) {
            Path to = dir.resolve("events-" + day + "." + n + ".log");
            if (!Files.exists(to)) { Files.move(f, to); return; }
        }
        Files.delete(f);
    }
}
