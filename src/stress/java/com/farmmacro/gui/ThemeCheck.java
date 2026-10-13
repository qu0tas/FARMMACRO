package com.farmmacro.gui;

import java.util.HashSet;
import java.util.Set;

/** v1.16.0 Kaelix: темы меню (все применяются, читаемы, ручка видна на дорожке) и кадры логотипа (цикл без пропусков). */
public final class ThemeCheck {
    private ThemeCheck() {}

    static int checks;
    static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("ThemeCheck: " + what);
        checks++;
    }

    static double lum(int c) {
        double r = ((c >> 16) & 0xFF) / 255.0, g = ((c >> 8) & 0xFF) / 255.0, b = (c & 0xFF) / 255.0;
        return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b);
    }
    static double lin(double v) { return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }
    static double contrast(int a, int b) {
        double la = lum(a), lb = lum(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    public static void main() {
        Set<Integer> windows = new HashSet<>();
        check("quiet".equals(Ui.theme()), "по умолчанию — quiet, а не " + Ui.theme());
        for (String[] t : Ui.THEMES) {
            Ui.applyTheme(t[0]);
            check(t[0].equals(Ui.theme()), t[0] + ": не применилась");
            check(contrast(Ui.TEXT, Ui.WINDOW) >= 7, t[0] + ": текст на окне " + contrast(Ui.TEXT, Ui.WINDOW));
            check(contrast(Ui.TEXT, Ui.CARD) >= 7, t[0] + ": текст на карточке");
            check(contrast(Ui.SUB, Ui.WINDOW) >= 3, t[0] + ": подпись на окне " + contrast(Ui.SUB, Ui.WINDOW));
            check(contrast(Ui.ON_ACCENT, Ui.ACCENT) >= 2.5, t[0] + ": текст кнопки на акценте " + contrast(Ui.ON_ACCENT, Ui.ACCENT));
            check(contrast(Ui.KNOB_ON, Ui.TOGGLE_ON) >= 1.8, t[0] + ": ручка на вкл. дорожке " + contrast(Ui.KNOB_ON, Ui.TOGGLE_ON));
            check(contrast(Ui.TEXT, Ui.TOOLTIP | 0xFF000000) >= 7, t[0] + ": текст подсказки");
            check(Ui.DANGER != Ui.ACCENT && Ui.DANGER != Ui.ON, t[0] + ": опасность совпадает с акцентом");
            check(((Ui.HUD_BG >>> 24) & 0xFF) >= 0xA0, t[0] + ": фон HUD слишком прозрачный");
            check(lum(Ui.HUD_BG) < 0.05, t[0] + ": HUD должен остаться тёмным (цветной текст HUD)");
            check(windows.add(Ui.WINDOW), t[0] + ": окно как у другой темы");
            check(t[0].equals("classic") ? !Ui.flat() && Ui.SHADOWS : Ui.flat() && !Ui.SHADOWS, t[0] + ": плоскость/тени");
        }
        Ui.applyTheme("нет-такой");
        check("quiet".equals(Ui.theme()), "неизвестная тема → quiet");
        Ui.sync(null);
        check("quiet".equals(Ui.theme()), "null → quiet");
        Ui.applyTheme("paper"); Ui.sync("paper");
        check("paper".equals(Ui.theme()), "sync той же темы");
        Ui.sync("quiet");

        // логотип: все 96 кадров по кругу, без пропусков, ровно за LOOP_MS
        Set<Integer> fr = new HashSet<>();
        int prev = Logo.frame(0), jumps = 0;
        for (long ms = 0; ms < Logo.LOOP_MS * 2; ms += 5) {
            int f = Logo.frame(ms);
            check(f >= 0 && f < Logo.FRAMES, "кадр вне атласа " + f);
            fr.add(f);
            if (f != prev && f != (prev + 1) % Logo.FRAMES) jumps++;
            prev = f;
        }
        check(fr.size() == Logo.FRAMES, "кадров показано " + fr.size());
        check(jumps == 0, "пропуски кадров " + jumps);
        check(Logo.frame(Logo.LOOP_MS) == 0 && Logo.frame(Long.MAX_VALUE / 2) < Logo.FRAMES, "цикл");
        check(Logo.gradient(0) == Logo.gradient(1) && Logo.gradient(0.999) != 0, "градиент по кругу");
        System.out.printf("ThemeCheck (v1.16.0 Kaelix): %d тем, %d проверок — OK; кадров логотипа %d, цикл %.1f с%n",
                Ui.THEMES.length, checks, Logo.FRAMES, Logo.LOOP_MS / 1000.0);
    }
}
