package com.farmmacro.mixin;
import com.mojang.blaze3d.platform.FramerateLimitTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(FramerateLimitTracker.class)
public class PerformanceFpsMixin {
 @Inject(method="getFramerateLimit",at=@At("RETURN"),cancellable=true)
 private void kaelix$background(CallbackInfoReturnable<Integer> ci){ci.setReturnValue(com.farmmacro.visual.PerformanceMode.backgroundLimit(ci.getReturnValueI()));}
}
