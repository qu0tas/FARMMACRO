package com.farmmacro.panic;

import com.farmmacro.config.ModConfig;
import com.farmmacro.gui.Ui;
import com.farmmacro.util.Guard;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * Красный экран паники: заливка + затемнённые края + надпись «ПАНИКА» и причина.
 * Плавно гаснет за panicRedScreenTicks.
 */
public class PanicOverlayRenderer {

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("farmmacro", "panic_overlay"), Guard.hud("panic_overlay", (g, delta) -> {
            ModConfig cfg = ModConfig.INSTANCE;
            int left = PanicDetector.INSTANCE.getRedScreenTicks();
            int max = cfg.panicRedScreenTicks;
            if (!cfg.panicRedScreenEnabled || left <= 0 || max <= 0) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            float ratio = Math.max(0, Math.min(1, (left - delta.getGameTimeDeltaPartialTick(false)) / (float) max));
            float flash = 0.85f + 0.15f * (float) Math.sin(System.currentTimeMillis() / 90.0);
            int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();

            g.fill(0, 0, w, h, Ui.alpha(0xFFFF1E2D, ratio * 0.38f * flash));
            int edge = Math.max(20, h / 5);
            int dark = Ui.alpha(0xFF6A0010, ratio * 0.7f);
            g.fillGradient(0, 0, w, edge, dark, 0x006A0010);
            g.fillGradient(0, h - edge, w, h, 0x006A0010, dark);

            float textA = Math.min(1, ratio * 2.2f);
            if (textA < 0.05f) return;
            Matrix3x2fStack pose = g.pose();
            pose.pushMatrix();
            try {
                pose.translate(w / 2f, h / 2f - 26);
                pose.scale(3f, 3f);
                String title = "ПАНИКА";
                g.text(mc.font, title, -mc.font.width(title) / 2, -4, Ui.alpha(0xFFFFFFFF, textA), true);
            } finally {
                pose.popMatrix();     // стек матриц не должен «протечь» даже при исключении
            }

            String reason = PanicDetector.INSTANCE.getOverlayText();
            if (reason != null) {
                String r = Ui.ellipsize(mc.font, reason, w - 40);
                int rw = mc.font.width(r) + 16;
                Ui.pill(g, w / 2 - rw / 2, h / 2 - 6, rw, 16, Ui.alpha(0xFF000000, 0.55f * textA));
                g.text(mc.font, r, w / 2 - mc.font.width(r) / 2, h / 2 - 2, Ui.alpha(0xFFFFFFFF, textA), false);
            }
        }));
    }
}
