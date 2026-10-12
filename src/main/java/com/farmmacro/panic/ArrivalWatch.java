package com.farmmacro.panic;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * v1.14 «Массовый телепорт» (идея — blazebot, код свой): N разных игроков «появились» ближе near бл за окно.
 * Появился = сейчас ближе near, а на прошлом тике был дальше far (или его не было видно).
 * Не считается в первые worldGraceMs в мире и selfGraceMs после своего телепорта/варпа.
 * Без Minecraft — проверка PlayersCheck.
 */
public final class ArrivalWatch {
    public static final double FAR = 30;
    public static final long SELF_GRACE_MS = 3000;

    private Map<Integer, Double> prev = new HashMap<>();
    private final ArrayDeque<long[]> arrivals = new ArrayDeque<>();   // {ms, id}
    private long worldSinceMs = -1, selfMoveMs = Long.MIN_VALUE / 2;

    public void onWorld(long nowMs) { worldSinceMs = nowMs; prev.clear(); arrivals.clear(); }
    public void onSelfTeleport(long nowMs) { selfMoveMs = nowMs; arrivals.clear(); }
    public void reset() { prev.clear(); arrivals.clear(); }

    /**
     * Один тик. dist — расстояние до каждого видимого игрока (id сущности → блоки), без себя (и без друзей, если надо).
     * @return сколько разных игроков появилось за окно (0 — не считаем: начало мира/свой телепорт)
     */
    public int tick(long nowMs, Map<Integer, Double> dist, double near, long windowMs, long worldGraceMs) {
        for (var e : dist.entrySet()) {
            if (e.getValue() > near) continue;
            Double was = prev.get(e.getKey());
            if (was == null || was > FAR) arrivals.addLast(new long[]{nowMs, e.getKey()});
        }
        prev = new HashMap<>(dist);
        while (!arrivals.isEmpty() && nowMs - arrivals.peekFirst()[0] > windowMs) arrivals.pollFirst();
        if (worldSinceMs < 0 || nowMs - worldSinceMs < worldGraceMs) return 0;
        if (nowMs - selfMoveMs < SELF_GRACE_MS) return 0;
        Set<Long> ids = new LinkedHashSet<>();
        for (long[] a : arrivals) ids.add(a[1]);
        return ids.size();
    }
}
