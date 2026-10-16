package com.farmmacro.combat;
import com.farmmacro.camera.TrackingTurn;
import com.farmmacro.config.ModConfig;
import net.minecraft.client.Minecraft;
import java.util.SplittableRandom;
/** Bounded search goals and fixation timing only; CombatCamera alone integrates mouse motion. */
public final class IdleLook {
    public record Goal(double yaw,double pitch,double speed){}
    public enum Context { INACTIVE, EMPTY, COVERED, WAITING }
    private enum Look { SECTOR, NEIGHBOR, RETURN }
    private final SplittableRandom random=new SplittableRandom();
    private long emptySince,lastFrame;
    private boolean running,hasGoal,fixating;
    private double baseYaw,basePitch,pause,goalYaw,goalPitch,goalAge,goalSpeed,settledFor;
    private Context context=Context.INACTIVE;
    private Look look=Look.SECTOR;
    public boolean running(){return running;}
    public String status(){
        if(context==Context.COVERED&&!running)return "Осмотр: открытых целей нет; пауза перед поиском сектора";
        if(context==Context.WAITING)return "Осмотр: ожидание очереди / доступной цели";
        if(!running)return emptySince!=0?"Осмотр: пауза без целей":"Осмотр: ожидание";
        return "Осмотр: "+(fixating?"фиксация":switch(look){case SECTOR->"новый сектор";case NEIGHBOR->"проверка рядом";case RETURN->"возврат к исходному сектору";});
    }
    public void reset(){emptySince=lastFrame=0;running=hasGoal=fixating=false;pause=goalAge=settledFor=0;context=Context.INACTIVE;}
    public void update(Minecraft mc,boolean allowed,Context reason){
        if(!allowed||!ModConfig.INSTANCE.aimIdleLook||mc.player==null){reset();return;}
        if(reason!=Context.EMPTY&&reason!=Context.COVERED){reset();context=reason;return;}
        context=reason;
        long now=System.nanoTime();if(emptySince==0)emptySince=now;
        if(!running&&now-emptySince>=(long)ModConfig.INSTANCE.motion.camera.aimIdleDelayMs*1_000_000L){
            running=true;anchor(mc);lastFrame=0;pause=0;
        }
    }
    private void anchor(Minecraft mc){
        baseYaw=mc.player.getYRot();basePitch=Math.clamp(mc.player.getXRot(),-4,12);
        hasGoal=fixating=false;settledFor=goalAge=pause=0;
    }
    public Goal goal(Minecraft mc){
        if(!running||mc.player==null)return null;
        long now=System.nanoTime();double elapsed=lastFrame==0?1/60.0:Math.max(0,(now-lastFrame)/1e9);lastFrame=now;
        // Do not spend an old fixation or replay a stale sector after a long render stall.
        if(elapsed>.25){anchor(mc);elapsed=0;}
        var c=ModConfig.INSTANCE;boolean vertical=c.aimPitch||c.aimScytheOnly;
        double yaw=mc.player.getYRot(),pitch=mc.player.getXRot();
        if(hasGoal){
            goalAge+=elapsed;
            boolean reached=Math.hypot(TrackingTurn.wrap(goalYaw-yaw),vertical?goalPitch-pitch:0)<1.6;
            settledFor=reached?settledFor+elapsed:0;
            double timeout=Math.clamp(Math.abs(TrackingTurn.wrap(goalYaw-baseYaw))/Math.max(20,goalSpeed)*2+1.2,1.5,5);
            if(!fixating&&(settledFor>=.06||goalAge>timeout)){
                fixating=true;
                // Short looks predominate; occasional longer fixation, not every fourth glance.
                pause=random.nextDouble()<.23?random.nextDouble(.45,1.05):random.nextDouble(.10,.34);
            }
            if(fixating){pause-=elapsed;if(pause<=0)hasGoal=false;}
        }
        if(!hasGoal){
            double current=TrackingTurn.wrap(yaw-baseYaw),limit=c.motion.camera.aimIdleYaw,offset;
            double choice=random.nextDouble();
            if(choice<.43){
                look=Look.NEIGHBOR;offset=current+(random.nextBoolean()?1:-1)*random.nextDouble(6,Math.max(7,limit*.32));
            }else if(choice<.63&&Math.abs(current)>limit*.25){
                look=Look.RETURN;offset=random.nextDouble(-.18,.18)*limit;
            }else{
                look=Look.SECTOR;
                double side=current==0?(random.nextBoolean()?1:-1):Math.signum(current)*(random.nextDouble()<.68?-1:1);
                offset=side*random.nextDouble(.38,.96)*limit;
            }
            offset=Math.clamp(offset,-limit,limit);
            if(Math.abs(offset-current)<5){look=Look.RETURN;offset=current>=0?-limit*.35:limit*.35;}
            goalYaw=baseYaw+offset;
            goalPitch=vertical?Math.clamp(basePitch+random.nextDouble(-.8,1.2),-8,16):pitch;
            goalSpeed=c.motion.camera.aimIdleSpeed*(look==Look.NEIGHBOR?random.nextDouble(.75,1.1):random.nextDouble(.9,1.22));
            goalAge=settledFor=pause=0;hasGoal=true;fixating=false;
        }
        return new Goal(goalYaw,goalPitch,goalSpeed);
    }
}
