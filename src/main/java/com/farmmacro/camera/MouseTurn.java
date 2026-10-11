package com.farmmacro.camera;

import java.util.SplittableRandom;

/**
 * v1.11.1: поворот «как мышь» — без зависимостей от игры (проверяется в routeStress, MouseTurnCheck).
 * Поворот разбит на «пиксели» мыши: один пиксель = {@link #pixelDeg(double)} градусов при текущей чувствительности
 * (как в {@code MouseHandler.turnPlayer}: (s·0.6+0.2)³·8 сырых единиц, {@code Entity.turn} умножает на 0.15).
 * Шаг крупнее {@link #MAX_STEP_DEG} делится на равные части (как мышь с высоким DPI), чтобы итог был ближе 0.2°.
 * Итоговый угол = начальный + целое число шагов, поэтому он не «ровный» (не 135.000°): если всё же вышел бы ровным
 * (кратным 0.1°), берётся соседний шаг.
 * Профиль скорости — разгон и торможение (минимальный рывок: 10u³ − 15u⁴ + 6u⁵), средняя скорость = заданной;
 * небольшой перелёт (0…overshootMax, не больше 3 %) и короткая доводка обратно.
 */
public final class MouseTurn {
    /** Шаг не больше этого (градусов): при большей чувствительности пиксель делится на части. */
    public static final double MAX_STEP_DEG = 0.13;
    /** Перелёт — не больше 3 % от поворота. */
    public static final double OVERSHOOT_LIMIT = 0.03;

    /** Градусов на один пиксель мыши при чувствительности sens (0…1, как в настройках игры: 0.5 = 100 %). */
    public static double pixelDeg(double sens) {
        double s = sens * 0.6000000238418579 + 0.20000000298023224;
        return s * s * s * 8.0 * 0.15;
    }

    /** Шаг поворота: пиксель или его равная часть (не больше MAX_STEP_DEG). */
    public static double stepDeg(double sens) {
        double px = pixelDeg(sens);
        int k = (int) Math.max(1, Math.ceil(px / MAX_STEP_DEG - 1e-9));
        return px / k;
    }

    public final double step;
    /** Сколько шагов по yaw/pitch в итоге и в точке перелёта. */
    public final long totalYaw, totalPitch, aimYaw, aimPitch;
    public final double overshoot;
    private final double t1, t2;
    private double t;
    private long doneYaw, donePitch;
    private boolean finished;

    /**
     * @param yaw0/pitch0 откуда (текущий взгляд), dYaw/dPitch — сколько повернуть (yaw уже через wrapDegrees)
     * @param degPerSec средняя скорость; ≤ 0 — за один кадр
     * @param overshootMax доля 0…0.03
     */
    public MouseTurn(double yaw0, double pitch0, double dYaw, double dPitch, double sens, double degPerSec,
                     double overshootMax, SplittableRandom rnd) {
        step = stepDeg(sens);
        long ny = Math.round(dYaw / step), np = Math.round(dPitch / step);
        ny = avoidRound(yaw0, ny, dYaw);
        np = avoidRound(pitch0, np, dPitch);
        totalYaw = ny; totalPitch = np;
        double len = Math.hypot(ny, np) * step;
        double ov = 0;
        if (len > 0 && overshootMax > 0) {
            // запас на округление до шага: итоговый перелёт всё равно ≤ 3 %
            double room = Math.min(overshootMax, OVERSHOOT_LIMIT) - 1.25 * step / len;
            if (room > 0) ov = rnd.nextDouble() * room;
        }
        overshoot = ov;
        aimYaw = Math.round(ny * (1 + ov));
        aimPitch = Math.round(np * (1 + ov));
        if (degPerSec <= 0 || len == 0) { t1 = 0; t2 = 0; }
        else {
            t1 = Math.max(0.05, len * (1 + ov) / degPerSec);
            t2 = ov > 0 ? Math.max(0.06, Math.min(0.35, len * ov / (degPerSec * 0.35))) : 0;
        }
    }

    /** Итог кратный 0.1° (±0.006°) — взять соседний шаг ближе к цели. */
    private long avoidRound(double from, long n, double want) {
        double end = from + n * step;
        double tenth = end * 10;
        if (Math.abs(tenth - Math.rint(tenth)) * 0.1 > 0.006) return n;
        double err = want - n * step;
        return n + (err >= 0 ? 1 : -1);
    }

    private static double ease(double u) {
        u = Math.max(0, Math.min(1, u));
        return u * u * u * (10 + u * (-15 + 6 * u));
    }

    /** Кадр: сколько шагов повернуть сейчас {yaw, pitch}. dt — секунд с прошлого кадра. */
    public long[] frame(double dt) {
        if (finished) return new long[]{0, 0};
        t += Math.max(0, dt);
        long wy, wp;
        if (t1 <= 0 || t >= t1 + t2) { wy = totalYaw; wp = totalPitch; finished = true; }
        else if (t < t1) { double e = ease(t / t1); wy = Math.round(aimYaw * e); wp = Math.round(aimPitch * e); }
        else {
            double e = ease((t - t1) / t2);
            wy = Math.round(aimYaw + (totalYaw - aimYaw) * e); wp = Math.round(aimPitch + (totalPitch - aimPitch) * e);
        }
        long[] d = {wy - doneYaw, wp - donePitch};
        doneYaw = wy; donePitch = wp;
        return d;
    }

    public boolean finished() { return finished; }
    /** Длительность всего поворота, секунд. */
    public double duration() { return t1 + t2; }
}
