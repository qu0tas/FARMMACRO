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
    /** Тики этого прохода и «хвост» на последней точке, чтобы длина круга не совпала с прошлым (−1 — ещё не решали). */
    private int passTicks, tail = -1;
    /** «Случайность» прохода (null — выкл). */
    private com.farmmacro.macro.Humanizer.RouteShaper shaper;
    /** v1.11 «Аномалия скорости». */
    private final VelocityCheck velocity = new VelocityCheck();
    private boolean lastOnGround, lastJump;
    /** Куда автоход вёл в последний тик (единичный вектор x, z) или null — стоит / не играет. */
    private double[] moveDir;

    private RouteVariants.Session variants;
    private PointRoute activeRoute;
    private int variantNumber, pathRevision;
    public int pathRevision() { return pathRevision; }
    public String activeName() { return activeRoute==null ? RouteBuffer.INSTANCE.route().name : activeRoute.name; }
    private boolean resumePass;
    public int variantNumber() { return variantNumber; }
    public com.farmmacro.macro.MacroSettings activeSettings() {
        return activeRoute==null ? RouteBuffer.INSTANCE.settings() : activeRoute.settings;
    }
    public long variantSeed() { return variants == null ? 0 : variants.variantSeed; }
    public java.util.List<RoutePoint> activePoints() { return activeRoute == null ? RouteBuffer.INSTANCE.points() : activeRoute.points; }
    public void beginRun(boolean resume) {
        if (!resume) { variants = null; activeRoute = null; variantNumber = 0; }
        resumePass=resume;
    }
    public void invalidate() {
        if (!started) { variants=null;activeRoute=null;variantNumber=0;resumePass=false; }
    }
    private void prepareVariants(Minecraft mc) {
        PointRoute ideal = RouteBuffer.INSTANCE.route();
        java.util.List<PointRoute> alternatives = new java.util.ArrayList<>();
        java.util.List<Double> weights = new java.util.ArrayList<>();
        for (var a : ideal.settings.routeRandom.alternatives) {
            PointRoute other = RouteStorage.INSTANCE.load(a.filename);
            if (!RouteVariants.compatible(ideal, other)) {
                MacroManager.INSTANCE.msgPublic(mc, "§eМаршрут «" + a.filename + "» пропущен: другой мир/измерение или старт дальше 2 бл");
                continue;
            }
            alternatives.add(other); weights.add(a.weight);
        }
        variants = new RouteVariants.Session(ideal, alternatives, weights);
    }
    private RouteWalker() {}

    // v1.13.1 «Анти-застревание»: отойти назад (обратно нажатым клавишам) с прыжком, потом снова по маршруту
    private static final int UNSTUCK_TICKS = 12;
    private int unstuckLeft, unstuckTries;
    private long lastUnstuckMs;
    private boolean lf, lb, ll, lr;              // клавиши хода прошлого тика
    private boolean uf, ub, ul, ur;              // клавиши манёвра

    @Override public int length() { return activeRoute == null ? RouteBuffer.INSTANCE.size() : activeRoute.points.size(); }

    @Override
    public double[] position(int index) {
        RoutePoint p = length() == 0 ? null : activePoints().get(Math.max(0, Math.min(length() - 1, index)));
        return p == null ? new double[]{0, 0, 0} : new double[]{p.x, p.y, p.z};
    }

    private final PointActionRuntime pointActions = new PointActionRuntime();
    public void startPass(Minecraft mc, int index) {
        pointActions.cancel();
        boolean newLap = activeRoute == null || !resumePass && index == 0 && (!started || core.done());
        resumePass=false;
        if (variants == null) prepareVariants(mc);
        PointRoute r = newLap ? variants.next(index==0) : activeRoute.copy();
        variantNumber = variants.number;
        if (newLap) {
            com.farmmacro.panic.EventLog.log("ROUTE_VARIANT", "маршрут " + r.name + ", вариант " + variantNumber + ", сид " + variants.variantSeed);
            var h = com.farmmacro.macro.Humanizer.lap();
            if (!RouteBuffer.INSTANCE.settings().routeRandom.alternatives.isEmpty())
                com.farmmacro.macro.MouseHold.INSTANCE.begin(h == null ? r.settings.hold : h.shapeHold(r.settings.hold), true);
        }
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
        if (human != null && newLap && RouteRandomSettings.OFF.equals(RouteBuffer.INSTANCE.settings().routeRandom.mode)) human.shapeRoute(r);
        if(!ModConfig.INSTANCE.uiAdvanced)for(var p:r.points)p.pointAction=new PointAction();
        activeRoute = r.copy(); pathRevision++;
        core.start(r, index, ModConfig.INSTANCE.stuckThresholdTicks);
        if (human != null) core.setReach(human.reachRadii(r.points.size(), ModConfig.INSTANCE.routeReachRadius));
        shaper = human != null ? human.routeShaper(r.points.size()).route(r.points) : null;
        camera = r.settings.camera;
        camTracker.reset();
        velocity.reset();
        unstuckLeft = 0;
        lastJump = false; lastOnGround = mc.player != null && mc.player.onGround(); moveDir = null;
        offTicks = 0; goodYaw = Float.NaN;
        int from = Math.max(0, core.segment());
        camTracker.update(mc, camera, from, "старт, точка " + (from + 1));
        started = true;
        passTicks = 0; tail = -1;
        int seg = core.segment();
        if (seg >= 0 && mc.player != null) applySlot(mc.player, core.points().get(seg).slot - 1);
    }

    @Override public boolean passDone() { return started && core.done() && (shaper == null || tail == 0); }
    @Override public int progress()      { return started ? core.target() : 0; }
    @Override public int resumeIndex()   { return started ? core.target() : 0; }
    @Override public boolean controlsCamera(ModConfig c) { return false; }

    @Override
    public void stop(Minecraft mc) {
        pointActions.cancel();
        started = false;
        offTicks = 0;
        shaper = null;
        moveDir = null;
    }

    /** Для детектора «Препятствие впереди»: направление хода автохода (x, z) или null. */
    public double[] moveDir() { return started ? moveDir : null; }

    /** Модель скорости не годится: в воздухе, прыжок, вода, лёд/песок душ, толчок/телепорт сервера, спуск, стена. */
    private void runPointAction(Minecraft mc,ModConfig c){int i=core.actionPoint();velocity.reset();offTicks=0;core.resetStuckWindow();if(pointActions.tick(mc,core.points().get(i),i,variantSeed())){core.completeAction();pointActions.cancel();com.farmmacro.macro.MouseHold.INSTANCE.suspend(false);}}

    private boolean velocityExcused(LocalPlayer p) {
        if (!p.onGround() || !lastOnGround || lastJump || p.horizontalCollision || p.isInWater() || p.isInLava()
                || p.isPassenger() || p.getAbilities().flying || p.isFallFlying() || p.isUsingItem() || p.isInPowderSnow
                || p.onClimbable() || core.dropPhase() != WalkCore.D_NONE || PanicDetector.INSTANCE.pushExcused()
                || (com.farmmacro.config.ModConfig.INSTANCE.lagGuard && com.farmmacro.panic.LagGuard.INSTANCE.lagging())) return true;
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
        if(core.actionPoint()>=0){runPointAction(mc,c);return false;}
        // «Случайность»: встать посреди пути / заминка — до решения WalkCore (он сам не жмёт клавиши и не ждёт «Застрял»)
        if (shaper != null) core.pauseExternal(shaper.stopNow(core.target(), core.dropPhase(), p.onGround(), core.pausing()));
        com.farmmacro.camera.SmoothTurn.look(human, p, core.externalPause() > 0 ? core.externalPause() + 1 : 0);
        if (unstuckLeft > 0) { unstuckTick(mc, p, o); return false; }
        
        boolean velOn = on && c.detectVelocity;
        String vel = velocity.observe(p.getX(), p.getZ(), !velOn || velocityExcused(p), c.velocityAnomaly);
        if (vel != null) {
            if (PanicDetector.INSTANCE.fire(mc, "velocity", vel)) return true;
        }
        if (on && c.lagGuard && com.farmmacro.panic.LagGuard.INSTANCE.lagging()) core.resetStuckWindow();   // v1.13: лаг — не «Застрял»
        core.tick(p.getX(), p.getY(), p.getZ(), p.onGround(), p.getYRot(), c.routeReachRadius,
                on && c.detectStuck, c.stuckThresholdTicks, on && c.detectDrift, c.driftThreshold, on && c.detectFloor, out);
        if(core.actionPoint()>=0){runPointAction(mc,c);return false;}
        if (core.dropPhase() != WalkCore.D_NONE) PanicDetector.INSTANCE.allowFall(10);  // урон от спуска — не паника
        // v1.15 «Возврат на маршрут»: толкнули/откатили далеко от линии (или на другой этаж) — дойти обратно своим путём
        if (rejoinCheck(mc, p, c)) return true;
        if (out.panic != null && out.panic.startsWith("Застрял") && c.antiStuckTries > 0) {
            long now = System.currentTimeMillis();
            if (now - lastUnstuckMs > 30_000) unstuckTries = 0;           // давно не застревал — попытки заново
            if (unstuckTries < c.antiStuckTries && (lf || lb || ll || lr)) {
                unstuckTries++;
                lastUnstuckMs = now;
                unstuckLeft = UNSTUCK_TICKS;
                uf = lb; ub = lf; ul = lr; ur = ll;                        // назад от того, куда шёл
                com.farmmacro.panic.EventLog.log("UNSTUCK", "попытка " + unstuckTries + "/" + c.antiStuckTries + ": " + out.panic);
                unstuckTick(mc, p, o);
                return false;
            }
            out.panic = out.panic + " (после " + unstuckTries + " попыток отойти)";
        }
        if (out.panic != null) {
            // v1.14.2-pre: через реестр детекторов (действие «Подозрительность»/«Журнал» — идём дальше)
            String id = out.panic.startsWith("Застрял") ? "stuck" : out.panic.startsWith("Сошёл") ? "drift" : "floor";
            // v1.15: уже решаем «вернуться на маршрут» (вне линии < REJOIN_TICKS) — «Сошёл»/«Не тот этаж» не поднимать
            if (offTicks > 0 && !"stuck".equals(id)) out.panic = null;
        }
        if (out.panic != null) {
            String id = out.panic.startsWith("Застрял") ? "stuck" : out.panic.startsWith("Сошёл") ? "drift" : "floor";
            if (PanicDetector.INSTANCE.fire(mc, id, out.panic)) return true;
        }
        passTicks++;
        if (core.done()) {
            release(o);
            // «без повторов подряд»: круг ровно как прошлый — постоять на последней точке ещё 1…3 тика
            if (shaper != null && human != null) {
                if (tail < 0) tail = human.lapTail(passTicks);
                else if (tail > 0) tail--;
            } else tail = 0;
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
        lf = out.forward; lb = out.back; ll = out.left; lr = out.right;
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

    /** Тик манёвра: 8 т назад (прыжок на 3-м), 4 т стоять; конец — окна «Застрял» и скорости заново. */
    private void unstuckTick(Minecraft mc, LocalPlayer p, Options o) {
        int t = UNSTUCK_TICKS - unstuckLeft;          // 0…11
        boolean move = t < 8;
        MacroManager.press(o.keyUp, move && uf);
        MacroManager.press(o.keyDown, move && ub);
        MacroManager.press(o.keyLeft, move && ul);
        MacroManager.press(o.keyRight, move && ur);
        MacroManager.press(o.keyJump, t == 2 || t == 3);
        MacroManager.press(o.keySprint, false);
        MacroManager.press(o.keyShift, false);
        MacroManager.press(o.keyAttack, false);
        MacroManager.press(o.keyUse, false);
        lastJump = true; lastOnGround = p.onGround();
        if (--unstuckLeft <= 0) {
            core.resetStuckWindow();
            velocity.reset();
            core.resteer(p.getX(), p.getZ(), p.getYRot(), out);
        }
    }

    private static void applySlot(LocalPlayer p, int slot) {
        MacroManager.selectSlot(p, slot);              // v1.15: с задержкой смены слота («Задержки рук»)
    }

    /** v1.15: сколько тиков подряд далеко от линии; последний взгляд, пока шли по линии. */
    private int offTicks;
    private float goodYaw = Float.NaN, goodPitch;
    /** Тиков подряд вне линии, после которых начинается возврат. */
    public static final int REJOIN_TICKS = 4;

    /** @return true — начат возврат (или стоп): дальше этот тик не играть */
    private boolean rejoinCheck(Minecraft mc, LocalPlayer p, ModConfig c) {
        double off = core.offLine(p.getX(), p.getZ());
        if (Double.isNaN(off)) { offTicks = 0; return false; }
        double ey = core.expectedY(p.getX(), p.getZ());
        boolean far = off > c.returnOffRoute || (p.onGround() && Math.abs(p.getY() - ey) > WalkCore.OTHER_FLOOR_DY);
        if (!far) {
            offTicks = 0;
            if (off < 0.8) { goodYaw = p.getYRot(); goodPitch = p.getXRot(); }
            return false;
        }
        if (!c.returnEnabled || !c.returnAfterPush) { offTicks = 0; return false; }
        if (++offTicks < REJOIN_TICKS) return false;
        offTicks = 0;
        int seg = Math.max(0, core.target() - 1);
        double t = core.segmentFraction(p.getX(), p.getZ());
        float yaw = Float.isNaN(goodYaw) ? p.getYRot() : goodYaw, pitch = Float.isNaN(goodYaw) ? p.getXRot() : goodPitch;
        String why = String.format(java.util.Locale.ROOT, "%.1f бл от линии%s", off,
                p.onGround() && Math.abs(p.getY() - ey) > WalkCore.OTHER_FLOOR_DY ? String.format(java.util.Locale.ROOT, ", по высоте %.1f", p.getY() - ey) : "");
        return MacroManager.INSTANCE.requestRejoin(mc, seg, t, yaw, pitch, why);
    }

    private static void release(Options o) {
        for (KeyMapping k : new KeyMapping[]{o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump,
                o.keySprint, o.keyShift, o.keyAttack, o.keyUse}) MacroManager.press(k, false);
    }
}
