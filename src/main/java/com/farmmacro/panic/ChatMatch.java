package com.farmmacro.panic;

import java.util.Locale;

/** Детектор «Чат»: есть ли в тексте ник игрока или слово из списка. Без зависимостей от игры (routeStress). */
public final class ChatMatch {
    private ChatMatch() {}

    /** @return «ник» / «слово «x»» / null. Без учёта регистра; ник короче 3 букв не ищем (слишком часто совпадает). */
    public static String hit(String text, String ownName, boolean mentionName, String keywords) {
        if (text == null || text.isBlank()) return null;
        String t = text.toLowerCase(Locale.ROOT);
        if (mentionName && ownName != null && ownName.length() >= 3 && t.contains(ownName.toLowerCase(Locale.ROOT)))
            return "ник";
        if (keywords != null)
            for (String w : keywords.split(",")) {
                String k = w.strip().toLowerCase(Locale.ROOT);
                if (!k.isEmpty() && t.contains(k)) return "слово «" + k + "»";
            }
        return null;
    }
}
