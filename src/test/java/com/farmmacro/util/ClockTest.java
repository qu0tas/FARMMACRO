package com.farmmacro.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ClockTest {
    @AfterEach
    void restore() {
        Clock.setTestSource(null);
    }

    @Test
    void millisAreMonotonicAndCloseToWallClock() {
        long prev = Clock.millis();
        for (int i = 0; i < 10_000; i++) {
            long now = Clock.millis();
            assertTrue(now >= prev, "время пошло назад");
            prev = now;
        }
        // выровнено по системным часам при старте; в тесте часы никто не переводит
        assertTrue(Math.abs(Clock.millis() - Clock.wallMillis()) < 1_000);
    }

    @Test
    void testSourceDrivesTimeouts() {
        AtomicLong fake = new AtomicLong(1_000);
        Clock.setTestSource(fake::get);
        long until = Clock.millis() + 3_000;
        fake.addAndGet(2_999);
        assertTrue(Clock.millis() < until);
        fake.addAndGet(1);
        assertEquals(until, Clock.millis());
    }
}
