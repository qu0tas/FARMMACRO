package com.farmmacro.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StepProfilerTest {
    @BeforeEach
    void reset() {
        StepProfiler.reset();
    }

    @Test
    void countsTotalsAndMax() {
        StepProfiler.record("a/step", 1_000_000);
        StepProfiler.record("a/step", 3_000_000);
        StepProfiler.record("b/step", 500_000);
        List<StepProfiler.Row> top = StepProfiler.top(5);
        assertEquals("a/step", top.get(0).name());
        assertEquals(2, top.get(0).count());
        assertEquals(4_000_000, top.get(0).totalNanos());
        assertEquals(3.0, top.get(0).maxMillis(), 1e-9);
        assertEquals(2.0, top.get(0).avgMillis(), 1e-9);
        assertTrue(StepProfiler.summary(1).startsWith("a/step 2.000/3.00ms×2"));
    }

    @Test
    void unsafeAndTooManyNamesFoldIntoOther() {
        StepProfiler.record("bad name with spaces", 1);
        StepProfiler.record(null, 1);
        for (int i = 0; i < StepProfiler.MAX_NAMES + 20; i++) StepProfiler.record("s/" + i, 1);
        List<StepProfiler.Row> all = StepProfiler.top(1_000);
        assertTrue(all.size() <= StepProfiler.MAX_NAMES + 1, "имён больше лимита: " + all.size());
        assertTrue(all.stream().anyMatch(r -> r.name().equals("other") && r.count() >= 2));
    }

    @Test
    void guardRunIsTimedEvenWhenStepThrows() {
        Guard.run("test/throws", () -> {
            throw new IllegalStateException("boom");
        });
        assertTrue(StepProfiler.top(10).stream().anyMatch(r -> r.name().equals("test/throws") && r.count() == 1));
    }
}
