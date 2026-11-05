package com.farmmacro.release;

import com.farmmacro.config.ModConfig;
import com.farmmacro.fishing.*;
import com.google.gson.Gson;
import java.util.SplittableRandom;

/** Bounded pure state tables. No game, server, sleeps, real input or large simulations. */
public final class FishingQuickCheck {
    private static void ok(boolean b, String why) {
        if (!b) throw new IllegalStateException("Fishing: " + why);
    }

    private static FishingSettings settings() {
        var c = new FishingSettings();
        c.reelMinMs = c.reelMaxMs = 100;
        c.recastMinMs = c.recastMaxMs = 400;
        c.selectBaseMs = c.useBaseMs = c.returnBaseMs = 100;
        c.selectSpreadMs = c.useSpreadMs = c.returnSpreadMs = 0;
        return c;
    }

    private static FishingCycle fish() {
        return new FishingCycle(new SplittableRandom(42));
    }

    private static void reel(FishingCycle f, FishingSettings c) {
        f.start(0);
        f.tick(0, 7, false, c);
        f.tick(50, 7, true, c);
        ok(f.tick(149, 7, true, c) == FishingCycle.Action.NONE, "no early reel");
        ok(f.tick(150, 7, true, c) == FishingCycle.Action.REEL, "one reel");
    }

    public static void main() {
        var c = settings();
        var f = fish();
        reel(f, c);
        ok(f.tick(200, 7, true, c) == FishingCycle.Action.NONE && f.reeled() == 1, "persisting !!! never double reels");
        f.tick(250, -1, false, c);
        ok(f.tick(649, -1, false, c) == FishingCycle.Action.NONE, "recast delay");
        ok(f.tick(650, -1, false, c) == FishingCycle.Action.CAST, "cast after gone");
        ok(f.tick(700, -1, false, c) == FishingCycle.Action.NONE, "no repeat cast");
        f.tick(750, 8, false, c);
        ok(f.stage() == FishingCycle.Stage.WAIT_BITE, "spawn acknowledged");
        f.creature(800);
        ok(f.tick(850, 8, false, c) == FishingCycle.Action.NONE, "late chat after new hook ignored");
        f.stop("manual");
        ok(f.tick(1000, 8, true, c) == FishingCycle.Action.NONE && !f.active(), "stop cancels queue");
        f = fish();
        f.start(0);
        f.tick(0, 1, false, c);
        f.tick(50, 1, true, c);
        ok(f.tick(150, -1, true, c) == FishingCycle.Action.NONE, "hook vanished before delayed reel");
        f = fish();
        reel(f, c);
        f.tick(1000, 7, true, c);
        f.tick(2000, 7, true, c);
        f.tick(3000, 7, true, c);
        f.tick(4200, 7, true, c);
        ok(!f.active() && f.reeled() == 1, "unremoved hook times out, no retries");
        f = fish();
        f.start(0);
        f.tick(3100, -1, false, c);
        ok(!f.active(), "long stall revokes");
        f = fish();
        f.start(100);
        f.tick(99, -1, false, c);
        ok(!f.active(), "backward time revokes");
        c = settings();
        c.fireveil = true;
        f = fish();
        reel(f, c);
        f.creature(160);
        f.creature(161);
        f.tick(200, -1, false, c);
        ok(f.tick(300, -1, false, c) == FishingCycle.Action.SELECT_TOOL, "tool slot");
        ok(f.tick(400, -1, false, c) == FishingCycle.Action.USE_TOOL, "tool use");
        ok(f.tick(500, -1, false, c) == FishingCycle.Action.RESTORE_ROD, "rod return");
        ok(
                f.tick(900, -1, false, c) == FishingCycle.Action.CAST && f.tools() == 1,
                "one tool per catch, delayed recast");
        c = settings();
        c.recast = false;
        f = fish();
        reel(f, c);
        f.tick(200, -1, false, c);
        ok(
                f.tick(700, -1, false, c) == FishingCycle.Action.NONE && f.stage() == FishingCycle.Stage.WAIT_HOOK,
                "recast opt out");
        c.fireveil = true;
        f.creature(710);
        f.tick(720, -1, false, c);
        ok(f.tick(820, -1, false, c) == FishingCycle.Action.SELECT_TOOL, "tool without recast");
        f.stop("panic");
        ok(f.tick(920, -1, false, c) == FishingCycle.Action.NONE, "panic cancels tool use");
        c = settings();
        f = fish();
        reel(f, c);
        f.tick(200, -1, false, c);
        f.tick(250, 99, false, c);
        ok(f.stage() == FishingCycle.Stage.WAIT_BITE && f.casts() == 0, "manual new hook cancels queued recast");
        ok(
                FishingMarker.candidate(16) && !FishingMarker.candidate(16.01) && !FishingMarker.candidate(Double.NaN),
                "near marker bounds");
        ok(
                FishingMarker.competitor(1, .8) && FishingMarker.competitor(1, 1.1) && !FishingMarker.competitor(1, 2),
                "neighbor/tie reject");
        var rng = new SplittableRandom(12);
        for (int i = 0; i < 32; i++) {
            int d = FishingCycle.delay(rng, 56, 129, 2);
            ok(d >= 56 && d <= 129, "bounded reel delay");
            d = FishingCycle.delay(rng, 390, 980, 3);
            ok(d >= 390 && d <= 980, "bounded recast delay");
        }
        c = new FishingSettings();
        c.toolSlot = 99;
        c.reelMinMs = 9000;
        c.reelMaxMs = -1;
        c.recastMinMs = -8;
        c.biteTimeoutSec = 0;
        c.sanitize();
        ok(
                c.toolSlot == 9
                        && c.reelMinMs == 2000
                        && c.reelMaxMs == 2000
                        && c.recastMinMs == 200
                        && c.biteTimeoutSec == 10,
                "config clamp");
        var json = new Gson();
        var cfg = json.fromJson("{\"fishing\":null}", ModConfig.class);
        cfg.sanitize();
        ok(cfg.fishing != null && !cfg.fishing.enabled, "old/null config no autostart");
        FishingMessages.load();
        ok(FishingMessages.size() == 52, "canonical messages");
        ok(
                FishingMessages.creature("§aYou caught a Sea Walker.")
                        && !FishingMessages.creature("Player: You caught a Sea Walker.")
                        && !FishingMessages.creature("+5 Fishing XP"),
                "exact GAME text only");
        System.out.println(
                "FishingQuickCheck: PASS (own-marker bounds, single reel/cast, observed hook lifecycle, cancellation, Fireveil order, no-recast, config/messages/delay bounds; no game)");
    }

    private FishingQuickCheck() {}
}
