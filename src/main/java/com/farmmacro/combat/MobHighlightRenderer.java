package com.farmmacro.combat;
import com.farmmacro.config.ModConfig;
import com.farmmacro.util.Guard;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
/** Depth-tested body outlines. No see-through render type or hidden entity discovery. */
public final class MobHighlightRenderer {
    public static void register(){LevelRenderEvents.COLLECT_SUBMITS.register(ctx->{
        if(!Guard.isDisabled("combat/highlight"))Guard.runOrDisable("combat/highlight",()->collect(ctx));
    });}
    private static void collect(LevelRenderContext ctx){
        var c=ModConfig.INSTANCE;Minecraft mc=Minecraft.getInstance();
        if(!c.aimAssistEnabled||!c.aimHighlight||mc.level==null||mc.player==null)return;
        Vec3 camera=ctx.levelState().cameraRenderState.pos;
        float partial=mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for(var n:AimAssist.INSTANCE.visibleMobs()){
            var reason=AimAssist.INSTANCE.highlightReason(n);
            if(reason==TargetDiagnostics.Reason.INVALID||reason==TargetDiagnostics.Reason.FILTER||reason==TargetDiagnostics.Reason.RANGE||reason==TargetDiagnostics.Reason.COVER)continue;
            var m=n.mob();
            if(m.level()!=mc.level||m.isRemoved()||!m.isAlive()||m.isDeadOrDying()||m.isInvisibleTo(mc.player))continue;
            // Depth testing clips geometry behind walls even between visibility snapshots.
            AABB box=m.getBoundingBox().move(m.getPosition(partial).subtract(m.position())).inflate(.025).move(camera.scale(-1));
            int color=switch(reason){
                case CHOSEN->0xEFFF8844;case WAITING->0xBF9999CC;case FOV,SPECIAL->0xAF879399;
                default->AimAssist.zealot(n.name())?0xDFFFD34E:0xBF56D7EF;
            };
            ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(),RenderTypes.debugQuads(),(pose,vc)->outline(vc,pose,box,color));
        }
    }
    public static void outline(VertexConsumer vc,PoseStack.Pose p,AABB b,int col){
        double t=.012;
        // Twelve slim edge cuboids instead of an opaque filled body.
        for(int i=0;i<4;i++){
            double x=(i&1)==0?b.minX:b.maxX,y=(i&1)==0?b.minY:b.maxY,z=(i&2)==0?b.minZ:b.maxZ;
            prism(vc,p,new AABB(b.minX,y-t,z-t,b.maxX,y+t,z+t),col);
            prism(vc,p,new AABB(x-t,b.minY,z-t,x+t,b.maxY,z+t),col);
            double yy=(i&2)==0?b.minY:b.maxY;
            prism(vc,p,new AABB(x-t,yy-t,b.minZ,x+t,yy+t,b.maxZ),col);
        }
    }
    private static final int[][] FACES={{0,1,3,2},{4,6,7,5},{0,4,5,1},{2,3,7,6},{0,2,6,4},{1,5,7,3}};
    private static void prism(VertexConsumer v,PoseStack.Pose p,AABB b,int c){
        for(int[] face:FACES)for(int i:face)v.addVertex(p,(float)((i&1)==0?b.minX:b.maxX),(float)((i&2)==0?b.minY:b.maxY),(float)((i&4)==0?b.minZ:b.maxZ)).setColor(c);
    }
    private MobHighlightRenderer(){}
}
