package com.farmmacro.panic;

import com.farmmacro.config.ModConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;

/**
 * PanicOverlayRenderer — рисует красный оверлей поверх экрана во время паники.
 *
 * Регистрируется через HudElementRegistry в FarmMacroMod.
 * Интенсивность плавно спадает по мере истечения redScreenTicks.
 */
public class PanicOverlayRenderer {

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("farmmacro", "panic_overlay"), (drawContext, tickDeltaManager) -> {
            ModConfig cfg = ModConfig.INSTANCE;
            if (!cfg.panicRedScreenEnabled) return;

            int ticksLeft = PanicDetector.INSTANCE.getRedScreenTicks();
            int maxTicks  = cfg.panicRedScreenTicks;
            if (ticksLeft <= 0 || maxTicks <= 0) return;

            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;

            // ticksLeft убывает maxTicks→0, поэтому:
            // в начале ratio=1.0 (ярко красный), в конце ratio=0.0 (прозрачно)
            float ratio = (float) ticksLeft / (float) maxTicks;
            int alpha   = (int)(ratio * 160); // макс 160 из 255 (~63% прозрачности)
            int color   = (alpha << 24) | 0xFF0000;

            int w = client.getWindow().getGuiScaledWidth();
            int h = client.getWindow().getGuiScaledHeight();
            drawContext.fill(0, 0, w, h, color);
        });
    }
}
