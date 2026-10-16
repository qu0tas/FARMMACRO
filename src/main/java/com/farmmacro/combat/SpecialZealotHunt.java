package com.farmmacro.combat;
import com.farmmacro.config.ModConfig;
import com.farmmacro.panic.*;
import com.farmmacro.route.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.*;
import java.util.*;
/** Explicit title-triggered, bounded local search. No warps, slots, chest clicks or loot ownership inference. */
public final class SpecialZealotHunt {
    public static final SpecialZealotHunt INSTANCE=new SpecialZealotHunt();
    private enum State { IDLE,SEARCH,APPROACH,COMBAT }
    private State state=State.IDLE;
    private final ReturnWalker walker=new ReturnWalker();
    private boolean pending,mouseSeen;
    private double mouseX,mouseY;
    private long lastNotice,started;
    private ClientLevel world;
    private Vec3 origin,lastPosition;
    private Mob target;
    private int point,legTicks,ticks,hiddenTicks;
    private String status="Special: поиск выключен";
    private static final double[][] SURVEY={{4,0},{0,4},{-4,0},{0,-4},{6,6},{-6,6},{-6,-6},{6,-6}};
    public boolean active(){return state!=State.IDLE;}
    public boolean pending(){return pending;}
    public boolean walking(){return active()&&walker.active();}
    public Mob focus(){return state==State.COMBAT?target:null;}
    public String status(){return status;}
    public boolean notice(Minecraft mc,String raw){
        String s=ServerText.clean(raw).toLowerCase(Locale.ROOT);
        boolean exact=s.equals("special zealot")||s.equals("special zealot!")||s.equals("a special zealot has spawned nearby!")||s.equals("a special zealot has spawned nearby");
        long now=System.nanoTime();
        boolean paired=(pending||active())&&now-lastNotice<1_500_000_000L&&(s.equals("has spawned nearby!")||s.equals("has spawned nearby"));
        var c=ModConfig.INSTANCE;
        if(AimAssist.manualMode()||!exact&&!paired||!c.aimSpecialHunt||!c.aimZealotsOnly||!c.aimAssistEnabled||!AimAssist.INSTANCE.keyActive()||mc.player==null||mc.level==null
            ||mc.screen!=null||!com.farmmacro.window.BackgroundWork.permitted(mc)||mc.isPaused()||!AimAssist.INSTANCE.contextFree(mc))return false;
        if(exact&&now-lastNotice>1_500_000_000L){lastNotice=now;if(!active())pending=true;}
        // Only this expected game notice is accepted; other title events retain normal panic handling.
        return true;
    }
    public void cancel(Minecraft mc,String why){
        boolean owned=active()||walker.active();state=State.IDLE;pending=false;target=null;hiddenTicks=0;
        if(owned)walker.stop(mc);
        status="Special: "+why;
    }
    public void userInput(){if(active()||pending){cancel(Minecraft.getInstance(),"ручной ввод");AimAssist.INSTANCE.pauseLocal("ручной ввод во время Special");}}
    public void onMouseMove(double x,double y){
        if(mouseSeen&&active()&&Math.hypot(x-mouseX,y-mouseY)>2)userInput();mouseX=x;mouseY=y;mouseSeen=true;
    }
    public void tick(Minecraft mc,boolean allowed){
        var c=ModConfig.INSTANCE;
        if(AimAssist.manualMode()||!allowed||!c.aimSpecialHunt||!c.aimZealotsOnly||mc.player==null||mc.level==null||!mc.player.isAlive()){
            if(active()||pending)cancel(mc,"управление остановлено");return;
        }
        if(pending){
            pending=false;world=mc.level;origin=lastPosition=mc.player.position();started=System.nanoTime();state=State.SEARCH;
            target=null;point=legTicks=ticks=hiddenTicks=0;walker.finishFacing=false;walker.landAtGoal=true;
            status="Special: title получен, ищу рядом";
        }
        if(!active())return;
        if(mc.level!=world||mc.player.position().distanceTo(lastPosition)>8||mc.player.position().distanceTo(origin)>18){cancel(mc,"мир/телепорт/граница 18 бл");return;}
        lastPosition=mc.player.position();ticks++;
        if(System.nanoTime()-started>(long)c.aimSpecialSearchSeconds*1_000_000_000L){cancel(mc,"таймаут поиска/боя");return;}
        if(ticks%20==0)mc.player.sendOverlayMessage(Component.literal("§6Kaelix §7[Альфа] "+status));
        if(target!=null){
            if(target.isRemoved()||target.level()!=world){
                target=null;hiddenTicks=0;walker.stop(mc);state=State.SEARCH;
                status="Special исчез: продолжаю поиск, убийство не подтверждено";
            }else if(target.isDeadOrDying()||!target.isAlive()){
                cancel(mc,"смерть special наблюдалась, возвращаюсь к обычным целям");return;
            }else if(target.position().distanceTo(origin)>18){
                target=null;hiddenTicks=0;walker.stop(mc);state=State.SEARCH;
                status="Special вышел за границу поиска: не преследую за пределами18бл";
            }
        }
        boolean oldOpen=target!=null&&VisibleBody.valid(mc,target)
            &&VisibleBody.find(mc,target,mc.player.getEyePosition(),target.getBoundingBox().getCenter())!=null;
        boolean canReconsider=target==null||!oldOpen&&(state!=State.COMBAT||hiddenTicks>=8);
        if(canReconsider&&(ticks%2==0||ticks==1)){
            Mob previous=target;
            double best=target==null?18*18:target.distanceToSqr(mc.player);
            int bestBlocked=target==null?Integer.MAX_VALUE:1,bestId=target==null?Integer.MAX_VALUE:target.getId();
            for(var named:MobNames.nearby(mc,24)){
                Mob m=named.mob();if(!named.name().equalsIgnoreCase("Special Zealot")||!VisibleBody.valid(mc,m)||m.position().distanceTo(origin)>18)continue;
                double d=m.distanceToSqr(mc.player);if(d>18*18)continue;
                int blocked=VisibleBody.find(mc,m,mc.player.getEyePosition(),m.getBoundingBox().getCenter())==null?1:0;
                int distanceOrder=Double.compare(Math.rint(d*1e6),Math.rint(best*1e6));
                if(blocked<bestBlocked||blocked==bestBlocked&&(distanceOrder<0||distanceOrder==0&&m.getId()<bestId)){
                    bestBlocked=blocked;best=d;bestId=m.getId();target=m;
                }
            }
            if(target!=previous){walker.stop(mc);state=State.SEARCH;legTicks=hiddenTicks=0;}
        }
        if(target!=null){
            Vec3 center=target.getBoundingBox().getCenter(),eye=mc.player.getEyePosition();
            if(VisibleBody.find(mc,target,eye,center)!=null){
                hiddenTicks=0;walker.stop(mc);state=State.COMBAT;status="Special найден: приоритетный бой";return;
            }
            // Let the Combat lock wait through a short obstruction before navigation takes over.
            if(state==State.COMBAT&&++hiddenTicks<=8){status="Special: краткое перекрытие, жду открытия";return;}
            hiddenTicks=0;
            if(state!=State.APPROACH){walker.stop(mc);walker.begin(mc,PathFinder.pointGoal(target.getX(),target.getY(),target.getZ(),0),null,"Special Zealot — подход");state=State.APPROACH;legTicks=0;}
            else walker.updateGoal(mc,target.getX(),target.getY(),target.getZ());
            status="Special найден, подхожу: "+walker.motionState();
            var result=walker.tick(mc,c);
            if(result==ReturnWalker.Status.FAIL||++legTicks>100){cancel(mc,"подход не удался/таймаут участка");return;}
            if(result==ReturnWalker.Status.DONE){walker.stop(mc);state=State.SEARCH;}
            return;
        }
        state=State.SEARCH;
        if(walker.active()){
            var result=walker.tick(mc,c);
            if(result!=ReturnWalker.Status.RUNNING||++legTicks>80){walker.stop(mc);legTicks=0;}
            status="Ищу Special рядом: "+walker.motionState();return;
        }
        if(point>=SURVEY.length){cancel(mc,"область осмотрена, special не найден");return;}
        double[] off=SURVEY[point++];double x=origin.x+off[0],z=origin.z+off[1];
        var floor=ShotPath.trace(mc,new Vec3(x,origin.y+3,z),new Vec3(x,origin.y-6,z));
        if(floor==null||floor.getType()!=HitResult.Type.BLOCK||floor.getDirection()!=Direction.UP)return;
        Vec3 feet=floor.getLocation().add(0,.04,0);
        var terrain=new PathLevel(mc.level);
        if(!terrain.bodyClear(feet.x,feet.y,feet.z,.33,1.8)||terrain.hazard(floor.getBlockPos().getX(),floor.getBlockPos().getY(),floor.getBlockPos().getZ())!=PathFinder.H_NONE)return;
        walker.begin(mc,PathFinder.pointGoal(feet.x,feet.y,feet.z,0),null,"Поиск Special Zealot");legTicks=0;
    }
    private SpecialZealotHunt(){}
}
