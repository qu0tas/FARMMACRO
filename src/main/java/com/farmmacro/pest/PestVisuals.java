package com.farmmacro.pest;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.AABB;
public final class PestVisuals {
    public static void register(){LevelRenderEvents.COLLECT_SUBMITS.register(ctx->com.farmmacro.util.Guard.runOrDisable("pests/render",()->{
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        var origin=ctx.levelState().cameraRenderState.pos;
        if(com.farmmacro.config.ModConfig.INSTANCE.pests.esp)for(var e:PestHunter.INSTANCE.visiblePests()){
            if(e.level()!=mc.level||!e.isAlive()||e.isRemoved()||e.isInvisibleTo(mc.player))continue;
            var box=e.getBoundingBox().inflate(.03).move(origin.scale(-1));
            ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(),RenderTypes.debugQuads(),(pose,vc)->com.farmmacro.combat.MobHighlightRenderer.outline(vc,pose,box,0xCF71E8A7));
        }
        var assist=com.farmmacro.config.ModConfig.INSTANCE.pests.assist;int[] budget={192};
        if(assist.tracers){var from=mc.player.getEyePosition().add(mc.player.getLookAngle().scale(.5));int count=0;for(var e:PestHunter.INSTANCE.visiblePests()){if(++count>12)break;if(e.level()==mc.level&&e.isAlive()&&!e.isInvisibleTo(mc.player))line(ctx,origin,from,e.getBoundingBox().getCenter(),0xAFAA80EA,budget);}}
        if(assist.route&&PestHunter.INSTANCE.active())com.farmmacro.visual.RouteRenderer.collectServicePath(ctx,mc,PestHunter.INSTANCE.navigationPath());
        for(var v:PestTracker.arc(mc)){
            var dot=new AABB(v.x-.035,v.y-.035,v.z-.035,v.x+.035,v.y+.035,v.z+.035).move(origin.scale(-1));
            ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(),RenderTypes.debugQuads(),(pose,vc)->com.farmmacro.combat.MobHighlightRenderer.outline(vc,pose,dot,0xEFEEAC62));
        }
    }));}
    private static void line(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext ctx,net.minecraft.world.phys.Vec3 camera,net.minecraft.world.phys.Vec3 a,net.minecraft.world.phys.Vec3 b,int color,int[] budget){
        int n=Math.min(24,Math.max(1,(int)Math.ceil(a.distanceTo(b)*2)));for(int i=1;i<=n&&budget[0]-->0;i++){var p=a.lerp(b,i/(double)n);var box=new AABB(p.x-.025,p.y-.025,p.z-.025,p.x+.025,p.y+.025,p.z+.025).move(camera.scale(-1));ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(),RenderTypes.debugQuads(),(pose,vc)->com.farmmacro.combat.MobHighlightRenderer.outline(vc,pose,box,color));}
    }
    private PestVisuals(){}
}
