package com.farmmacro.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.NullMarked;

/**
 * Миграции {@code farmmacro.json} (и профилей/строк настроек) по полю {@code schemaVersion}.
 *
 * <p>Работает с JSON до разбора в {@link ModConfig}: переименованное поле можно перенести, а не потерять
 * молча (Gson просто пропускает незнакомые ключи). Диапазоны значений после разбора всё так же
 * поправляет {@link ModConfig#sanitize()}.
 *
 * <p>Как добавить миграцию: дописать шаг в {@link #STEPS} (индекс = версия «из»), увеличить
 * {@link #CURRENT}, добавить случай в {@code ConfigMigratorTest}.
 */
@NullMarked
public final class ConfigMigrator {
    /** Версия схемы, которую пишет эта сборка. */
    public static final int CURRENT = 1;

    /** Шаг i переводит схему i → i+1. */
    private static final List<Consumer<JsonObject>> STEPS = List.of(
            // 0 → 1 (2.1.0): поле появилось. Конфиги до v1.14.2 без welcomeDone — мастер первого запуска уже не
            // показываем (раньше это проверялось поиском подстроки в ModConfig.load).
            json -> {
                if (!json.has("welcomeDone")) json.addProperty("welcomeDone", true);
            });

    private ConfigMigrator() {}

    /** Итог миграции: с какой версии на какую и что сделано (для журнала). */
    public record Result(int from, int to, boolean newerThanThisBuild, List<String> notes) {
        public boolean changed() {
            return from != to;
        }
    }

    /** Версия схемы в JSON: нет поля или мусор — 0 (конфиг до 2.1.0). */
    public static int versionOf(JsonObject json) {
        JsonElement v = json.get("schemaVersion");
        if (v instanceof JsonPrimitive p && p.isNumber()) {
            double d = p.getAsDouble();
            if (d >= 0 && d <= 1_000_000 && d == Math.rint(d)) return (int) d;
        }
        return 0;
    }

    /**
     * Довести {@code json} до {@link #CURRENT} на месте. Конфиг от более новой сборки не трогаем: поля, которых
     * эта сборка не знает, Gson пропустит, а понижать версию нельзя.
     */
    public static Result migrate(JsonObject json) {
        int from = versionOf(json);
        if (from > CURRENT)
            return new Result(from, from, true, List.of("схема " + from + " новее этой сборки (" + CURRENT + ")"));
        List<String> notes = new ArrayList<>();
        for (int v = from; v < CURRENT; v++) {
            STEPS.get(v).accept(json);
            notes.add(v + "→" + (v + 1));
        }
        json.addProperty("schemaVersion", CURRENT);
        return new Result(from, CURRENT, false, List.copyOf(notes));
    }

    /** Помощник для будущих шагов: переименовать ключ, если старый есть, а нового ещё нет. */
    static void rename(JsonObject json, String from, String to) {
        if (!json.has(from)) return;
        JsonElement value = json.remove(from);
        if (!json.has(to)) json.add(to, value);
    }

    /** Совпадает с {@code STEPS.size()} — проверяется тестом. */
    static int stepCount() {
        return STEPS.size();
    }
}
