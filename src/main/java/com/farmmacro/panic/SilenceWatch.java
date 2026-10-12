package com.farmmacro.panic;

/**
 * v1.14 «Тишина сервера» (идея — blazebot SilenceWatch, код свой): нет ни одного пакета от сервера дольше порога —
 * соединение умерло молча. Тогда закрываем его сами и считаем вылетом (дальше — Reconnect / «Отключение»).
 * Время прихода пакета пишет миксин Connection.channelRead0 (сетевой поток) — поля volatile.
 * Первые {@link #JOIN_GRACE_MS} после входа и {@link #REARM_MS} после срабатывания — не судим.
 * Без Minecraft — проверка PlayersCheck.
 */
public final class SilenceWatch {
    public static final SilenceWatch INSTANCE = new SilenceWatch();
    public static final long JOIN_GRACE_MS = 30_000, REARM_MS = 60_000;

    private volatile long lastPacketMs = -1, joinedMs = -1, firedMs = Long.MIN_VALUE / 2;

    public void onPacket(long nowMs) { lastPacketMs = nowMs; }
    public void onJoin(long nowMs) { joinedMs = nowMs; lastPacketMs = nowMs; }
    public void reset() { lastPacketMs = -1; joinedMs = -1; }

    /** Сколько мс нет ни одного пакета (−1 — не на сервере). */
    public long silentMs(long nowMs) { long l = lastPacketMs; return l < 0 ? -1 : nowMs - l; }

    /** Пора закрыть соединение (и запомнить срабатывание). thresholdMs ≤ 0 — выкл. */
    public boolean shouldClose(long nowMs, long thresholdMs) {
        if (thresholdMs <= 0 || lastPacketMs < 0 || joinedMs < 0) return false;
        if (nowMs - joinedMs < JOIN_GRACE_MS || nowMs - firedMs < REARM_MS) return false;
        if (nowMs - lastPacketMs < thresholdMs) return false;
        firedMs = nowMs;
        return true;
    }
}
