package com.farmmacro.pest;

import com.farmmacro.config.ModConfig;
import com.farmmacro.camera.SmoothTurn;
import com.farmmacro.route.*;
import com.farmmacro.panic.*;
import com.farmmacro.combat.ShotPath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.*;
import java.util.*;
import java.util.regex.Pattern;

/** One opt-in, bounded travel action; only an exact expected server move can be acknowledged. */
public final class PestTravel {
    public static final PestTravel INSTANCE=new PestTravel();
    public enum Result { RUNNING,DONE,FAIL }
    private enum Phase { IDLE,AIM,WAIT }
    private Phase phase=Phase.IDLE;
    private ClientLevel world;private Vec3 goal,aim,from;
    private float yaw,pitch;private int slot,restore,age,stable;private long sentAt;
    private boolean ether,plot,observed;
    private BlockPos block;
    private String command,status="Ожидаю";
    private int budget;private String expectedId,expectedUuid;
    private static final Pattern RANGE=Pattern.compile("(?i)teleport (?:to your targeted block up to )?(\\d+(?:\\.\\d+)?) blocks");
    public boolean busy(){return phase!=Phase.IDLE;}
    public String status(){return status;}
    public void resetBudget(){budget=0;}
    private static PestSettings cfg(){return ModConfig.INSTANCE.pests;}
    private record Tool(int slot,boolean ether,double range){}
    private static String id(net.minecraft.world.item.ItemStack stack){var d=stack.get(DataComponents.CUSTOM_DATA);return d==null?"":d.copyTag().getCompoundOrEmpty("ExtraAttributes").getStringOr("id","");}
    private Tool tool(Minecraft mc,boolean wantEther){
        for(int i=0;i<9;i++){
            var stack=mc.player.getInventory().getItem(i);String id=id(stack);
            if(!Set.of("ASPECT_OF_THE_VOID","ASPECT_OF_THE_END","ETHERWARP_CONDUIT").contains(id))continue;
            var lines=stack.get(DataComponents.LORE);if(lines==null)continue;
            String lore=ServerText.clean(String.join(" ",lines.lines().stream().map(l->l.getString()).toList()));
            if(wantEther&&!lore.contains("Ether Transmission"))continue;
            if(!wantEther&&(!lore.contains("Instant Transmission")||id.equals("ETHERWARP_CONDUIT")))continue;
            // Parse the relevant ability section only: never mistake 57-block etherwarp for an 8-block blink.
            String ability=wantEther?"Ether Transmission":"Instant Transmission";int start=lore.indexOf(ability);
            String section=lore.substring(start);int next=section.indexOf("Ability:",ability.length());if(next>=0)section=section.substring(0,next);
            var m=RANGE.matcher(section);if(!m.find())continue;double range=Double.parseDouble(m.group(1));
            if(range>=(wantEther?8:4)&&range<=(wantEther?100:16))return new Tool(i,wantEther,range);
        }
        return null;
    }
    public boolean toPlot(Minecraft mc,PestPlots.Plot p,int vacuum){
        if(!context(mc)||budget>=12)return false;
        var landing=cfg().landing(p.id());
        if(cfg().plotWarp&&landing!=null&&Objects.equals(landing.world,RouteBuffer.worldId(mc))&&Objects.equals(landing.dimension,RouteBuffer.dimensionId(mc))&&mc.player.position().distanceTo(new Vec3(landing.x,landing.y,landing.z))>2){
            String name=PestPlots.INSTANCE.commandName(p.id());if(name.matches("[a-zA-Z0-9 _-]{1,32}")){
                goal=new Vec3(landing.x,landing.y,landing.z);yaw=landing.yaw;pitch=landing.pitch;aim=null;block=null;
                command="plotteleport "+name;slot=vacuum;restore=vacuum;ether=false;plot=true;return begin(mc);
            }
        }
        if(cfg().etherwarp){var tool=tool(mc,true);if(tool!=null&&roof(mc,p,tool)){restore=vacuum;return begin(mc);}}
        if(cfg().aotv){var tool=tool(mc,false);if(tool!=null&&blink(mc,p,tool)){restore=vacuum;return begin(mc);}}
        status="Нет подтверждаемого warp-перехода: обычный полёт/подход";return false;
    }
    /** Shredder is a layout-specific pre-stage, not an item. Retains actual view, never guesses a hidden roof. */
    public boolean shredderStep(Minecraft mc,int vacuum){
        if(!cfg().shredder||!context(mc)||budget>=12)return false;
        var tool=tool(mc,true);if(tool==null)return false;
        Vec3 eye=mc.player.getEyePosition(),end=eye.add(mc.player.getLookAngle().scale(tool.range));
        var hit=ShotPath.trace(mc,eye,end);
        if(hit==null||hit.getType()!=HitResult.Type.BLOCK||hit.getDirection()!=Direction.UP)return false;
        if(!landingOn(mc,hit.getBlockPos(),tool))return false;
        yaw=mc.player.getYRot();pitch=mc.player.getXRot();restore=vacuum;return begin(mc);
    }
    private boolean landingOn(Minecraft mc,BlockPos at,Tool tool){
        var w=new PathLevel(mc.level);double top=w.top(at.getX(),at.getY(),at.getZ());
        if(top<.875||w.hazard(at.getX(),at.getY(),at.getZ())!=PathFinder.H_NONE)return false;
        goal=new Vec3(at.getX()+.5,at.getY()+top,at.getZ()+.5);
        if(!new GroundProbe(w).clear(goal.x,goal.y,goal.z,goal.x,goal.z,.31))return false;
        aim=new Vec3(goal.x,goal.y-.02,goal.z);
        if(mc.player.getEyePosition().distanceTo(aim)>tool.range-.2||mc.player.position().distanceTo(goal)<2)return false;
        var ray=ShotPath.trace(mc,mc.player.getEyePosition(),aim);
        if(ray==null||ray.getType()!=HitResult.Type.BLOCK||!ray.getBlockPos().equals(at)||ray.getDirection()!=Direction.UP)return false;
        block=at;slot=tool.slot;ether=true;plot=false;command=null;
        angles(mc,aim);return true;
    }
    private boolean roof(Minecraft mc,PestPlots.Plot p,Tool tool){
        Vec3 dir=new Vec3(p.x()-mc.player.getX(),0,p.z()-mc.player.getZ()).normalize();
        double remaining=Math.hypot(p.x()-mc.player.getX(),p.z()-mc.player.getZ());
        for(double distance:new double[]{Math.min(Math.min(tool.range-3,44),remaining-2),24,12}){
            if(distance<4||distance>=remaining)continue;
            int x=(int)Math.floor(mc.player.getX()+dir.x*distance),z=(int)Math.floor(mc.player.getZ()+dir.z*distance);
            if(!mc.level.hasChunk(x>>4,z>>4))continue;
            int roof=(int)Math.min(mc.player.getY()+ModConfig.INSTANCE.returnFlyCeil,160);
            for(int y=roof;y>=(int)mc.player.getY()-4;y--)
                if(landingOn(mc,new BlockPos(x,y,z),tool))return true;
        }
        return false;
    }
    private boolean blink(Minecraft mc,PestPlots.Plot p,Tool tool){
        from=mc.player.position();Vec3 d=new Vec3(p.x()-from.x,0,p.z()-from.z);
        if(d.length()<tool.range+3)return false;d=d.normalize();goal=from.add(d.scale(tool.range));
        if(!NavigationMotion.airClear(new PathLevel(mc.level),from.x,from.y,from.z,goal.x,goal.y,goal.z,.33))return false;
        if(!mc.player.getAbilities().flying&&!new GroundProbe(new PathLevel(mc.level)).clear(goal.x,goal.y,goal.z,goal.x,goal.z,.31))return false;
        aim=mc.player.getEyePosition().add(d.scale(tool.range));angles(mc,aim);
        slot=tool.slot;ether=false;plot=false;block=null;command=null;return true;
    }
    private void angles(Minecraft mc,Vec3 pos){var eye=mc.player.getEyePosition();yaw=WalkCore.yawTo(eye.x,eye.z,pos.x,pos.z);pitch=(float)-Math.toDegrees(Math.atan2(pos.y-eye.y,Math.hypot(pos.x-eye.x,pos.z-eye.z)));}
    public boolean advance(Minecraft mc,PestPlots.Plot p,int vacuum){
        if(!context(mc)||budget>=12)return false;
        if(cfg().etherwarp){var tool=tool(mc,true);if(tool!=null&&roof(mc,p,tool)){restore=vacuum;return begin(mc);}}
        if(cfg().aotv){var tool=tool(mc,false);if(tool!=null&&blink(mc,p,tool)){restore=vacuum;return begin(mc);}}
        return false;
    }
    private static String uuid(net.minecraft.world.item.ItemStack stack){var d=stack.get(DataComponents.CUSTOM_DATA);return d==null?"":d.copyTag().getCompoundOrEmpty("ExtraAttributes").getStringOr("uuid","");}
    private boolean begin(Minecraft mc){
        if(!NavigationInput.acquire(this))return false;
        world=mc.level;from=mc.player.position();age=stable=0;observed=false;sentAt=0;budget++;
        mc.player.getInventory().setSelectedSlot(slot);PanicDetector.INSTANCE.expectSlot(slot);
        expectedId=id(mc.player.getMainHandItem());expectedUuid=uuid(mc.player.getMainHandItem());
        phase=Phase.AIM;status=plot?"Plot warp: подготовка к известной сохранённой точке":"Предметный переход: реальное наведение";return true;
    }
    private boolean context(Minecraft mc){return mc.player!=null&&mc.level!=null&&mc.gameMode!=null&&mc.screen==null&&mc.isWindowActive()&&PestHunter.INSTANCE.active()&&!PanicHandoff.pending()&&PestHunter.garden(mc)&&!busy();}
    public Result tick(Minecraft mc){
        if(!busy())return Result.DONE;
        if(mc.player==null||mc.level!=world||mc.screen!=null||!mc.isWindowActive()||PanicHandoff.pending()||!NavigationInput.owns(this)||mc.player.getInventory().getSelectedSlot()!=slot)return fail(mc,"контекст/слот изменился");
        if(!expectedId.equals(id(mc.player.getMainHandItem()))||!expectedUuid.isEmpty()&&!expectedUuid.equals(uuid(mc.player.getMainHandItem())))return fail(mc,"инструмент заменён");
        if(++age>160)return fail(mc,"8с: переход не подтверждён");
        var o=mc.options;for(var k:new net.minecraft.client.KeyMapping[]{o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keySprint,o.keyAttack,o.keyUse})NavigationInput.press(this,o,k,false);
        if(phase==Phase.AIM){
            if(mc.player.position().distanceTo(from)>.8)return fail(mc,"сдвиг до команды/использования");
            NavigationInput.press(this,o,o.keyShift,ether&&!id(mc.player.getMainHandItem()).equals("ETHERWARP_CONDUIT"));
            if(!plot){
                NavigationInput.camera(this,()->SmoothTurn.followMouse(yaw,pitch,cfg().aimSpeed));
                if(ReturnCore.angle(mc.player.getYRot(),mc.player.getXRot(),yaw,pitch)>.6){stable=0;return Result.RUNNING;}
                if(ether){var hit=ShotPath.trace(mc,mc.player.getEyePosition(),mc.player.getEyePosition().add(mc.player.getLookAngle().scale(100)));
                    if(hit==null||hit.getType()!=HitResult.Type.BLOCK||!hit.getBlockPos().equals(block)||hit.getDirection()!=Direction.UP)return fail(mc,"фактический луч не на выбранной крыше");}
                else if(!NavigationMotion.airClear(new PathLevel(mc.level),from.x,from.y,from.z,goal.x,goal.y,goal.z,.33))return fail(mc,"коридор изменился");
            }
            if(++stable<4)return Result.RUNNING;
            phase=Phase.WAIT;sentAt=System.currentTimeMillis();stable=0;
            if(plot)mc.player.connection.sendCommand(command);
            else if(mc.player.getCooldowns().isOnCooldown(mc.player.getMainHandItem())||mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND)==net.minecraft.world.InteractionResult.FAIL)return fail(mc,"клиент отклонил предмет/кулдаун");
            status="Жду точный серверный перенос; отправка ≠ успех";return Result.RUNNING;
        }
        NavigationInput.press(this,o,o.keyShift,false);
        if(System.currentTimeMillis()-sentAt>3000)return fail(mc,"сервер не подтвердил ожидаемую точку");
        if(!observed){
            if(mc.player.position().distanceTo(from)>.8)return fail(mc,"неожиданное перемещение без точного подтверждения");
            return Result.RUNNING;
        }
        if(mc.player.position().distanceTo(goal)>1.25)return fail(mc,"после подтверждения покинута цель");
        if(mc.player.getDeltaMovement().length()>.12){stable=0;return Result.RUNNING;}
        if(++stable<3)return Result.RUNNING;
        cancel(mc);status="Переход наблюдался в ожидаемую точку";return Result.DONE;
    }
    /** Called after vanilla applies a position packet, before generic guards. Single-use, no grace area/time-only bypass. */
    public boolean acceptServerMove(Minecraft mc,String kind,double distance){
        if(phase!=Phase.WAIT||observed||!"телепорта".equals(kind)||mc.level!=world||mc.player==null||!NavigationInput.owns(this)||PanicHandoff.pending()
            ||mc.player.getInventory().getSelectedSlot()!=slot||sentAt==0||System.currentTimeMillis()-sentAt>3000||distance<.1)return false;
        var p=mc.player;
        if(p.position().distanceTo(goal)>1.25||Math.abs(p.getY()-goal.y)>.4||ReturnCore.angle(p.getYRot(),p.getXRot(),yaw,pitch)>2)return false;
        if(!NavigationMotion.airClear(new PathLevel(mc.level),goal.x,goal.y,goal.z,goal.x,goal.y,goal.z,.3))return false;
        observed=true;return true;
    }
    private Result fail(Minecraft mc,String why){cancel(mc);status=why;return Result.FAIL;}
    public void cancel(Minecraft mc){
        if(NavigationInput.owns(this)){
            if(mc.options!=null)for(var k:new net.minecraft.client.KeyMapping[]{mc.options.keyUse,mc.options.keyAttack,mc.options.keyShift})NavigationInput.press(this,mc.options,k,false);
            SmoothTurn.stop();NavigationInput.release(this);
            if(mc.player!=null&&mc.level==world&&mc.player.getInventory().getSelectedSlot()==slot&&!PanicHandoff.pending()){
                mc.player.getInventory().setSelectedSlot(restore);PanicDetector.INSTANCE.expectSlot(restore);
            }
        }
        phase=Phase.IDLE;
    }
    private PestTravel(){}
}
