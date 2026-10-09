package com.farmmacro.panic;

import com.farmmacro.config.ModConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * PanicOverlayRenderer — рисует красный оверлей поверх экрана во время паники.
 *
 * Регистрируется через HudRenderCallback в FarmMacroMod.
 * Интенсивность плавно спадает по мере истечения redScreenTicks.
 */
public class PanicOverlayRenderer {

    public static void register() {
        HudRenderCallback.EVENT.register((drawContext, tickDeltaManager) -> {
            ModConfig cfg = ModConfig.INSTANCE;
            if (!cfg.panicRedScreenEnabled) return;

            int ticksLeft = PanicDetector.INSTANCE.getRedScreenTicks();
            int maxTicks  = cfg.panicRedScreenTicks;
            if (ticksLeft <= 0 || maxTicks <= 0) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            // ticksLeft убывает maxTicks→0, поэтому:
            // в начале ratio=1.0 (ярко красный), в конце ratio=0.0 (прозрачно)
            float ratio = (float) ticksLeft / (float) maxTicks;
            int alpha   = (int)(ratio * 160); // макс 160 из 255 (~63% прозрачности)
            int color   = (alpha << 24) | 0xFF0000;

            int w = client.getWindow().getScaledWidth();
            int h = client.getWindow().getScaledHeight();
            drawContext.fill(0, 0, w, h, color);
        });
    }
}
