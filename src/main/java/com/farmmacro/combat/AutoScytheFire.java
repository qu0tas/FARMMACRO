package com.farmmacro.combat;
import com.farmmacro.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
/** Opt-in main-hand item use after real-view alignment, not an attack or silent rotation. */
public final class AutoScytheFire {
    private long lastAttempt,windowSince;
    private Mob timingTarget;
    private int timingPlan; // 0 normal, 1 early edge, 2 short late press
    private long timingDelay;
    public void reset(){timingTarget=null;windowSince=0;timingPlan=0;timingDelay=0;}
    private void plan(Mob target){
        var c=ModConfig.INSTANCE;var random=java.util.concurrent.ThreadLocalRandom.current();
        timingTarget=target;windowSince=0;int roll=random.nextInt(100);
        timingPlan=roll<c.aimEarlyShotChance?1:roll<c.aimEarlyShotChance+25?2:0;
        timingDelay=timingPlan==2?random.nextLong(70,181)*1_000_000L:0;
    }
    private String status="Автовыстрел выключен";
    public String status(){return status;}
    private boolean waitFor(String reason){windowSince=0;status=reason;return false;}
    public boolean tick(Minecraft mc,Mob target,boolean armed){
        var c=ModConfig.INSTANCE;
        if(!c.aimAutoFire){reset();status="Автовыстрел выключен";return false;}
        if(!armed||mc.player==null||mc.level==null||mc.gameMode==null||target==null||mc.screen!=null||mc.isPaused()||!com.farmmacro.window.BackgroundWork.permitted(mc)
            ||com.farmmacro.panic.PanicHandoff.pending()||com.farmmacro.route.NavigationInput.busy()
            ||com.farmmacro.camera.SmoothTurn.isActive())return waitFor("Автовыстрел: ожидаю активное наведение");
        var special=SpecialZealotHunt.INSTANCE;
        if(special.pending()||special.active()&&special.focus()!=target)return waitFor("Автовыстрел: приоритет Special");
        if(!ShotPath.holdingScythe(mc))return waitFor("Автовыстрел: нужна Glacial Scythe в основной руке");
        if(mc.player.isUsingItem()||mc.options.keyUse.isDown())return waitFor("Автовыстрел: приоритет ручному использованию");
        if(!VisibleBody.valid(mc,target))return waitFor("Автовыстрел: цель потеряна");
        if(mc.player.getCooldowns().isOnCooldown(mc.player.getMainHandItem()))return waitFor("Автовыстрел: кулдаун предмета");
        boolean humanTiming=c.aimHumanShotTiming&&"human".equals(c.motion.camera.mode(c.motion))&&c.motion.camera.aimHumanStrength>0;
        if(timingTarget!=target)plan(target);
        var eye=mc.player.getEyePosition();var box=target.getBoundingBox();
        // Fresh geometry, never just an ESP/selection snapshot and never center-only.
        var open=VisibleBody.find(mc,target,eye,box.getCenter());
        if(open==null)return waitFor("Автовыстрел: тело полностью закрыто");
        boolean area=ScytheAreaAim.enabled(mc);
        var geometry=ScytheShotGeometry.evaluate(mc,target,open.point(),humanTiming&&timingPlan==1,area);
        if(!geometry.ready())return waitFor(geometry.kind()==ScytheShotGeometry.Kind.UNLOADED?
            "Автовыстрел: линия проходит через неизвестные чанки":"Автовыстрел: жду открытый фактический луч / допустимую область");
        long now=System.nanoTime();long interval=(long)Math.clamp(c.aimAutoFireIntervalMs,100,1500)*1_000_000L;
        if(lastAttempt!=0&&now-lastAttempt<interval)return waitFor("Автовыстрел: интервал");
        if(humanTiming&&timingPlan==2){
            if(windowSince==0)windowSince=now;
            if(now-windowSince<timingDelay){status="Автовыстрел: короткая задержка нажатия, камера продолжает движение";return false;}
        }
        if(AimAssist.INSTANCE.stopForPlayerOverlap(mc))return waitFor("Автовыстрел: пересечение с игроком — стоп");
        lastAttempt=now; // Rate limit attempts even if the server rejects mana/ability use.
        var result=mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
        if(result==net.minecraft.world.InteractionResult.FAIL)return waitFor("Автовыстрел: клиент отклонил использование, цель сохранена");
        String phase=humanTiming?(timingPlan==1?"раннее нажатие у края":timingPlan==2?"задержанное нажатие":"обычное нажатие"):"обычное нажатие";
        status="Автовыстрел: "+phase+" — запрос отправлен, "+switch(geometry.kind()){
            case BODY->"луч на теле";case EARLY_EDGE->"ранняя зона у тела";case AREA_EDGE->"зона рядом с телом";
            case GROUND_SPLASH->"земля рядом с хитбоксом";default->"линия проверена";
        }+" (сервер/попадание не подтверждены)";
        plan(target); // New timing for the next shot, including a surviving locked target.
        return true;
    }
}
