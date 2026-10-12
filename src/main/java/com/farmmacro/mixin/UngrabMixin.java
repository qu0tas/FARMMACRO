package com.farmmacro.mixin;

import com.farmmacro.camera.Ungrab;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** v1.13.1 «Отпустить курсор»: игра не захватывает курсор снова (клик в окно, закрытие меню). */
@Mixin(MouseHandler.class)
public class UngrabMixin {
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void farmmacro$noGrab(CallbackInfo ci) {
        if (Ungrab.blockGrab()) ci.cancel();
    }
}
