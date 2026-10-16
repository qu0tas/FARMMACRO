package com.farmmacro.combat;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
/** Collision ray with loaded-chunk verification, not a prediction of server damage or projectile lifetime. */
public final class ShotPath {
    public static boolean holdingScythe(Minecraft mc){
        if(mc.player==null)return false;
        var stack=mc.player.getMainHandItem();var data=stack.get(DataComponents.CUSTOM_DATA);
        String id=data==null?"":data.copyTag().getCompoundOrEmpty("ExtraAttributes").getStringOr("id","");
        if(!id.isEmpty())return id.equals("GLACIAL_SCYTHE");
        return com.farmmacro.panic.ServerText.clean(stack.getHoverName().getString()).contains("Glacial Scythe");
    }
    public static boolean clear(Minecraft mc,Vec3 from,Vec3 to){
        var hit=trace(mc,from,to);return hit!=null&&hit.getType()==HitResult.Type.MISS;
    }
    public static net.minecraft.world.phys.BlockHitResult trace(Minecraft mc,Vec3 from,Vec3 to){
        if(mc.level==null||mc.player==null||!finite(from)||!finite(to))return null;
        // Never raycast through unloaded chunks: unloaded air is not evidence of a clear shot.
        int x=((int)Math.floor(from.x))>>4,z=((int)Math.floor(from.z))>>4;
        int endX=((int)Math.floor(to.x))>>4,endZ=((int)Math.floor(to.z))>>4;
        // Corrupt coordinates must not turn the loaded-chunk DDA into an unbounded main-thread loop.
        if(Math.abs((long)endX-x)+Math.abs((long)endZ-z)>4096||Math.abs(to.y-from.y)>65536)return null;
        double dx=to.x-from.x,dz=to.z-from.z;
        int sx=dx>0?1:dx<0?-1:0,sz=dz>0?1:dz<0?-1:0;
        double tx=sx==0?Double.POSITIVE_INFINITY:((sx>0?(x+1)*16.0:x*16.0)-from.x)/dx;
        double tz=sz==0?Double.POSITIVE_INFINITY:((sz>0?(z+1)*16.0:z*16.0)-from.z)/dz;
        double stepX=sx==0?Double.POSITIVE_INFINITY:16/Math.abs(dx),stepZ=sz==0?Double.POSITIVE_INFINITY:16/Math.abs(dz);
        double entered=0;
        while(true){
            if(!mc.level.getChunkSource().hasChunk(x,z)){
                // An actual collision before an unknown extension is useful; unknown air is never clear.
                if(entered<=0)return null;
                Vec3 knownEnd=from.lerp(to,Math.max(0,entered-1e-7));
                var hit=mc.level.clip(new ClipContext(from,knownEnd,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));
                return hit.getType()==HitResult.Type.BLOCK?hit:null;
            }
            if(x==endX&&z==endZ)break;
            // Do not cross past an endpoint exactly on a negative-direction chunk boundary.
            if(x==endX){entered=tz;z+=sz;tz+=stepZ;}
            else if(z==endZ){entered=tx;x+=sx;tx+=stepX;}
            else if(tx<=tz){entered=tx;x+=sx;tx+=stepX;}else{entered=tz;z+=sz;tz+=stepZ;}
        }
        return mc.level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));
    }
    private static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
    private ShotPath(){}
}
