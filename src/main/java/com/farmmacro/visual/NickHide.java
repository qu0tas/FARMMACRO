package com.farmmacro.visual;

import com.farmmacro.config.ModConfig;
import com.farmmacro.panic.PlayerRules;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * v1.16.3 «Скрытие ника»: свой ник (и по желанию ники друзей) заменяется при отрисовке текста — чат, Tab, ник над
 * головой, табло, титры (миксин {@code NickFontMixin} на {@code Font.prepareText/width}). На сервер ничего не уходит,
 * детекторы читают пакеты и видят настоящий ник.
 *
 * Режимы: always — всегда; shots — только на скриншоте паники: {@link #armShot} включает замену на 2 кадра,
 * красный экран в эти кадры не рисуется, снимок — после кадра с заменой ({@link #onFrame}).
 */
public final class NickHide {
    private NickHide() {}

    private static volatile List<NickText.Pair> pairs = List.of();
    private static volatile boolean always;
    private static volatile int shotFrames;
    private static Runnable shotTask;
    private static String sig = "";

    /** Сейчас заменять (из потока отрисовки). */
    public static boolean active() { return (always || shotFrames > 0) && !pairs.isEmpty(); }

    /** Идёт снимок без ника — красный экран не рисовать. */
    public static boolean shotPending() { return shotTask != null; }

    /** Каждый тик: обновить список замен (дёшево — по подписи настроек). */
    public static void tick(Minecraft mc) {
        ModConfig c = ModConfig.INSTANCE;
        String own = mc.getUser() != null ? mc.getUser().getName() : null;
        String s = c.nickHideMode + "|" + own + "|" + c.nickHideText + "|" + c.nickHideFriends + "|" + c.friendNames + "|" + c.nickFriendText;
        if (!s.equals(sig)) {
            sig = s;
            pairs = "off".equals(c.nickHideMode) ? List.of()
                    : NickText.pairs(own, c.nickHideText, c.nickHideFriends, PlayerRules.parseNames(c.friendNames), c.nickFriendText);
        }
        always = "always".equals(c.nickHideMode);
    }

    /** Для текста вне экрана (Discord, имя файла скриншота): заменить, если скрытие включено (любой режим). */
    public static String forText(String s) {
        if ("off".equals(ModConfig.INSTANCE.nickHideMode)) return s;
        return NickText.replace(s, pairs);
    }

    public static String apply(String s) {
        if (!active() || s == null) return s;
        return NickText.replace(s, pairs);
    }

    public static FormattedCharSequence apply(FormattedCharSequence seq) {
        if (!active() || seq == null) return seq;
        List<NickText.Pair> pr = pairs;
        int[] cps = new int[32];
        Style[] st = new Style[32];
        int[] n = {0};
        try {
            final int[][] cb = {cps};
            final Style[][] sb = {st};
            seq.accept((i, style, cp) -> {
                if (n[0] == cb[0].length) {
                    cb[0] = java.util.Arrays.copyOf(cb[0], n[0] * 2);
                    sb[0] = java.util.Arrays.copyOf(sb[0], n[0] * 2);
                }
                cb[0][n[0]] = cp; sb[0][n[0]] = style; n[0]++;
                return true;
            });
            cps = cb[0]; st = sb[0];
        } catch (Throwable t) { return seq; }
        int len = n[0];
        if (len < 2) return seq;
        int[] exact = java.util.Arrays.copyOf(cps, len);
        List<int[]> hits = NickText.find(exact, pr);
        if (hits.isEmpty()) return seq;
        List<Integer> oc = new ArrayList<>(len + 8);
        List<Style> os = new ArrayList<>(len + 8);
        int pos = 0;
        for (int[] h : hits) {
            for (int j = pos; j < h[0]; j++) { oc.add(exact[j]); os.add(st[j]); }
            Style s0 = st[h[0]];
            pr.get(h[2]).to().codePoints().forEach(cp -> { oc.add(cp); os.add(s0); });
            pos = h[1];
        }
        for (int j = pos; j < len; j++) { oc.add(exact[j]); os.add(st[j]); }
        int[] fc = oc.stream().mapToInt(Integer::intValue).toArray();
        Style[] fs = os.toArray(new Style[0]);
        return sink -> {
            for (int i = 0; i < fc.length; i++) if (!sink.accept(i, fs[i], fc[i])) return false;
            return true;
        };
    }

    /** Режим shots: снимок task — после кадра, нарисованного без ника. Иначе — сразу. */
    public static void armShot(Runnable task) {
        if (!"shots".equals(ModConfig.INSTANCE.nickHideMode) || pairs.isEmpty()) { task.run(); return; }
        shotTask = task;
        shotFrames = 2;
    }

    /** Каждый кадр (до отрисовки). */
    public static void onFrame() {
        if (shotTask == null) return;
        if (--shotFrames <= 0) {
            Runnable t = shotTask;
            shotTask = null;
            shotFrames = 0;
            t.run();
        }
    }
}
