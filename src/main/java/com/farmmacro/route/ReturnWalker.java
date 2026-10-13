package com.farmmacro.route;

import com.farmmacro.config.ModConfig;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.panic.EventLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;

import java.util.List;
import java.util.Locale;

/**
 * v1.15 «Возврат на маршрут» в игре: путь {@link PathFinder} по миру ({@link PathLevel}), ходьба {@link ReturnCore}
 * клавишами W/A/S/D, камера — «как мышь» ({@link com.farmmacro.camera.SmoothTurn#turnMouse}) по ходу и в конце —
 * к камере маршрута; «Случайность» смазывает смены клавиш (RouteShaper), как у автохода.
 * Сбили с пути / застрял / упал — новый путь с текущего места (до {@code returnReplans} раз).
 * Не нашёл — {@link Status#FAIL}: MacroManager делает обычный стоп с сообщением (не паника).
 * ЛКМ/ПКМ во время возврата отпущены, слот не меняется.
 * <p>v1.16.2: путь ищется в фоне ({@link PathJob}) — пока ищем, стоим (клавиши отпущены), HUD — «ищу путь…».
 * Лестницы/лианы/леса — лезем (ReturnCore); закрытая деревянная дверь/калитка впереди по пути — смотрим на неё и ПКМ.
 */
public final class ReturnWalker {
    public enum Status { RUNNING, DONE, FAIL }

    private final ReturnCore core = new ReturnCore();
    private final WalkCore.Out out = new WalkCore.Out();
    private List<PathFinder.Goal> goals;
    private boolean active;
    private int replans;
    private java.util.Map<CachedPathWorld.Cell,Short> cachedCells=java.util.Map.of();
    private int cachedWait;
    public boolean waitingForChunk() { return cachedWait>0; }
    public boolean usingCache() { return !cachedCells.isEmpty(); }
    private String fail;
    private PathFinder.Goal goal;
    private com.farmmacro.macro.Humanizer.RouteShaper shaper;
    private float issuedYaw = Float.NaN, issuedPitch = Float.NaN;
    private int ticks;
    /** v1.15.1 полёт: не вышло взлететь — дальше без полёта; двойной прыжок (такт, попытки). */
    private boolean noFly;
    private int tap, tapTries;
    public static final int TAP_TRIES = 3;
    /** Последний путь (для HUD и отрисовки). */
    private List<RoutePoint> path = List.of();
    private String what = "";
    /** v1.16.2: поиск в фоне — что делать с результатом. */
    private PathJob job;
    private int jobKind, jobTicks;
    private static final int J_BEGIN = 0, J_REPLAN = 1, J_NOFLY = 2;
    /** Поиск дольше этого (тиков) — стоп с причиной. */
    public static final int JOB_MAX_TICKS = 400;
    private java.util.function.IntFunction<float[]> endCam;
    private boolean failedPlanning;
    /** v1.16.2 двери: какую открываем, тиков на неё, нажатий ПКМ, тиков держать ПКМ, пауза до следующего нажатия. */
    private net.minecraft.core.BlockPos door;
    private int doorTicks, doorClicks, useHold, useWait;
    private float doorYaw = Float.NaN, doorPitch = Float.NaN;
    public static final double DOOR_REACH = 3.0;

    public boolean active()      { return active; }
    public String failText()     { return fail; }
    public PathFinder.Goal goal() { return goal; }
    public int replans()         { return replans; }
    public List<RoutePoint> path() { return active ? path : List.of(); }
    public String what()         { return what; }
    /** v1.16.2: идёт поиск пути (в фоне). */
    public boolean planning()    { return active && job != null; }
    /** Открываем дверь/калитку. */
    public boolean openingDoor() { return active && door != null; }
    /** Не нашёлся самый первый путь (а не сбили по дороге) — MacroManager может стартовать без подхода. */
    public boolean failedPlanning() { return failedPlanning; }

    /** Остаток пути, блоков. */
    public double remaining(LocalPlayer p) { return active && p != null ? core.remaining(p.getX(), p.getZ()) : 0; }
    public boolean turningToRoute() { return active && core.phase() == ReturnCore.Phase.TURN; }

    /** Параметры поиска из настроек. */
    public static PathFinder.Params params() { return params(null, true); }

    /** fly — разрешить полёт (если включён «Есть полёт» и игрок может летать). */
    public static PathFinder.Params params(LocalPlayer pl, boolean fly) {
        ModConfig c = ModConfig.INSTANCE;
        PathFinder.Params p = new PathFinder.Params();
        p.maxDist = c.returnMaxDist;
        p.maxNodes = 80_000;
        p.fly = fly && c.returnFly && (pl == null || pl.getAbilities().mayfly || pl.getAbilities().flying);
        p.flyCeil = c.returnFlyCeil;
        p.flyingNow = pl != null && pl.getAbilities().flying;
        return p;
    }

    /** Найти путь от игрока (без запуска) — для проверки «можно ли дойти». */
    public static PathFinder.Result plan(Minecraft mc, List<PathFinder.Goal> goals) { return plan(mc, goals, true); }

    public static PathFinder.Result plan(Minecraft mc, List<PathFinder.Goal> goals, boolean fly) {
        PathJob j = PathJob.start(mc, goals, fly);
        PathFinder.Result r = j.result();
        logPlan(r, j);
        return r;
    }

    private static void logPlan(PathFinder.Result r, PathJob j) {
        EventLog.log("RETURN", "поиск пути: " + r + (r.cachedCells.isEmpty() ? "" : " · по кэшу") + String.format(Locale.ROOT, " (%d клеток, %.1f мс в фоне; снимок %d секций — %.1f мс)",
                r.expanded, r.micros / 1000.0, j.sections, j.snapMicros / 1000.0));
    }

    /**
     * Начать: путь ищется в фоне, потом идём. endCam(join) — камера, с которой продолжится маршрут (join — точка,
     * куда придём; известна после поиска). Не нашли — {@link #tick} вернёт FAIL ({@link #failText}, {@link #failedPlanning}).
     */
    public void begin(Minecraft mc, List<PathFinder.Goal> goals, java.util.function.IntFunction<float[]> endCam, String what) {
        this.goals = goals;
        this.what = what;
        this.endCam = endCam;
        replans = 0; fail = null; ticks = 0; noFly = false; tap = 0; tapTries = 0; failedPlanning = false;
        door = null; doorTicks = 0; doorClicks = 0; useHold = 0; useWait = 0;
        path = List.of();
        cachedCells=java.util.Map.of();
        active = true;
        plan(mc, J_BEGIN, true);
    }

    private void plan(Minecraft mc, int kind, boolean fly) {
        if (job != null) job.cancel();
        job = PathJob.start(mc, goals, fly);
        jobKind = kind; jobTicks = 0;
    }

    /** Тик ожидания поиска: стоим; готово — идём по новому пути. */
    private Status tickJob(Minecraft mc, LocalPlayer p, Options o) {
        release(o);
        if (!job.done()) {
            if (++jobTicks > JOB_MAX_TICKS) { job.cancel(); job = null; fail = "поиск пути не уложился в " + JOB_MAX_TICKS / 20 + " с"; active = false; return Status.FAIL; }
            return Status.RUNNING;
        }
        PathFinder.Result r = job.result();
        logPlan(r, job);
        int kind = jobKind;
        job = null;
        if (!r.ok) {
            fail = kind == J_NOFLY ? "нужен полёт, а взлететь не вышло: " + r.reason : r.reason;
            failedPlanning = kind == J_BEGIN;
            active = false;
            return Status.FAIL;
        }
        if (kind == J_BEGIN) {
            float[] cam = endCam != null ? endCam.apply(r.goal.join) : new float[]{p.getYRot(), p.getXRot()};
            start(r, p, cam[0], cam[1]);
            EventLog.log("RETURN", what + ": иду " + r.points.size() + " точек, к отрезку " + (r.goal.seg + 1)
                    + " (поиск " + jobTicks + " т)");
        } else {
            start(r, p, core.endYaw(), core.endPitch());
        }
        return Status.RUNNING;
    }

    private void start(PathFinder.Result r, LocalPlayer p, float endYaw, float endPitch) {
        goal = r.goal;
        cachedCells=new java.util.HashMap<>(r.cachedCells); cachedWait=0;
        path = r.points;
        core.start(r.points, p.getX(), p.getZ(), endYaw, endPitch);
        var h = com.farmmacro.macro.Humanizer.lap();
        shaper = h != null ? h.routeShaper(0).route(core.points()) : null;
        if (h != null) core.core().setReach(h.reachRadii(core.points().size(), ReturnCore.REACH));
        issuedYaw = Float.NaN; issuedPitch = Float.NaN;
    }

    public void stop(Minecraft mc) {
        if (active && com.farmmacro.camera.SmoothTurn.isActive()) com.farmmacro.camera.SmoothTurn.stop();
        if (job != null) { job.cancel(); job = null; }
        door = null;
        active = false;
        shaper = null;
        path = List.of();
    }

    /** С какой точки маршрута (индекс) продолжать после возврата. */
    public int joinIndex() { return goal == null ? 0 : goal.join; }

    public Status tick(Minecraft mc, ModConfig c) {
        if (!active) return Status.FAIL;
        LocalPlayer p = mc.player;
        Options o = mc.options;
        ticks++;
        if (job != null) return tickJob(mc, p, o);
        if(!cachedCells.isEmpty()) {
            PathLevel live=new PathLevel(mc.level);
            if(!c.chunkCacheEnabled || CachedPathWorld.mismatch(cachedCells,live,k -> mc.level.hasChunk((int)(k>>32),(int)k))) {
                release(o);
                if(++replans>c.returnReplans) { fail="кэш не совпал с миром: лимит перестроений";active=false;return Status.FAIL; }
                EventLog.log("RETURN","кэш не совпал с загруженными блоками — перестроение");
                plan(mc,J_REPLAN,!noFly);return Status.RUNNING;
            }
            cachedCells.keySet().removeIf(cell -> mc.level.hasChunk(cell.x()>>4,cell.z()>>4));
        }
        // Never walk blindly into unknown client terrain: wait for the immediate footprint to load.
        if(path!=null && !path.isEmpty()) {
            RoutePoint next=path.get(Math.min(core.target(),path.size()-1));
            double dx=next.x-p.getX(),dz=next.z-p.getZ(),n=Math.hypot(dx,dz);
            double step=Math.min(2,n),nx=p.getX()+(n<1e-9?0:dx/n*step),nz=p.getZ()+(n<1e-9?0:dz/n*step);
            boolean ready=true;
            for(double ax:new double[]{nx-.3,nx+.3}) for(double az:new double[]{nz-.3,nz+.3})
                if(!mc.level.hasChunk(((int)Math.floor(ax))>>4,((int)Math.floor(az))>>4)) ready=false;
            if(!ready) {
                release(o);core.core().resetStuckWindow();
                if(++cachedWait>200) { fail="область пути по кэшу не загрузилась за 10 с";active=false;return Status.FAIL; }
                return Status.RUNNING;
            }
        }
        cachedWait=0;
        if (doorStep(mc, p, o, c)) return Status.RUNNING;
        boolean flying = p.getAbilities().flying;
        core.naturalLook=c.pathNaturalLook;core.lookAhead=c.pathLookAhead;
        ReturnCore.Phase ph = core.tick(p.getX(), p.getY(), p.getZ(), p.onGround(), flying, p.getYRot(), p.getXRot(), p.horizontalCollision, out);
        if (core.needFly && ph == ReturnCore.Phase.WALK) {
            // взлёт: прыжок — отпустить — прыжок (окно игры 7 тиков), до TAP_TRIES попыток; не вышло — путь без полёта
            com.farmmacro.panic.PanicDetector.INSTANCE.allowFlightToggle(200);
            if (!p.getAbilities().mayfly || tapTries >= TAP_TRIES) {
                release(o);
                noFly = true;
                EventLog.log("RETURN", "взлететь не вышло (" + (p.getAbilities().mayfly ? "попыток " + tapTries : "летать сейчас нельзя") + ") — путь без полёта");
                plan(mc, J_NOFLY, false);
                return Status.RUNNING;
            }
            int k = tap++ % 12;                                // 0 жать, 1–2 отпустить, 3 жать, дальше ждать
            if (k == 11) tapTries++;
            release(o);
            MacroManager.press(o.keyJump, k == 0 || k == 3);
            turn(p, c);
            return Status.RUNNING;
        }
        if (flying || core.needFly) com.farmmacro.panic.PanicDetector.INSTANCE.allowFlightToggle(200);
        if (!core.needFly) tap = 0;
        if (ph == ReturnCore.Phase.REPLAN) {
            release(o);
            if (++replans > c.returnReplans) {
                fail = "сбили с пути " + replans + " раз (" + core.reason + ")";
                active = false;
                return Status.FAIL;
            }
            EventLog.log("RETURN", "новый путь (" + replans + "/" + c.returnReplans + "): " + core.reason);
            plan(mc, J_REPLAN, !noFly);
            return Status.RUNNING;
        }
        if (ph == ReturnCore.Phase.DONE) {
            release(o);
            active = false;
            EventLog.log("RETURN", what + ": на маршруте, продолжаю с точки " + (joinIndex() + 1) + " (" + ticks + " т, перепланов " + replans + ")");
            return Status.DONE;
        }
        turn(p, c);
        if (shaper != null && ph == ReturnCore.Phase.WALK && !core.turningInPlace) {
            shaper.tick(out, p.getX(), p.getZ(), core.target(), WalkCore.D_NONE, false);
            if (shaper.idleNow) core.core().resetStuckWindow();
        }
        MacroManager.press(o.keyUp, out.forward);
        MacroManager.press(o.keyDown, out.back);
        MacroManager.press(o.keyLeft, out.left);
        MacroManager.press(o.keyRight, out.right);
        MacroManager.press(o.keyJump, out.jump);
        MacroManager.press(o.keySprint, false);
        MacroManager.press(o.keyShift, out.sneak);            // в полёте — вниз
        MacroManager.press(o.keyAttack, false);
        MacroManager.press(o.keyUse, false);
        return Status.RUNNING;
    }

    /**
     * v1.16.2 двери: закрытая дверь/калитка (открывается рукой) впереди по пути ближе {@link #DOOR_REACH} — стоп,
     * взгляд на неё, ПКМ (1–2 тика, не чаще раза в 10 тиков, до 3 раз). Открылась — идём дальше; нет — стоп с причиной
     * (приват, редстоун). @return true — этот тик занят дверью
     */
    private boolean doorStep(Minecraft mc, LocalPlayer p, Options o, ModConfig c) {
        if (core.phase() != ReturnCore.Phase.WALK) { door = null; return false; }
        if (door == null) {
            door = findDoor(mc, p);
            if (door == null) return false;
            doorTicks = 0; doorClicks = 0; useHold = 0; useWait = 0; doorYaw = Float.NaN;
            EventLog.log("RETURN", "дверь на пути (" + door.toShortString() + ") — открываю");
        }
        var st = mc.level.getBlockState(door);
        if (!PathLevel.closedOpenable(st)) {
            if (doorClicks > 0) EventLog.log("RETURN", "дверь открыта (" + doorTicks + " т)");
            door = null;
            MacroManager.press(o.keyUse, false);
            core.core().resetStuckWindow();
            issuedYaw = Float.NaN;
            return false;
        }
        if (++doorTicks > 100 || (doorClicks >= 3 && useWait == 0 && useHold == 0)) {
            release(o);
            fail = "дверь " + door.toShortString() + " не открывается (приват или редстоун?)";
            active = false;
            door = null;
            return true;   // tick() вернёт FAIL на следующем тике
        }
        for (var k : new net.minecraft.client.KeyMapping[]{o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump,
                o.keySprint, o.keyShift, o.keyAttack}) MacroManager.press(k, false);
        // взгляд — в центр нижней клетки двери (у калитки — её центр)
        var eye = p.getEyePosition();
        double tx = door.getX() + 0.5, ty = door.getY() + 0.5, tz = door.getZ() + 0.5;
        float yaw = WalkCore.yawTo(eye.x, eye.z, tx, tz);
        float pitch = (float) -Math.toDegrees(Math.atan2(ty - eye.y, Math.hypot(tx - eye.x, tz - eye.z)));
        if (Float.isNaN(doorYaw) || ReturnCore.angle(doorYaw, doorPitch, yaw, pitch) > 2) {
            com.farmmacro.camera.SmoothTurn.turnMouse(p.getYRot() + WalkCore.wrap(yaw - p.getYRot()), pitch, c.returnTurnSpeed);
            doorYaw = yaw; doorPitch = pitch;
        }
        boolean aimed = mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bh
                && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && sameDoor(mc, bh.getBlockPos(), door);
        if (useHold > 0) { useHold--; MacroManager.press(o.keyUse, true); return true; }
        MacroManager.press(o.keyUse, false);
        if (useWait > 0) { useWait--; return true; }
        if (aimed && !com.farmmacro.camera.SmoothTurn.isActive()) {
            MacroManager.press(o.keyUse, true);
            useHold = 1; useWait = 10; doorClicks++;
        }
        return true;
    }

    /** Та же дверь (нижняя/верхняя половина двери — одна дверь). */
    private static boolean sameDoor(Minecraft mc, net.minecraft.core.BlockPos hit, net.minecraft.core.BlockPos door) {
        if (hit.equals(door)) return true;
        return hit.getX() == door.getX() && hit.getZ() == door.getZ() && Math.abs(hit.getY() - door.getY()) == 1
                && mc.level.getBlockState(hit).getBlock() instanceof net.minecraft.world.level.block.DoorBlock;
    }

    /** Закрытая дверь/калитка на ближайших DOOR_REACH блоках пути (на высоте ног и тела); нижняя половина двери. */
    private net.minecraft.core.BlockPos findDoor(Minecraft mc, LocalPlayer p) {
        List<RoutePoint> pts = core.points();
        int t = core.target();
        if (t >= pts.size()) return null;
        double ax = p.getX(), az = p.getZ(), ay = p.getY(), left = DOOR_REACH;
        var mp = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int i = t; i < pts.size() && left > 0; i++) {
            RoutePoint b = pts.get(i);
            double len = Math.hypot(b.x - ax, b.z - az);
            int n = Math.max(1, (int) Math.ceil(Math.min(len, left) / 0.25));
            for (int s = 0; s <= n; s++) {
                double k = len < 1e-6 ? 1 : Math.min(1, s * 0.25 / len);
                double x = ax + (b.x - ax) * k, z = az + (b.z - az) * k, y = ay + (b.y - ay) * k;
                for (int dy = 0; dy <= 1; dy++) {
                    mp.set((int) Math.floor(x), (int) Math.floor(y + 0.01) + dy, (int) Math.floor(z));
                    var st = mc.level.getBlockState(mp);
                    if (!PathLevel.closedOpenable(st)) continue;
                    var at = mp.immutable();
                    if (st.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                            && st.getValue(net.minecraft.world.level.block.DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) at = at.below();
                    if (p.getEyePosition().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(at)) <= DOOR_REACH + 0.5) return at;
                }
            }
            left -= len;
            ax = b.x; az = b.z; ay = b.y;
        }
        return null;
    }

    /** Камера — «как мышь»: новая цель, если желаемый взгляд ушёл от поставленного (по ходу — > 6°, в конце — точно). */
    private void turn(LocalPlayer p, ModConfig c) {
        float wy = core.wantYaw, wp = core.wantPitch;
        boolean end = core.phase() == ReturnCore.Phase.TURN;
        boolean busy = com.farmmacro.camera.SmoothTurn.isActive();
        double fromIssued = Float.isNaN(issuedYaw) ? 1e9 : ReturnCore.angle(issuedYaw, issuedPitch, wy, wp);
        double fromNow = ReturnCore.angle(p.getYRot(), p.getXRot(), wy, wp);
        boolean need;
        if (end) need = fromIssued > 0.05 || (!busy && fromNow > ReturnCore.END_TOL_DEG);
        else need = busy ? fromIssued > 14 : fromNow > (c.pathNaturalLook ? 2.5 : 6);
        if (!need) return;
        // поворачиваемся к ближайшему эквиваленту yaw (без лишних оборотов)
        float ty = p.getYRot() + WalkCore.wrap(wy - p.getYRot());
        com.farmmacro.camera.SmoothTurn.turnMouse(ty, wp, c.returnTurnSpeed);
        issuedYaw = wy; issuedPitch = wp;
    }

    private static void release(Options o) {
        for (var k : new net.minecraft.client.KeyMapping[]{o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump,
                o.keySprint, o.keyShift, o.keyAttack, o.keyUse}) MacroManager.press(k, false);
    }
}
