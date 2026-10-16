package com.farmmacro.route;

import com.farmmacro.camera.Mousemat;
import com.farmmacro.camera.SmoothTurn;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.macro.MouseHold;
import com.farmmacro.macro.HandDelays;
import com.farmmacro.panic.PanicHandoff;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.util.Mth;

/** RouteWalker adjunct, NOT another scheduler/input owner. Live world checks remain bounded. */
public final class FarmRuntime {
    private FarmSchemeSettings s;
    private PointRoute route;
    private boolean ownsTurn,spawnAttempted,spawnEligible;
    private float yaw,pitch;
    private int waitTicks,stable,progressTarget=-1,stalled;
    private double minX,maxX,minZ,maxZ,best=Double.POSITIVE_INFINITY;
    private static boolean mouseKnown;
    private static double mouseX,mouseY;

    public void beginRun(boolean resume){if(!resume)spawnAttempted=false;spawnEligible=!resume;mouseKnown=false;}
    public void start(PointRoute r,int index){
        cancel();route=r;s=r.settings.farm;
        spawnEligible=spawnEligible&&index==0;
        minX=minZ=Double.POSITIVE_INFINITY;maxX=maxZ=Double.NEGATIVE_INFINITY;
        for(var p:r.points){minX=Math.min(minX,p.x);maxX=Math.max(maxX,p.x);minZ=Math.min(minZ,p.z);maxZ=Math.max(maxZ,p.z);}
        var rng=java.util.concurrent.ThreadLocalRandom.current();
        var motion=com.farmmacro.config.ModConfig.INSTANCE.motion;
        double yr=motion.aether()?motion.yawRange:s.yawRange,pr=motion.aether()?motion.pitchRange:s.pitchRange;
        yaw=(float)(s.yaw+(yr>0?rng.nextDouble(-yr,yr):0));
        pitch=(float)Math.max(-89,Math.min(89,s.pitch+(pr>0?rng.nextDouble(-pr,pr):0)));
        progressTarget=-1;stalled=stable=waitTicks=0;best=Double.POSITIVE_INFINITY;
    }
    public boolean enabled(){return s!=null&&s.enabled;}
    public boolean camera(){return enabled()&&s.lockCamera&&!route.settings.camera.active();}
    public double radius(){return enabled()?Math.min(.45,s.reachRadius+(s.fastSwitch?.12:0)):0;}
    public void cancel(){if(ownsTurn)SmoothTurn.stop();ownsTurn=false;s=null;route=null;mouseKnown=false;}

    /** False means this tick was yielded or playback stopped; no core/action/hand work may follow. */
    public boolean before(Minecraft mc,WalkCore core){
        if(!enabled())return true;
        if(mc.player==null||mc.level==null||mc.screen!=null||!com.farmmacro.window.BackgroundWork.permitted(mc)||PanicHandoff.pending()||NavigationInput.busy()){
            stop(mc,"недоступно управление");return false;
        }
        var p=mc.player;
        double m=s.boundMargin;
        if(p.getX()<minX-m||p.getX()>maxX+m||p.getZ()<minZ-m||p.getZ()>maxZ+m){stop(mc,"за границами схемы");return false;}
        if(com.farmmacro.camera.Freelook.active()||com.farmmacro.camera.MouseLock.isLocked()){
            stop(mc,"Freelook/блокировка мыши мешает управлению");return false;
        }
        if(Mousemat.busy()){
            if(++waitTicks>240)stop(mc,"Squeaky Mousemat не завершил подготовку");
            else FarmRuntime.releaseForWait(mc,core);
            return false;
        }
        if(camera()){
            double error=Math.hypot(Mth.wrapDegrees(yaw-p.getYRot()),pitch-p.getXRot());
            double tolerance=com.farmmacro.config.ModConfig.INSTANCE.motion.aether()
                ?Math.max(.4,com.farmmacro.motion.AetherTurn.quantum(mc.options.sensitivity().get())*.75):.4;
            if(error>tolerance){
                if(++waitTicks>200){stop(mc,"не удалось выставить камеру");return false;}
                if(!ownsTurn||!SmoothTurn.isActive()){SmoothTurn.turnTo(yaw,pitch,s.turnSpeed);ownsTurn=true;}
                FarmRuntime.releaseForWait(mc,core);return false;
            }
            if(ownsTurn){SmoothTurn.stop();ownsTurn=false;}
        }
        waitTicks=0;
        if(stable<3){stable++;FarmRuntime.releaseForWait(mc,core);return false;}
        if(s.setspawnOnStart&&spawnEligible&&!spawnAttempted){
            spawnAttempted=true;
            var first=route.points.getFirst();
            double dist=Math.sqrt(Math.pow(first.x-p.getX(),2)+Math.pow(first.y-p.getY(),2)+Math.pow(first.z-p.getZ(),2));
            if(p.onGround()&&dist<=s.reachRadius+.25&&p.connection!=null){
                // Opt-in, literal command, once per explicit run; NOT confirmation of server success.
                p.connection.sendCommand("setspawn");
                MacroManager.INSTANCE.msgPublic(mc,"§7Ферма: отправлен /setspawn; ответ сервера не подтверждён");
            }else MacroManager.INSTANCE.msgPublic(mc,"§eФерма: /setspawn пропущен — старт не на опоре/вне радиуса");
        }
        return true;
    }
    public boolean apply(Minecraft mc,WalkCore core,WalkCore.Out out){
        if(!enabled())return true;
        var p=mc.player;
        if(core.pausing()||core.stoppedNow()){
            out.attack=out.use=false;stalled=0;best=Double.POSITIVE_INFINITY;
            return true;
        }
        int seg=core.segment(),target=core.target();
        if(core.dropPhase()!=WalkCore.D_NONE||target>=core.points().size())return true;
        var b=core.points().get(target);var a=seg>=0?core.points().get(seg):b;
        // Dedicated drop/jump/height-changing/action controllers keep their existing semantics.
        if(a.drop||a.jump||a.pointAction.active()||Math.abs(b.y-a.y)>.6)return true;
        if(Math.abs(p.getY()-a.y)>.65||!p.onGround()&&!p.isInWater()){
            stop(mc,"нет опоры на высоте ряда");return false;
        }
        double dist=Math.hypot(b.x-p.getX(),b.z-p.getZ());
        if(target!=progressTarget){progressTarget=target;stalled=0;best=dist;}
        if(dist<best-.08){best=dist;stalled=0;}
        else if(++stalled>100){stop(mc,"нет продвижения к ряду/точке за 5 с");return false;}
        String keys=a.farmKeys;
        if(keys!=null&&!keys.isEmpty()){
            var d=FarmSchemeSettings.vector(keys,p.getYRot());
            double dx=b.x-a.x,dz=b.z-a.z,len=Math.hypot(dx,dz);
            double off=core.offLine(p.getX(),p.getZ());
            // Preferred keys, never blindly contradictory to actual world geometry/camera.
            // Away from the centre line, retain WalkCore's bounded lateral correction.
            if(len>.001&&(d[0]*dx+d[1]*dz)/len>.995&&off<s.reachRadius*.5){
                out.forward=keys.contains("w");out.back=keys.contains("s");out.left=keys.contains("a");out.right=keys.contains("d");
            }
        }
        if(!out.forward||out.back)out.sprint=false;
        if(out.forward||out.back||out.left||out.right){
            var d=WalkCore.moveDir(out,p.getYRot());
            double lead=Math.max(.25,Math.min(.7,p.getDeltaMovement().horizontalDistance()*3+.25));
            if(!new GroundProbe(new PathLevel(mc.level)).clear(p.getX(),p.getY(),p.getZ(),p.getX()+d[0]*lead,p.getZ()+d[1]*lead,.3)){
                stop(mc,"край, препятствие или незагруженный участок ряда");return false;
            }
        }
        return true;
    }
    private static void releaseForWait(Minecraft mc,WalkCore core){
        core.resetStuckWindow();MouseHold.INSTANCE.suspend(true);HandDelays.releaseButtons();
        var o=mc.options;
        for(KeyMapping k:new KeyMapping[]{o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keyAttack,o.keyUse,o.keyShift,o.keySprint}){
            boolean toggle=k==o.keyShift&&o.toggleCrouch().get()||k==o.keySprint&&o.toggleSprint().get();
            if(toggle){if(k.isDown())k.setDown(true);}else k.setDown(false);
        }
    }
    private static void stop(Minecraft mc,String why){MacroManager.INSTANCE.stopPlayback(mc,"§eФерма: "+why+". Нужен ручной запуск");}
    public static void userInput(){
        var m=MacroManager.INSTANCE;
        if(m.isRoutePlaying()&&RouteWalker.INSTANCE.activeSettings().farm.enabled){
            stop(Minecraft.getInstance(),"ручное управление");mouseKnown=false;
        }
    }
    public static void onMouseMove(double x,double y){
        var mc=Minecraft.getInstance();
        if(!MacroManager.INSTANCE.isRoutePlaying()||!RouteWalker.INSTANCE.activeSettings().farm.enabled||mc.screen!=null||!com.farmmacro.window.BackgroundWork.permitted(mc)){
            mouseKnown=false;return;
        }
        if(mouseKnown&&Math.hypot(x-mouseX,y-mouseY)>.7)userInput();
        mouseX=x;mouseY=y;mouseKnown=true;
    }
}
