package com.farmmacro.gui;

import com.farmmacro.config.ModConfig;
import com.farmmacro.visual.CheekMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * v1.16.2: превью «Анимешных щёчек» в меню — лицо твоего скина (8×8 крупно) и щёки по текущим настройкам
 * (стиль, цвет, яркость, размер, высота, расстояние, «поверх второго слоя», пульсация). Раскладка — та же
 * {@link CheekMath#rect}, что и на модели в мире, так что что видно здесь — то и на голове.
 */
public final class CheekPreview {
    private CheekPreview() {}

    public static final int SIZE = 56, HEIGHT = SIZE + 10;

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath("farmmacro", "textures/entity/cheeks/" + name + ".png");
    }

    public static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w) {
        if (mc.player == null) return;
        ModConfig c = ModConfig.INSTANCE;
        int fx = x + 10, fy = y + 4;
        Identifier skin = mc.player.getSkin().body().texturePath();
        // лицо без второго слоя; второй слой — до или после щёк
        PlayerFaceExtractor.extractRenderState(g, skin, fx, fy, SIZE, false, false, -1);
        if (!c.cheeksOverHat) hat(g, skin, fx, fy);
        float alpha = CheekMath.alpha(c.cheeksOpacity, c.cheeksPulse, mc.player.tickCount);
        int a = Math.round(alpha * 255);
        if (c.cheeksEnabled && a > 0) {
            int tint = (a << 24) | (CheekMath.rgb(c.cheeksColor) & 0xFFFFFF), white = (a << 24) | 0xFFFFFF;
            String base = CheekMath.base(c.cheeksStyle), deco = CheekMath.deco(c.cheeksStyle);
            for (boolean left : new boolean[]{true, false}) {
                float[] q = CheekMath.rect(left, c.cheeksHeight, c.cheeksSpread, c.cheeksSize);
                if (q == null) continue;
                quad(g, tex(base), fx, fy, q, tint);
                if (deco != null) quad(g, tex(deco), fx, fy, q, white);
            }
        }
        if (c.cheeksOverHat) hat(g, skin, fx, fy);
        Ui.text(g, mc.font, c.cheeksEnabled ? "Превью — твой скин" : "Щёчки выключены", fx + SIZE + 10, fy + SIZE / 2 - 4, Ui.SUB);
    }

    private static void hat(GuiGraphicsExtractor g, Identifier skin, int fx, int fy) {
        g.blit(RenderPipelines.GUI_TEXTURED, skin, fx, fy, 40f, 8f, SIZE, SIZE, 8, 8, 64, 64);
    }

    /** Щека q = {x0, y0, x1, y1, u0, v0, u1, v1} (пиксели лица, UV 0..1); u1 < u0 — зеркально. Единица — 1/16 пикселя лица. */
    private static void quad(GuiGraphicsExtractor g, Identifier t, int fx, int fy, float[] q, int argb) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        try {
            float k = SIZE / 8f / 16f;
            pose.translate(fx, fy);
            pose.scale(k, k);
            int x0 = Math.round(q[0] * 16), y0 = Math.round(q[1] * 16), x1 = Math.round(q[2] * 16), y1 = Math.round(q[3] * 16);
            float u0 = Math.min(q[4], q[6]), u1 = Math.max(q[4], q[6]);
            boolean mirror = q[6] < q[4];
            int rw = Math.max(1, Math.round((u1 - u0) * 64)), rh = Math.max(1, Math.round((q[7] - q[5]) * 32));
            if (mirror) { pose.translate(x1, 0); pose.scale(-1, 1); x0 = 0; x1 = x1 - Math.round(q[0] * 16); }
            else { x1 -= x0; }
            g.blit(RenderPipelines.GUI_TEXTURED, t, x0, y0, u0 * 64, q[5] * 32, x1, y1 - y0, rw, rh, 64, 32, argb);
        } finally {
            pose.popMatrix();
        }
    }
}
