package com.farmmacro.mixin;
import com.farmmacro.combat.AimAssist;
import com.farmmacro.util.Guard;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Independent per-frame update before camera rendering, without requiring physical mouse movement. */
@Mixin(GameRenderer.class)
public class AimFrameMixin {
    @Inject(method="render",at=@At("HEAD"))
    private void farmmacro$aimFrame(DeltaTracker tracker,boolean renderLevel,CallbackInfo ci){
        if(!Guard.run("camera/freecam-frame",com.farmmacro.camera.Freecam::frame))com.farmmacro.camera.Freecam.off(net.minecraft.client.Minecraft.getInstance());
        Guard.runOrDisable("camera/turn",com.farmmacro.camera.SmoothTurn::onFrame);
        if(!Guard.run("combat/aim-frame",AimAssist.INSTANCE::onFrame))AimAssist.INSTANCE.frameFailed();
    }
}
