package com.farmmacro.mixin;

import com.farmmacro.camera.Freecam;
import com.farmmacro.camera.SmoothTurn;
import com.farmmacro.combat.AimAssist;
import com.farmmacro.util.Guard;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Independent per-frame update before camera rendering, without requiring physical mouse movement. */
@Mixin(GameRenderer.class)
public class AimFrameMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void farmmacro$aimFrame(DeltaTracker tracker, boolean renderLevel, CallbackInfo ci) {
        if (!Guard.run("camera/freecam-frame", Freecam::frame)) Freecam.off(Minecraft.getInstance());
        Guard.runOrDisable("camera/turn", SmoothTurn::onFrame);
        if (!Guard.run("combat/aim-frame", AimAssist.INSTANCE::onFrame)) AimAssist.INSTANCE.frameFailed();
    }
}
