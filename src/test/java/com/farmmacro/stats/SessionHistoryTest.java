package com.farmmacro.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SessionHistoryTest {
    private static final long T0 = 1_760_000_000_000L - 1_760_000_000_000L % 60_000; // целая минута

    @Test
    void csvRoundTrip() {
        var run = new SessionHistory.Run(T0, 90 * 60_000L, "Wheat", "farm, \"main\"", 1_500_000, 12_345.5, 7, "finish");
        String line = SessionHistory.csvLine(run);
        var back = SessionHistory.parse(line);
        assertNotNull(back);
        assertEquals(run.startedAt(), back.startedAt());
        assertEquals(run.durationMs(), back.durationMs());
        assertEquals(run.crop(), back.crop());
        assertEquals(run.route(), back.route());
        assertEquals(run.net(), back.net(), 0.5);
        assertEquals(run.xp(), back.xp(), 0.05);
        assertEquals(run.loops(), back.loops());
        assertEquals(1_000_000, back.coinsPerHour(), 1);
    }

    @Test
    void csvEscapesFormulasAndQuotes() {
        assertEquals("\"'=HYPERLINK(1)\"", SessionHistory.quote("=HYPERLINK(1)"));
        assertEquals("\"a,b\"", SessionHistory.quote("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", SessionHistory.quote("say \"hi\""));
        assertEquals("plain", SessionHistory.quote("plain"));
        assertEquals(List.of("a", "b,c", "d\"e"), SessionHistory.splitCsv("a,\"b,c\",\"d\"\"e\""));
        var r = SessionHistory.parse(
                SessionHistory.csvLine(new SessionHistory.Run(T0, 60_000, "-crop", "+r", 1, 1, 0, "@x")));
        assertNotNull(r);
        assertEquals("-crop", r.crop());
        assertEquals("@x", r.end());
    }

    @Test
    void missingObservationsStayEmpty() {
        var run = new SessionHistory.Run(T0, 30_000, "—", "—", Double.NaN, Double.NaN, 0, "stop");
        String line = SessionHistory.csvLine(run);
        assertTrue(line.contains(",,,"), line);
        var back = SessionHistory.parse(line);
        assertNotNull(back);
        assertTrue(Double.isNaN(back.net()));
        assertNull(SessionHistory.parse("broken,line"));
    }

    @Test
    void aggregatesProfitPerHourByCrop() {
        var runs = List.of(
                new SessionHistory.Run(T0, 60 * 60_000L, "Wheat", "a", 100_000, 10, 1, "finish"),
                new SessionHistory.Run(T0, 30 * 60_000L, "Wheat", "b", 80_000, 5, 1, "stop"),
                new SessionHistory.Run(T0, 60 * 60_000L, "Carrot", "c", 50_000, Double.NaN, 1, "finish"),
                new SessionHistory.Run(T0, 60 * 60_000L, "Carrot", "c", Double.NaN, 1, 1, "lost-world"));
        var totals = SessionHistory.byCrop(runs);
        assertEquals(2, totals.size());
        assertEquals("Wheat", totals.get(0).crop());
        assertEquals(2, totals.get(0).runs());
        assertEquals(120_000, totals.get(0).coinsPerHour(), 1e-6);
        assertEquals(15, totals.get(0).xp(), 1e-9);
        assertEquals(1, totals.get(1).runs(), "запуск без наблюдённых монет в сумму не идёт");
    }

    @Test
    void deltaHandlesCounterResets() {
        assertEquals(50, SessionHistory.delta(100, 150), 0);
        assertEquals(20, SessionHistory.delta(100, 20), 0);
        assertEquals(20, SessionHistory.delta(Double.NaN, 20), 0);
        assertTrue(Double.isNaN(SessionHistory.delta(5, Double.NaN)));
    }

    @Test
    void headerMatchesColumns() {
        assertEquals(9, SessionHistory.HEADER.split(",").length);
        String csv = SessionHistory.csv(List.of(new SessionHistory.Run(T0, 60_000, "x", "y", 1, 1, 1, "z")));
        assertTrue(csv.startsWith(SessionHistory.HEADER + "\n"));
        assertEquals(2, csv.lines().count());
    }
}
