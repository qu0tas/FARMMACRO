package com.farmmacro.panic;

import java.util.Set;

/** Short continuation of an already-running farm only; unknown and critical alarms fail closed. */
public final class FarmStopDelay {
    private static final Set<String> SOFT = Set.of(
            "playerNear",
            "staff",
            "chat",
            "nearbyChat",
            "pm",
            "title",
            "spectator",
            "playerJoin",
            "twin",
            "nameless",
            "afk",
            "bps",
            "farmingXp",
            "suspicion");

    public static boolean soft(String id) {
        return id != null && SOFT.contains(id);
    }

    public static boolean eligible(String id, boolean farmPlaying, boolean combat, boolean enabled) {
        return enabled && farmPlaying && !combat && soft(id);
    }

    private String id, reason;
    private long started, duration;
    private boolean active;

    public boolean active() {
        return active;
    }

    public String id() {
        return id;
    }

    public String reason() {
        return reason;
    }

    public void arm(String id, String reason, long now, int millis) {
        if (active) return; // first cause wins; repeated alarms cannot move the deadline
        this.id = id;
        this.reason = reason;
        started = now;
        duration = Math.clamp(millis, 0, 1200) * 1_000_000L;
        active = true;
    }

    public boolean due(long now) {
        return active && now - started >= duration;
    }

    public void clear() {
        active = false;
        id = reason = null;
        started = duration = 0;
    }
}
