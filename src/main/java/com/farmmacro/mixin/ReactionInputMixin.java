package com.farmmacro.mixin;

import com.farmmacro.panic.HumanReaction;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** v1.16.3: любой ввод мыши игрока прекращает «человеческую реакцию» (javap 26.1.2: onMove(JDD), onButton(JMouseButtonInfo;I)). */
@Mixin(MouseHandler.class)
public class ReactionInputMixin {

    @Inject(method = "onMove", at = @At("HEAD"))
    private void farmmacro$reactMove(long window, double x, double y, CallbackInfo ci) {
        try { HumanReaction.onMouseMove(x, y); } catch (Throwable ignored) {}
    }

    @Inject(method = "onButton", at = @At("HEAD"))
    private void farmmacro$reactButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        try { if (action == 1) HumanReaction.onUserInput("кнопка мыши"); } catch (Throwable ignored) {}
    }
}
