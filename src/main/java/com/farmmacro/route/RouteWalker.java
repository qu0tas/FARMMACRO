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
 *  • точка-«спуск»: дойти, шагнуть по направлению спуска до обрыва, упасть, после приземления пауза, дальше — от места
 *    приземления; «Не тот этаж» / «Нет пути» — по высоте (детектор detectFloor);
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
    /** «Случайность» прохода (null — выкл). */
    private com.farmmacro.macro.Humanizer.RouteShaper shaper;
    /** v1.11 «Аномалия скорости». */
    private final VelocityCheck velocity = new VelocityCheck();
    private boolean lastOnGround, lastJump;
    /** Куда автоход вёл в последний тик (единичный вектор x, z) или null — стоит / не играет. */
    private double[] moveDir;

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
        // глубина спусков — заново по миру (точку могли сдвинуть, мир поменяться), только для этого прохода
        if (mc.level != null) {
            TerrainLevel w = new TerrainLevel(mc.level);
            for (int i = 0; i < r.points.size(); i++)
                if (r.points.get(i).drop) {
                    double keep = r.points.get(i).dropDepth;
                    if (Double.isNaN(Terrain.fillDepth(w, r.points, i))) r.points.get(i).dropDepth = keep;  // чанк не загружен — как в файле
                }
        }
        var human = com.farmmacro.macro.Humanizer.lap();       // null — выкл или идеальный круг (точно как маршрут)
        if (human != null) human.shapeRoute(r);
        core.start(r, index, ModConfig.INSTANCE.stuckThresholdTicks);
        if (human != null) core.setReach(human.reachRadii(r.points.size(), ModConfig.INSTANCE.routeReachRadius));
        shaper = human != null ? human.routeShaper(r.points.size()) : null;
        camera = r.settings.camera;
        camTracker.reset();
        velocity.reset();
        lastJump = false; lastOnGround = mc.player != null && mc.player.onGround(); moveDir = null;
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
        shaper = null;
        moveDir = null;
    }

    /** Для детектора «Препятствие впереди»: направление хода автохода (x, z) или null. */
    public double[] moveDir() { return started ? moveDir : null; }

    /** Модель скорости не годится: в воздухе, прыжок, вода, лёд/песок душ, толчок/телепорт сервера, спуск, стена. */
    private boolean velocityExcused(LocalPlayer p) {
        if (!p.onGround() || !lastOnGround || lastJump || p.horizontalCollision || p.isInWater() || p.isInLava()
                || p.isPassenger() || p.getAbilities().flying || p.isFallFlying() || p.isUsingItem() || p.isInPowderSnow
                || p.onClimbable() || core.dropPhase() != WalkCore.D_NONE || PanicDetector.INSTANCE.pushExcused()) return true;
        var lvl = p.level();
        var below = lvl.getBlockState(p.getBlockPosBelowThatAffectsMyMovement()).getBlock();
        var at = lvl.getBlockState(p.blockPosition()).getBlock();
        return Math.abs(below.getFriction() - 0.6f) > 1e-4 || Math.abs(below.getSpeedFactor() - 1f) > 1e-4
                || Math.abs(at.getSpeedFactor() - 1f) > 1e-4;
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
        var human = com.farmmacro.macro.Humanizer.ACTIVE;
        // «Случайность»: встать посреди пути / заминка — до решения WalkCore (он сам не жмёт клавиши и не ждёт «Застрял»)
        if (shaper != null) core.pauseExternal(shaper.stopNow(core.target(), core.dropPhase(), p.onGround(), core.pausing()));
        com.farmmacro.camera.SmoothTurn.look(human, p, core.externalPause() > 0 ? core.externalPause() + 1 : 0);
        boolean velOn = on && c.detectVelocity;
        String vel = velocity.observe(p.getX(), p.getZ(), !velOn || velocityExcused(p), c.velocityAnomaly);
        if (vel != null) {
            PanicDetector.INSTANCE.triggerPanic(mc, vel);
            return true;
        }
        core.tick(p.getX(), p.getY(), p.getZ(), p.onGround(), p.getYRot(), c.routeReachRadius,
                on && c.detectStuck, c.stuckThresholdTicks, on && c.detectDrift, c.driftThreshold, on && c.detectFloor, out);
        if (core.dropPhase() != WalkCore.D_NONE) PanicDetector.INSTANCE.allowFall(10);  // урон от спуска — не паника
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
        if (shaper != null) {
            int ss = core.segment();
            boolean airHold = ss >= 0 && ss < core.points().size() && core.points().get(ss).airHold;
            shaper.tick(out, p.getX(), p.getZ(), core.target(), core.dropPhase(), airHold);
            if (shaper.idleNow) core.resetStuckWindow();      // зазор/пауза на старте — не «Застрял»
        }
        boolean stopped = core.stoppedNow();
        if (stopped) { out.attack = false; out.use = false; }    // остановился — руки тоже отпустил
        if (human != null) human.stopLeftTicks = stopped ? core.externalPause() + 1 : 0;
        com.farmmacro.macro.MouseHold.INSTANCE.suspend(stopped);
        if (out.slot >= 0) applySlot(p, out.slot);
        int hs = core.segment();
        com.farmmacro.macro.MouseHold.INSTANCE.position(core.target(), hs >= 0 && hs < core.points().size() && core.points().get(hs).hold);
        // v1.11: в воде упёрся в бортик, а точка выше — держать прыжок, чтобы выплыть (канал у грядок)
        int tgt = core.target();
        if (!out.jump && (out.forward || out.back || out.left || out.right) && p.isInWater() && p.horizontalCollision
                && core.dropPhase() == WalkCore.D_NONE && tgt >= 0 && tgt < core.points().size()
                && core.points().get(tgt).y > p.getY() + 0.4) out.jump = true;
        velocity.command(out, p.getYRot());
        lastJump = out.jump; lastOnGround = p.onGround();
        boolean keys = out.forward || out.back || out.left || out.right;
        moveDir = keys ? WalkCore.moveDir(out, p.getYRot()) : null;
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
