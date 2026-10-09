package com.farmmacro.camera;

import com.farmmacro.panic.PanicDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Плавный детерминированный поворот камеры к заданному yaw/pitch.
 *
 * Вызывается каждый кадр сразу после того, как игра применила движение мыши
 * ({@code MouseHandler.handleAccumulatedMovement}, миксин {@code CameraTurnMixin}), и поворачивает игрока тем же
 * {@code Entity.turn}, что и мышь — поэтому картинка плавная при любом FPS.
 *
 * Скорость постоянная (градусов в секунду), в последних {@link #EASE_DEG}° линейно замедляется до 20 %.
 * Никакой случайности: один и тот же поворот всегда идёт одинаково.
 *
 * Каждый свой шаг сообщается детектору паники ({@link PanicDetector#expectTurn}) как приращение,
 * поэтому движение мыши поверх поворота макроса по-прежнему ловится.
 */
public final class SmoothTurn {
    private SmoothTurn() {}

    public enum Owner { PRESET, WALKER }

    private static final float EASE_DEG = 12f;

    private static boolean active;
    private static Owner owner;
    private static float targetYaw, targetPitch;
    private static double speed;
    private static long lastNs;

    /**
     * Начать (или перенацелить) поворот.
     * @param degPerSec ≤ 0 — мгновенно (в ближайший кадр)
     */
    public static void turnTo(float yaw, float pitch, double degPerSec, Owner who) {
        if (!active) lastNs = 0;
        targetYaw = yaw;
        targetPitch = Mth.clamp(pitch, -90f, 90f);
        speed = degPerSec;
        owner = who;
        active = true;
    }

    public static void stop(Owner who) { if (active && owner == who) active = false; }
    public static void stopAll()       { active = false; }
    public static boolean isActive(Owner who) { return active && owner == who; }
    public static boolean isActive()   { return active; }

    /** Сколько градусов осталось довернуть (0, если поворота нет). */
    public static float remaining(LocalPlayer p) {
        if (!active || p == null) return 0;
        return (float) Math.hypot(Mth.wrapDegrees(targetYaw - p.getYRot()), targetPitch - p.getXRot());
    }

    /** Каждый кадр, после мыши. */
    public static void onFrame() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        long now = System.nanoTime();
        if (!active || p == null) { lastNs = 0; if (p == null) active = false; return; }
        if (mc.isPaused()) { lastNs = now; return; }
        double dt = lastNs == 0 ? 1 / 60.0 : Math.min(0.1, (now - lastNs) / 1e9);
        lastNs = now;

        float ey = Mth.wrapDegrees(targetYaw - p.getYRot());
        float ep = targetPitch - p.getXRot();
        double err = Math.hypot(ey, ep);
        if (err < 0.01) {
            if (owner == Owner.PRESET) active = false;
            return;
        }
        double step;
        if (speed <= 0) step = err;
        else {
            double v = speed * Math.max(0.2, Math.min(1.0, err / EASE_DEG));
            step = Math.min(err, v * dt);
            if (err - step < 0.02) step = err;          // добить остаток, а не ползти к нулю
        }
        float k = (float) (step / err);
        apply(p, ey * k, ep * k);
        if (owner == Owner.PRESET && step >= err) active = false;
    }

    /** Повернуть как мышь (Entity.turn принимает «сырые» единицы, ×0.15 внутри). */
    private static void apply(LocalPlayer p, float dYaw, float dPitch) {
        float y0 = p.getYRot(), x0 = p.getXRot();
        p.turn(dYaw / 0.15, dPitch / 0.15);
        PanicDetector.INSTANCE.expectTurn(p.getYRot() - y0, p.getXRot() - x0);
    }
}
