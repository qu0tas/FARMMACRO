package com.farmmacro.pest;
import com.farmmacro.route.*;
import com.farmmacro.camera.SmoothTurn;
import com.farmmacro.panic.PanicHandoff;
import com.farmmacro.combat.ShotPath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.*;
import java.util.*;
/** Explicit leaf-only clearing; never mines a farm roof/floor or an arbitrary unseen obstruction. */
public final class PestClearing {
    private final Set<BlockPos> attempted=new HashSet<>();
    private boolean began;
    private BlockPos block;private ClientLevel world;private int ticks;
    public void reset(){attempted.clear();block=null;}
    public boolean begin(Minecraft mc,Vec3 towards){
        if(!com.farmmacro.config.ModConfig.INSTANCE.pests.breakLeaves||towards==null||attempted.size()>=3||mc.player==null||mc.level==null)return false;
        var hit=ShotPath.trace(mc,mc.player.getEyePosition(),towards);
        if(hit==null||hit.getType()!=HitResult.Type.BLOCK||attempted.contains(hit.getBlockPos())||mc.player.getEyePosition().distanceTo(hit.getLocation())>3)return false;
        var at=hit.getBlockPos();if(!mc.level.getBlockState(at).is(BlockTags.LEAVES)||at.getY()<mc.player.getY())return false;
        if(!NavigationInput.acquire(this))return false;
        attempted.add(at);block=at;world=mc.level;ticks=0;began=false;return true;
    }
    /** 0 waiting,1 done,-1 failed. All attempts and duration bounded. */
    public int tick(Minecraft mc){
        if(block==null)return -1;
        if(mc.player==null||mc.level!=world||mc.screen!=null||PanicHandoff.pending()||!NavigationInput.owns(this)||++ticks>80){cancel(mc);return -1;}
        if(!mc.level.getBlockState(block).is(BlockTags.LEAVES)){cancel(mc);return 1;}
        var o=mc.options;for(var k:new net.minecraft.client.KeyMapping[]{o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keyShift,o.keySprint,o.keyUse,o.keyAttack})NavigationInput.press(this,o,k,false);
        Vec3 aim=block.getCenter(),eye=mc.player.getEyePosition();if(eye.distanceTo(aim)>3.8){cancel(mc);return -1;}
        float yaw=WalkCore.yawTo(eye.x,eye.z,aim.x,aim.z),pitch=(float)-Math.toDegrees(Math.atan2(aim.y-eye.y,Math.hypot(aim.x-eye.x,aim.z-eye.z)));
        NavigationInput.camera(this,()->SmoothTurn.followMouse(yaw,pitch,com.farmmacro.config.ModConfig.INSTANCE.pests.aimSpeed));
        var ray=ShotPath.trace(mc,eye,eye.add(mc.player.getLookAngle().scale(3.5)));
        if(ray==null||ray.getType()!=HitResult.Type.BLOCK||!ray.getBlockPos().equals(block))return 0;
        if(mc.gameMode==null){cancel(mc);return -1;}
        if(!began){mc.gameMode.startDestroyBlock(block,ray.getDirection());began=true;}else mc.gameMode.continueDestroyBlock(block,ray.getDirection());
        return 0;
    }
    public void cancel(Minecraft mc){if(NavigationInput.owns(this)){if(mc.gameMode!=null)mc.gameMode.stopDestroyBlock();SmoothTurn.stop();NavigationInput.release(this);}block=null;}
}
