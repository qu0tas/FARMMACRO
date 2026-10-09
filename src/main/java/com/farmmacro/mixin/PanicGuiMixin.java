package com.farmmacro.mixin;

import com.farmmacro.macro.MacroManager;
import com.farmmacro.panic.PanicDetector;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class PanicGuiMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("FarmMacro/PanicGuiMixin");

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void onSetScreen(@Nullable Screen screen, CallbackInfo ci) {
        boolean playing = MacroManager.INSTANCE.isPlaying();
        LOGGER.debug("[PanicGuiMixin] setScreen вызван: screen={} playing={}",
                screen != null ? screen.getClass().getSimpleName() : "null", playing);

        if (screen == null) return;
        if (!playing) return;
        if (screen instanceof net.minecraft.client.gui.screen.GameMenuScreen) {
            LOGGER.debug("[PanicGuiMixin] Игнорирую GameMenuScreen (пауза)");
            return;
        }

        LOGGER.warn("[PanicGuiMixin] Внешний GUI во время макроса: {}", screen.getClass().getName());
        PanicDetector.INSTANCE.notifyGuiOpenedExternally();
    }
}
