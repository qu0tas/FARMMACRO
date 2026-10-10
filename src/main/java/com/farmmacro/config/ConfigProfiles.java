package com.farmmacro.config;

import com.farmmacro.macro.HumanSettings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Профили настроек (вкладка «Конфиги»): весь {@link ModConfig} в config/farmmacro/configs/&lt;имя&gt;.json,
 * кроме пресетов камеры (на них ссылаются макросы/маршруты по id) и клавиш. Плюс готовые наборы ({@link #applyPreset}).
 */
public final class ConfigProfiles {
    private ConfigProfiles() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("FarmMacro/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final int NAME_MAX = 40;

    public static Path dir() { return ModConfig.modDir().resolve("configs"); }

    /** Имя файла из имени профиля: буквы, цифры, пробел, _ - . ; пусто — null. */
    public static String clean(String name) {
        if (name == null) return null;
        String s = name.strip().replaceAll("[^\\p{L}\\p{N} _.\\-]", "_");
        while (s.startsWith(".")) s = s.substring(1);
        if (s.length() > NAME_MAX) s = s.substring(0, NAME_MAX);
        s = s.strip();
        return s.isEmpty() ? null : s;
    }

    public static List<String> list() {
        List<String> out = new ArrayList<>();
        Path d = dir();
        if (!Files.isDirectory(d)) return out;
        try (Stream<Path> st = Files.list(d)) {
            st.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(p -> { String n = p.getFileName().toString(); out.add(n.substring(0, n.length() - 5)); });
        } catch (Exception e) {
            LOGGER.warn("Не удалось прочитать папку профилей {}", d, e);
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public static boolean exists(String name) {
        String n = clean(name);
        return n != null && Files.exists(dir().resolve(n + ".json"));
    }

    /** @return null — сохранено, иначе текст ошибки */
    public static String save(String name) {
        String n = clean(name);
        if (n == null) return "Введи имя профиля";
        try {
            Files.createDirectories(dir());
            Path tmp = dir().resolve(n + ".json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) { GSON.toJson(ModConfig.INSTANCE, w); }
            Files.move(tmp, dir().resolve(n + ".json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Профиль настроек сохранён: {}", n);
            return null;
        } catch (Exception e) {
            LOGGER.error("Не удалось сохранить профиль {}", n, e);
            return "Не удалось сохранить: " + e.getMessage();
        }
    }

    /** Загрузить: всё, кроме пресетов камеры и клавиш. @return null — ок, иначе ошибка */
    public static String load(String name) {
        String n = clean(name);
        if (n == null) return "Нет имени";
        Path f = dir().resolve(n + ".json");
        ModConfig loaded;
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            loaded = GSON.fromJson(r, ModConfig.class);
        } catch (Exception e) {
            LOGGER.error("Не удалось прочитать профиль {}", f, e);
            return "Файл профиля не читается";
        }
        if (loaded == null) return "Пустой файл профиля";
        apply(loaded);
        LOGGER.info("Профиль настроек загружен: {} | {}", n, ModConfig.INSTANCE.describeThresholds());
        return null;
    }

    /** Подменить текущие настройки, сохранив пресеты камеры и клавиши. */
    static void apply(ModConfig loaded) {
        ModConfig cur = ModConfig.INSTANCE;
        loaded.camPresets = cur.camPresets;
        loaded.camSelected = cur.camSelected;
        loaded.camNextId = cur.camNextId;
        loaded.keyRecord = cur.keyRecord; loaded.keyPlay = cur.keyPlay; loaded.keyClear = cur.keyClear; loaded.keyOpenGui = cur.keyOpenGui;
        loaded.sanitize();
        ModConfig.INSTANCE = loaded;
        ModConfig.save();
    }

    public static boolean delete(String name) {
        String n = clean(name);
        if (n == null) return false;
        try { return Files.deleteIfExists(dir().resolve(n + ".json")); }
        catch (Exception e) { LOGGER.warn("Не удалось удалить профиль {}", n, e); return false; }
    }

    // ── Готовые наборы ───────────────────────────────────────────────────────

    public record Preset(String id, String label, String tip) {}

    public static final List<Preset> PRESETS = List.of(
            new Preset("human", "Человек (рекомендую)", "Все детекторы, общая случайность: смазанные углы, разные паузы, "
                    + "иногда встать/протупить, редкие идеальные круги"),
            new Preset("careful", "Осторожный", "Как «Человек», но больше остановок и «протупить», игрок рядом — 24 бл"),
            new Preset("soft", "Мягкие детекторы", "Для лагающих/людных серверов: без «любого телепорта», входа/выхода и слов в чате "
                    + "(ник — да)"),
            new Preset("plain", "Без случайности", "Случайность выключена — проходы точно как записаны"));

    /** Рекомендуемая общая случайность («Человек»). */
    public static HumanSettings humanRecommended() {
        HumanSettings h = new HumanSettings();
        h.enabled = true; h.seed = 0;
        h.reachJitter = 0.12; h.cornerSlop = 0.35;
        h.overlapChance = 35; h.overlapMax = 3; h.gapChance = 20; h.gapMax = 2;
        h.hesitateChance = 8; h.hesitateMax = 8;
        h.pauseJitterPct = 25; h.pauseJitterTicks = 3;
        h.landDelayMin = 0; h.landDelayMax = 6; h.edgeReleaseMax = 2;
        h.jumpDelayMax = 2; h.sprintDelayMax = 3; h.startDelayMax = 20; h.holdDelayJitter = 6;
        h.idleStretchPct = 15; h.actionDelayMax = 2; h.camNoiseDeg = 0.3;
        h.bindJitterDeg = 0.35; h.lookPitchDeg = 0.4; h.stopLookDeg = 3;
        h.perfectLapChance = 8; h.perfectStreakChance = 30; h.streakMin = 2; h.streakMax = 4;
        h.midStopChance = 12; h.midStopMin = 15; h.midStopMax = 60;
        h.afkChance = 2; h.afkMin = 120; h.afkMax = 600;
        return h;
    }

    private static void allDetectors(ModConfig c) {
        c.panicEnabled = true;
        c.detectRotation = true; c.yawThreshold = 5; c.pitchThreshold = 5;
        c.detectServerMove = true; c.serverMoveThreshold = 1.0; c.serverRotateThreshold = 2.0f; c.serverMoveAny = true;
        c.detectKnockback = true; c.knockbackThreshold = 0.05;
        c.detectChat = true; c.chatMentionName = true; c.chatKeywords = ModConfig.DEFAULT_KEYWORDS;
        c.detectTitle = true; c.detectGameMode = true;
        c.detectPlayerNear = true; c.playerNearRadius = 12;
        c.detectSpectator = true; c.detectPlayerJoin = true; c.detectHeldItem = true;
        c.detectBlockInFace = true; c.detectSlotChange = true; c.detectGuiOpen = true; c.detectDamage = true;
        c.detectPotionEffect = true; c.detectStuck = true; c.detectFloor = true;
    }

    /** Применить готовый набор к текущим настройкам (визуал, HUD, звук, маршрут — не трогает) и сохранить. */
    public static void applyPreset(String id) {
        ModConfig c = ModConfig.INSTANCE;
        switch (id) {
            case "human" -> {
                allDetectors(c);
                c.humanMaster = true; c.humanUseGlobal = true; c.humanGlobal = humanRecommended();
                c.smoothTurnJitterPct = 15;
            }
            case "careful" -> {
                allDetectors(c);
                c.playerNearRadius = 24;
                HumanSettings h = humanRecommended();
                h.hesitateChance = 12; h.perfectLapChance = 5;
                h.midStopChance = 20; h.midStopMin = 20; h.midStopMax = 80;
                h.afkChance = 4; h.afkMin = 200; h.afkMax = 1200;
                c.humanMaster = true; c.humanUseGlobal = true; c.humanGlobal = h;
                c.smoothTurnJitterPct = 20;
            }
            case "soft" -> {
                allDetectors(c);
                c.serverMoveAny = false; c.detectPlayerJoin = false; c.chatKeywords = "";
                c.knockbackThreshold = 0.2; c.playerNearRadius = 6;
            }
            case "plain" -> c.humanMaster = false;
            default -> { return; }
        }
        c.sanitize();
        ModConfig.save();
        LOGGER.info("Готовый набор «{}» применён: {}", id, c.describeThresholds());
    }
}
