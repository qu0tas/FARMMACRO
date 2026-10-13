package com.farmmacro.mixin;

import com.farmmacro.panic.HumanReaction;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** v1.16.3: нажатие клавиши игроком прекращает «человеческую реакцию» (javap 26.1.2: keyPress(JILKeyEvent;)). */
@Mixin(KeyboardHandler.class)
public class ReactionKeyMixin {

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void farmmacro$reactKey(long window, int action, KeyEvent event, CallbackInfo ci) {
        try { if (action == 1) HumanReaction.onUserInput("клавиша"); } catch (Throwable ignored) {}
    }
}
