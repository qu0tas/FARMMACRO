package com.farmmacro.combat;

import java.util.*;

/** Separate continuous intervals: no accumulation across observers, look-away or long client stalls. */
public final class GazeTimer {
    private record Sample(long first, long last) {}

    private final Map<UUID, Sample> samples = new HashMap<>();

    public boolean sample(UUID id, boolean looking, long now, double seconds) {
        if (!looking) {
            samples.remove(id);
            return false;
        }
        var s = samples.get(id);
        if (s == null || now < s.last || now - s.last > 750_000_000L) s = new Sample(now, now);
        samples.put(id, new Sample(s.first, now));
        return now - s.first >= Math.clamp(seconds, 5, 10) * 1_000_000_000L;
    }

    public void retain(Set<UUID> present) {
        samples.keySet().retainAll(present);
    }

    public void clear() {
        samples.clear();
    }
}
