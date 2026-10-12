package com.farmmacro.panic;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v1.13: разбор текста сервера — табло (sidebar), системный чат, титры. Без Minecraft (проверка — ServerTextCheck).
 */
public final class ServerText {
    private ServerText() {}

    /** Слова перезагрузки/лимбо в системном сообщении или титре (без учёта регистра). */
    public static final List<String> RESTART_WORDS = List.of(
            "server closing", "server is restarting", "server restart", "restarting in", "reboot", "about to restart",
            "you can't use this when the server is about to", "evacuat", "limbo",
            "перезагр", "рестарт", "сервер закрывается", "лимбо");

    private static final Pattern CLOSING = Pattern.compile("server closing:?\\s*(\\d{1,2}):(\\d{2})", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER = Pattern.compile("(\\d[\\d,.  ]*)");

    /** Убрать коды цвета §x и «невидимые»/эмодзи-символы, которыми Hypixel разбивает строки табло. */
    public static String clean(String s) {
        if (s == null) return "";
        String t = s.replaceAll("§.", "");
        StringBuilder b = new StringBuilder(t.length());
        t.codePoints().forEach(cp -> {
            int type = Character.getType(cp);
            boolean ok = Character.isLetterOrDigit(cp) || Character.isSpaceChar(cp)
                    || (cp < 0x2000 && type != Character.CONTROL && type != Character.FORMAT
                        && type != Character.PRIVATE_USE && type != Character.SURROGATE && type != Character.UNASSIGNED);
            if (ok) b.appendCodePoint(Character.isSpaceChar(cp) ? ' ' : cp);
        });
        return b.toString().replaceAll(" {2,}", " ").strip();
    }

    /** «Server closing: 0:25» → 25 (секунд); нет такой строки — −1. */
    public static int closingSeconds(List<String> lines) {
        for (String raw : lines) {
            Matcher m = CLOSING.matcher(clean(raw));
            if (m.find()) return Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
        }
        return -1;
    }

    /** Слово перезагрузки/лимбо в тексте или null. */
    public static String restartWord(String text) {
        String t = clean(text).toLowerCase(Locale.ROOT);
        for (String w : RESTART_WORDS) if (t.contains(w)) return w;
        return null;
    }

    /** Skyblock: «[MVP+] Ник is visiting Your Garden!» (или Island) → да. */
    public static boolean isVisitor(String text) {
        String t = clean(text).toLowerCase(Locale.ROOT);
        return t.contains(" is visiting your garden") || t.contains(" is visiting your island");
    }

    /**
     * Jacob's Contest на табло: после строки «Jacob's Contest» ищем «Collected 12,345» или «Bronze with 1,234».
     * @return собрано в конкурсе, −1 — конкурса на табло нет
     */
    public static long jacobCollected(List<String> lines) {
        boolean in = false;
        for (String raw : lines) {
            String t = clean(raw), l = t.toLowerCase(Locale.ROOT);
            if (l.contains("jacob's contest") || l.contains("jacobs contest")) { in = true; continue; }
            if (!in) continue;
            int at = l.indexOf("collected");
            int len = 9;
            if (at < 0) { at = l.indexOf(" with "); len = 6; }
            if (at < 0) continue;
            Matcher m = NUMBER.matcher(t.substring(at + len));
            if (m.find()) {
                String digits = m.group(1).replaceAll("[^0-9]", "");
                if (!digits.isEmpty() && digits.length() <= 15) return Long.parseLong(digits);
            }
        }
        return -1;
    }
}
