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
 */
public final class ReturnWalker {
    public enum Status { RUNNING, DONE, FAIL }

    private final ReturnCore core = new ReturnCore();
    private final WalkCore.Out out = new WalkCore.Out();
    private List<PathFinder.Goal> goals;
    private boolean active;
    private int replans;
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

    public boolean active()      { return active; }
    public String failText()     { return fail; }
    public PathFinder.Goal goal() { return goal; }
    public int replans()         { return replans; }
    public List<RoutePoint> path() { return active ? path : List.of(); }
    public String what()         { return what; }

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
        LocalPlayer p = mc.player;
        PathFinder.Result r = new PathFinder(new PathLevel(mc.level), params(p, fly)).find(p.getX(), p.getY(), p.getZ(), p.onGround(), goals);
        EventLog.log("RETURN", "поиск пути: " + r + String.format(Locale.ROOT, " (%d клеток, %.1f мс)", r.expanded, r.micros / 1000.0));
        return r;
    }

    /**
     * Начать: найти путь и идти. endYaw/endPitch — камера, с которой продолжится маршрут.
     * @return false — путь не найден (failText)
     */
    public boolean begin(Minecraft mc, List<PathFinder.Goal> goals, float endYaw, float endPitch, String what) {
        this.goals = goals;
        this.what = what;
        replans = 0; fail = null; ticks = 0; noFly = false; tap = 0; tapTries = 0;
        PathFinder.Result r = plan(mc, goals);
        if (!r.ok) { fail = r.reason; active = false; return false; }
        LocalPlayer p = mc.player;
        start(r, p, endYaw, endPitch);
        active = true;
        EventLog.log("RETURN", what + ": иду " + r.points.size() + " точек, к отрезку " + (r.goal.seg + 1));
        return true;
    }

    private void start(PathFinder.Result r, LocalPlayer p, float endYaw, float endPitch) {
        goal = r.goal;
        path = r.points;
        core.start(r.points, p.getX(), p.getZ(), endYaw, endPitch);
        var h = com.farmmacro.macro.Humanizer.lap();
        shaper = h != null ? h.routeShaper(0).route(core.points()) : null;
        if (h != null) core.core().setReach(h.reachRadii(core.points().size(), ReturnCore.REACH));
        issuedYaw = Float.NaN; issuedPitch = Float.NaN;
    }

    public void stop(Minecraft mc) {
        if (active && com.farmmacro.camera.SmoothTurn.isActive()) com.farmmacro.camera.SmoothTurn.stop();
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
        boolean flying = p.getAbilities().flying;
        ReturnCore.Phase ph = core.tick(p.getX(), p.getY(), p.getZ(), p.onGround(), flying, p.getYRot(), p.getXRot(), p.horizontalCollision, out);
        if (core.needFly && ph == ReturnCore.Phase.WALK) {
            // взлёт: прыжок — отпустить — прыжок (окно игры 7 тиков), до TAP_TRIES попыток; не вышло — путь без полёта
            com.farmmacro.panic.PanicDetector.INSTANCE.allowFlightToggle(200);
            if (!p.getAbilities().mayfly || tapTries >= TAP_TRIES) {
                release(o);
                noFly = true;
                EventLog.log("RETURN", "взлететь не вышло (" + (p.getAbilities().mayfly ? "попыток " + tapTries : "летать сейчас нельзя") + ") — путь без полёта");
                PathFinder.Result r = plan(mc, goals, false);
                if (!r.ok) { fail = "нужен полёт, а взлететь не вышло: " + r.reason; active = false; return Status.FAIL; }
                start(r, p, core.endYaw(), core.endPitch());
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
            PathFinder.Result r = plan(mc, goals, !noFly);
            if (!r.ok) { fail = r.reason; active = false; return Status.FAIL; }
            start(r, p, core.endYaw(), core.endPitch());
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

    /** Камера — «как мышь»: новая цель, если желаемый взгляд ушёл от поставленного (по ходу — > 6°, в конце — точно). */
    private void turn(LocalPlayer p, ModConfig c) {
        float wy = core.wantYaw, wp = core.wantPitch;
        boolean end = core.phase() == ReturnCore.Phase.TURN;
        boolean busy = com.farmmacro.camera.SmoothTurn.isActive();
        double fromIssued = Float.isNaN(issuedYaw) ? 1e9 : ReturnCore.angle(issuedYaw, issuedPitch, wy, wp);
        double fromNow = ReturnCore.angle(p.getYRot(), p.getXRot(), wy, wp);
        boolean need;
        if (end) need = fromIssued > 0.05 || (!busy && fromNow > ReturnCore.END_TOL_DEG);
        else need = busy ? fromIssued > 20 : fromNow > 6;
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
