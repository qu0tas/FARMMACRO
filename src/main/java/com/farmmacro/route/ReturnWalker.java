package com.farmmacro.route;

import com.farmmacro.config.ModConfig;
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
    /** Hunting approaches may finish hovering; ordinary returns still land. */
    public boolean landAtGoal=true;
    public boolean finishFacing=true;
    private int replans;
    private final java.util.Map<Long,Integer> recovery=new java.util.LinkedHashMap<>();
    private void markBlocked(double x,double z){
        long key=((long)(int)Math.floor(x)<<32)^((int)Math.floor(z)&0xffffffffL);
        if(recovery.size()>=32&&!recovery.containsKey(key))recovery.remove(recovery.keySet().iterator().next());
        recovery.merge(key,1,(a,b)->Math.min(4,a+b));
    }
    private void rememberBlocked(){
        if(core.points().isEmpty())return;
        RoutePoint target=core.points().get(Math.min(core.target(),core.points().size()-1));
        var p=Minecraft.getInstance().player;
        if(p==null)return;
        double tx=core.guided?core.guideX:target.x,tz=core.guided?core.guideZ:target.z;
        double dx=tx-p.getX(),dz=tz-p.getZ(),n=Math.hypot(dx,dz);
        // Penalise the local failed corridor, not only the distant end of a simplified straight.
        if(n>.25){double step=Math.min(n,1.25);markBlocked(p.getX()+dx/n*step,p.getZ()+dz/n*step);}
        if(Math.hypot(target.x-p.getX(),target.z-p.getZ())<2)markBlocked(target.x,target.z);
    }
    private net.minecraft.client.multiplayer.ClientLevel startedLevel;
    private double previousX,previousY,previousZ;
    public String motionState(){
        if(flightSwitch.starting())return "взлёт: "+flightSwitch.state;
        if(core.motion.mode==NavigationMotion.Mode.FLY)return "полёт: "+core.flightMode();
        if(core.phase()==ReturnCore.Phase.WALK&&core.transition.rising())return switch(core.transition.state){
            case APPROACH->"подход к подъёму";case ALIGN->"выравнивание перед подъёмом";case STEP->"ступени без прыжка";
            case JUMP->"прыжок вверх";case AIR->"подъём в воздухе";case WAIT->"подтверждение подъёма";
            case BLOCKED->"подъём заблокирован";default->"иду";
        };
        if(core.naturalLook) return switch(core.motion.mode) {
            case WALK -> "иду"; case SPRINT -> "бегу"; case JUMP -> "бег + прыжок"; case FALL -> "спуск";
            case FLY -> "полёт"; case CLIMB -> "лестница"; case ALIGN -> "поворот"; case BRAKE -> "торможу";
        };
        return core.guided ? switch(core.guide.state){
        case WALK -> "иду"; case CORNER -> "поворот"; case AVOID -> "обход";
        case BRAKE -> "замедляюсь"; case BLOCKED -> "проверяю проход"; case ALIGN -> "осматриваю путь";
    } : "иду";}
    public int pathIndex(){return core.target();}
    public List<double[]> debugRays(){
        if(!core.guided)return List.of();
        var rays=new java.util.ArrayList<double[]>();
        for(int i=0;i<core.guide.rays;i++)rays.add(new double[]{core.guide.rayX[i],core.guide.rayZ[i],core.guide.rayPass[i]?1:0});
        return rays;
    }
    private int lastGoalUpdate=-100,goalUpdates;
    /** Live target API for future pest module; call only with a verified target on the client thread. */
    public boolean updateGoal(Minecraft mc,double x,double y,double z){
        if(!active||goals==null||goals.size()!=1||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))return false;
        var old=goals.getFirst();
        if(ticks-lastGoalUpdate<12||Math.sqrt(Math.pow(old.x-x,2)+Math.pow(old.y-y,2)+Math.pow(old.z-z,2))<.9)return false;
        if(goalUpdates>=30){fail="цель слишком часто меняет позицию — стоп";stop(mc);release(mc.options);return false;}
        goalUpdates++;lastGoalUpdate=ticks;goals=PathFinder.pointGoal(x,y,z,old.join);
        release(mc.options);plan(mc,J_REPLAN,!noFly);return true;
    }
    public double[] steeringPoint(){return core.guided?new double[]{core.guideX,core.guideZ}:null;}
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
    private final FlightSwitch flightSwitch=new FlightSwitch();
    private final FlightArrival arrival=new FlightArrival();
    private double flightX,flightY,flightZ;
    public String flightState(){return flightSwitch.state+" / "+core.flightMode();}
    public static final int TAP_TRIES = 3;
    /** Последний путь (для HUD и отрисовки). */
    private List<RoutePoint> path = List.of();
    private String what = "";
    /** v1.16.2: поиск в фоне — что делать с результатом. */
    private PathJob job;
    private int jobKind, jobTicks;
    private double planX,planY,planZ;
    private static final int J_BEGIN = 0, J_REPLAN = 1, J_NOFLY = 2;
    /** Поиск дольше этого (тиков) — стоп с причиной. */
    public static final int JOB_MAX_TICKS = 400;
    private java.util.function.IntFunction<float[]> endCam;
    private boolean failedPlanning;
    private final NavigationDoor doors=new NavigationDoor();
    public static final double DOOR_REACH=NavigationDoor.REACH;

    public boolean active()      { return active; }
    public String technicalState(){return !active?"IDLE":job!=null?"PLANNING":cachedWait>0?"WAIT_CHUNK":doors.active()?"DOOR":core.phase().name()+"/"+core.motion.mode.name();}
    public String failText()     { return fail; }
    public PathFinder.Goal goal() { return goal; }
    public int replans()         { return replans; }
    public List<RoutePoint> path() { return active ? path : List.of(); }
    public List<RoutePoint> remainingPath(){return active&&!path.isEmpty()?path.subList(Math.min(core.target(),path.size()-1),path.size()):List.of();}
    public String what()         { return what; }
    /** v1.16.2: идёт поиск пути (в фоне). */
    public boolean planning()    { return active && job != null; }
    /** Открываем дверь/калитку. */
    public boolean openingDoor() { return active && doors.active(); }
    /** Не нашёлся самый первый путь (а не сбили по дороге) — MacroManager может стартовать без подхода. */
    public boolean failedPlanning() { return failedPlanning; }

    /** Остаток пути, блоков. */
    public double remaining(LocalPlayer p) { return active && p != null ? core.remaining(p.getX(), p.getZ()) : 0; }
    public boolean turningToRoute() { return active && core.phase() == ReturnCore.Phase.TURN; }

    /** Параметры поиска из настроек. */
    public static boolean canFly(LocalPlayer p) { return FlightCapability.allowed(p); }
    public static String flightStatus(Minecraft mc) {
        return FlightCapability.describe(mc);
    }
    public static PathFinder.Params params() { return params(null, true); }

    /** fly — разрешить полёт (если включён «Есть полёт» и игрок может летать). */
    public static PathFinder.Params params(LocalPlayer pl, boolean fly) {
        ModConfig c = ModConfig.INSTANCE;
        PathFinder.Params p = new PathFinder.Params();
        p.maxDist = c.returnMaxDist;
        p.maxNodes = 80_000;
        p.fly = fly && (pl==null ? c.returnFly : canFly(pl) && (c.returnFlyAuto || c.returnFly || pl.getAbilities().flying));
        p.flyCeil = c.returnFlyCeil;
        p.anticipateFlight=true;
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
        if(!acquireInput(mc))return;
        release(mc.options);
        startedLevel=mc.level;
        if(mc.player!=null){previousX=mc.player.getX();previousY=mc.player.getY();previousZ=mc.player.getZ();}
        this.goals = List.copyOf(goals);
        this.what = what;
        this.endCam = endCam;
        goalUpdates=0;lastGoalUpdate=-100;
        recovery.clear();replans = 0; fail = null; ticks = 0; noFly = false; failedPlanning = false;
        doors.reset();
        path = List.of();
        cachedCells=java.util.Map.of();
        active = true;
        plan(mc, J_BEGIN, true);
    }

    /** Explicit loaded, verified flight corridor for a survey; no A* staircase, warps or physics edits.
     * Bounded to 30 blocks so checks never extend into unseen terrain. */
    public boolean beginFlightLeg(Minecraft mc,double x,double y,double z,String label) {
        if(mc.player==null||mc.level==null||!canFly(mc.player))return false;
        var p=mc.player;double length=Math.hypot(x-p.getX(),z-p.getZ());
        if(length>30 || Math.abs(y-p.getY())>ModConfig.INSTANCE.returnFlyCeil)return false;
        var w=new PathLevel(mc.level);
        double cruise=Math.max(y,p.getY()+(p.getAbilities().flying?0:1.3));
        for(int i=0;i<=Math.ceil(length/.5);i++) {
            double t=i/Math.max(1,Math.ceil(length/.5));
            double cx=p.getX()+(x-p.getX())*t,cz=p.getZ()+(z-p.getZ())*t;
            for(double ox:new double[]{-.35,.35})for(double oz:new double[]{-.35,.35})
                if(!mc.level.hasChunk(((int)Math.floor(cx+ox))>>4,((int)Math.floor(cz+oz))>>4))return false;
        }
        if(!NavigationMotion.airClear(w,p.getX(),p.getY(),p.getZ(),p.getX(),cruise,p.getZ(),.32)
            ||!NavigationMotion.airClear(w,p.getX(),cruise,p.getZ(),x,cruise,z,.32)
            ||!NavigationMotion.airClear(w,x,cruise,z,x,y,z,.32))return false;
        if(!acquireInput(mc))return false;
        release(mc.options);
        if(job!=null){job.cancel();job=null;}
        startedLevel=mc.level;previousX=p.getX();previousY=p.getY();previousZ=p.getZ();
        goals=PathFinder.pointGoal(x,y,z,0);what=label;recovery.clear();replans=0;fail=null;ticks=0;noFly=false;
        failedPlanning=false;active=true;flightSwitch.reset();doors.reset();
        PathFinder.Result result=new PathFinder.Result();result.ok=true;result.goal=goals.getFirst();
        if(cruise>p.getY()+.2 || !p.getAbilities().flying){RoutePoint up=new RoutePoint(p.getX(),cruise,p.getZ());up.fly=true;result.points.add(up);}
        RoutePoint across=new RoutePoint(x,cruise,z);across.fly=true;result.points.add(across);
        if(y<cruise-.2){RoutePoint down=new RoutePoint(x,y,z);down.fly=true;result.points.add(down);}
        start(result,p,p.getYRot(),p.getXRot());return true;
    }

    private void plan(Minecraft mc, int kind, boolean fly) {
        com.farmmacro.util.Diagnostics.navigation(kind==J_BEGIN?com.farmmacro.util.Diagnostics.NavEvent.SEARCH_STARTED:com.farmmacro.util.Diagnostics.NavEvent.REPLAN);
        com.farmmacro.camera.SmoothTurn.stopFollowing();
        if (job != null) job.cancel();
        path=List.of();planX=mc.player.getX();planY=mc.player.getY();planZ=mc.player.getZ();
        job = PathJob.start(mc, goals, fly,recovery);
        jobKind = kind; jobTicks = 0;
    }

    /** Тик ожидания поиска: стоим; готово — идём по новому пути. */
    private Status tickJob(Minecraft mc, LocalPlayer p, Options o) {
        release(o);
        if (!job.done()) {
            if (++jobTicks > JOB_MAX_TICKS) { job.cancel(); job = null; fail = "поиск пути не уложился в " + JOB_MAX_TICKS / 20 + " с"; stop(mc); return Status.FAIL; }
            return Status.RUNNING;
        }
        if(Math.hypot(p.getX()-planX,p.getZ()-planZ)>.65||Math.abs(p.getY()-planY)>.75){
            job.cancel();job=null;
            if(++replans>ModConfig.INSTANCE.returnReplans){fail="позиция изменилась во время поиска: лимит";stop(mc);return Status.FAIL;}
            plan(mc,J_REPLAN,!noFly);return Status.RUNNING;
        }
        PathFinder.Result r = job.result();
        logPlan(r, job);
        int kind = jobKind;
        job = null;
        if (!r.ok) {
            com.farmmacro.util.Diagnostics.navigation(com.farmmacro.util.Diagnostics.NavEvent.SEARCH_FAILED);
            fail = kind == J_NOFLY ? "нужен полёт, а взлететь не вышло: " + r.reason : r.reason;
            failedPlanning = kind == J_BEGIN;
            stop(mc);
            return Status.FAIL;
        }
        com.farmmacro.util.Diagnostics.navigation(com.farmmacro.util.Diagnostics.NavEvent.PATH_READY);
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
        // Remove grid stair-stepping only where the complete swept flight corridor is clear.
        var simplified=new java.util.ArrayList<RoutePoint>();
        for(int i=0;i<r.points.size();i++) {
            RoutePoint from=r.points.get(i);simplified.add(from);
            if(!from.fly||from.climb||from.drop||from.jump||from.pointAction!=null&&from.pointAction.active())continue;
            int far=i;
            for(int j=i+1;j<r.points.size();j++) {
                RoutePoint to=r.points.get(j);
                if(!to.fly||to.climb||to.drop||to.jump||to.pointAction!=null&&to.pointAction.active())break;
                if(!NavigationMotion.airClear(new PathLevel(startedLevel),from.x,from.y,from.z,to.x,to.y,to.z,.32))break;
                far=j;
            }
            if(far>i+1)i=far-1;
        }
        path = simplified;
        core.landAtGoal=landAtGoal;core.finishFacing=finishFacing;
        core.motionSeed=System.nanoTime();
        arrival.reset();flightX=p.getX();flightY=p.getY();flightZ=p.getZ();
        core.start(path, p.getX(), p.getZ(), endYaw, endPitch);
        var h = com.farmmacro.macro.Humanizer.lap();
        shaper = h != null ? h.routeShaper(0).route(core.points()) : null;
        if (h != null && !com.farmmacro.config.ModConfig.INSTANCE.pathNaturalLook) core.core().setReach(h.reachRadii(core.points().size(), ReturnCore.REACH));
        issuedYaw = Float.NaN; issuedPitch = Float.NaN;
    }

    private boolean acquireInput(Minecraft mc){
        if(mc==null||mc.player==null||mc.level==null||!com.farmmacro.panic.PanicHandoff.allowStart(mc)){
            fail="нет допустимого контекста для навигации";failedPlanning=false;stop(mc);return false;
        }
        if(com.farmmacro.camera.Mousemat.busy()||com.farmmacro.camera.Freelook.active()
            ||com.farmmacro.camera.MouseLock.isLocked()||com.farmmacro.panic.HumanReaction.running()){
            fail="камера занята другой функцией — завершите её перед навигацией";failedPlanning=false;stop(mc);return false;
        }
        if(!NavigationInput.acquire(this)){
            fail="другая навигация уже управляет клавишами";failedPlanning=false;return false;
        }
        return true;
    }

    public void manualStop(Minecraft mc){
        if(active)com.farmmacro.util.Diagnostics.navigation(com.farmmacro.util.Diagnostics.NavEvent.MANUAL_STOP);
        fail="ручное управление — навигация отменена";failedPlanning=false;stop(mc);
    }

    public void stop(Minecraft mc) {
        if(active||job!=null)com.farmmacro.util.Diagnostics.navigation(fail==null?com.farmmacro.util.Diagnostics.NavEvent.STOPPED:com.farmmacro.util.Diagnostics.NavEvent.FAILED);
        if(NavigationInput.owns(this)){
            if(mc!=null)release(mc.options);
            com.farmmacro.camera.SmoothTurn.stop();
            NavigationInput.release(this);
        }
        if (job != null) { job.cancel(); job = null; }
        flightSwitch.reset();arrival.reset();doors.reset();active = false;shaper = null;path = List.of();
    }

    /** С какой точки маршрута (индекс) продолжать после возврата. */
    public int joinIndex() { return goal == null ? 0 : goal.join; }

    public Status tick(Minecraft mc, ModConfig c) {
        if (!active)return Status.FAIL; // An idle instance must never release another walker's inputs/camera.
        if(!NavigationInput.owns(this)){fail="потеряно владение навигацией";stop(mc);return Status.FAIL;}
        if(com.farmmacro.panic.PanicHandoff.pending()||mc.screen!=null||!com.farmmacro.window.BackgroundWork.permitted(mc)||mc.isPaused()){
            fail="окно/фокус/пауза — навигация остановлена";stop(mc);return Status.FAIL;
        }
        if(com.farmmacro.camera.Mousemat.busy()||com.farmmacro.camera.Freelook.active()||com.farmmacro.camera.MouseLock.isLocked()){
            fail="камера передана другой функции — навигация остановлена";stop(mc);return Status.FAIL;
        }
        LocalPlayer p = mc.player;
        Options o = mc.options;
        if(p==null || mc.level==null || mc.level!=startedLevel || !p.isAlive()
            || Math.sqrt(Math.pow(p.getX()-previousX,2)+Math.pow(p.getY()-previousY,2)+Math.pow(p.getZ()-previousZ,2))>8) {
            fail="мир/игрок/позиция изменились — навигация отменена";stop(mc);release(o);return Status.FAIL;
        }
        previousX=p.getX();previousY=p.getY();previousZ=p.getZ();
        if(++ticks>12000){fail="превышено время навигации (10 минут)";stop(mc);release(o);return Status.FAIL;}
        if (job != null) return tickJob(mc, p, o);
        if(!cachedCells.isEmpty()) {
            PathLevel live=new PathLevel(mc.level);
            if(!c.chunkCacheEnabled || CachedPathWorld.mismatch(cachedCells,live,k -> mc.level.hasChunk((int)(k>>32),(int)k))) {
                release(o);
                if(++replans>c.returnReplans) { fail="кэш не совпал с миром: лимит перестроений";stop(mc);return Status.FAIL; }
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
                release(o);com.farmmacro.camera.SmoothTurn.stopFollowing();core.core().resetStuckWindow();
                if(++cachedWait>200) { fail="область пути по кэшу не загрузилась за 10 с";stop(mc);return Status.FAIL; }
                return Status.RUNNING;
            }
        }
        cachedWait=0;
        if(core.phase()==ReturnCore.Phase.WALK){
            boolean wasOpening=doors.active();
            NavigationDoor.Result doorResult=NavigationInput.camera(this,()->doors.tick(mc,core.points(),core.target(),c.returnTurnSpeed));
            if(wasOpening&&doorResult==NavigationDoor.Result.CLEAR){issuedYaw=Float.NaN;issuedPitch=Float.NaN;}
            if(doorResult!=NavigationDoor.Result.CLEAR){
                release(o);core.core().resetStuckWindow();
                if(doorResult==NavigationDoor.Result.BLOCKED){
                    fail=doors.failure();stop(mc);return Status.FAIL;
                }
                return Status.RUNNING;
            }
        }
        boolean flying = p.getAbilities().flying;
        if(flightSwitch.hadFlight&&!flying&&!p.onGround()&&!flightSwitch.starting()){
            fail="полёт неожиданно выключился в воздухе — управление остановлено";stop(mc);return Status.FAIL;
        }
        core.steeringWorld=new PathLevel(mc.level);
        core.climbAttached=p.onClimbable();
        core.naturalLook=c.pathNaturalLook;core.lookAhead=c.pathLookAhead;
        core.flightSpeed=Math.clamp(p.getAbilities().getFlyingSpeed()*12,.15,1.5);
        core.motion.sprintAllowed=c.pathSprint && (p.getFoodData().getFoodLevel()>6 || p.getAbilities().mayfly);
        core.motion.jumpsAllowed=c.pathSprintJump;core.motion.variation=c.pathVariation;core.motion.heightLook=c.pathHeightLook;
        ReturnCore.Phase ph = core.tick(p.getX(), p.getY(), p.getZ(), p.onGround(), flying, p.getYRot(), p.getXRot(), p.horizontalCollision, out);
        if ((core.needFly||flightSwitch.starting()) && ph == ReturnCore.Phase.WALK) {
            com.farmmacro.panic.PanicDetector.INSTANCE.allowFlightToggle(4);
            var toggle=flightSwitch.tick(canFly(p),flying,p.onGround(),core.steeringWorld,p.getX(),p.getY(),p.getZ());
            if(toggle==FlightSwitch.Result.FAIL){
                release(o);noFly=true;EventLog.log("RETURN",flightSwitch.reason+" — пробую наземный путь");
                flightSwitch.reset();plan(mc,J_NOFLY,false);return Status.RUNNING;
            }
            if(toggle==FlightSwitch.Result.WAIT){release(o);NavigationInput.takeoffJump(this,o,flightSwitch.jump);turn(p,c);return Status.RUNNING;}
        }
        if(flying){flightSwitch.hadFlight=true;com.farmmacro.panic.PanicDetector.INSTANCE.allowFlightToggle(4);}
        if (ph == ReturnCore.Phase.REPLAN) {
            rememberBlocked();release(o);
            if (++replans > c.returnReplans) {
                fail = "исчерпан лимит перестроений " + c.returnReplans + " (" + core.reason + ")";
                stop(mc);
                return Status.FAIL;
            }
            EventLog.log("RETURN", "новый путь (" + replans + "/" + c.returnReplans + "): " + core.reason);
            plan(mc, J_REPLAN, !noFly);
            return Status.RUNNING;
        }
        if (ph == ReturnCore.Phase.DONE && flightSwitch.hadFlight) {
            RoutePoint finalPoint=path.getLast();
            double vx=p.getX()-flightX,vy=p.getY()-flightY,vz=p.getZ()-flightZ;
            flightX=p.getX();flightY=p.getY();flightZ=p.getZ();
            var done=arrival.tick(core.steeringWorld,landAtGoal,p.onGround(),flying,p.getX(),p.getY(),p.getZ(),vx,vy,vz,p.getYRot(),finalPoint,out);
            if(done==FlightArrival.Result.FAIL){fail=arrival.reason;stop(mc);return Status.FAIL;}
            if(done==FlightArrival.Result.WAIT){
                if(Math.hypot(finalPoint.x-p.getX(),finalPoint.z-p.getZ())>.2){
                    core.wantYaw=WalkCore.yawTo(p.getX(),p.getZ(),finalPoint.x,finalPoint.z);turn(p,c);
                }
                apply(o);return Status.RUNNING;
            }
            if(finishFacing&&ReturnCore.angle(p.getYRot(),p.getXRot(),core.endYaw(),core.endPitch())>ReturnCore.END_TOL_DEG){
                release(o);core.wantYaw=core.endYaw();core.wantPitch=core.endPitch();turn(p,c);return Status.RUNNING;
            }
        }else{flightX=p.getX();flightY=p.getY();flightZ=p.getZ();}
        if (ph == ReturnCore.Phase.DONE) {
            stop(mc);
            EventLog.log("RETURN", what + ": на маршруте, продолжаю с точки " + (joinIndex() + 1) + " (" + ticks + " т, перепланов " + replans + ")");
            return Status.DONE;
        }
        turn(p, c);
        if (!c.pathNaturalLook && shaper != null && ph == ReturnCore.Phase.WALK && !core.turningInPlace
            &&!core.transition.rising()&&core.core().dropPhase()==WalkCore.D_NONE&&p.onGround()&&!flying
            &&!out.jump&&!out.sneak) {
            shaper.tick(out, p.getX(), p.getZ(), core.target(), WalkCore.D_NONE, false);
            if (shaper.idleNow) core.core().resetStuckWindow();
        }
        apply(o);
        return Status.RUNNING;
    }

    private void apply(Options o){
        press(o.keyUp, out.forward);
        press(o.keyDown, out.back);
        press(o.keyLeft, out.left);
        press(o.keyRight, out.right);
        press(o.keyJump, out.jump);
        press(o.keySprint, out.sprint);
        press(o.keyShift, out.sneak);            // в полёте — вниз
        press(o.keyAttack, false);
        press(o.keyUse, false);
    }

    /** Камера — «как мышь»: новая цель, если желаемый взгляд ушёл от поставленного (по ходу — > 6°, в конце — точно). */
    private void turn(LocalPlayer p, ModConfig c) {
        NavigationInput.camera(this,()->turnOwned(p,c));
    }
    private void turnOwned(LocalPlayer p, ModConfig c) {
        float wy = core.wantYaw, wp = core.wantPitch;
        if(c.pathNaturalLook||p.getAbilities().flying||flightSwitch.starting()) {
            com.farmmacro.camera.SmoothTurn.followMouse(p.getYRot()+WalkCore.wrap(wy-p.getYRot()),wp,c.returnTurnSpeed);
            return;
        }
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

    private void press(net.minecraft.client.KeyMapping key,boolean down){
        NavigationInput.press(this,Minecraft.getInstance().options,key,down);
    }
    private void release(Options o) {
        if(o==null||!NavigationInput.owns(this))return;
        for (var k : new net.minecraft.client.KeyMapping[]{o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump,
                o.keySprint, o.keyShift, o.keyAttack, o.keyUse}) press(k, false);
    }
}
