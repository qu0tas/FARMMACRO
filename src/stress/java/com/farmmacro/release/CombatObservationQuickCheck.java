package com.farmmacro.release;

import com.farmmacro.combat.*;
import com.farmmacro.config.CombatPanicSettings;
import com.farmmacro.config.ModConfig;
import java.util.*;

/** Small continuous-gaze and recent-target checks; no table automation or game. */
public final class CombatObservationQuickCheck {
    private static void ok(boolean value, String label) {
        if (!value) throw new IllegalStateException(label);
    }

    public static void main() {
        var timer = new GazeTimer();
        var a = new UUID(0, 1);
        var b = new UUID(0, 2);
        for (int i = 0; i < 14; i++) ok(!timer.sample(a, true, i * 500_000_000L, 7), "early gaze");
        ok(timer.sample(a, true, 7_000_000_000L, 7), "continuous gaze");
        ok(!timer.sample(b, true, 7_000_000_000L, 7), "different observer");
        ok(!timer.sample(a, false, 7_100_000_000L, 7), "look-away");
        ok(!timer.sample(a, true, 7_200_000_000L, 7), "fresh interval");
        ok(!timer.sample(a, true, 9_000_000_000L, 7), "stall reset");
        timer.retain(Set.of(b));
        ok(!timer.sample(a, true, 9_100_000_000L, 7), "leaving reset");
        timer.clear();
        var memory = new RecentTargetMemory();
        for (int i = 0; i < 30; i++) memory.observe(i, i, 70, 0, i);
        ok(memory.recent(30).size() == 24, "memory cap");
        ok(memory.recent(30).getFirst().id() == 29, "recent first");
        memory.observe(31, Double.NaN, 0, 0, 31);
        ok(memory.recent(31).size() == 24, "reject NaN");
        ok(memory.recent(21_000_000_000L).isEmpty(), "TTL");
        memory.clear();
        var panic = new CombatPanicSettings();
        panic.gazeSeconds = 99;
        panic.gazeRadius = Double.NaN;
        panic.sanitize();
        ok(panic.gazeSeconds == 10 && panic.gazeRadius == 12, "gaze settings");
        ok(
                Arrays.stream(ModConfig.class.getFields())
                        .noneMatch(f -> f.getName().equals("experiments")),
                "removed table config");
        try {
            Class.forName(
                    "com.farmmacro.experiment.Experiments", false, CombatObservationQuickCheck.class.getClassLoader());
            throw new IllegalStateException("table runtime remained");
        } catch (ClassNotFoundException expected) {
        }
        System.out.println(
                "CombatObservationQuickCheck: PASS (continuous separate gaze intervals/reset/settings, memory TTL/cap, table config/runtime absent; no game)");
    }

    private CombatObservationQuickCheck() {}
}
