package com.farmmacro.camera;

import com.farmmacro.panic.PanicDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Плавный детерминированный поворот камеры к заданному yaw/pitch. С 1.6.0 — только для применения пресета
 * по клавише/кнопке; автоход и привязки камеры к макросу камеру сами плавно не крутят.
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

    private static final float EASE_DEG = 12f;

    private static boolean active;
    private static float targetYaw, targetPitch;
    private static double speed;
    private static long lastNs;
    /** v1.11.1 «как мышь» (camMouseLike): план поворота по пикселям мыши; null — обычный плавный. */
    private static MouseTurn mouse;

    /**
     * Начать (или перенацелить) поворот.
     * @param degPerSec ≤ 0 — мгновенно (в ближайший кадр)
     */
    public static void turnTo(float yaw, float pitch, double degPerSec) { turnTo(yaw, pitch, degPerSec, false); }

    /**
     * v1.15 «Возврат на маршрут»: поворот всегда «как мышь» ({@link MouseTurn}: пиксели при текущей чувствительности,
     * разгон/торможение, лёгкий перелёт), даже если в «Камере» выбран «плавно».
     */
    public static void turnMouse(float yaw, float pitch, double degPerSec) { turnTo(yaw, pitch, degPerSec, true); }

    public static float targetYaw()   { return targetYaw; }
    public static float targetPitch() { return targetPitch; }

    private static void turnTo(float yaw, float pitch, double degPerSec, boolean forceMouse) {
        if (!active) lastNs = 0;
        targetYaw = yaw;
        targetPitch = Mth.clamp(pitch, -90f, 90f);
        int jp = com.farmmacro.config.ModConfig.INSTANCE.smoothTurnJitterPct;
        speed = jp > 0 && degPerSec > 0
                ? degPerSec * (1 + java.util.concurrent.ThreadLocalRandom.current().nextDouble(-jp, jp) / 100.0) : degPerSec;
        mouse = null;
        com.farmmacro.config.ModConfig c = com.farmmacro.config.ModConfig.INSTANCE;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if ((c.camMouseLike || forceMouse) && degPerSec > 0 && p != null) {
            double sens = mc.options.sensitivity().get();
            mouse = new MouseTurn(p.getYRot(), p.getXRot(), Mth.wrapDegrees(targetYaw - p.getYRot()), targetPitch - p.getXRot(),
                    sens, speed, (forceMouse ? Math.max(1, c.camOvershootPct) : c.camOvershootPct) / 100.0, new java.util.SplittableRandom(System.nanoTime()));
        }
        active = true;
    }

    public static void stop()          { active = false; mouse = null; }
    public static boolean isActive()   { return active; }

    /** Сколько градусов осталось довернуть (0, если поворота нет). */
    public static float remaining(LocalPlayer p) {
        if (!active || p == null) return 0;
        float r = (float) Math.hypot(Mth.wrapDegrees(targetYaw - p.getYRot()), targetPitch - p.getXRot());
        return mouse != null ? r * (float) (1 + 2 * mouse.overshoot) : r;
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

        if (mouse != null) {
            long[] d = mouse.frame(dt);
            if (d[0] != 0 || d[1] != 0) apply(p, (float) (d[0] * mouse.step), (float) (d[1] * mouse.step));
            if (mouse.finished()) { active = false; mouse = null; }
            return;
        }
        float ey = Mth.wrapDegrees(targetYaw - p.getYRot());
        float ep = targetPitch - p.getXRot();
        double err = Math.hypot(ey, ep);
        if (err < 0.01) { active = false; return; }
        double step;
        if (speed <= 0) step = err;
        else {
            double v = speed * Math.max(0.2, Math.min(1.0, err / EASE_DEG));
            step = Math.min(err, v * dt);
            if (err - step < 0.02) step = err;          // добить остаток, а не ползти к нулю
        }
        float k = (float) (step / err);
        apply(p, ey * k, ep * k);
        if (step >= err) active = false;
    }

    /**
     * «Случайность → Оглядеться»: шаг {@link com.farmmacro.macro.Humanizer.Look} (на ходу — шум pitch, на остановке —
     * yaw и pitch) как движение мыши, детектору — как свой поворот. stopLeft — сколько ещё стоять (0 — идём).
     */
    public static void look(com.farmmacro.macro.Humanizer h, LocalPlayer p, int stopLeft) {
        if (h == null || p == null) return;
        if (h != lookOwner) { lookOwner = h; lookBase = false; }
        // v1.11.2: смещение ставится от «своего» угла абсолютно, а не суммой мелких поворотов — float-ошибки
        // не копятся, и когда смещение вернулось в 0, камера ровно там, где была (до 0.000°)
        if (lookBase) {                                   // кто-то другой повернул (привязка, пресет) — сдвигаем базу
            baseYaw += p.getYRot() - expYaw; basePitch += p.getXRot() - expPitch;
        } else if (h.look.atZero()) { baseYaw = p.getYRot(); basePitch = p.getXRot(); }
        h.look.tick(stopLeft, h.lapPerfect());
        float ty, tp;
        if (h.look.atZero()) { ty = baseYaw; tp = basePitch; }
        else { ty = baseYaw + (float) h.look.yawOff(); tp = Math.max(-90f, Math.min(90f, basePitch + (float) h.look.pitchOff())); }
        float y0 = p.getYRot(), x0 = p.getXRot();
        if (ty != y0 || tp != x0) {
            p.setYRot(ty); p.setXRot(tp);
            p.yRotO += ty - y0; p.xRotO += tp - x0;       // как Entity.turn — без рывка интерполяции
            PanicDetector.INSTANCE.expectTurn(ty - y0, tp - x0);
        }
        lookBase = !h.look.atZero();
        expYaw = ty; expPitch = tp;
    }

    private static com.farmmacro.macro.Humanizer lookOwner;
    private static boolean lookBase;
    private static float baseYaw, basePitch, expYaw, expPitch;

    /** Повернуть как мышь (Entity.turn принимает «сырые» единицы, ×0.15 внутри). */
    private static void apply(LocalPlayer p, float dYaw, float dPitch) {
        float y0 = p.getYRot(), x0 = p.getXRot();
        p.turn(dYaw / 0.15, dPitch / 0.15);
        PanicDetector.INSTANCE.expectTurn(p.getYRot() - y0, p.getXRot() - x0);
    }
}
