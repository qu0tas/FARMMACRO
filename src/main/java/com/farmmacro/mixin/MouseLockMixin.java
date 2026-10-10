package com.farmmacro.mixin;

import com.farmmacro.camera.MouseLock;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Блокировка мыши: {@code turnPlayer(double)} вызывается из {@code handleAccumulatedMovement} только в мире без окна;
 * отменяем его в HEAD. Накопленное движение (accumulatedDX/DY) обнуляет сам вызывающий метод после turnPlayer
 * (javap 26.1.2), поэтому после снятия блокировки рывка нет. SmoothTurn (RETURN handleAccumulatedMovement) не затронут.
 */
@Mixin(MouseHandler.class)
public class MouseLockMixin {

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void farmmacro$mouseLock(double movementTime, CallbackInfo ci) {
        boolean block;
        try { block = MouseLock.shouldBlock(); }
        catch (Throwable t) { block = false; }
        if (block) ci.cancel();
    }
}
