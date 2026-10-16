package com.farmmacro.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Conservative client geometry, not server projectile range, damage or hit confirmation. */
public final class ScytheShotGeometry {
    public enum Kind { BODY, EARLY_EDGE, AREA_EDGE, GROUND_SPLASH, BLOCKED, UNLOADED }
    public record Check(Kind kind){public boolean ready(){return kind!=Kind.BLOCKED&&kind!=Kind.UNLOADED;}}
    /** Local conservative policy; wiki describes a 3-block ground explosion, not a guaranteed sphere. */
    public static final double SPLASH_REACH=2.3;
    public static boolean splash(Minecraft mc,Vec3 impact,AABB body,Vec3 opening){
        return body.distanceToSqr(impact)<=SPLASH_REACH*SPLASH_REACH
            &&ShotPath.clear(mc,impact.add(0,.025,0),opening);
    }
    public static Check evaluate(Minecraft mc,Mob target,Vec3 opening,boolean early,boolean area){
        Vec3 eye=mc.player.getEyePosition();AABB body=target.getBoundingBox();
        double length=eye.distanceTo(body.getCenter())+Math.max(body.maxY-body.minY,1)+4;
        Vec3 end=eye.add(mc.player.getLookAngle().scale(length));
        double inset=Math.min(.08,Math.min(body.maxX-body.minX,Math.min(body.maxY-body.minY,body.maxZ-body.minZ))*.12);
        double padding=early?.55:area?.3:-inset;
        AABB corridor=body.inflate(padding);
        var entry=corridor.contains(eye)?java.util.Optional.of(eye):corridor.clip(eye,end);
        if(entry.isPresent()&&ShotPath.clear(mc,eye,entry.get())&&ShotPath.clear(mc,entry.get(),opening)){
            // Inflate permits an optional early/area approximation; it is not proof of projectile collision.
            return new Check(early?Kind.EARLY_EDGE:area?Kind.AREA_EDGE:Kind.BODY);
        }
        // Do not reject a clear body hit merely because the extension beyond the mob is unloaded.
        if(!area)return new Check(Kind.BLOCKED);
        var obstacle=ShotPath.trace(mc,eye,end);
        if(obstacle==null)return new Check(Kind.UNLOADED);
        if(obstacle.getType()==HitResult.Type.BLOCK&&obstacle.getDirection()==Direction.UP
            &&splash(mc,obstacle.getLocation(),body,opening))return new Check(Kind.GROUND_SPLASH);
        return new Check(Kind.BLOCKED);
    }
    private ScytheShotGeometry(){}
}
