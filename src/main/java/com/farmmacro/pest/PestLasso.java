package com.farmmacro.pest;
import com.farmmacro.camera.SmoothTurn;
import com.farmmacro.combat.*;
import com.farmmacro.panic.*;
import com.farmmacro.route.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.*;
import java.util.*;
/** Single-target Hunting state machine: no vacuum-kill fallback and no blind held reel. */
public final class PestLasso {
    public enum Result{RUNNING,CAUGHT,FAIL}
    private enum Phase{IDLE,STUN,AIM,ATTACH,REEL}
    private Phase phase=Phase.IDLE;private Entity target;private String type,toolId,toolUuid,status="";
    private int slot,vacuum,ticks,age,tries,aligned,reels,stunHeld,promptTicks,lastAttached,goneTicks,blockedTicks;private long sentAt,promptAt,caughtAt,lastReel;private boolean attached,latched,escaped;
    private net.minecraft.client.multiplayer.ClientLevel world;
    private static PestAssistSettings cfg(){return com.farmmacro.config.ModConfig.INSTANCE.pests.assist;}
    public boolean active(){return phase!=Phase.IDLE;}
    public String status(){return status;}
    public boolean begin(Minecraft mc,Entity e,String kind,int vac,double hp){
        int l=PestItems.lassoSlot(mc);if(l<0||kind==null||!NavigationInput.acquire(this))return false;
        if(e instanceof Leashable holder&&holder.getLeashHolder()!=null){NavigationInput.release(this);return false;}
        target=e;type=kind;vacuum=vac;slot=l;world=mc.level;ticks=age=tries=aligned=reels=stunHeld=promptTicks=lastAttached=goneTicks=blockedTicks=0;sentAt=promptAt=caughtAt=lastReel=0;attached=latched=escaped=false;
        phase=Phase.AIM;select(mc,l);
        if(cfg().stun&&!kind.equals("Earthworm")&&PestAssistSettings.contains(cfg().stunTypes,kind)){
            var stack=mc.player.getInventory().getItem(vac);double dps=PestItems.dps(stack);
            if(Double.isFinite(hp)&&Double.isFinite(dps)&&hp>100+dps*cfg().stunTicks/20.0*3&&mc.player.distanceTo(e)<3.3){phase=Phase.STUN;select(mc,vac);}
        }
        status="Hunting: наведение для броска";return true;
    }
    private void select(Minecraft mc,int s){slot=s;mc.player.getInventory().setSelectedSlot(s);PanicDetector.INSTANCE.expectSlot(s);var held=mc.player.getMainHandItem();toolId=PestItems.id(held);toolUuid=PestItems.uuid(held);aligned=0;age=0;}
    public void signal(Minecraft mc,String raw,boolean overlay){
        if(!active()||mc.level!=world||sentAt==0||target==null)return;String text=ServerText.clean(raw),u=text.toUpperCase(Locale.ROOT);long now=System.currentTimeMillis();
        // Untyped global REEL overlays can belong to another creature: accept only the target's marker below.
        if(overlay&&u.contains("REEL")&&Objects.equals(type,PestItems.type(text)))promptAt=now;
        if(u.contains("ESCAPED")&&(u.contains("REEL")||u.contains("LASSO"))&&Objects.equals(type,PestItems.type(text)))escaped=true;
        if(!overlay&&attached&&lastReel>0&&now-lastReel<2500&&Objects.equals(type,PestItems.type(text))&&text.toLowerCase(Locale.ROOT).contains("shard")&&text.toLowerCase(Locale.ROOT).contains("you caught "))caughtAt=now;
    }
    public Result tick(Minecraft mc){
        if(!active())return Result.FAIL;
        if(mc.player==null||mc.level!=world||mc.screen!=null||!mc.isWindowActive()||PanicHandoff.pending()||!NavigationInput.owns(this)||mc.player.getInventory().getSelectedSlot()!=slot||!toolId.equals(PestItems.id(mc.player.getMainHandItem()))||!toolUuid.isEmpty()&&!toolUuid.equals(PestItems.uuid(mc.player.getMainHandItem())))return fail(mc,"контекст/инструмент Hunting изменился");
        if(++ticks>cfg().lassoSeconds*20||++age>cfg().lassoSeconds*20)return fail(mc,"таймаут Hunting");
        var o=mc.options;for(var k:new net.minecraft.client.KeyMapping[]{o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keySprint,o.keyShift,o.keyAttack,o.keyUse})NavigationInput.press(this,o,k,false);
        if(caughtAt>0&&(target.isRemoved()||!target.isAlive())&&System.currentTimeMillis()-caughtAt<2500){cancel(mc);status="Hunting: типизированный shard-сигнал + исчезновение своей цели";return Result.CAUGHT;}
        if(target.isRemoved()||!target.isAlive()){if(caughtAt==0&&++goneTicks<35){status="Hunting: жду результат, исчезновение не поимка";return Result.RUNNING;}return fail(mc,"цель исчезла без подтверждённого shard-результата");}
        goneTicks=0;
        Vec3 eye=mc.player.getEyePosition(),center=target.getBoundingBox().getCenter();var point=target instanceof Mob m?VisibleBody.find(mc,m,eye,center):null;
        if(point==null||eye.distanceTo(point.point())>4.7){if(++blockedTicks<=6){status="Hunting: кратко жду открытое тело без использования";return Result.RUNNING;}return fail(mc,"Hunting: цель закрыта/вышла из безопасной дальности");}blockedTicks=0;
        Vec3 aim=point.point();float yaw=WalkCore.yawTo(eye.x,eye.z,aim.x,aim.z),pitch=(float)-Math.toDegrees(Math.atan2(aim.y-eye.y,Math.hypot(aim.x-eye.x,aim.z-eye.z)));
        NavigationInput.camera(this,()->SmoothTurn.followMouse(yaw,pitch,com.farmmacro.config.ModConfig.INSTANCE.pests.aimSpeed));
        boolean body=target.getBoundingBox().inflate(.08).clip(eye,eye.add(mc.player.getLookAngle().scale(4.7))).isPresent();boolean aimed=body&&ReturnCore.angle(mc.player.getYRot(),mc.player.getXRot(),yaw,pitch)<5;
        aligned=aimed?aligned+1:0;
        if(phase==Phase.STUN){
            if(age>40||mc.player.distanceTo(target)>3.3){phase=Phase.AIM;if(!selectLasso(mc))return fail(mc,"лассо пропало во время pre-stun");return Result.RUNNING;}
            if(aligned>=3){NavigationInput.press(this,o,o.keyUse,true);if(stunHeld++==0)mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);}
            if(stunHeld>=cfg().stunTicks){NavigationInput.press(this,o,o.keyUse,false);mc.gameMode.releaseUsingItem(mc.player);phase=Phase.AIM;if(!selectLasso(mc))return fail(mc,"лассо пропало во время pre-stun");}
            status="Hunting: короткое opt-in воздействие Vacuum; оглушение не предполагается";return Result.RUNNING;
        }
        if(!PestItems.lasso(mc.player.getMainHandItem()))return fail(mc,"нет того же лассо");
        boolean ownLeash=target instanceof Leashable l&&l.getLeashHolder()==mc.player;
        boolean prompt=System.currentTimeMillis()-promptAt<300;
        int checked=0;for(Entity e:com.farmmacro.util.LoadedEntities.get(mc)){
            if(++checked>1024)break;if(!(e instanceof ArmorStand)||e.getCustomName()==null)continue;
            String label=ServerText.clean(e.getCustomName().getString());
            if(!label.toUpperCase(Locale.ROOT).matches(".*\\bREEL(?: IN)?(?:!| NOW!| RIGHT CLICK|\\s)*.*"))continue;
            if(Math.hypot(e.getX()-target.getX(),e.getZ()-target.getZ())>2||e.getY()-target.getY()<-1||e.getY()-target.getY()>4)continue;
            // Reject a prompt also near another recognized pest; no ambiguous stamina association.
            boolean ambiguous=false;for(Entity other:PestHunter.INSTANCE.visiblePests())if(other!=target&&other.distanceToSqr(e)<target.distanceToSqr(e)+.2){ambiguous=true;break;}
            if(!ambiguous)prompt=true;
        }
        promptTicks=prompt?promptTicks+1:0;
        if(ownLeash||prompt){attached=true;lastAttached=ticks;if(phase==Phase.ATTACH){phase=Phase.REEL;age=0;}}
        if(escaped){escaped=false;return retry(mc,"сервер сообщил escape");}
        if(phase==Phase.AIM&&aligned>=3){
            if(ownLeash)return fail(mc,"существующая привязка до нашего броска: не перебрасываю");
            if(++tries>cfg().lassoRetries)return fail(mc,"лимит бросков");
            if(!click(mc))return fail(mc,"предмет/кулдаун отклонил бросок");sentAt=System.currentTimeMillis();phase=Phase.ATTACH;age=0;status="Hunting: запрос броска, жду attachment";
        }else if(phase==Phase.ATTACH&&age>45)return retry(mc,"нет наблюдаемого attachment");
        else if(phase==Phase.REEL){
            if(!prompt)latched=false;
            if(promptTicks>=2&&!latched&&aligned>=2&&System.currentTimeMillis()-lastReel>350){if(++reels>12)return fail(mc,"лимит слоёв/reel");if(!click(mc))return fail(mc,"reel отклонён");latched=true;lastReel=System.currentTimeMillis();status="Hunting: reel "+reels+", жду серверный результат";}
            if(!ownLeash&&!prompt&&ticks-lastAttached>60)return fail(mc,"привязка больше не подтверждается; не перебрасываю живое лассо вслепую");
        }
        return Result.RUNNING;
    }
    private boolean selectLasso(Minecraft mc){int l=PestItems.lassoSlot(mc);if(l<0)return false;select(mc,l);return true;}
    private boolean click(Minecraft mc){return !mc.player.getCooldowns().isOnCooldown(mc.player.getMainHandItem())&&mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND)!=net.minecraft.world.InteractionResult.FAIL;}
    private Result retry(Minecraft mc,String why){if(tries>=cfg().lassoRetries)return fail(mc,why);phase=Phase.AIM;age=aligned=0;attached=latched=false;promptAt=caughtAt=0;status="Hunting: ограниченный повтор — "+why;return Result.RUNNING;}
    private Result fail(Minecraft mc,String why){cancel(mc);status=why;return Result.FAIL;}
    public void cancel(Minecraft mc){
        if(NavigationInput.owns(this)){NavigationInput.press(this,mc.options,mc.options.keyUse,false);SmoothTurn.stop();NavigationInput.release(this);
            if(mc.player!=null&&mc.level==world&&!PanicHandoff.pending()&&mc.player.getInventory().getSelectedSlot()==slot&&toolId.equals(PestItems.id(mc.player.getMainHandItem()))){mc.player.getInventory().setSelectedSlot(vacuum);PanicDetector.INSTANCE.expectSlot(vacuum);}}
        phase=Phase.IDLE;
    }
}
