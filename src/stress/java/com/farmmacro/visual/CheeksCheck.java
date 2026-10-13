package com.farmmacro.visual;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/** v1.16.1 «Анимешные щёчки»: раскладка, зеркало, обрезка по лицу, стили и текстуры (без игры). */
public final class CheeksCheck {
    private CheeksCheck() {}

    static int checks;
    static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("CheeksCheck: " + what);
        checks++;
    }
    static boolean near(float a, double b) { return Math.abs(a - b) < 1e-4; }

    public static void main() {
        // по умолчанию: симметрично, под глазами (строка 4), внутри лица
        float[] l = CheekMath.rect(true, 5.6, 2.45, 100), r = CheekMath.rect(false, 5.6, 2.45, 100);
        check(l != null && r != null, "щёки по умолчанию пропали");
        check(near(l[0] + r[2], 8) && near(l[2] + r[0], 8), "щёки не симметричны");
        check(l[1] >= 4.5f, "щека залезает на глаза: y0=" + l[1]);
        check(l[0] >= 0 && r[2] <= 8 && l[3] <= 8, "щека вылезла за лицо");
        check(near(l[4], 0) && near(l[6], 1), "левая щека: u должно идти 0→1");
        check(near(r[4], 1) && near(r[6], 0), "правая щека: u должно быть зеркальным 1→0");
        check(near(l[2] - l[0], CheekMath.BASE_W) && near(l[3] - l[1], CheekMath.BASE_H), "размер 100% не равен базовому");

        // крупные и разведённые к краю — обрезаются по лицу, UV тоже
        float[] big = CheekMath.rect(true, 7.5, 3.5, 200);
        check(big != null && near(big[0], 0) && near(big[3], 8), "обрезка по краю лица");
        check(big[4] > 0 && big[4] < 1 && big[7] < 1, "UV при обрезке");
        float[] bigR = CheekMath.rect(false, 7.5, 3.5, 200);
        check(near(bigR[2], 8) && near(bigR[6], big[4]) && near(bigR[4], 1), "обрезка правой щеки не зеркальна");
        check(CheekMath.rect(true, 7.5, 3.5, 50) != null, "маленькая щека у края пропала");

        // стили и цвета
        for (CheekMath.Choice s : CheekMath.STYLES) {
            check(CheekMath.isStyle(s.id()), "стиль " + s.id());
            check(tex(CheekMath.base(s.id())) != null, "нет текстуры пятна для " + s.id());
            String d = CheekMath.deco(s.id());
            if (d != null) check(tex(d) != null, "нет текстуры украшения " + d);
        }
        check(CheekMath.deco("soft") == null && CheekMath.deco("stripes") == null, "лишние украшения");
        check(!CheekMath.isStyle("nope") && !CheekMath.isColor("nope"), "неизвестный id принят");
        check(CheekMath.rgb("nope") == CheekMath.COLORS.get(0).rgb(), "неизвестный цвет — не розовый");

        // украшение — с внешней (левой) стороны текстуры левой щеки
        for (String d : new String[]{"deco_hearts", "deco_lines"}) {
            BufferedImage img = tex(d);
            check(img.getWidth() == 64 && img.getHeight() == 32, d + ": размер");
            int x = firstOpaqueX(img);
            check(x >= 0 && x < 16, d + ": сердечко должно быть у внешнего (левого) края, x=" + x);
        }
        BufferedImage soft = tex("blush_soft");
        check(alpha(soft, 32, 16) > 200 && alpha(soft, 0, 0) == 0, "пятно: центр плотный, угол прозрачный");

        // прозрачность
        check(CheekMath.alpha(0, false, 0) == 0 && CheekMath.alpha(100, false, 0) == 1, "alpha 0/100");
        for (int t = 0; t < 400; t++) {
            float a = CheekMath.alpha(80, true, t);
            check(a > 0.55f && a <= 0.8f + 1e-6f, "пульсация вне диапазона: " + a);
        }
        System.out.println("CheeksCheck: OK (" + checks + " проверок)");
    }

    static int alpha(BufferedImage img, int x, int y) { return (img.getRGB(x, y) >>> 24) & 0xFF; }

    static int firstOpaqueX(BufferedImage img) {
        for (int x = 0; x < img.getWidth(); x++)
            for (int y = 0; y < img.getHeight(); y++) if (alpha(img, x, y) > 128) return x;
        return -1;
    }

    static BufferedImage tex(String name) {
        try (InputStream in = CheeksCheck.class.getResourceAsStream("/assets/farmmacro/textures/entity/cheeks/" + name + ".png")) {
            return in == null ? null : ImageIO.read(in);
        } catch (Exception e) {
            return null;
        }
    }
}
