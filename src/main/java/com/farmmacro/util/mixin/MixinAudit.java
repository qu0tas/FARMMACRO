package com.farmmacro.util.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Отчёт о миксинах: после обновления Minecraft сразу видно, какой миксин не применился,
 * вместо «что-то молча перестало работать». Миксин считается применённым, когда его цель загружена;
 * цели, которые грузятся позже (экраны, чужие моды), до этого момента числятся «ожидающими».
 */
public final class MixinAudit {
    private static final Logger LOGGER = LoggerFactory.getLogger("Kaelix/Mixins");
    private static final String CONFIG = "farmmacro.mixins.json";
    /** Необязательные миксины для других модов (@Pseudo): без этих модов их цель не появится — это норма. */
    public static final Map<String, String> OPTIONAL = Map.of("LowRenderSodiumMixin", "sodium");

    private static final Map<String, String> APPLIED = new ConcurrentHashMap<>();
    private static volatile @Nullable String mixinPackage;
    private static volatile @Nullable List<String> expected;

    private MixinAudit() {}

    static void configured(String pkg) {
        mixinPackage = pkg;
    }

    static void applied(String mixinClassName, String targetClassName) {
        APPLIED.put(simple(mixinClassName), targetClassName);
    }

    /** Простые имена миксинов из конфига (раздел client). Пустой список, если конфиг не прочитался. */
    public static List<String> expected() {
        List<String> e = expected;
        if (e != null) return e;
        List<String> out = new ArrayList<>();
        try (InputStream in = MixinAudit.class.getClassLoader().getResourceAsStream(CONFIG)) {
            if (in != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                for (String side : new String[] {"mixins", "client"}) {
                    JsonElement list = root.get(side);
                    if (list instanceof JsonArray arr) arr.forEach(x -> out.add(simple(x.getAsString())));
                }
            }
        } catch (Exception ex) {
            LOGGER.warn("Не прочитан {}: {}", CONFIG, ex.toString());
        }
        e = List.copyOf(out);
        expected = e;
        return e;
    }

    public static Set<String> applied() {
        return new TreeSet<>(APPLIED.keySet());
    }

    /** Ожидаемые, но пока не применённые, без необязательных миксинов для отсутствующих модов. */
    public static Set<String> pending(Predicate<String> modLoaded) {
        Set<String> out = new TreeSet<>();
        for (String m : expected()) {
            if (APPLIED.containsKey(m)) continue;
            String mod = OPTIONAL.get(m);
            if (mod != null && !modLoaded.test(mod)) continue;
            out.add(m);
        }
        return out;
    }

    public static String summary(Predicate<String> modLoaded) {
        Set<String> pending = pending(modLoaded);
        int total = expected().size();
        StringBuilder b = new StringBuilder("Mixins: ")
                .append(APPLIED.size())
                .append('/')
                .append(total)
                .append(" applied");
        if (mixinPackage == null) b.append(" (audit plugin not loaded)");
        if (!pending.isEmpty()) b.append("; pending ").append(pending);
        Map<String, String> skipped = new TreeMap<>();
        OPTIONAL.forEach((m, mod) -> {
            if (!APPLIED.containsKey(m) && !modLoaded.test(mod)) skipped.put(m, mod);
        });
        if (!skipped.isEmpty()) b.append("; optional skipped ").append(skipped.keySet());
        return b.toString();
    }

    /** Один раз в журнал: что не применилось к моменту, когда игрок уже в мире. */
    public static void logOnce(String when, Predicate<String> modLoaded) {
        if (!LOGGED.add(when)) return;
        Set<String> pending = pending(modLoaded);
        if (pending.isEmpty()) LOGGER.info("{} — {}", when, summary(modLoaded));
        else
            LOGGER.warn(
                    "{} — {} (цель не загружена или миксин не подошёл к этой версии игры)", when, summary(modLoaded));
    }

    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    /** Тиков в мире до отчёта: часть целей (например, RenderSectionRegion) грузится только после первых чанков. */
    static final int REPORT_AFTER_TICKS = 600;

    private static int ticksInWorld;

    /** Вызывается каждый тик с игроком в мире; через ~30 с один раз пишет итог в журнал. */
    public static void tickInWorld() {
        if (ticksInWorld > REPORT_AFTER_TICKS) return;
        if (++ticksInWorld == REPORT_AFTER_TICKS) logOnce("30 с в мире", FabricLoader.getInstance()::isModLoaded);
    }

    private static String simple(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(dot + 1);
    }
}
