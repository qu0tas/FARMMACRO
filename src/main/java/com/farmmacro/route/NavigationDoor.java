package com.farmmacro.route;

import com.farmmacro.camera.SmoothTurn;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Client-thread door interaction for an input-owning ReturnWalker. Never holds the use key. */
public final class NavigationDoor {
    public enum Result { CLEAR, BUSY, BLOCKED }
    public static final double REACH=3.0;
    private static final int MAX_TICKS=100, RETRY_TICKS=10, MAX_ATTEMPTS=3, MAX_SAMPLES=32;
    private BlockPos door;
    private BlockState expected;
    private Vec3 aim;
    private int ticks, attempts, wait, hidden, openTicks;
    // Survives client-predicted opening / server re-close, but only for this navigation run.
    private final java.util.Map<BlockPos,Integer> attemptsByDoor=new java.util.HashMap<>();
    private float issuedYaw=Float.NaN, issuedPitch=Float.NaN;
    private String failure;

    public boolean active(){return door!=null;}
    public String failure(){return failure;}
    public void reset(){clearTarget();attemptsByDoor.clear();}
    private void clearTarget(){door=null;expected=null;aim=null;ticks=attempts=wait=hidden=openTicks=0;issuedYaw=Float.NaN;failure=null;}

    /** Called only after world/focus/panic/ownership guards. CLEAR does not issue movement. */
    public Result tick(Minecraft mc,List<RoutePoint> points,int index,double turnSpeed){
        if(door==null){
            BlockPos found=find(mc,points,index);
            if(found==null)return Result.CLEAR;
            clearTarget();door=found;expected=mc.level.getBlockState(door);attempts=attemptsByDoor.getOrDefault(door,0);
            SmoothTurn.stop();
        }
        if(!loaded(mc,door)||mc.gameMode==null)return blocked("область двери недоступна");
        BlockState state=mc.level.getBlockState(door);
        if(state.getBlock()!=expected.getBlock())return blocked("дверь заменена во время подхода");
        if(++ticks>MAX_TICKS)return blocked("дверь не открылась за 100 тиков");
        if(!PathLevel.closedOpenable(state)){
            // Client prediction is not proof of a server acknowledgement. Debounce only; keep the retry ledger.
            if(++openTicks<3)return Result.BUSY;
            clearTarget();SmoothTurn.stop();return Result.CLEAR;
        }
        openTicks=0;
        // The last attempt also gets a complete response window; do not fail immediately after clicking.
        if(wait>0)wait--;
        else if(attempts>=MAX_ATTEMPTS)return blocked("дверь не открылась после трёх взаимодействий");
        Vec3 visible=visiblePoint(mc,door);
        if(visible==null){
            if(++hidden>20)return blocked("потеряна видимость или дальность двери");
            SmoothTurn.stop();aim=null;issuedYaw=Float.NaN;return Result.BUSY;
        }
        hidden=0;
        // Keep a valid chosen surface to avoid jumping between upper/lower halves each tick.
        if(aim==null||!visible(mc,door,aim))aim=visible;
        Vec3 eye=mc.player.getEyePosition();
        float yaw=WalkCore.yawTo(eye.x,eye.z,aim.x,aim.z);
        float pitch=(float)-Math.toDegrees(Math.atan2(aim.y-eye.y,Math.hypot(aim.x-eye.x,aim.z-eye.z)));
        if(Float.isNaN(issuedYaw)||ReturnCore.angle(issuedYaw,issuedPitch,yaw,pitch)>2
            ||!SmoothTurn.isActive()&&ReturnCore.angle(mc.player.getYRot(),mc.player.getXRot(),yaw,pitch)>2){
            SmoothTurn.turnMouse(mc.player.getYRot()+WalkCore.wrap(yaw-mc.player.getYRot()),pitch,turnSpeed);
            issuedYaw=yaw;issuedPitch=pitch;
        }
        if(wait>0||attempts>=MAX_ATTEMPTS||ticks==1||mc.player.isShiftKeyDown())return Result.BUSY;
        if(!attemptsByDoor.containsKey(door)&&attemptsByDoor.size()>=64)return blocked("лимит взаимодействий с разными дверями");
        // Fresh ray from actual player view, not a cached crosshair or a desired camera angle.
        BlockHitResult hit=clip(mc,eye.add(mc.player.getLookAngle().scale(reach(mc))));
        if(hit.getType()!=HitResult.Type.BLOCK||!same(mc,hit.getBlockPos(),door))return Result.BUSY;
        if(eye.distanceTo(hit.getLocation())>reach(mc)+1e-6)return Result.BUSY;
        // Never fall through to vanilla useItem (air/weapon ability), never queue a generic right-click.
        var result=mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit);
        if(result instanceof net.minecraft.world.InteractionResult.Success success
            &&success.swingSource()==net.minecraft.world.InteractionResult.SwingSource.CLIENT)mc.player.swing(InteractionHand.MAIN_HAND);
        attempts++;attemptsByDoor.put(door,attempts);wait=RETRY_TICKS;
        return Result.BUSY;
    }

    private Result blocked(String reason){failure=reason;return Result.BLOCKED;}
    private static double reach(Minecraft mc){return Math.max(0,Math.min(REACH,mc.player.blockInteractionRange()));}
    private static boolean loaded(Minecraft mc,BlockPos p){return mc.level.hasChunk(p.getX()>>4,p.getZ()>>4);}
    private static BlockPos root(BlockPos p,BlockState s){
        return s.getBlock() instanceof DoorBlock&&s.getValue(DoorBlock.HALF)==DoubleBlockHalf.UPPER?p.below():p;
    }
    private static boolean same(Minecraft mc,BlockPos hit,BlockPos target){
        if(!loaded(mc,hit)||!loaded(mc,target))return false;
        BlockState s=mc.level.getBlockState(hit),t=mc.level.getBlockState(target);
        return s.getBlock()==t.getBlock()&&root(hit,s).equals(target);
    }
    private static BlockHitResult clip(Minecraft mc,Vec3 to){
        return mc.level.clip(new ClipContext(mc.player.getEyePosition(),to,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,mc.player));
    }
    private static boolean visible(Minecraft mc,BlockPos door,Vec3 point){
        if(mc.player.getEyePosition().distanceTo(point)>reach(mc))return false;
        BlockHitResult hit=clip(mc,point);
        return hit.getType()==HitResult.Type.BLOCK&&same(mc,hit.getBlockPos(),door);
    }
    private static Vec3 visiblePoint(Minecraft mc,BlockPos door){
        Vec3 best=null;double bestScore=Double.POSITIVE_INFINITY;
        int halves=mc.level.getBlockState(door).getBlock() instanceof DoorBlock?2:1;
        for(int dy=0;dy<halves;dy++){
            BlockPos p=door.above(dy);if(!loaded(mc,p))continue;
            BlockState s=mc.level.getBlockState(p);if(!same(mc,p,door))continue;
            int boxes=0;
            for(AABB b:s.getShape(mc.level,p).toAabbs()){
                if(++boxes>32)break;
                // Selection shape (not the cell centre / taller fence collision shape).
                for(double fy:new double[]{.5,.25,.75}){
                    Vec3 point=new Vec3(p.getX()+(b.minX+b.maxX)*.5,p.getY()+b.minY+(b.maxY-b.minY)*fy,p.getZ()+(b.minZ+b.maxZ)*.5);
                    if(!visible(mc,door,point))continue;
                    Vec3 e=mc.player.getEyePosition();
                    float yaw=WalkCore.yawTo(e.x,e.z,point.x,point.z);
                    float pitch=(float)-Math.toDegrees(Math.atan2(point.y-e.y,Math.hypot(point.x-e.x,point.z-e.z)));
                    double score=ReturnCore.angle(mc.player.getYRot(),mc.player.getXRot(),yaw,pitch);
                    if(score<bestScore){bestScore=score;best=point;}
                }
            }
        }
        return best;
    }

    /** Scan only the immediate swept body corridor; a nearby off-route door is not a task. */
    private static BlockPos find(Minecraft mc,List<RoutePoint> points,int index){
        if(mc.player==null||mc.level==null||index>=points.size())return null;
        double ax=mc.player.getX(),ay=mc.player.getY(),az=mc.player.getZ(),remaining=REACH;
        Set<BlockPos> checked=new HashSet<>();int samples=0;
        for(int i=index;i<points.size()&&remaining>0&&samples<MAX_SAMPLES;i++){
            RoutePoint p=points.get(i);
            double dx=p.x-ax,dy=p.y-ay,dz=p.z-az,len=Math.sqrt(dx*dx+dy*dy+dz*dz),distance=Math.min(len,remaining);
            int steps=Math.max(1,(int)Math.ceil(distance/.2));
            for(int j=0;j<=steps&&samples++<MAX_SAMPLES;j++){
                double t=len<1e-9?1:distance/len*j/steps;
                double x=ax+dx*t,y=ay+dy*t,z=az+dz*t;
                AABB body=new AABB(x-.30,y+.001,z-.30,x+.30,y+PathFinder.BODY-.001,z+.30);
                for(int bx=(int)Math.floor(body.minX);bx<=(int)Math.floor(body.maxX);bx++)
                  for(int bz=(int)Math.floor(body.minZ);bz<=(int)Math.floor(body.maxZ);bz++)
                    for(int by=(int)Math.floor(y)-1;by<=(int)Math.floor(body.maxY);by++){
                        BlockPos at=new BlockPos(bx,by,bz);if(!loaded(mc,at))continue;
                        BlockState s=mc.level.getBlockState(at);if(!PathLevel.closedOpenable(s))continue;
                        boolean intersects=false;
                        for(AABB b:s.getCollisionShape(mc.level,at).toAabbs())if(b.move(at).intersects(body)){intersects=true;break;}
                        if(!intersects)continue;
                        BlockPos door=root(at,s);if(!checked.add(door))continue;
                        // Around a corner/out of reach: keep following the approach, not a stationary 5s timeout.
                        if(visiblePoint(mc,door)!=null)return door;
                    }
            }
            remaining-=len;ax=p.x;ay=p.y;az=p.z;
        }
        return null;
    }
}
