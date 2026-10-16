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
    private static final java.util.Set<String> PERF_RUNTIME=java.util.Set.of("perfSavedFps","perfSavedClouds","perfSavedParticles","perfSavedShadows","perfSavedEntityDist","perfSavedChunks","perfAppliedChunks","perfAppliedFps");
    private static final java.util.Set<String> LOCAL_PRIVACY=java.util.Set.of("webhookEnabled","webhookScreenshot","webhookSessions","webhookDetails","webhookServer","webhookMediaConsent","webhookCooldownSec","sessionSave");
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
        CombatProfiles.captureActive(ModConfig.INSTANCE);
        String n = clean(name);
        if (n == null) return "Введи имя профиля";
        try {
            Files.createDirectories(dir());
            Path tmp = dir().resolve(n + ".json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) { var portable=GSON.toJsonTree(ModConfig.INSTANCE).getAsJsonObject();for(String key:LOCAL_PRIVACY)portable.remove(key);for(String key:PERF_RUNTIME)portable.remove(key);if(portable.has("telemetry")){portable.getAsJsonObject("telemetry").remove("bazaar");portable.getAsJsonObject("telemetry").remove("save");}GSON.toJson(com.farmmacro.notify.PrivateText.portable(portable), w); }
            Files.move(tmp, dir().resolve(n + ".json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Профиль настроек сохранён: {}", n);
            return null;
        } catch (Exception e) {
            LOGGER.error("Не удалось сохранить профиль ({})", e.getClass().getSimpleName());
            return "Не удалось сохранить (" + e.getClass().getSimpleName() + ")";
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
            LOGGER.error("Не удалось прочитать профиль ({})", e.getClass().getSimpleName());
            return "Файл профиля не читается";
        }
        if (loaded == null) return "Пустой файл профиля";
        apply(loaded);
        LOGGER.info("Профиль настроек загружен: {} | {}", n, ModConfig.INSTANCE.describeThresholds());
        return null;
    }

    /** v1.16.2: служебное и личное — не в строку настроек (как и не в профиль при загрузке). */
    private static final java.util.Set<String> NOT_IN_CODE = java.util.Set.of("camPresets", "camSelected", "camNextId",
            "camDefaultsRev", "lowRenderSavedChunks", "perfSavedFps", "perfSavedClouds", "perfSavedParticles", "perfSavedShadows",
            "perfSavedEntityDist", "uiAdvanced", "uiSections", "keyRecord", "keyPlay", "keyClear", "keyOpenGui",
            "lastSeenVersion", "welcomeDone", "webhookEnabled", "webhookScreenshot", "webhookSessions", "webhookDetails", "webhookServer", "webhookMediaConsent", "webhookCooldownSec", "sessionSave");

    /** v1.16.2: все настройки (как профиль) одной строкой — скопировать и отправить. */
    public static String exportCode() {
        CombatProfiles.captureActive(ModConfig.INSTANCE);
        com.google.gson.JsonObject o = new Gson().toJsonTree(ModConfig.INSTANCE).getAsJsonObject();
        for (String k : NOT_IN_CODE) o.remove(k);for(String key:PERF_RUNTIME)o.remove(key);
        if(o.has("telemetry")){o.getAsJsonObject("telemetry").remove("bazaar");o.getAsJsonObject("telemetry").remove("save");}
        return ConfigCode.encode(o.toString());
    }

    /** v1.16.2: применить строку настроек. @return null — ок, иначе причина */
    public static String importCode(String code) {
        ModConfig loaded;
        try {
            String json = ConfigCode.decode(code);
            com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            for (String k : NOT_IN_CODE) o.remove(k);for(String key:PERF_RUNTIME)o.remove(key);
            loaded = new Gson().fromJson(o, ModConfig.class);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            LOGGER.error("Строка настроек не читается ({})", e.getClass().getSimpleName());
            return "Строка не читается: " + e.getClass().getSimpleName();
        }
        if (loaded == null) return "Пустая строка настроек";
        loaded.welcomeDone = ModConfig.INSTANCE.welcomeDone;
        apply(loaded);
        LOGGER.info("Настройки загружены из строки | {}", ModConfig.INSTANCE.describeThresholds());
        return null;
    }

    /** Подменить текущие настройки, сохранив пресеты камеры и клавиши. */
    static void apply(ModConfig loaded) {
        com.farmmacro.macro.Scheduler.cancel("CONFIG_CHANGED");com.farmmacro.macro.Reconnect.cancel();com.farmmacro.notify.DiscordNotify.disabled();
        com.farmmacro.loadout.GlobalLoadouts.INSTANCE.cancel(net.minecraft.client.Minecraft.getInstance(),"замена полного конфига");
        com.farmmacro.visual.PerformanceMode.off(net.minecraft.client.Minecraft.getInstance(),"смена профиля");
        ModConfig cur = ModConfig.INSTANCE;
        loaded.webhookEnabled=cur.webhookEnabled;loaded.webhookScreenshot=cur.webhookScreenshot;loaded.webhookSessions=cur.webhookSessions;loaded.webhookDetails=cur.webhookDetails;loaded.webhookServer=cur.webhookServer;loaded.webhookMediaConsent=cur.webhookMediaConsent;loaded.webhookCooldownSec=cur.webhookCooldownSec;loaded.sessionSave=cur.sessionSave;
        if(loaded.telemetry==null)loaded.telemetry=new com.farmmacro.stats.StatsSettings();loaded.telemetry.bazaar=cur.telemetry.bazaar;loaded.telemetry.save=cur.telemetry.save;com.farmmacro.stats.StatsPrices.disabled();
        loaded.camPresets = cur.camPresets;
        loaded.camSelected = cur.camSelected;
        loaded.camNextId = cur.camNextId;
        loaded.camDefaultsRev = cur.camDefaultsRev;
        loaded.lowRenderSavedChunks = cur.lowRenderSavedChunks;   // служебное, не часть профиля
        loaded.perfSavedFps = cur.perfSavedFps; loaded.perfSavedClouds = cur.perfSavedClouds;
        loaded.perfSavedParticles = cur.perfSavedParticles; loaded.perfSavedShadows = cur.perfSavedShadows;
        loaded.perfSavedEntityDist = cur.perfSavedEntityDist;loaded.perfSavedChunks=cur.perfSavedChunks;loaded.perfAppliedChunks=cur.perfAppliedChunks;loaded.perfAppliedFps=cur.perfAppliedFps;
        loaded.uiAdvanced = cur.uiAdvanced; loaded.uiSections = cur.uiSections;    // вид меню — не часть профиля
        loaded.lastSeenVersion = cur.lastSeenVersion;                              // v1.16.2 «Что нового» — не часть профиля
        loaded.keyRecord = cur.keyRecord; loaded.keyPlay = cur.keyPlay; loaded.keyClear = cur.keyClear; loaded.keyOpenGui = cur.keyOpenGui;
        loaded.sanitize();
        ModConfig.INSTANCE = loaded;
        com.farmmacro.stats.Telemetry.resetSession();
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
            new Preset("optimal", "Оптимально", "Мои настройки: детекторы без «игрока рядом» и входа/выхода, блок рядом 1.5 бл, "
                    + "сирена 1 раз, общая случайность «Человек» с «Камера точно», реже «встать»; новым маршрутам и записям — "
                    + "зажим ЛКМ весь проход и своя случайность"),
            new Preset("human", "Человек (рекомендую)", "Все детекторы, общая случайность: углы по весам (перекрытие/зазор/шаг), "
                    + "гаусс-паузы без повторов подряд, микропаузы, иногда встать/протупить, редкие идеальные круги"),
            new Preset("careful", "Осторожный", "Как «Человек», но углы теснее, чаще заминки, встать и «протупить», игрок рядом — 24 бл"),
            new Preset("soft", "Мягкие детекторы", "Для лагающих/людных серверов: без «любого телепорта», входа/выхода, слов в чате и личных сообщений, "
                    + "«откат назад» — в подозрительность, игрок рядом — только если стоит 3 с (ник и staff — да)"),
            new Preset("plain", "Без случайности", "Случайность выключена — проходы точно как записаны"));

    /** Рекомендуемая общая случайность («Человек»). */
    public static HumanSettings humanRecommended() {
        HumanSettings h = HumanSettings.presetHuman();      // ходьба/круги — v1.11.1, configSweep
        h.landDelayMin = 0; h.landDelayMax = 6; h.edgeReleaseMax = 2;
        h.holdDelayJitter = 6; h.idleStretchPct = 15; h.actionDelayMax = 2; h.camNoiseDeg = 0.3;
        h.bindJitterDeg = 0.35; h.lookPitchDeg = 0.4; h.stopLookDeg = 3;
        return h;
    }

    /** «Осторожный»: ходьба HumanSettings.presetCareful(), камера/действия как у «Человека». */
    public static HumanSettings humanCareful() {
        HumanSettings h = HumanSettings.presetCareful();
        HumanSettings r = humanRecommended();
        h.landDelayMin = r.landDelayMin; h.landDelayMax = r.landDelayMax; h.edgeReleaseMax = r.edgeReleaseMax;
        h.holdDelayJitter = r.holdDelayJitter; h.idleStretchPct = r.idleStretchPct; h.actionDelayMax = r.actionDelayMax;
        h.camNoiseDeg = r.camNoiseDeg; h.bindJitterDeg = r.bindJitterDeg; h.lookPitchDeg = r.lookPitchDeg; h.stopLookDeg = r.stopLookDeg;
        return h;
    }

    /** «Оптимально» (v1.12.1, по настройкам игрока): «Человек» + «Камера точно», без шума камеры, реже «встать». */
    public static HumanSettings humanOptimal() {
        HumanSettings h = humanRecommended();
        h.midStopChance = 7;
        h.landDelayMin = 0; h.landDelayMax = 8; h.edgeReleaseMax = 2;
        h.idleStretchPct = 9; h.actionDelayMax = 2; h.camNoiseDeg = 0;
        h.holdDelayJitter = 6;
        h.cameraExact = true;
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
        c.detectServerSync = true; c.serverPosEpsilon = 0.03; c.serverRotEpsilon = 0.05f;
        c.detectMoveAttrs = true; c.attrEpsilon = 1.0;
        c.detectObstacle = true; c.obstacleRange = 4.0;
        c.detectVelocity = true; c.velocityAnomaly = 0.25;
        c.suspicionEnabled = true; c.suspicionLimit = 10; c.suspicionHalfLifeSec = 60;
        c.suspWeightSync = 1; c.suspWeightRollback = 2; c.suspWeightKnock = 2;
        c.suspWeightPlayerFar = 2; c.suspWeightChatWord = 4; c.suspWeightActionBar = 0.5;
        c.chatWordsSoft = false;
        // v1.13: детекторы мира и сервера (BPS, банвейв, Jacob, «откат назад» — по желанию, их не трогаем)
        c.lagGuard = true; c.detectFire = true; c.detectSticky = true; c.stickyRadius = 8; c.detectCage = true;
        c.detectFarmBlock = true; c.farmBlockRadius = 16; c.suspWeightFarmBlock = 3;
        c.detectDesync = true; c.desyncPct = 75; c.detectDisconnect = true; c.detectRestart = true; c.detectVisitor = true;
        c.suspWeightLagback = 3;
        // v1.14: игроки (идеи blazebot), бедрок/клетка по числу, тишина сервера. Друзья, скриншот и Discord — не трогаем
        c.detectStaffNear = true; c.staffRadius = 30; c.staffTab = true;
        c.detectTwin = true; c.twinRadius = 16;
        c.detectMassTp = true; c.massTpMin = 5; c.massTpRadius = 8; c.massTpWindowMs = 1000; c.massTpWorldGraceSec = 10;
        c.detectNameless = true; c.namelessRadius = 8; c.namelessDwellMs = 1000;
        c.detectPm = true; c.detectAfkMsg = true;
        c.detectBedrockBurst = true; c.bedrockBurstMin = 2; c.bedrockBurstRadius = 8; c.bedrockBurstMs = 300;
        c.detectCageCount = true; c.cageCountMin = 4; c.cageCountRadius = 1.5; c.cageCountMs = 1000;
        c.detectSilence = true; c.silenceSec = 30;
        c.playerNearDelaySec = 0;
        // v1.14.2-pre: свои действия из таблицы «Все детекторы» — обратно по умолчанию (свои звуки и веса не трогаем)
        if (c.detectorActions != null) c.detectorActions.clear();
    }

    /** Применить готовый набор к текущим настройкам (визуал, HUD, звук, маршрут — не трогает) и сохранить. */
    public static void applyPreset(String id) {
        ModConfig c = ModConfig.INSTANCE;
        switch (id) {
            case "optimal" -> {
                allDetectors(c);
                c.detectPlayerNear = false; c.detectPlayerJoin = false; c.detectDrift = false;
                c.blockDetectRadius = 1.5; c.stuckThresholdTicks = 30;
                c.panicSoundEnabled = true; c.panicSound = "builtin:siren"; c.panicSoundSafe = true;
                c.panicSoundVolume = 1.0f; c.panicSoundRepeats = 1; c.panicSoundPitch = 1.0f;
                c.panicRedScreenEnabled = true; c.panicRedScreenTicks = 60; c.eventLogEnabled = true;
                c.humanMaster = true; c.humanUseGlobal = true; c.humanGlobal = humanOptimal();
                c.smoothTurnJitterPct = 15;
                c.newItemSettings = com.farmmacro.macro.MacroSettings.optimalNew();
            }
            case "human" -> {
                allDetectors(c);
                c.humanMaster = true; c.humanUseGlobal = true; c.humanGlobal = humanRecommended();
                c.smoothTurnJitterPct = 15;
            }
            case "careful" -> {
                allDetectors(c);
                c.playerNearRadius = 24;
                c.obstacleRange = 5.0; c.velocityAnomaly = 0.2;
                c.suspicionLimit = 8; c.suspicionHalfLifeSec = 90;
                HumanSettings h = humanCareful();
                c.humanMaster = true; c.humanUseGlobal = true; c.humanGlobal = h;
                c.smoothTurnJitterPct = 20;
            }
            case "soft" -> {
                allDetectors(c);
                c.serverMoveAny = false; c.detectPlayerJoin = false; c.chatKeywords = "";
                c.knockbackThreshold = 0.2; c.playerNearRadius = 6;
                c.serverPosEpsilon = 0.5; c.serverRotEpsilon = 1.0f; c.attrEpsilon = 5.0;
                c.lagbackSoft = true; c.suspWeightFarmBlock = 2; c.desyncPct = 85;
                c.playerNearDelaySec = 3; c.detectPm = false; c.massTpMin = 6;
                c.obstacleRange = 3.0; c.velocityAnomaly = 0.4;
                c.suspicionLimit = 16; c.suspicionHalfLifeSec = 45; c.suspWeightActionBar = 0;
            }
            case "plain" -> c.humanMaster = false;
            default -> { return; }
        }
        c.sanitize();
        ModConfig.save();
        LOGGER.info("Готовый набор «{}» применён: {}", id, c.describeThresholds());
    }
}
