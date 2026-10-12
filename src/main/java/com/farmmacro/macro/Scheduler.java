package com.farmmacro.macro;

import com.farmmacro.config.ModConfig;
import com.farmmacro.panic.EventLog;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Random;

/**
 * v1.13.1 «Расписание» и «Таймер выхода».
 * Расписание: макрос играет N мин (± разброс) → штатный стоп «перерыв» (точка остановки сохраняется) → M мин (± разброс)
 * стоим → продолжить с места (как O). Любой стоп не от расписания (вручную, паника, End) — расписание сбрасывается.
 * Таймер выхода: через N мин после старта (перерывы считаются) — стоп и выход с сервера (переподключения не будет).
 */
public final class Scheduler {
    private Scheduler() {}

    public enum Phase { OFF, FARM, BREAK }

    private static Phase phase = Phase.OFF;
    private static long farmLeftTicks, breakLeftTicks;
    /** Начало сессии для таймера выхода (мс), 0 — нет сессии. */
    private static long sessionStartMs;
    private static final Random RND = new Random();

    public static Phase phase() { return phase; }
    public static long farmLeftTicks() { return farmLeftTicks; }
    public static long breakLeftTicks() { return breakLeftTicks; }

    /** До выхода, мс (−1 — таймер выключен или сессии нет). */
    public static long leaveLeftMs() {
        int m = ModConfig.INSTANCE.leaveAfterMin;
        if (m <= 0 || sessionStartMs == 0) return -1;
        return Math.max(0, sessionStartMs + m * 60_000L - System.currentTimeMillis());
    }

    /** Минуты ± разброс (pct %), не меньше 1. Чистая функция — проверка в BigPanicCheck. */
    public static double jitterMinutes(double base, int pct, double u01) {
        double k = 1 + (u01 * 2 - 1) * pct / 100.0;
        return Math.max(1, base * k);
    }

    private static long ticks(int minutes, int pct) {
        return Math.round(jitterMinutes(minutes, pct, RND.nextDouble()) * 1200);
    }

    /** End / ручной сброс. */
    public static void cancel() { phase = Phase.OFF; sessionStartMs = 0; }

    /** Каждый тик (и без игрока). */
    public static void tick(Minecraft mc) {
        ModConfig c = ModConfig.INSTANCE;
        MacroManager m = MacroManager.INSTANCE;
        boolean active = m.isActive();

        // ── таймер выхода ──
        if (c.leaveAfterMin > 0 && mc.player != null) {
            if (sessionStartMs == 0 && active) sessionStartMs = System.currentTimeMillis();
            if (sessionStartMs != 0 && !active && phase != Phase.BREAK) sessionStartMs = 0;   // остановили не расписанием
            if (sessionStartMs != 0 && leaveLeftMs() == 0) {
                sessionStartMs = 0;
                phase = Phase.OFF;
                EventLog.log("LEAVE", "таймер выхода " + c.leaveAfterMin + " мин");
                m.stopByLimit(mc, "§e⏏ Таймер выхода (" + c.leaveAfterMin + " мин) — выхожу с сервера");
                Reconnect.markIntentional();
                mc.disconnectFromWorld(Component.literal("FarmMacro: таймер выхода"));
                return;
            }
        } else if (c.leaveAfterMin <= 0) sessionStartMs = 0;

        // ── расписание ──
        if (!c.schedEnabled || mc.player == null) { if (phase != Phase.OFF && mc.player == null) phase = Phase.OFF; if (!c.schedEnabled) phase = Phase.OFF; return; }
        switch (phase) {
            case OFF -> {
                if (m.isPlaying()) { phase = Phase.FARM; farmLeftTicks = ticks(c.schedFarmMin, c.schedJitterPct); }
            }
            case FARM -> {
                if (!active) { phase = Phase.OFF; return; }                 // стоп вручную / паника
                if (m.isPlaying() && --farmLeftTicks <= 0) {
                    breakLeftTicks = ticks(c.schedBreakMin, c.schedJitterPct);
                    phase = Phase.BREAK;
                    String min = String.format(java.util.Locale.ROOT, "%.1f", breakLeftTicks / 1200.0);
                    EventLog.log("SCHED", "перерыв " + min + " мин");
                    m.stopByLimit(mc, "§e⏸ Расписание: перерыв " + min + " мин, потом продолжу с места");
                }
            }
            case BREAK -> {
                if (active) { phase = Phase.FARM; farmLeftTicks = ticks(c.schedFarmMin, c.schedJitterPct); return; }  // запустили сами
                if (--breakLeftTicks <= 0) {
                    EventLog.log("SCHED", "перерыв окончен — продолжаю");
                    m.resumeFromSaved(mc);
                    if (m.isActive()) { phase = Phase.FARM; farmLeftTicks = ticks(c.schedFarmMin, c.schedJitterPct); }
                    else { phase = Phase.OFF; sessionStartMs = 0; }
                }
            }
        }
    }
}
