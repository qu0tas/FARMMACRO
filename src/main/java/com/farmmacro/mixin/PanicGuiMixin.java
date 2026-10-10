package com.farmmacro.mixin;

import com.farmmacro.panic.PanicDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Детектор «открылось чужое окно» (фильтрация — в PanicDetector) и отпуск «Зажима мыши» при любом окне. */
@Mixin(Minecraft.class)
public class PanicGuiMixin {

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void farmmacro$onSetScreen(@Nullable Screen screen, CallbackInfo ci) {
        com.farmmacro.util.Guard.run("hold/screen", () -> com.farmmacro.macro.MouseHold.INSTANCE.onScreenOpening(screen));
        PanicDetector.INSTANCE.onScreenOpening(screen);
    }
}
