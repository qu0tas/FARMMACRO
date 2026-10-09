package com.farmmacro.mixin;

import com.farmmacro.camera.SmoothTurn;
import com.farmmacro.util.Guard;
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
        Guard.runOrDisable("camera/turn", SmoothTurn::onFrame);
    }
}
