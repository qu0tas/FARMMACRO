package com.farmmacro.panic;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * v1.14: сколько каждый «ключ» (игрок) непрерывно находится в зоне. Ушёл — счёт заново.
 * «Игрок рядом: задержка N с», «Игрок без имени дольше 1 с». Без Minecraft — PlayersCheck.
 */
public final class DwellTimer<K> {
    private final Map<K, Long> since = new HashMap<>();

    /** Кто сейчас в зоне. @return первый, кто стоит ≥ dwellMs (или null) */
    public K update(long nowMs, Set<K> inside, long dwellMs) {
        since.keySet().retainAll(inside);
        K hit = null;
        for (K k : inside) {
            long s = since.computeIfAbsent(k, x -> nowMs);
            if (hit == null && nowMs - s >= dwellMs) hit = k;
        }
        return hit;
    }

    public long elapsed(K k, long nowMs) { Long s = since.get(k); return s == null ? -1 : nowMs - s; }
    public void clear() { since.clear(); }
}
