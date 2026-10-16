package com.farmmacro.panic;

import java.util.Locale;
import java.util.regex.Pattern;

/** Детектор «Чат»: есть ли в тексте ник игрока или слово из списка. Без зависимостей от игры (routeStress). */
public final class ChatMatch {
    private ChatMatch() {}

    /**
     * @return «ник» / «слово «x»» / null. Без учёта регистра; ник короче 3 букв не ищем (слишком часто совпадает).
     * v1.14: слово, записанное ЗАГЛАВНЫМИ (ROTATED, BEDROCK CAGED), ищется с учётом регистра и целым словом —
     * это правило сопоставления регистра, не доказательство происхождения сообщения.
     */
    public static String hit(String text, String ownName, boolean mentionName, String keywords) {
        if (text == null || text.isBlank()) return null;
        if (mentionName && ownName != null && ownName.length() >= 3 && Pattern.compile("(?iu)(?<![\\p{L}\\p{N}_])"+Pattern.quote(ownName)+"(?![\\p{L}\\p{N}_])").matcher(text).find())
            return "ник";
        if (keywords != null)
            for (String w : keywords.split(",")) {
                String raw = w.strip();
                if (raw.isEmpty()) continue;
                if (isCaps(raw)) {
                    if (Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(raw) + "(?![\\p{L}\\p{N}_])").matcher(text).find())
                        return "слово «" + raw + "»";
                } else if (Pattern.compile("(?iu)(?<![\\p{L}\\p{N}_])"+Pattern.quote(raw)+"(?![\\p{L}\\p{N}_])").matcher(text).find())
                    return "слово «" + raw.toLowerCase(Locale.ROOT) + "»";
            }
        return null;
    }

    /** Есть буквы, и все буквы заглавные, длина ≥ 3. */
    static boolean isCaps(String w) {
        boolean letter = false;
        for (int i = 0; i < w.length(); i++) {
            char ch = w.charAt(i);
            if (Character.isLetter(ch)) { letter = true; if (!Character.isUpperCase(ch)) return false; }
        }
        return letter && w.length() >= 3;
    }
}
