package com.farmmacro.route;

import java.util.List;
import java.util.Locale;

/**
 * v1.15 «Возврат на маршрут»: идти по найденному пути ({@link PathFinder}) — без зависимостей от игры
 * (проверяется симуляцией в routeStress, PathCheck).
 * Ходит тем же {@link WalkCore} (W/A/S/D по текущему yaw), но, в отличие от маршрута, камера поворачивается по ходу
 * (как человек): {@link #wantYaw} — на точку впереди, pitch — сразу к взгляду маршрута. Разворот больше
 * {@link #TURN_STOP_DEG} — сначала повернуться на месте. В конце — повернуться ровно к камере маршрута и только потом
 * продолжить маршрут. Сбили с пути, упал, застрял — {@link Phase#REPLAN} (новый путь с текущего места).
 */
public final class ReturnCore {
    public enum Phase { WALK, TURN, DONE, REPLAN }

    /** Разворот больше — стоим и поворачиваемся. */
    public static final double TURN_STOP_DEG = 70;
    /** Готово: камера ближе этого к камере маршрута. */
    public static final double END_TOL_DEG = 0.6;
    /** Сбили с пути: дальше от линии пути. */
    public static final double OFF_PATH = 1.6;
    /** Окно «застрял» (тиков). */
    public static final int STUCK_TICKS = 30;
    /** Точка «смотреть на»: не ближе этого впереди по пути. */
    public static final double LOOK_AHEAD = 2.0;
    public static final double REACH = 0.35;

    private final WalkCore core = new WalkCore();
    private PointRoute route;
    private float endYaw, endPitch;
    private Phase phase = Phase.DONE;
    public String reason;
    /** Куда смотреть (yaw, pitch) — ReturnWalker поворачивает «как мышь». */
    public float wantYaw, wantPitch;
    public boolean naturalLook;public double lookAhead=2.5;
    /** В этот тик стоим ради поворота. */
    public boolean turningInPlace;
    private int lowTicks;
    public PathFinder.World steeringWorld;
    public final GroundGuide guide=new GroundGuide();
    public boolean guided;
    public final NavigationMotion motion=new NavigationMotion();
    public long motionSeed;
    public boolean landAtGoal=true;public boolean finishFacing=true;
    private final FlightGuide flightGuide=new FlightGuide();
    public String flightMode(){return flightGuide.mode.toString();}
    public double flightSpeed=.6;
    private void flightGaze(double x,double y,double z,float yaw){
        if(route==null||route.points.isEmpty()||core.done())return;
        RoutePoint view=route.points.get(core.target());
        if(Math.hypot(view.x-x,view.z-z)<.7){
            for(int i=core.target()+1;i<route.points.size();i++){
                var next=route.points.get(i);
                if(!next.fly)break;
                if(Math.hypot(next.x-x,next.z-z)>.7){view=next;break;}
            }
        }
        double d=Math.hypot(view.x-x,view.z-z);
        wantYaw=d>.25?WalkCore.yawTo(x,z,view.x,view.z):yaw;
        wantPitch=motion.heightLook?(float)Math.clamp(-Math.toDegrees(Math.atan2(view.y-y,Math.max(2,d))),-30,30):0;
    }
    private double lastY=Double.NaN,observedVy;
    private final NavigationProgress progress=new NavigationProgress();
    public final GroundTransition transition=new GroundTransition();
    public final NavigationClimb climb=new NavigationClimb();
    public final NavigationDrop drop=new NavigationDrop();
    public boolean climbAttached;
    private boolean openMotion;
    public double guideX,guideZ;
    private double observedVx,observedVz;
    private double startX, startZ;

    /** Путь pts от места (sx, sz); в конце повернуться к (endYaw, endPitch). */
    public void start(List<RoutePoint> pts, double sx, double sz, float endYaw, float endPitch) {
        startX = sx; startZ = sz;
        start(pts, endYaw, endPitch);
    }

    public void start(List<RoutePoint> pts, float endYaw, float endPitch) {
        route = new PointRoute();
        for (RoutePoint p : pts) route.points.add(p.copy());
        this.endYaw = endYaw; this.endPitch = endPitch;
        core.start(route, 0, STUCK_TICKS);
        core.requireHeight=true;flightGuide.reset();lastY=Double.NaN;motion.reset(motionSeed);progress.reset();transition.reset();climb.reset();drop.reset();
        phase = route.points.isEmpty() ? Phase.TURN : Phase.WALK;
        reason = null; lowTicks = 0; guide.reset(); guided=false; lastX = Double.NaN; needFly = false;
        wantYaw = endYaw; wantPitch = endPitch;
    }

    public WalkCore core() { return core; }
    public Phase phase() { return phase; }
    public List<RoutePoint> points() { return route == null ? List.of() : route.points; }
    public int target() { return core.target(); }
    public float endYaw() { return endYaw; }
    public float endPitch() { return endPitch; }

    /** Остаток пути по прямым, блоков. */
    public double remaining(double x, double z) {
        if (route == null || route.points.isEmpty() || phase != Phase.WALK) return 0;
        int t = core.target();
        RoutePoint p = route.points.get(t);
        double d = Math.hypot(p.x - x, p.z - z);
        for (int i = t; i + 1 < route.points.size(); i++)
            d += Math.hypot(route.points.get(i + 1).x - route.points.get(i).x, route.points.get(i + 1).z - route.points.get(i).z);
        return d;
    }

    /**
     * Один тик. horizontalCollision — упёрся в стену (для прыжка на ступеньку).
     * @return фаза после тика; клавиши — в out
     */
    public Phase tick(double x, double y, double z, boolean onGround, float yaw, float pitch, boolean horizontalCollision,
                      WalkCore.Out out) {
        return tick(x, y, z, onGround, false, yaw, pitch, horizontalCollision, out);
    }

    /** v1.15.1: путь просит взлететь (следующая точка — «летим», а игрок не летит) — ReturnWalker дважды жмёт прыжок. */
    public boolean needFly;
    /** Скорость по XZ прошлого тика (для торможения в полёте). */
    private double lastX = Double.NaN, lastZ, speed;
    /** Полёт: сход с линии допускается больше (инерция в воздухе). */
    public static final double OFF_PATH_FLY = 2.5;

    /**
     * Тик с полётом. flying — игрок сейчас летит (способность): прыжок — вверх, присед — вниз; точки «летим»
     * (RoutePoint.fly) — по высоте тоже; на вертикальном отрезке (та же клетка) — только вверх/вниз.
     */
    public Phase tick(double x, double y, double z, boolean onGround, boolean flying, float yaw, float pitch,
                      boolean horizontalCollision, WalkCore.Out out) {
        observedVy=Double.isNaN(lastY)?0:y-lastY;lastY=y;
        core.navigationFlying=flying;
        motion.tick();
        openMotion=false;
        if(steeringWorld!=null && phase==Phase.WALK && !core.done()) {
            RoutePoint p=route.points.get(core.target());
            double d=Math.hypot(p.x-x,p.z-z);
            openMotion=onGround&&!flying&&!p.fly&&!p.climb&&!p.drop&&!p.jump&&Math.abs(p.y-y)<.1&&d>5
                &&Math.abs(WalkCore.wrap(WalkCore.yawTo(x,z,p.x,p.z)-yaw))<18
                &&new GroundProbe(steeringWorld).clear(x,y,z,x+(p.x-x)/d*4,z+(p.z-z)/d*4,.42);
        }
        guide.speedLimit=motion.speed(openMotion);
        Phase result=tickInternal(x,y,z,onGround,flying,yaw,pitch,horizontalCollision,out);
        if(result==Phase.WALK && route!=null && !core.done()) {
            RoutePoint next=route.points.get(core.target());
            double distance=Math.hypot(next.x-x,next.z-z)+Math.abs(next.y-y)*.75;
            boolean aligning=turningInPlace||transition.state==GroundTransition.State.ALIGN
                ||guided&&guide.state==GroundGuide.State.ALIGN||flying&&flightGuide.mode==FlightGuide.Mode.ALIGN;
            if(progress.tick(core.target(),distance,Math.abs(WalkCore.wrap(wantYaw-yaw)),aligning,core.pausing()))
                return replan("нет сближения с точкой 4 секунды: движение туда-сюда не считается прогрессом");
        }
        if(result!=Phase.WALK || route==null || core.done())return result;
        RoutePoint target=route.points.get(core.target());
        motion.mode=flying||needFly?NavigationMotion.Mode.FLY:target.climb?NavigationMotion.Mode.CLIMB:out.jump?NavigationMotion.Mode.JUMP:
            !onGround||core.dropPhase()!=WalkCore.D_NONE?NavigationMotion.Mode.FALL:NavigationMotion.Mode.WALK;
        if(guided && steeringWorld!=null)motion.ground(steeringWorld,x,y,z,yaw,onGround,openMotion,guide,out);
        if(flying && steeringWorld!=null) {
            flightGuide.sprintAllowed=motion.sprintAllowed;flightGuide.normalSpeed=flightSpeed;
            flightGuide.tick(x,y,z,observedVx,observedVy,observedVz,yaw,target.x,target.y,target.z,out);
            if(!flightGuide.constrain(steeringWorld,x,y,z,observedVx,observedVy,observedVz,yaw,target.x,target.y,target.z,out))
                return replan("предсказанный воздушный коридор заблокирован");
            core.resetStuckWindow();
            flightGaze(x,y,z,yaw);
            return result; // Ground gaze must not overwrite flight alignment/lookahead.
        }
        if(needFly){flightGaze(x,y,z,yaw);return result;}
        if(!naturalLook)return result;
        double tx=guided?guideX:target.x,ty=target.y,tz=guided?guideZ:target.z;
        if(Math.hypot(tx-x,tz-z)<.35 && Math.abs(target.y-y)<WalkCore.NAV_GROUND_HEIGHT_TOL && !target.climb && core.dropPhase()==WalkCore.D_NONE && core.target()+1<route.points.size()) {
            RoutePoint next=route.points.get(core.target()+1);tx=next.x;tz=next.z;
        }
        float[] gaze=motion.gaze(x,y,z,tx,ty,tz,observedVx,observedVz,yaw);
        // Bounded preview on clear ground prepares a bend without looking backwards or ignoring a local obstacle.
        boolean preview=guided&&(guide.state==GroundGuide.State.WALK||guide.state==GroundGuide.State.CORNER);
        wantYaw=preview?gaze[0]+(float)Math.clamp(WalkCore.wrap(wantYaw-gaze[0]),-25,25):gaze[0];
        wantPitch=gaze[1];
        // Gaze and movement share the same local intent. Do not run sideways through a sharp turn.
        if(onGround&&!flying&&!target.climb&&!out.jump&&Math.abs(target.y-y)<.6&&core.dropPhase()==WalkCore.D_NONE
            &&Math.abs(WalkCore.wrap(wantYaw-yaw))>65) {
            out.forward=out.back=out.left=out.right=out.sprint=out.jump=false;
            turningInPlace=true;motion.mode=NavigationMotion.Mode.ALIGN;core.resetStuckWindow();
        }
        return result;
    }

    private Phase tickInternal(double x, double y, double z, boolean onGround, boolean flying, float yaw, float pitch,
                      boolean horizontalCollision, WalkCore.Out out) {
        needFly = false;
        observedVx=Double.isNaN(lastX)?0:x-lastX;observedVz=Double.isNaN(lastX)?0:z-lastZ;
        guided=false;
        speed = Double.isNaN(lastX) ? 0 : Math.hypot(x - lastX, z - lastZ);
        lastX = x; lastZ = z;
        out.forward = out.back = out.left = out.right = out.jump = out.sprint = out.sneak = out.attack = out.use = false;
        out.slot = -1; out.panic = null;
        turningInPlace = false;
        if (phase == Phase.DONE || phase == Phase.REPLAN) return phase;
        if (phase == Phase.TURN) {
            wantYaw = endYaw; wantPitch = endPitch;
            if (angle(yaw, pitch, endYaw, endPitch) <= END_TOL_DEG) phase = Phase.DONE;
            return phase;
        }
        if (!core.done()) {
            RoutePoint ft = route.points.get(core.target());
            if (ft.fly && !flying) {                            // взлететь: стоим, ReturnWalker делает двойной прыжок
                flightGaze(x,y,z,yaw);
                // Do not wait forever for tiny residual airborne drift before attempting legal flight.
                if (onGround && speed > 0.08) { core.resetStuckWindow(); return phase; }
                needFly = true;
                core.resetStuckWindow();
                return phase;
            }

        }
        if(!core.done()&&!flying){
            RoutePoint ft=route.points.get(core.target());
            boolean leavingClimb=climbAttached;
            if(!leavingClimb&&steeringWorld!=null&&core.target()>0){
                var previous=route.points.get(core.target()-1);
                // onClimbable can clear at the top before the full footprint reaches
                // the platform. Keep the bounded exit while the last rung is observed.
                leavingClimb=previous.climb&&Math.hypot(previous.x-x,previous.z-z)<1.3
                    &&(steeringWorld.climb((int)Math.floor(previous.x),(int)Math.floor(previous.y-.01),(int)Math.floor(previous.z))!=null
                        ||steeringWorld.climb((int)Math.floor(previous.x),(int)Math.floor(previous.y+.01),(int)Math.floor(previous.z))!=null);
            }
            if(leavingClimb&&!ft.climb&&!ft.fly){
                wantYaw=WalkCore.yawTo(x,z,ft.x,ft.z);
                if(!climb.exit(steeringWorld,core.target(),ft,x,y,z,yaw,out))return replan(climb.reason);
                if(Math.hypot(ft.x-x,ft.z-z)>.12||Math.abs(ft.y-y)>.19){core.resetStuckWindow();return phase;}
            }
            if(ft.climb&&Math.abs(ft.y-y)>.19){
                if(steeringWorld==null)return replan("нет геометрии лестницы");
                wantYaw=WalkCore.yawTo(x,z,ft.x,ft.z);
                if(!climb.tick(steeringWorld,core.target(),ft,x,y,z,yaw,climbAttached,out))return replan(climb.reason);
                core.resetStuckWindow();return phase;
            }
        }
        core.tick(x, y, z, onGround, yaw, REACH, true, STUCK_TICKS, !flying, OFF_PATH, false, out);
        out.attack = false; out.use = false; out.slot = -1; out.sneak = false; out.sprint = false; out.jump = false;
        if (out.panic != null) {
            String p = out.panic;
            out.panic = null;
            out.forward = out.back = out.left = out.right = out.jump = false;
            return replan(p.startsWith("Застрял") ? "застрял" : "сбили с пути");
        }
        if (core.done()) {
            out.forward = out.back = out.left = out.right = out.jump = false;
            if (flying) { phase=Phase.DONE;wantYaw=endYaw;wantPitch=endPitch;return phase; }   // сесть: полёт сам выключится на земле
            phase = finishFacing?Phase.TURN:Phase.DONE;
            wantYaw = endYaw; wantPitch = endPitch;
            if (angle(yaw, pitch, endYaw, endPitch) <= END_TOL_DEG) phase = Phase.DONE;
            return phase;
        }
        int t = core.target();
        RoutePoint tp = route.points.get(t);
        if(core.dropPhase()!=WalkCore.D_NONE&&!flying){
            if(!drop.tick(steeringWorld,x,y,z,tp,yaw,core.dropPhase(),out))return replan(drop.reason);
        }else drop.reset();
        transition.target(t);
        // сбили с линии пути (в т. ч. на первом отрезке — от места старта; WalkCore там идёт прямо к точке)
        double ax = t > 0 ? route.points.get(t - 1).x : startX, az = t > 0 ? route.points.get(t - 1).z : startZ;
        if (RouteBuffer.distToSegmentXZ(x, z, ax, az, tp.x, tp.z) > (flying ? OFF_PATH_FLY : OFF_PATH)) return replan("сбили с пути");
        // упал ниже пути (столкнули с края) — новый путь
        double expY = Math.min(tp.y, t > 0 ? route.points.get(t - 1).y : tp.y);
        if (onGround && !flying && y < expY - 1.2) { if (++lowTicks >= 3) return replan("упал с пути"); }
        else lowTicks = 0;
        // взгляд: точка впереди по пути, не ближе LOOK_AHEAD
        double[] look = lookPoint(x, z, t);
        if (Math.hypot(look[0] - x, look[1] - z) > 0.3) wantYaw = WalkCore.yawTo(x, z, look[0], look[1]);
        wantPitch = endPitch;
        if(naturalLook){var a=com.farmmacro.camera.PathLook.angles(route.points,t,x,y,z,lookAhead,wantYaw);wantYaw=a[0];wantPitch=a[1];}
        if(!flying&&!tp.fly&&!tp.climb&&core.dropPhase()==WalkCore.D_NONE&&!core.pausing()
            &&tp.y-y>Terrain.STEP){
            wantYaw=WalkCore.yawTo(x,z,tp.x,tp.z);
            if(!transition.rise(steeringWorld,x,y,z,tp,yaw,onGround,out))return replan(transition.reason);
            turningInPlace=transition.state==GroundTransition.State.ALIGN;
            core.resetStuckWindow();
            return phase;
        }
        // A plain approach waypoint can be in the same cell as the next climb node.
        // GroundGuide must not try to avoid that very ladder indefinitely.
        if(steeringWorld!=null&&!flying&&onGround&&!tp.fly&&!tp.climb&&!tp.drop&&!tp.jump
            &&core.dropPhase()==WalkCore.D_NONE&&!core.pausing()&&Math.abs(tp.y-y)<.19
            &&Math.hypot(tp.x-x,tp.z-z)<=1.4&&t+1<route.points.size()){
            var next=route.points.get(t+1);
            if(next.climb&&Math.hypot(next.x-tp.x,next.z-tp.z)<.1
                &&steeringWorld.climb((int)Math.floor(tp.x),(int)Math.floor(tp.y+.01),(int)Math.floor(tp.z))!=null){
                wantYaw=WalkCore.yawTo(x,z,tp.x,tp.z);
                if(!climb.approach(steeringWorld,t,tp,x,y,z,yaw,out))return replan(climb.reason);
                core.resetStuckWindow();return phase;
            }
        }
        boolean special=tp.fly||tp.climb||Math.abs(tp.y-y)>.6||out.jump||!onGround||core.dropPhase()!=WalkCore.D_NONE||core.pausing();
        if(steeringWorld!=null && !special && !flying) {
            double d=Math.hypot(tp.x-x,tp.z-z);
            // Rounded corner remains within the waypoint acceptance corridor. Never skip action vertices.
            double gx=tp.x,gz=tp.z;
            boolean corner=false;
            if(t+1<route.points.size()) {
                var next=route.points.get(t+1);
                corner=Math.abs(WalkCore.wrap(WalkCore.yawTo(x,z,tp.x,tp.z)-WalkCore.yawTo(tp.x,tp.z,next.x,next.z)))>25;
                if(d<.5 && !next.fly&&!next.climb&&!tp.jump && Math.abs(next.y-y)<.1) {
                    double len=Math.hypot(next.x-tp.x,next.z-tp.z),k=Math.min(.18,len)/Math.max(1e-6,len);
                    double cx=tp.x+(next.x-tp.x)*k,cz=tp.z+(next.z-tp.z)*k;
                    if(new GroundProbe(steeringWorld).clear(x,y,z,cx,cz,.32)){gx=cx;gz=cz;}
                }
            }
            GroundProbe probe=new GroundProbe(steeringWorld);
            if(naturalLook){double[] aim=motion.aim(probe,x,y,z,gx,gz,openMotion);gx=aim[0];gz=aim[1];}
            guide.tick(probe,x,y,z,observedVx,observedVz,yaw,gx,gz,remaining(x,z),corner&&d<1.3,out);
            guided=true;guideX=guide.aimX;guideZ=guide.aimZ;
            // Look at the chosen corridor, not a conflicting independent point around a wall.
            float localYaw=WalkCore.yawTo(x,z,guideX,guideZ);
            if(guide.state==GroundGuide.State.AVOID||guide.state==GroundGuide.State.BLOCKED
                ||Math.abs(WalkCore.wrap(wantYaw-localYaw))>50) wantYaw=localYaw;
            if(guide.blockedTicks()>12) return replan("проход заблокирован");
            if(guide.state==GroundGuide.State.ALIGN || guide.state==GroundGuide.State.BRAKE || guide.state==GroundGuide.State.BLOCKED)core.resetStuckWindow();
            return phase;
        }
        if(naturalLook && Math.hypot(tp.x-x,tp.z-z)>.35)wantYaw=WalkCore.yawTo(x,z,tp.x,tp.z);
        double err = Math.abs(WalkCore.wrap(wantYaw - yaw));
        if (err > TURN_STOP_DEG && (onGround || flying) && !out.jump) {
            out.forward = out.back = out.left = out.right = false;
            if (!flying) out.jump = false;
            turningInPlace = true;
            core.resetStuckWindow();
            return phase;
        }
        return phase;
    }

    private double[] lookPoint(double x, double z, int t) {
        List<RoutePoint> p = route.points;
        double need = LOOK_AHEAD;
        double ax = x, az = z;
        for (int i = t; i < p.size(); i++) {
            double bx = p.get(i).x, bz = p.get(i).z, d = Math.hypot(bx - ax, bz - az);
            if (d >= need || i == p.size() - 1) {
                if (d < 1e-6) return new double[]{bx, bz};
                double k = Math.min(1, need / d);
                return new double[]{ax + (bx - ax) * k, az + (bz - az) * k};
            }
            need -= d; ax = bx; az = bz;
        }
        RoutePoint l = p.get(p.size() - 1);
        return new double[]{l.x, l.z};
    }

    private Phase replan(String why) {
        reason = why;
        phase = Phase.REPLAN;
        return phase;
    }

    public static double angle(float yaw, float pitch, float y2, float p2) {
        return Math.hypot(WalkCore.wrap(y2 - yaw), p2 - pitch);
    }

    @Override public String toString() {
        return String.format(Locale.ROOT, "%s, точка %d/%d", phase, core.target() + 1, route == null ? 0 : route.points.size());
    }
}
