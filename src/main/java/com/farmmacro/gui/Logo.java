package com.farmmacro.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * v1.16.0: логотип Kaelix — буква «K», внутри плывёт синяя плазма (из левого верхнего угла в правый нижний, по кругу).
 * Кадры заранее нарисованы в атласе textures/gui/logo/k_atlas.png (12×8 кадров по 64 px, blur — плавно при любом масштабе).
 * Надпись рядом — переливающийся градиент по буквам.
 */
public final class Logo {
    private Logo() {}

    static final int CELL = 64, COLS = 12, ROWS = 8, FRAMES = COLS * ROWS;
    /** Полный цикл анимации, мс (кадр ≈ 62 мс). */
    static final long LOOP_MS = 6000;
    private static final Identifier ATLAS = Identifier.fromNamespaceAndPath("farmmacro", "textures/gui/logo/k_atlas.png");

    /** Градиент как у логотипа: тёмно-синий → синий → белый блик → голубой → синий. */
    private static final int[] STOPS = {0xFF2A3AD0, 0xFF4C6CF0, 0xFF6F9AFF, 0xFFE1F0FF, 0xFF6AB4FF, 0xFF3456E0, 0xFF2A3AD0};

    static int frame(long ms) {
        return (int) ((ms % LOOP_MS) * FRAMES / LOOP_MS);
    }

    /** Квадрат size×size с левым верхним углом (x, y). */
    public static void draw(GuiGraphicsExtractor g, int x, int y, int size) {
        int f = frame(System.currentTimeMillis());
        int u = (f % COLS) * CELL, v = (f / COLS) * CELL;
        g.blit(RenderPipelines.GUI_TEXTURED, ATLAS, x, y, u, v, size, size, CELL, CELL, CELL * COLS, CELL * ROWS);
    }

    /** Цвет градиента в точке t (0..1, по кругу). */
    static int gradient(double t) {
        t -= Math.floor(t);
        double p = t * (STOPS.length - 1);
        int i = (int) p;
        return Ui.lerp(STOPS[i], STOPS[Math.min(STOPS.length - 1, i + 1)], (float) (p - i));
    }

    /** Надпись, по которой бежит блик (слева направо, по кругу). */
    public static void word(GuiGraphicsExtractor g, Font f, String s, int x, int y) { word(g, f, s, x, y, 0); }

    /** То же вразрядку: между буквами +gap px. */
    public static void word(GuiGraphicsExtractor g, Font f, String s, int x, int y, int gap) {
        double phase = (System.currentTimeMillis() % 3000) / 3000.0;
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            g.text(f, ch, cx, y, gradient(i * 0.09 - phase), false);
            cx += f.width(ch) + gap;
        }
    }

    public static int wordWidth(Font f, String s) { return f.width(s); }
}
