package com.farmmacro.panic;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * v1.14: «очередь» блоков от сервера — сколько разных позиций за окно (бедрок очередью, клетка по числу блоков).
 * Без Minecraft — проверка PlayersCheck.
 */
public final class BurstWatch {
    private final ArrayDeque<long[]> events = new ArrayDeque<>();   // {ms, pos}

    public void add(long nowMs, long pos) {
        if (events.size() < 512) events.addLast(new long[]{nowMs, pos});
    }

    /** Разных позиций за последние windowMs. */
    public int count(long nowMs, long windowMs) {
        while (!events.isEmpty() && nowMs - events.peekFirst()[0] > windowMs) events.pollFirst();
        Set<Long> s = new HashSet<>();
        for (long[] e : events) s.add(e[1]);
        return s.size();
    }

    public void clear() { events.clear(); }
}
