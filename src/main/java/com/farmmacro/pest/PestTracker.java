package com.farmmacro.pest;
import com.farmmacro.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.phys.Vec3;
import java.util.*;
/** Short, explicitly requested particle capture. A trail is a direction hint, NOT a hidden entity. */
public final class PestTracker {
    private static final List<Vec3> samples=new ArrayList<>();
    private static ClientLevel level;
    private static long began,nextUse;
    public static boolean request(Minecraft mc){
        long now=System.currentTimeMillis();
        if(now<nextUse||mc.player==null||mc.level==null||mc.gameMode==null||mc.screen!=null||com.farmmacro.panic.PanicHandoff.pending()||!PestHunter.INSTANCE.active()||VacuumInfo.read(mc.player.getMainHandItem())==null||!PestHunter.garden(mc))return false;
        if(mc.player.isShiftKeyDown())return false;
        clear();level=mc.level;began=now;nextUse=now+1400;
        // LocalPlayer.swing verified by javap: vanilla animation packet, no block/entity attack.
        mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        return true;
    }
    public static void particle(Minecraft mc,ClientboundLevelParticlesPacket p){
        long now=System.currentTimeMillis();
        if(!mc.isSameThread()||level!=mc.level||mc.player==null||began==0||now-began>2200||samples.size()>=96||!PestHunter.INSTANCE.active())return;
        if(p.getParticle().getType()!=ParticleTypes.ANGRY_VILLAGER||p.getCount()!=1||p.getMaxSpeed()!=0||p.getXDist()!=0||p.getYDist()!=0||p.getZDist()!=0)return;
        Vec3 v=new Vec3(p.getX(),p.getY(),p.getZ());if(!Double.isFinite(v.x)||!Double.isFinite(v.y)||!Double.isFinite(v.z))return;
        Vec3 last=samples.isEmpty()?mc.player.position():samples.getLast();double d=v.distanceTo(last);
        if(samples.isEmpty()?d>5:d<.03||d>2.8)return;
        samples.add(v);
    }
    public static boolean ready(){return began>0&&System.currentTimeMillis()-began>=2300;}
    public static Vec3 hint(Minecraft mc){
        if(level!=mc.level||samples.size()<5||System.currentTimeMillis()-began>5000)return null;
        Vec3 a=samples.get(Math.max(0,samples.size()-5)),b=samples.getLast(),delta=b.subtract(a);
        if(delta.horizontalDistance()<.7)return null;
        // Conservative linear tangent, independent implementation; no fitted remote pest position.
        return b.add(delta.normalize().scale(10));
    }
    public static List<Vec3> arc(Minecraft mc){return ModConfig.INSTANCE.pests.drawArc&&level==mc.level&&System.currentTimeMillis()-began<5000?List.copyOf(samples):List.of();}
    public static void clear(){samples.clear();level=null;began=0;}
    private PestTracker(){}
}
