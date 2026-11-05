package com.farmmacro.mixin;

import com.farmmacro.util.Guard;
import com.farmmacro.visual.NickHide;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Каждый кадр после движения мыши (на любом выходе из метода) — шаг плавного поворота камеры (пресеты, автоход). */
@Mixin(MouseHandler.class)
public class CameraTurnMixin {

    @Inject(method = "handleAccumulatedMovement", at = @At("RETURN"))
    private void farmmacro$smoothTurn(CallbackInfo ci) {
        // SmoothTurn is stepped once in AimFrameMixin, including background frames.
        Guard.run("nick/frame", NickHide::onFrame);
    }
}
