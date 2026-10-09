package com.farmmacro.mixin;

import com.farmmacro.route.RouteEditor;
import com.farmmacro.util.Guard;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Редактор маршрута: клики ЛКМ/ПКМ забирает редактор, в мир (ломание, удары, использование) они не уходят.
 * {@code handleKeybinds} зовётся из тика игры без открытого окна; клики снимаем до того, как их увидит игра.
 */
@Mixin(Minecraft.class)
public class EditorInputMixin {

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void farmmacro$editorClicks(CallbackInfo ci) {
        if (!RouteEditor.isActive()) return;
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.screen != null || mc.player == null) return;
        while (mc.options.keyAttack.consumeClick()) Guard.run("editor/left", () -> RouteEditor.onLeftClick(mc));
        while (mc.options.keyUse.consumeClick())    Guard.run("editor/right", () -> RouteEditor.onRightClick(mc));
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void farmmacro$noAttack(CallbackInfoReturnable<Boolean> cir) {
        if (RouteEditor.isActive()) cir.setReturnValue(false);
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void farmmacro$noUse(CallbackInfo ci) {
        if (RouteEditor.isActive()) ci.cancel();
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void farmmacro$noContinue(boolean down, CallbackInfo ci) {
        if (RouteEditor.isActive()) ci.cancel();
    }
}
