package com.farmmacro.fishing;

import java.util.random.RandomGenerator;

/** Tick-owned state machine adapted from lyn1dev/skyblock-autofisher (CC0), pinned in notices.
 * No scheduled callbacks survive stop, no packets/IO/game classes. Hook removal/spawn is observed.
 * Time is monotonic milliseconds supplied by caller; one action maximum per tick.
 */
public final class FishingCycle {
    public enum Stage {
        OFF,
        WAIT_HOOK,
        WAIT_BITE,
        REEL_DELAY,
        WAIT_GONE,
        RECAST_DELAY,
        WAIT_CAST,
        SELECT_TOOL,
        USE_TOOL,
        RESTORE_ROD
    }

    public enum Action {
        NONE,
        REEL,
        CAST,
        SELECT_TOOL,
        USE_TOOL,
        RESTORE_ROD
    }

    private final RandomGenerator random;
    private Stage stage = Stage.OFF;
    private long due, limit, lastNow, lastReel = -1, lastTool = -1;
    private int hook = -1, reeled, casts, tools;
    private boolean creature, toolDone;
    private String stopReason = "не запущена";

    public FishingCycle(RandomGenerator random) {
        this.random = random;
    }

    public Stage stage() {
        return stage;
    }

    public boolean active() {
        return stage != Stage.OFF;
    }

    public String stopReason() {
        return stopReason;
    }

    public int reeled() {
        return reeled;
    }

    public int casts() {
        return casts;
    }

    public int tools() {
        return tools;
    }

    public int expectedHook() {
        return hook;
    }

    public void start(long now) {
        stopReason = "";
        hook = -1;
        reeled = casts = tools = 0;
        creature = toolDone = false;
        lastReel = lastTool = -1;
        lastNow = now;
        stage = Stage.WAIT_HOOK;
        limit = now + 60_000;
    }

    public void stop(String reason) {
        stage = Stage.OFF;
        hook = -1;
        creature = false;
        due = limit = 0;
        stopReason = reason;
    }

    public void creature(long now) {
        if (active()
                && lastReel >= 0
                && now >= lastReel
                && now - lastReel <= 4000
                && !toolDone
                && (stage == Stage.WAIT_GONE || stage == Stage.RECAST_DELAY || stage == Stage.WAIT_HOOK))
            creature = true;
    }
    /** Sum-of-uniform layers, like upstream, without unbounded Gaussian tails or modulo skew. */
    public static int delay(RandomGenerator r, int min, int max, int layers) {
        int span = Math.max(0, max - min);
        layers = Math.clamp(layers, 1, 4);
        int sum = 0;
        for (int i = 0; i < layers; i++) sum += r.nextInt(span + 1);
        return min + (int) Math.round((double) sum / layers);
    }

    private void waitBite(int id, long now, FishingSettings c) {
        hook = id;
        stage = Stage.WAIT_BITE;
        limit = now + c.biteTimeoutSec * 1000L;
        creature = false;
        lastReel = -1;
    }

    private void afterCatch(long now, FishingSettings c) {
        if (c.fireveil && creature && !toolDone && (lastTool < 0 || now - lastTool >= 5000)) {
            creature = false;
            toolDone = true;
            stage = Stage.SELECT_TOOL;
            due = now + delay(random, c.selectBaseMs, c.selectBaseMs + c.selectSpreadMs, 1);
            limit = due + 5000;
            return;
        }
        creature = false;
        if (c.recast) {
            stage = Stage.RECAST_DELAY;
            due = now + delay(random, c.recastMinMs, c.recastMaxMs, 3);
            limit = due + 3000;
        } else {
            stage = Stage.WAIT_HOOK;
            limit = now + 60_000;
        }
    }

    public Action tick(long now, int observedHook, boolean bite, FishingSettings c) {
        if (!active()) return Action.NONE;
        if (now < lastNow || now - lastNow > 3000) {
            stop("пауза/скачок времени — запусти вручную");
            return Action.NONE;
        }
        lastNow = now;
        if (now > limit) {
            stop("таймаут " + stage + " — без повторных кликов");
            return Action.NONE;
        }
        switch (stage) {
            case WAIT_HOOK -> {
                if (observedHook >= 0) {
                    waitBite(observedHook, now, c);
                } else if (creature) afterCatch(now, c);
            }
            case WAIT_CAST -> {
                if (observedHook >= 0) {
                    waitBite(observedHook, now, c);
                }
            }
            case WAIT_BITE -> {
                if (observedHook != hook) {
                    if (observedHook >= 0) waitBite(observedHook, now, c);
                    else {
                        stage = Stage.WAIT_HOOK;
                        limit = now + 60_000;
                    }
                    return Action.NONE;
                }
                if (bite) {
                    stage = Stage.REEL_DELAY;
                    due = now + delay(random, c.reelMinMs, c.reelMaxMs, 2);
                }
            }
            case REEL_DELAY -> {
                if (observedHook != hook) {
                    stage = Stage.WAIT_HOOK;
                    limit = now + 60_000;
                    return Action.NONE;
                }
                if (now >= due) {
                    if (!bite) {
                        stage = Stage.WAIT_BITE;
                        return Action.NONE;
                    }
                    stage = Stage.WAIT_GONE;
                    lastReel = now;
                    limit = now + 4000;
                    reeled++;
                    creature = false;
                    toolDone = false;
                    return Action.REEL;
                }
            }
            case WAIT_GONE -> {
                if (observedHook >= 0 && observedHook != hook) {
                    stop("поплавок сменился во время подсечки");
                    return Action.NONE;
                }
                if (observedHook < 0) {
                    hook = -1;
                    afterCatch(now, c);
                }
            }
            case RECAST_DELAY -> {
                if (observedHook >= 0) {
                    waitBite(observedHook, now, c);
                    return Action.NONE;
                }
                if (creature && c.fireveil && !toolDone) {
                    afterCatch(now, c);
                    return Action.NONE;
                }
                if (!c.recast) {
                    stage = Stage.WAIT_HOOK;
                    limit = now + 60_000;
                    return Action.NONE;
                }
                if (now >= due) {
                    stage = Stage.WAIT_CAST;
                    limit = now + 4000;
                    casts++;
                    return Action.CAST;
                }
            }
            case SELECT_TOOL -> {
                if (!c.fireveil || observedHook >= 0) {
                    stop("контекст Fireveil изменился");
                    return Action.NONE;
                }
                if (now >= due) {
                    stage = Stage.USE_TOOL;
                    due = now + delay(random, c.useBaseMs, c.useBaseMs + c.useSpreadMs, 1);
                    limit = due + 3000;
                    return Action.SELECT_TOOL;
                }
            }
            case USE_TOOL -> {
                if (now >= due) {
                    lastTool = now;
                    tools++;
                    stage = Stage.RESTORE_ROD;
                    due = now + delay(random, c.returnBaseMs, c.returnBaseMs + c.returnSpreadMs, 1);
                    limit = due + 3000;
                    return Action.USE_TOOL;
                }
            }
            case RESTORE_ROD -> {
                if (now >= due) {
                    afterCatch(now, c);
                    return Action.RESTORE_ROD;
                }
            }
            default -> {}
        }
        return Action.NONE;
    }
}
