package com.farmmacro.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** en_us и ru_ru — одинаковые ключи; всё, что код спрашивает через {@link Tr}, есть в обоих. */
class LangFilesTest {
    private static Map<String, String> lang(String code) throws IOException {
        try (InputStream in = LangFilesTest.class.getResourceAsStream("/assets/farmmacro/lang/" + code + ".json")) {
            if (in == null) throw new IOException("нет " + code);
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            Map<String, String> m = new TreeMap<>();
            o.entrySet().forEach(e -> m.put(e.getKey(), e.getValue().getAsString()));
            return m;
        }
    }

    private static long placeholders(String s) {
        return Pattern.compile("%(\\d+\\$)?[sd]").matcher(s).results().count();
    }

    @Test
    void sameKeysAndPlaceholders() throws IOException {
        Map<String, String> en = lang("en_us"), ru = lang("ru_ru");
        Set<String> onlyEn = new TreeSet<>(en.keySet()), onlyRu = new TreeSet<>(ru.keySet());
        onlyEn.removeAll(ru.keySet());
        onlyRu.removeAll(en.keySet());
        assertTrue(onlyEn.isEmpty() && onlyRu.isEmpty(), "только en_us: " + onlyEn + ", только ru_ru: " + onlyRu);
        for (String k : en.keySet()) {
            assertFalse(en.get(k).isBlank(), "пусто в en_us: " + k);
            assertFalse(ru.get(k).isBlank(), "пусто в ru_ru: " + k);
            assertEquals(placeholders(en.get(k)), placeholders(ru.get(k)), "разное число %s: " + k);
        }
    }

    @Test
    void everyCatalogKeyExists() throws IOException {
        Map<String, String> en = lang("en_us"), ru = lang("ru_ru");
        for (int i = 0; i < ModuleCatalog.CATEGORIES.length; i++) {
            assertTrue(
                    en.containsKey("farmmacro.category." + i) && ru.containsKey("farmmacro.category." + i),
                    "категория " + i);
            assertEquals(ModuleCatalog.CATEGORIES[i], ru.get("farmmacro.category." + i), "ru_ru = текст по умолчанию");
        }
        Set<String> ids = new TreeSet<>();
        for (ModuleCatalog.Module m : ModuleCatalog.ALL) {
            assertTrue(ids.add(m.id()), "повтор id " + m.id());
            for (String suffix : new String[] {".title", ".desc"}) {
                String key = "farmmacro.module." + m.id() + suffix;
                assertTrue(en.containsKey(key), "нет в en_us: " + key);
                assertTrue(ru.containsKey(key), "нет в ru_ru: " + key);
            }
        }
    }

    @Test
    void everyLiteralTrKeyInSourcesExists() throws IOException {
        Map<String, String> en = lang("en_us");
        Pattern call = Pattern.compile("Tr\\.t\\(\"([a-z0-9_.]+)\"\\s*,"); // только целые ключи-литералы
        Path root = Path.of("src/main/java");
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = call.matcher(Files.readString(f, StandardCharsets.UTF_8));
                while (m.find())
                    if (!en.containsKey(m.group(1))) missing.add(m.group(1) + " (" + f.getFileName() + ")");
            }
        }
        assertTrue(missing.isEmpty(), "ключи без перевода: " + missing);
    }
}
