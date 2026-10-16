package com.farmmacro.camera;

import com.farmmacro.panic.PanicDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Shared per-frame turns for presets, farm alignment and navigation.
 * Kaelix keeps MouseTurn/TrackingTurn; alpha28 optionally selects independent AetherTurn.
 * Applies only Entity.turn after vanilla mouse input and reports actual deltas to PanicDetector.
 * Existing navigation lease, panic, screen/focus guards stay authoritative; no movement/packets here.
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
    private static TrackingTurn tracking;
    private static com.farmmacro.motion.AetherTurn aether;
    private static boolean following;
    private static Object cameraOwner;
    private static final com.farmmacro.motion.CameraTurnLease bindingLease=new com.farmmacro.motion.CameraTurnLease();
    private static boolean mouseKnown;
    private static double mouseX,mouseY;
    public static boolean bindingBusy(){return bindingLease.busy();}
    public static boolean ownsBinding(com.farmmacro.motion.CameraTurnLease.Ticket t){return bindingLease.owns(t);}
    /** Do not steal an existing turn/navigation. Ticket remains exclusive until observed-angle acknowledgement. */
    public static com.farmmacro.motion.CameraTurnLease.Ticket beginBinding(float yaw,float pitch,double speed){
        var mc=Minecraft.getInstance();
        if(isActive()||com.farmmacro.route.NavigationInput.busy()||mc.player==null||mc.level==null
            ||mc.screen!=null||!com.farmmacro.window.BackgroundWork.permitted(mc)||!com.farmmacro.config.ModConfig.INSTANCE.motion.aether()
            ||com.farmmacro.panic.PanicHandoff.pending()||!Float.isFinite(yaw)||!Float.isFinite(pitch)
            ||!Double.isFinite(speed)||speed<=0)return null;
        pitch=Mth.clamp(pitch,-89,89);
        turnTo(yaw,pitch,speed);
        if(!active)return null;
        return bindingLease.acquire(yaw,pitch);
    }
    public static boolean acknowledgeBinding(com.farmmacro.motion.CameraTurnLease.Ticket t){return bindingLease.release(t);}
    public static void cancelBinding(com.farmmacro.motion.CameraTurnLease.Ticket t){if(bindingLease.owns(t))stop();}
    /** Only while a bound route turn is pending. Preserve the physical event for the player. */
    public static void bindingUserInput(){
        if(!bindingBusy())return;
        var mc=Minecraft.getInstance();if(mc.screen!=null)return;
        stop();com.farmmacro.macro.MacroManager.INSTANCE.stopPlayback(mc,"§eКамера: ручное управление. Нужен ручной запуск");
    }
    public static void bindingMouseMove(double x,double y){
        boolean moved=mouseKnown&&Double.isFinite(x)&&Double.isFinite(y)&&Math.hypot(x-mouseX,y-mouseY)>.7;
        mouseX=x;mouseY=y;mouseKnown=Double.isFinite(x)&&Double.isFinite(y);
        var mc=Minecraft.getInstance();if(moved&&mc.screen==null&&mc.isWindowActive()&&mc.mouseHandler.isMouseGrabbed())bindingUserInput();
    }
    private static void completed(){
        active=false;mouse=null;tracking=null;aether=null;following=false;cameraOwner=null;
        bindingLease.arrive();
    }
    /** Retarget without restarting angular velocity. Dedicated to autonomous navigation. */
    public static void followMouse(float yaw,float pitch,double degPerSec) {
        if(bindingBusy())return;
        if(!Float.isFinite(yaw)||!Float.isFinite(pitch)||!Double.isFinite(degPerSec)){stop();return;}
        if(!com.farmmacro.route.NavigationInput.cameraAllowed())return;
        if(com.farmmacro.panic.PanicHandoff.pending()){stop();return;}
        var settings=com.farmmacro.config.ModConfig.INSTANCE.motion;
        boolean useAether=settings.aether()&&degPerSec>0;
        Object owner=com.farmmacro.route.NavigationInput.cameraOwner();
        if(!following||cameraOwner!=owner||(useAether!=(aether!=null))){
            stop();following=true;lastNs=0;
            if(useAether){var p=Minecraft.getInstance().player;if(p==null)return;aether=new com.farmmacro.motion.AetherTurn(p.getYRot(),p.getXRot(),yaw,pitch,true,settings,java.util.concurrent.ThreadLocalRandom.current());}
            else tracking=new TrackingTurn();
        }
        cameraOwner=com.farmmacro.route.NavigationInput.cameraOwner();
        targetYaw=yaw;targetPitch=Mth.clamp(pitch,-89f,89f);speed=degPerSec;mouse=null;active=true;
    }
    public static void stopFollowing(){if(following)stop();}

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
        if(bindingBusy())return;
        if(!Float.isFinite(yaw)||!Float.isFinite(pitch)||!Double.isFinite(degPerSec)){stop();return;}
        if(!com.farmmacro.route.NavigationInput.cameraAllowed())return;
        if(com.farmmacro.panic.PanicHandoff.pending()){stop();return;}
        aether=null;following=false;tracking=null;cameraOwner=com.farmmacro.route.NavigationInput.cameraOwner();
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
        if(c.motion.aether()&&degPerSec>0&&p!=null){
            // Do not mix Kaelix speed jitter/overshoot into the independent Aether backend.
            speed=degPerSec;lastNs=0;
            aether=new com.farmmacro.motion.AetherTurn(p.getYRot(),p.getXRot(),targetYaw,targetPitch,false,c.motion,java.util.concurrent.ThreadLocalRandom.current());
        } else if ((c.camMouseLike || forceMouse) && degPerSec > 0 && p != null) {
            double sens = mc.options.sensitivity().get();
            mouse = new MouseTurn(p.getYRot(), p.getXRot(), Mth.wrapDegrees(targetYaw - p.getYRot()), targetPitch - p.getXRot(),
                    sens, speed, (forceMouse ? Math.max(1, c.camOvershootPct) : c.camOvershootPct) / 100.0, new java.util.SplittableRandom(System.nanoTime()));
        }
        active = true;
    }

    public static void stop()          { bindingLease.cancel();active = false; mouse = null; tracking=null; aether=null; following=false; cameraOwner=null; }
    public static boolean isActive()   { return active || bindingBusy(); }

    /** Сколько градусов осталось довернуть (0, если поворота нет). */
    public static float remaining(LocalPlayer p) {
        if (!active || p == null) return 0;
        float r = (float) Math.hypot(Mth.wrapDegrees(targetYaw - p.getYRot()), targetPitch - p.getXRot());
        return mouse != null ? r * (float) (1 + 2 * mouse.overshoot) : r;
    }

    /** Каждый кадр, после мыши. */
    public static void onFrame() {
        if(com.farmmacro.panic.PanicHandoff.pending()){stop();return;}
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        long now = System.nanoTime();
        if (!active || p == null) { lastNs = 0; if (p == null) stop(); return; }
        if(!com.farmmacro.route.NavigationInput.cameraValid(cameraOwner)){stop();return;}
        if (mc.isPaused()||!com.farmmacro.window.BackgroundWork.permitted(mc)||mc.screen!=null) { stop();return; }
        double dt = lastNs == 0 ? 1 / 60.0 : Math.min(0.1, (now - lastNs) / 1e9);
        lastNs = now;

        if(aether!=null){
            if(!com.farmmacro.config.ModConfig.INSTANCE.motion.aether()){stop();return;}
            var d=aether.frame(p.getYRot(),p.getXRot(),targetYaw,targetPitch,mc.options.sensitivity().get(),speed,dt);
            if(d.yaw()!=0||d.pitch()!=0)apply(p,(float)d.yaw(),(float)d.pitch());
            if(!following&&aether.finished())completed();
            return;
        }
        if(tracking!=null){
            var d=tracking.frame(p.getYRot(),p.getXRot(),targetYaw,targetPitch,
                mc.options.sensitivity().get(),speed,dt);
            if(d.yaw()!=0||d.pitch()!=0)apply(p,(float)d.yaw(),(float)d.pitch());
            return;
        }
        if (mouse != null) {
            long[] d = mouse.frame(dt);
            if (d[0] != 0 || d[1] != 0) apply(p, (float) (d[0] * mouse.step), (float) (d[1] * mouse.step));
            if (mouse.finished()) completed();
            return;
        }
        float ey = Mth.wrapDegrees(targetYaw - p.getYRot());
        float ep = targetPitch - p.getXRot();
        double err = Math.hypot(ey, ep);
        if (err < 0.01) { completed(); return; }
        double step;
        if (speed <= 0) step = err;
        else {
            double v = speed * Math.max(0.2, Math.min(1.0, err / EASE_DEG));
            step = Math.min(err, v * dt);
            if (err - step < 0.02) step = err;          // добить остаток, а не ползти к нулю
        }
        float k = (float) (step / err);
        apply(p, ey * k, ep * k);
        if (step >= err) completed();
    }

    /**
     * «Случайность → Оглядеться»: шаг {@link com.farmmacro.macro.Humanizer.Look} (на ходу — шум pitch, на остановке —
     * yaw и pitch) как движение мыши, детектору — как свой поворот. stopLeft — сколько ещё стоять (0 — идём).
     */
    public static void look(com.farmmacro.macro.Humanizer h, LocalPlayer p, int stopLeft) {
        if (h == null || p == null || bindingBusy() || com.farmmacro.route.NavigationInput.busy()) return;
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
            if(!com.farmmacro.motion.MotionController.absolute(com.farmmacro.motion.MotionPolicy.Owner.FARM,ty,tp,false))return;
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
        var owner=com.farmmacro.route.NavigationInput.busy()?com.farmmacro.motion.MotionPolicy.Owner.NAVIGATION:
            com.farmmacro.macro.MacroManager.INSTANCE.isActive()?com.farmmacro.motion.MotionPolicy.Owner.FARM:com.farmmacro.motion.MotionPolicy.Owner.PRESET;
        com.farmmacro.motion.MotionController.turn(owner,dYaw,dPitch);
    }
}
