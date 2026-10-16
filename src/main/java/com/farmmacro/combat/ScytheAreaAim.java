package com.farmmacro.combat;
import com.farmmacro.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.concurrent.ThreadLocalRandom;
/** Smooth aim-point plans: body edge or verified nearby floor, never a random delta stream. */
public final class ScytheAreaAim {
    private Vec3 offset=Vec3.ZERO,desired=Vec3.ZERO;private double clock;private int side=1;private boolean planned;
    public void reset(){offset=desired=Vec3.ZERO;clock=0;side=1;planned=false;}
    public static boolean enabled(Minecraft mc){var c=ModConfig.INSTANCE;return c.aimScytheArea&&c.motion.camera.aimHumanStrength>0&&"human".equals(c.motion.camera.mode(c.motion))&&ShotPath.holdingScythe(mc);}
    public Vec3 point(Minecraft mc,Mob mob,Vec3 eye,Vec3 center,double dt){
        if(!enabled(mc))return center;
        var box=mob.getBoundingBox();var random=ThreadLocalRandom.current();var c=ModConfig.INSTANCE;
        double amount=c.motion.camera.aimHumanStrength/100.0,height=box.maxY-box.minY;
        double dx=center.x-eye.x,dz=center.z-eye.z,length=Math.hypot(dx,dz);
        Vec3 lateral=length>.01?new Vec3(-dz/length,0,dx/length):new Vec3(1,0,0);
        clock-=Math.clamp(dt,0,.05);
        if(clock<=0){
            clock=random.nextDouble(.28,.72);
            if(random.nextDouble()<.45)side=-side;
            boolean near=random.nextInt(100)<c.aimScytheNearChance;
            // Projected half-width of the real box along screen-horizontal direction.
            double half=(Math.abs(lateral.x)*(box.maxX-box.minX)+Math.abs(lateral.z)*(box.maxZ-box.minZ))/2;
            Vec3 edge=lateral.scale(side*(half+.08+random.nextDouble(.04,.14)*amount))
                .add(0,random.nextDouble(-.16,.08)*height,0);
            if(near){
                desired=edge;
                if((c.aimPitch||c.aimScytheOnly)&&random.nextDouble()<.60){
                    Vec3 ground=lateral.scale(side*random.nextDouble(.55,1.05)).add(0,-height/2-.25,0);
                    Vec3 candidate=resolve(mc,eye,center.add(ground),center,mob,true);
                    if(candidate!=null)desired=candidate.subtract(center);
                }
            }else{
                double spread=.08+.14*amount;
                desired=new Vec3(random.nextDouble(-spread,spread),random.nextDouble(-.16,.10)*height,random.nextDouble(-spread,spread));
            }
        }
        // Acquire a varied goal immediately; HumanAimTurn still moves the actual camera smoothly.
        // Otherwise short shot/next cycles fired at the center before drift had time to develop.
        if(!planned){offset=desired;planned=true;}
        else offset=offset.lerp(desired,1-Math.exp(-5.5*Math.clamp(dt,0,.05)));
        Vec3 candidate=resolve(mc,eye,center.add(offset),center,mob,false);
        return candidate==null?center:candidate;
    }
    private Vec3 resolve(Minecraft mc,Vec3 eye,Vec3 candidate,Vec3 center,Mob mob,boolean floorRequired){
        var hit=ShotPath.trace(mc,eye,candidate);if(hit==null)return null;
        if(hit.getType()==HitResult.Type.MISS)return floorRequired?null:candidate;
        Vec3 above=hit.getLocation().add(0,.025,0);
        if(hit.getDirection()==Direction.UP&&ScytheShotGeometry.splash(mc,hit.getLocation(),mob.getBoundingBox(),center))return above;
        return null;
    }
}
