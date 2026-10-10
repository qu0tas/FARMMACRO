package com.farmmacro.route;

import com.farmmacro.config.ModConfig;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.macro.PlaybackSource;
import com.farmmacro.panic.PanicDetector;
import com.farmmacro.visual.RoutePath;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;

/**
 * Автоход по маршруту из точек — источник воспроизведения для MacroManager. Решения принимает {@link WalkCore}:
 *  • камеру сам НЕ поворачивает: yaw/pitch остаются такими, какие поставил игрок; если у маршрута выбран пресет
 *    камеры — он ставится мгновенно при старте/на точке смены (PointRoute.settings.camera), дальше yaw/pitch не меняются;
 *  • направление к точке раскладывается по осям текущего yaw и нажимается клавишами W/A/S/D (8 направлений);
 *  • точка достигнута в радиусе {@code routeReachRadius} по XZ или если её уже проскочили по ходу отрезка;
 *  • в точке её параметры (действие, присед, спринт, слот, pitch) включаются на отрезок до следующей, затем пауза
 *    и один прыжок, если заданы;
 *  • препятствия не обходит: «вперёд» нажат, а игрок почти не сдвинулся за {@code stuckThresholdTicks} —
 *    паника «Застрял»; отошёл от линии отрезка дальше {@code driftThreshold} — «Сошёл с маршрута».
 * Своих поворотов у автохода нет, поэтому детектор «Камера повернулась» ловит любое движение мыши.
 */
public final class RouteWalker implements PlaybackSource {
    public static final RouteWalker INSTANCE = new RouteWalker();

    private final WalkCore core = new WalkCore();
    private final WalkCore.Out out = new WalkCore.Out();
    private final com.farmmacro.camera.CameraBinding.Tracker camTracker = new com.farmmacro.camera.CameraBinding.Tracker();
    private com.farmmacro.camera.CameraBinding camera;
    private boolean started;

    private RouteWalker() {}

    @Override public int length() { return RouteBuffer.INSTANCE.size(); }

    @Override
    public double[] position(int index) {
        RoutePoint p = RouteBuffer.INSTANCE.get(Math.max(0, Math.min(length() - 1, index)));
        return p == null ? new double[]{0, 0, 0} : new double[]{p.x, p.y, p.z};
    }

    @Override
    public void startPass(Minecraft mc, int index) {
        PointRoute r = RouteBuffer.INSTANCE.route().copy();
        core.start(r, index, ModConfig.INSTANCE.stuckThresholdTicks);
        camera = r.settings.camera;
        camTracker.reset();
        int from = Math.max(0, core.segment());
        camTracker.update(mc, camera, from, "старт, точка " + (from + 1));
        started = true;
        int seg = core.segment();
        if (seg >= 0 && mc.player != null) applySlot(mc.player, core.points().get(seg).slot - 1);
    }

    @Override public boolean passDone() { return started && core.done(); }
    @Override public int progress()      { return started ? core.target() : 0; }
    @Override public int resumeIndex()   { return started ? core.target() : 0; }
    @Override public boolean controlsCamera(ModConfig c) { return false; }

    @Override
    public void stop(Minecraft mc) {
        started = false;
    }

    /** Для RouteRenderer: (target − 1 + доля пройденного отрезка) × FRAME_SCALE. */
    public int progressFrame() {
        if (!started || core.points().isEmpty()) return -1;
        if (core.done()) return (core.points().size() - 1) * RoutePath.FRAME_SCALE;
        if (core.target() == 0) return 0;
        LocalPlayer p = Minecraft.getInstance().player;
        double f = p == null ? 0 : core.segmentFraction(p.getX(), p.getZ());
        return (int) Math.round((core.target() - 1 + f) * RoutePath.FRAME_SCALE);
    }

    @Override
    public boolean tick(Minecraft mc, ModConfig c) {
        LocalPlayer p = mc.player;
        Options o = mc.options;
        boolean on = c.panicEnabled;
        core.tick(p.getX(), p.getZ(), p.getYRot(), c.routeReachRadius,
                on && c.detectStuck, c.stuckThresholdTicks, on && c.detectDrift, c.driftThreshold, out);
        if (out.panic != null) {
            PanicDetector.INSTANCE.triggerPanic(mc, out.panic);
            return true;
        }
        if (core.done()) {
            release(o);
            return false;
        }
        // точка смены камеры: «с точки N» — когда дошли до N (отрезок от неё), мгновенно, затем те же оси
        int seg = Math.max(0, core.segment());
        if (camTracker.update(mc, camera, seg, "точка " + (seg + 1))) core.resteer(p.getX(), p.getZ(), p.getYRot(), out);
        if (out.slot >= 0) applySlot(p, out.slot);
        int hs = core.segment();
        com.farmmacro.macro.MouseHold.INSTANCE.position(core.target(), hs >= 0 && hs < core.points().size() && core.points().get(hs).hold);
        MacroManager.press(o.keyUp, out.forward);
        MacroManager.press(o.keyDown, out.back);
        MacroManager.press(o.keyLeft, out.left);
        MacroManager.press(o.keyRight, out.right);
        MacroManager.press(o.keySprint, out.sprint);
        MacroManager.press(o.keyShift, out.sneak);
        MacroManager.press(o.keyAttack, out.attack);
        MacroManager.press(o.keyUse, out.use);
        MacroManager.press(o.keyJump, out.jump);
        return false;
    }

    private static void applySlot(LocalPlayer p, int slot) {
        if (slot < 0 || slot > 8) return;
        if (p.getInventory().getSelectedSlot() != slot) p.getInventory().setSelectedSlot(slot);
        PanicDetector.INSTANCE.expectSlot(slot);
    }

    private static void release(Options o) {
        for (KeyMapping k : new KeyMapping[]{o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump,
                o.keySprint, o.keyShift, o.keyAttack, o.keyUse}) MacroManager.press(k, false);
    }
}
