package com.farmmacro.macro;

import java.util.LinkedHashMap;
import java.util.Map;

/** Наборы для сравнения в ConfigSweep (копия значений ConfigProfiles — тот класс тянет FabricLoader). */
final class ConfigSweepPresets {
    private ConfigSweepPresets() {}

    static HumanSettings base() {
        HumanSettings h = new HumanSettings();
        h.enabled = true;
        return h;
    }

    static HumanSettings human() {
        HumanSettings h = base();
        h.reachJitter = 0.12; h.cornerSlop = 0.35;
        h.overlapChance = 35; h.overlapMax = 3; h.gapChance = 20; h.gapMax = 2;
        h.hesitateChance = 8; h.hesitateMax = 8;
        h.pauseJitterPct = 25; h.pauseJitterTicks = 3;
        h.jumpDelayMax = 2; h.sprintDelayMax = 3; h.startDelayMax = 20;
        h.perfectLapChance = 8; h.perfectStreakChance = 30; h.streakMin = 2; h.streakMax = 4;
        h.midStopChance = 12; h.midStopMin = 15; h.midStopMax = 60;
        h.afkChance = 2; h.afkMin = 120; h.afkMax = 600;
        return h;
    }

    static HumanSettings careful() {
        HumanSettings h = human();
        h.hesitateChance = 12; h.perfectLapChance = 5;
        h.midStopChance = 20; h.midStopMin = 20; h.midStopMax = 80;
        h.afkChance = 4; h.afkMin = 200; h.afkMax = 1200;
        return h;
    }

    static Map<String, HumanSettings> named() {
        Map<String, HumanSettings> m = new LinkedHashMap<>();
        m.put("по умолчанию", base());
        m.put("Человек (1.10.0)", human());
        m.put("Осторожный (1.10.0)", careful());
        return m;
    }
}
