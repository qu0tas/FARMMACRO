package com.farmmacro.panic;

import com.farmmacro.config.ModConfig;
import com.farmmacro.panic.Detectors.Action;
import com.farmmacro.panic.Detectors.Def;

import java.util.HashSet;
import java.util.Set;

/** v1.14.2-pre: реестр детекторов без Minecraft — id, действия, троттлинг, счётчики, sanitize, «Включить все». */
public final class DetectorsCheck {
    private static void check(boolean ok, String msg) {
        if (!ok) throw new IllegalStateException("DetectorsCheck: " + msg);
    }

    public static void main(String... args) {
        ModConfig saved = ModConfig.INSTANCE;
        ModConfig c = new ModConfig();
        ModConfig.INSTANCE = c;
        try {
            run(c);
        } finally {
            ModConfig.INSTANCE = saved;
            Detectors.resetStats();
        }
        System.out.println("DetectorsCheck: OK (" + Detectors.all().size() + " детекторов)");
    }

    private static void run(ModConfig c) {
        c.sanitize();
        // ── список ──
        Set<String> ids = new HashSet<>();
        for (Def d : Detectors.all()) {
            check(ids.add(d.id()), "повтор id " + d.id());
            check(d.allowed().contains(d.def()), d.id() + ": действие по умолчанию не разрешено");
            check(d.allowed().contains(Action.OFF), d.id() + ": нельзя выключить");
            check(Detectors.GROUPS.contains(d.group()), d.id() + ": неизвестная группа " + d.group());
            check(d.name() != null && !d.name().isBlank() && d.hint() != null, d.id() + ": нет названия/подсказки");
            check(!d.testable() || d.example() != null, d.id() + ": нет примера для ▶");
            check(Detectors.get(d.id()) == d, d.id() + ": get");
        }
        check(Detectors.all().size() >= 40, "мало детекторов: " + Detectors.all().size());
        for (String must : new String[]{"rotation", "stuck", "drift", "velocity", "floor", "staff", "jacob", "silence", "lagGuard", "suspicion", "lagback"})
            check(ids.contains(must), "нет детектора " + must);

        // ── действие ↔ прежнее поле ──
        check(Detectors.action("rotation") == Action.PANIC && c.detectRotation, "rotation по умолчанию — паника");
        Detectors.setAction("rotation", Action.OFF);
        check(!c.detectRotation && Detectors.action("rotation") == Action.OFF, "OFF выключает поле");
        Detectors.setAction("rotation", Action.SUSP);
        check(c.detectRotation && Detectors.action("rotation") == Action.SUSP && "susp".equals(c.detectorActions.get("rotation")), "SUSP включает и запоминает");
        c.detectRotation = false;
        check(Detectors.action("rotation") == Action.OFF, "поле выкл → OFF при любом действии");
        c.detectRotation = true;
        check(Detectors.action("rotation") == Action.SUSP, "своё действие возвращается");
        Detectors.setAction("rotation", Action.PANIC);
        check(!c.detectorActions.containsKey("rotation"), "по умолчанию — не хранится");
        Detectors.setAction("lagGuard", Action.SUSP);
        check(!c.detectorActions.containsKey("lagGuard"), "неразрешённое действие игнорируется");

        // ── next ──
        check(Detectors.next("rotation", 1) == Action.SUSP && Detectors.next("rotation", -1) == Action.OFF, "next по кругу");
        check(Detectors.next("lagGuard", 1) == Action.OFF && Detectors.next("silence", 1) == Action.LOG, "next защиты/своего");
        check(Detectors.action("lagback") == (c.lagbackSoft ? Action.SUSP : Action.OFF), "lagback ↔ lagbackSoft");

        // ── record: паника — каждый раз, мягкие — не чаще REPEAT_MS ──
        Detectors.resetStats();
        check(Detectors.record("damage", "a", 1000) == Action.PANIC && Detectors.record("damage", "b", 1001) == Action.PANIC, "паника без троттлинга");
        Detectors.setAction("damage", Action.LOG);
        check(Detectors.record("damage", "c", 2000) == Action.LOG, "журнал");
        check(Detectors.record("damage", "d", 2000 + Detectors.REPEAT_MS - 1) == null, "повтор раньше 5 с — null");
        check(Detectors.record("damage", "e", 2000 + Detectors.REPEAT_MS) == Action.LOG, "через 5 с — снова");
        check(Detectors.count("damage") == 4, "count: " + Detectors.count("damage"));
        check("e".equals(Detectors.recent("damage").get(0).text()), "recent — новые сверху");
        Detectors.setAction("damage", Action.OFF);
        check(Detectors.record("damage", "f", 99_000) == null && Detectors.count("damage") == 4, "OFF не считается");
        Detectors.setAction("damage", Action.PANIC);
        for (int i = 0; i < 30; i++) Detectors.record("slot", "x" + i, i);
        check(Detectors.recent("slot").size() == Detectors.RECENT && Detectors.count("slot") == 30, "recent ≤ 8");

        // ── soft: всегда считается; LOG → false ──
        check(Detectors.soft("knockback", "слабый", 1), "soft по умолчанию — очки");
        Detectors.setAction("knockback", Action.LOG);
        check(!Detectors.soft("knockback", "слабый", 2) && Detectors.count("knockback") == 2, "soft LOG — только журнал");
        Detectors.setAction("knockback", Action.PANIC);
        check(Detectors.totalCount() == Detectors.count("damage") + Detectors.count("slot") + Detectors.count("knockback"), "totalCount");
        Detectors.resetStats();
        check(Detectors.totalCount() == 0 && Detectors.recent("slot").isEmpty(), "resetStats");

        // ── вес ──
        check(Detectors.weight("rotation") == Detectors.DEFAULT_WEIGHT, "вес по умолчанию");
        Detectors.setWeight("rotation", 7.5);
        check(Detectors.weight("rotation") == 7.5, "свой вес");
        c.detectorWeights.put("slot", 1e9);
        check(Detectors.weight("slot") == 100, "вес ≤ 100");
        Detectors.setWeight("lagback", 4);
        check(c.suspWeightLagback == 4 && !c.detectorWeights.containsKey("lagback"), "вес отката — своё поле");
        Detectors.setWeight("rotation", Detectors.DEFAULT_WEIGHT);
        check(!c.detectorWeights.containsKey("rotation"), "вес по умолчанию — не хранится");

        // ── звук ──
        check(Detectors.sound("rotation") == null && Detectors.sound(null) == null, "звук — общий");
        Detectors.setSound("rotation", "builtin:beep");
        check("builtin:beep".equals(Detectors.sound("rotation")), "свой звук");
        Detectors.setSound("rotation", null);
        check(Detectors.sound("rotation") == null, "звук сброшен");

        // ── sanitize ──
        c.detectorActions.put("nope", "susp");
        c.detectorActions.put("rotation", "bad");
        c.detectorActions.put("slot", "panic");          // = по умолчанию
        c.detectorActions.put("lagGuard", "log");        // не разрешено
        c.detectorActions.put("damage", "off");          // OFF — только полем
        c.detectorActions.put("heldItem", "log");        // ок
        c.detectorSounds.put("nope", "builtin:x");
        c.detectorSounds.put("gui", " ");
        c.detectorSounds.put("fire", "builtin:siren");
        c.detectorWeights.put("nope", 2.0);
        c.detectorWeights.put("gui", Double.NaN);
        c.detectorWeights.put("fire", -5.0);
        c.welcomeWhere = "mars"; c.welcomePreset = "plain";
        c.sanitize();
        check(c.detectorActions.size() == 1 && "log".equals(c.detectorActions.get("heldItem")), "sanitize actions: " + c.detectorActions);
        check(c.detectorSounds.size() == 1 && c.detectorSounds.containsKey("fire"), "sanitize sounds: " + c.detectorSounds);
        check(c.detectorWeights.get("fire") == 0 && !c.detectorWeights.containsKey("gui") && !c.detectorWeights.containsKey("nope")
                && c.detectorWeights.get("slot") == 100, "sanitize weights: " + c.detectorWeights);
        check("skyblock".equals(c.welcomeWhere) && "optimal".equals(c.welcomePreset), "sanitize мастера");
        c.detectorActions = null; c.detectorSounds = null; c.detectorWeights = null;
        c.sanitize();
        check(c.detectorActions != null && c.detectorSounds != null && c.detectorWeights != null, "null → пустые словари");
        check(!Detectors.describe(c).contains("null"), "describe");

        // ── включить / выключить все ──
        Detectors.setAll(false);
        check(Detectors.enabledCount() == 0, "выключить все: " + Detectors.enabledCount());
        Detectors.setAll(true);
        check(!c.detectBps && !c.detectBanwave && !c.detectJacob && !c.detectDrift && !c.lagbackSoft,
                "включить все не трогает BPS/банвейв/Jacob/сход/откат");
        Set<String> needsOwnData=Set.of("bps","farmingXp","banwave","jacob","drift","lagback");
        for(Def d:Detectors.all())check(d.enabled()!=needsOwnData.contains(d.id()),"setAll unexpected: "+d.id());
        check(Detectors.enabledCount() == Detectors.all().size() - needsOwnData.size(), "включить все: " + Detectors.enabledCount());
    }
}
