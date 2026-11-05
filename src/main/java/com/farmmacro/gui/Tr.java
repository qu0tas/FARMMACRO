package com.farmmacro.gui;

import com.farmmacro.config.ModConfig;
import net.minecraft.client.resources.language.I18n;
import org.jspecify.annotations.NullMarked;

/**
 * 2.1.0: перевод строки интерфейса с русским текстом по умолчанию.
 *
 * <p>Ключ есть в {@code assets/farmmacro/lang/<язык>.json} — берём перевод, нет — {@code fallback}. Так строки
 * переносятся в lang постепенно, без «дыр» в меню. По умолчанию ({@code uiFollowGameLanguage=false}) всегда
 * русский текст, чтобы не смешивать языки, пока переведена только часть строк. Набор ключей en_us/ru_ru сверяет {@code LangFilesTest}.
 */
@NullMarked
public final class Tr {
    private Tr() {}

    public static String t(String key, String fallback) {
        // Пока большая часть меню на русском, смешанный интерфейс включается только явно.
        if (!ModConfig.INSTANCE.uiFollowGameLanguage) return fallback;
        try {
            return I18n.exists(key) ? I18n.get(key) : fallback;
        } catch (RuntimeException e) {
            return fallback; // язык ещё не загружен (очень ранний экран) — русский текст
        }
    }
}
