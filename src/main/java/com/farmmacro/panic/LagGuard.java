package com.farmmacro.panic;

/**
 * v1.13 «Лаги сервера»: время между пакетами времени мира (ClientboundSetTimePacket, сервер шлёт их раз в секунду).
 * Пакетов нет дольше {@link #LAG_MS} — сервер лагает (v1.14: тишина > 30 с — лаг, только если молчат и все остальные пакеты); после лага ещё {@link #AFTER_MS} считаем «только что лагало».
 * Пока лагает, «Застрял» и «Аномалия скорости» не паникуют, а мелкие откаты идут в подозрительность.
 *
 * Время меряется в сетевом потоке (момент прихода пакета), читается из главного — поэтому synchronized.
 * Без Minecraft — проверяется в routeStress (LagCheck).
 */
public final class LagGuard {
    public static final LagGuard INSTANCE = new LagGuard();

    public static final long LAG_MS = 1300, AFTER_MS = 900;
    /** Дольше этого пакетов нет — сервер их просто не шлёт (не лаг): ждём снова регулярных пакетов. */
    public static final long SILENT_MS = 30_000;
    private static final int TPS_SAMPLES = 5, REGULAR_MIN = 3;

    private long lastMs = -1, lastGame, afterUntil;
    private int regular;                                  // сколько пакетов подряд пришли с нормальным интервалом
    private final double[] tps = new double[TPS_SAMPLES];
    private int tpsCount, tpsPos;

    /** Пакет времени пришёл в момент nowMs (мс), серверное время мира gameTime (тики). */
    public synchronized void onTime(long nowMs, long gameTime) {
        if (lastMs >= 0) {
            long dt = nowMs - lastMs;
            if (dt > SILENT_MS) regular = 0;
            else {
                if (dt > LAG_MS) afterUntil = nowMs + AFTER_MS;
                if (regular < REGULAR_MIN) regular++;
                long dg = gameTime - lastGame;
                if (dt > 0 && dg >= 0 && dg < 20 * 600) {
                    tps[tpsPos] = Math.min(20.0, dg * 1000.0 / dt);
                    tpsPos = (tpsPos + 1) % TPS_SAMPLES;
                    if (tpsCount < TPS_SAMPLES) tpsCount++;
                }
            }
        }
        lastMs = nowMs; lastGame = gameTime;
    }

    /** Сервер лагает сейчас или только что (≤ AFTER_MS назад) перестал. */
    public synchronized boolean lagging(long nowMs) { return lagging(nowMs, -1); }

    /**
     * v1.14: с учётом «Тишины сервера». anySilentMs — сколько нет вообще никаких пакетов (−1 — неизвестно).
     * Пакетов времени нет дольше SILENT_MS: если другие пакеты идут — сервер просто не шлёт время (не лаг);
     * если молчит всё — это лаг (а дальше «Тишина сервера» закроет соединение).
     */
    public synchronized boolean lagging(long nowMs, long anySilentMs) {
        if (lastMs < 0 || regular < REGULAR_MIN) return false;      // сервер ещё не показал, что шлёт время регулярно
        long gap = nowMs - lastMs;
        if (gap > SILENT_MS) return anySilentMs > LAG_MS;
        return gap > LAG_MS || nowMs < afterUntil;
    }

    public boolean lagging() {
        long now = System.currentTimeMillis();
        return lagging(now, SilenceWatch.INSTANCE.silentMs(now));
    }

    /** Сколько мс нет пакета времени (−1 — ещё не было). */
    public synchronized long silentMs(long nowMs) { return lastMs < 0 ? -1 : nowMs - lastMs; }

    /** Средний TPS по последним пакетам; NaN — мало данных. */
    public synchronized double tps() {
        if (tpsCount == 0) return Double.NaN;
        double s = 0;
        for (int i = 0; i < tpsCount; i++) s += tps[i];
        return s / tpsCount;
    }

    /** Новый сервер / отключение. */
    public synchronized void reset() {
        lastMs = -1; lastGame = 0; afterUntil = 0; regular = 0; tpsCount = 0; tpsPos = 0;
    }
}
