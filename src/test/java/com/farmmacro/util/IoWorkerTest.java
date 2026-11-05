package com.farmmacro.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IoWorkerTest {
    @Test
    void retriesWithBackoffThenSucceeds() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<Long> sleeps = new ArrayList<>();
        boolean ok = IoWorker.runWithRetry(
                "t1",
                () -> {
                    if (calls.incrementAndGet() < 3) throw new IOException("disk busy");
                },
                sleeps::add);
        assertTrue(ok);
        assertEquals(3, calls.get());
        assertEquals(List.of(IoWorker.BACKOFF_MS[0], IoWorker.BACKOFF_MS[1]), sleeps);
    }

    @Test
    void givesUpAfterAllAttempts() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        boolean ok = IoWorker.runWithRetry(
                "t2",
                () -> {
                    calls.incrementAndGet();
                    throw new IOException("read-only");
                },
                ms -> {});
        assertFalse(ok);
        assertEquals(IoWorker.BACKOFF_MS.length + 1, calls.get());
    }

    @Test
    void coalescesAndFlushWritesLatest(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("cfg.json");
        AtomicInteger writes = new AtomicInteger();
        for (int i = 0; i < 200; i++) {
            String body = "v" + i;
            IoWorker.submitLatest("test-cfg", () -> {
                writes.incrementAndGet();
                AtomicFiles.writeUtf8(f, w -> w.write(body));
            });
        }
        assertTrue(IoWorker.flush(10_000), "очередь не опустела");
        assertEquals("v199", Files.readString(f, StandardCharsets.UTF_8));
        assertTrue(writes.get() <= 200);
        assertTrue(IoWorker.technicalState().startsWith("queue=0 "));
    }
}
