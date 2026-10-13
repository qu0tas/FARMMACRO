package com.farmmacro.visual;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * v1.16.3 «Скрытие ника», без Minecraft: какие ники на что менять и поиск в тексте (без учёта регистра, только
 * целое слово: до и после не буква/цифра/«_»). В строках с кодами «§x» код перед ником границей считается.
 * Проверка — NickTextCheck.
 */
public final class NickText {
    private NickText() {}

    public record Pair(String from, String to) {}

    public static final int MAX_TEXT = 16;

    /** Замены: свой ник → ownText; друзья → friendText (если hideFriends). Длинные — первыми (чтобы «Bob_1» раньше «Bob»). */
    public static List<Pair> pairs(String own, String ownText, boolean hideFriends, Collection<String> friends, String friendText) {
        List<Pair> out = new ArrayList<>();
        if (valid(own)) out.add(new Pair(own, clean(ownText, "Player")));
        if (hideFriends && friends != null) {
            String ft = clean(friendText, "Friend");
            for (String f : friends) {
                if (!valid(f) || (own != null && f.equalsIgnoreCase(own))) continue;
                boolean dup = false;
                for (Pair p : out) if (p.from().equalsIgnoreCase(f)) { dup = true; break; }
                if (!dup) out.add(new Pair(f, ft));
            }
        }
        out.sort(Comparator.comparingInt((Pair p) -> p.from().length()).reversed());
        return List.copyOf(out);
    }

    private static boolean valid(String n) { return n != null && n.length() >= 2 && n.length() <= 32; }

    /** Текст замены: без §, обрезан до 16 символов; пустой — запасной. */
    public static String clean(String s, String fallback) {
        String t = s == null ? "" : s.replaceAll("§.?", "").strip();
        if (t.length() > MAX_TEXT) t = t.substring(0, MAX_TEXT).strip();
        return t.isEmpty() ? fallback : t;
    }

    static boolean word(int cp) { return cp == '_' || (cp < 128 && Character.isLetterOrDigit(cp)) || (cp >= 128 && Character.isLetterOrDigit(cp)); }

    private static boolean eqIc(int a, int b) {
        return a == b || Character.toLowerCase(a) == Character.toLowerCase(b);
    }

    /** Совпадения в тексте из кодовых точек: {начало, конец (искл.), индекс пары}, по порядку, без пересечений. */
    public static List<int[]> find(int[] cps, List<Pair> pairs) {
        List<int[]> out = new ArrayList<>();
        if (cps == null || pairs == null || pairs.isEmpty()) return out;
        int[][] from = new int[pairs.size()][];
        for (int k = 0; k < pairs.size(); k++) from[k] = pairs.get(k).from().codePoints().toArray();
        int i = 0;
        outer:
        while (i < cps.length) {
            boolean startOk = i == 0 || !word(cps[i - 1]) || (i >= 2 && cps[i - 2] == '§');
            if (startOk) {
                for (int k = 0; k < from.length; k++) {
                    int[] f = from[k];
                    if (i + f.length > cps.length) continue;
                    boolean m = true;
                    for (int j = 0; j < f.length; j++) if (!eqIc(cps[i + j], f[j])) { m = false; break; }
                    if (!m) continue;
                    int e = i + f.length;
                    if (e < cps.length && word(cps[e])) continue;
                    out.add(new int[]{i, e, k});
                    i = e;
                    continue outer;
                }
            }
            i++;
        }
        return out;
    }

    /** Заменить в обычной строке. */
    public static String replace(String s, List<Pair> pairs) {
        if (s == null || s.isEmpty() || pairs == null || pairs.isEmpty()) return s;
        if (!mayContain(s, pairs)) return s;
        int[] cps = s.codePoints().toArray();
        List<int[]> hits = find(cps, pairs);
        if (hits.isEmpty()) return s;
        StringBuilder b = new StringBuilder(s.length());
        int pos = 0;
        for (int[] h : hits) {
            for (int j = pos; j < h[0]; j++) b.appendCodePoint(cps[j]);
            b.append(pairs.get(h[2]).to());
            pos = h[1];
        }
        for (int j = pos; j < cps.length; j++) b.appendCodePoint(cps[j]);
        return b.toString();
    }

    /** Быстрая проверка: есть ли в строке хоть один ник (без учёта регистра). */
    public static boolean mayContain(String s, List<Pair> pairs) {
        String low = s.toLowerCase(Locale.ROOT);
        for (Pair p : pairs) if (low.contains(p.from().toLowerCase(Locale.ROOT))) return true;
        return false;
    }
}
