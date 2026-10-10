package com.farmmacro.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Настройки мода (config/farmmacro.json). Все поля читает/пишет GUI напрямую;
 * после изменения достаточно вызвать {@link #save()}.
 * Неизвестные поля из старых версий Gson просто игнорирует, отсутствующие берутся по умолчанию,
 * после загрузки значения проходят {@link #sanitize()}.
 */
public class ModConfig {

    public static ModConfig INSTANCE = new ModConfig();

    private static final Logger LOGGER = LoggerFactory.getLogger("FarmMacro/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("farmmacro.json");

    // ── Детекторы паники (работают только пока макрос играет) ────────────────
    public boolean panicEnabled        = true;

    /** Камеру повернули мышью (макрос камеру не крутит, если не включён replayCamera). */
    public boolean detectRotation      = true;
    public float   yawThreshold        = 5.0f;
    public float   pitchThreshold      = 5.0f;

    /** Сервер сам сдвинул/повернул игрока (пакеты телепорта и поворота). */
    public boolean detectServerMove    = true;
    /** Минимальный сдвиг за один пакет, блоков (мелкие откаты от лагов игнорируются). */
    public double  serverMoveThreshold = 1.0;
    /** Минимальный поворот за один пакет, градусов. */
    public float   serverRotateThreshold = 2.0f;

    /** Рядом с игроком появился твёрдый блок (пакет от сервера, свои постановки не считаются). */
    public boolean detectBlockInFace   = true;
    /** На сколько блоков расширяется хитбокс игрока при проверке. */
    public double  blockDetectRadius   = 0.75;

    /**
     * Любой пакет телепорта/поворота от сервера, даже без сдвига (/tp @s ~ ~ ~, «проверка на реакцию»).
     * Откат античита («moved too quickly») тоже считается.
     */
    public boolean serverMoveAny       = true;
    /** Сервер толкнул: пакет скорости игрока (отдача, удочка, плагины) или отдача взрыва. */
    public boolean detectKnockback     = true;
    /** Минимальная скорость толчка, блоков за тик. */
    public double  knockbackThreshold  = 0.05;
    /** Чат: сообщение с твоим ником или словом из списка (свои сообщения не считаются). */
    public boolean detectChat          = true;
    public boolean chatMentionName     = true;
    /** Слова через запятую, без учёта регистра. */
    public String  chatKeywords        = DEFAULT_KEYWORDS;
    public static final String DEFAULT_KEYWORDS = "макро, macro, бот, bot, афк, afk, ты тут, ответь, проверка, check, админ";
    /** Титр/подзаголовок на экране (/title); actionbar — только с ником или словом из списка. */
    public boolean detectTitle         = true;
    /** Режим игры, полёт, респавн / смена мира, посадили на сущность. */
    public boolean detectGameMode      = true;
    /** Другой игрок подошёл ближе радиуса (невидимый тоже — сущность приходит). */
    public boolean detectPlayerNear    = true;
    public double  playerNearRadius    = 12;
    /** Игрок в списке (Tab) стал наблюдателем: его самого не видно, а смену режима — видно. */
    public boolean detectSpectator     = true;
    /** Игрок зашёл / вышел (выход из Tab = часто «ваниш»). */
    public boolean detectPlayerJoin    = true;
    /** Предмет в руке сменился или пропал сам (слот тот же; кончились семена/блоки — не считается). */
    public boolean detectHeldItem      = true;

    public boolean detectSlotChange    = true;
    public boolean detectGuiOpen       = true;
    public boolean detectDamage        = true;
    /** Новый эффект / смена уровня / досрочное снятие (обновление того же эффекта маяком — игнор). */
    public boolean detectPotionEffect  = true;

    /** Застревание: по записи игрок должен был пройти заметное расстояние, а реально стоит. */
    public boolean detectStuck         = true;
    public int     stuckThresholdTicks = 30;

    /** Сход с маршрута: траектория отклонилась от записанной больше чем на N блоков. */
    public boolean detectDrift         = false;
    /** Маршрут по точкам: «Не тот этаж» после спуска, «Нет пути» к точке на другом этаже. */
    public boolean detectFloor         = true;
    public double  driftThreshold      = 4.0;

    // ── Реакция на панику ────────────────────────────────────────────────────
    public boolean panicSoundEnabled          = true;
    /** builtin:siren|klaxon|alarm|alert, mc:<id> или file:<имя файла в config/farmmacro/sounds> */
    public String  panicSound                 = "builtin:siren";
    /**
     * «Безопасный звук»: только через движок игры, без javax.sound и декодирования.
     * По умолчанию включён, пока системный режим не проверен в игре (v1.2.1).
     */
    public boolean panicSoundSafe             = true;
    /** true — играть через звуковую систему ОС (не зависит от громкости Minecraft). Работает при panicSoundSafe=false. */
    public boolean panicSoundSystem           = true;
    public float   panicSoundVolume           = 1.0f;
    public float   panicSoundPitch            = 1.0f;
    public int     panicSoundRepeats          = 2;
    public int     panicSoundRepeatDelayTicks = 30;

    public boolean panicRedScreenEnabled      = true;
    public int     panicRedScreenTicks        = 60;

    // ── Запуск и автоматизация ───────────────────────────────────────────────
    public boolean loopEnabled           = false;
    /** Сколько кругов пройти в режиме цикла (0 = бесконечно). */
    public int     loopLimit             = 0;
    /** Остановить после N минут (0 = без лимита). */
    public int     timeLimitMinutes      = 0;
    /** Остановить, когда в инвентаре не осталось пустых слотов. */
    public boolean stopWhenInventoryFull = false;
    /** Обратный отсчёт перед стартом, секунд (0 = сразу). */
    public int     startCountdownSeconds = 0;
    /** Предупреждать, если игрок дальше N блоков от точки старта записи. */
    public double  startPointWarnDistance = 2.0;
    /** Не запускать, если игрок далеко от точки старта. */
    public boolean requireStartPoint     = false;
    /** Повторять повороты камеры из записи (иначе камера остаётся как есть). */
    public boolean replayCamera          = false;
    /** Общий выключатель «Случайности» (у каждого макроса/маршрута своя, в ⚙). */
    public boolean humanMaster           = true;
    /** Брать «Случайность» из общих настроек (вкладка «Конфиги»), а не из ⚙ макроса/маршрута. */
    public boolean humanUseGlobal        = false;
    /** Общая «Случайность» (вкладка «Конфиги»), сохраняется в профилях. */
    public com.farmmacro.macro.HumanSettings humanGlobal = new com.farmmacro.macro.HumanSettings();
    /** Плавный поворот пресета (K): скорость ± N % каждый раз (0 — всегда одинаково). */
    public int     smoothTurnJitterPct   = 0;
    /** Включать блокировку мыши при старте макроса (после отсчёта). */
    public boolean mouseLockOnStart      = false;
    /** Снимать блокировку мыши и при обычном стопе (паника, End, выход из мира снимают всегда). */
    public boolean mouseUnlockOnStop     = false;
    /** Мягкий звук, когда макрос закончился сам (лимит/конец записи). */
    public boolean finishSoundEnabled    = true;

    // ── HUD ──────────────────────────────────────────────────────────────────
    public boolean statsHudEnabled       = true;
    public boolean navHudEnabled         = true;
    /** Маленькая стрелка к цели вокруг прицела (когда макрос не играет). */
    public boolean navCrosshairEnabled   = true;

    // ── Визуал: China Hat ────────────────────────────────────────────────────
    public boolean hatEnabled            = true;
    /** gradient | solid | rainbow */
    public String  hatStyle              = "gradient";
    /** id пресета из visual.HatColors */
    public String  hatColor1             = "purple";
    public String  hatColor2             = "orange";
    /** 0–100 % */
    public int     hatOpacity            = 45;
    /** Радиус полей, блоков. */
    public double  hatRadius             = 0.7;
    /** Высота конуса от полей до вершины, блоков. */
    public double  hatHeight             = 0.3;
    /** Подъём полей над макушкой, блоков. */
    public double  hatOffset             = 0.08;
    /** Скорость вращения/перелива (0 — стоит). */
    public double  hatSpeed              = 1.0;
    public boolean hatFirstPerson        = false;
    public int     hatSegments           = 48;
    public boolean hatAllPlayers         = false;

    // ── Визуал: маршрут макроса ──────────────────────────────────────────────
    public boolean routeEnabled          = true;
    /** always | playing */
    public String  routeMode             = "always";
    /** off | dim | full — видно ли сквозь блоки */
    public String  routeSeeThrough       = "dim";
    /** Толщина ленты, блоков. */
    public double  routeWidth            = 0.08;
    /** Радиус отрисовки вокруг игрока, блоков. */
    public int     routeRadius           = 48;
    public boolean routeArrows           = true;
    /** Шаг стрелок, блоков. */
    public double  routeArrowSpacing     = 4.0;
    /** Участки ЛКМ/ПКМ другим цветом, отметки прыжка и приседания. */
    public boolean routeShowActions      = true;
    /** Сдвигать маршрут на смещение точки запуска (как детектор «сход с маршрута»). */
    public boolean routeRelative         = true;
    /** Цвет ленты впереди (id пресета из visual.HatColors). */
    public String  routeColor            = "cyan";
    /** Цвета элементов маршрута и HUD: id пресета HatColors или "default" (как раньше). */
    public String  routePointColor       = "default";
    public String  routeLabelColor       = "default";
    public String  routeStartColor       = "default";
    public String  routeStopColor        = "default";
    public String  routeArrowColor       = "default";
    public String  snakePreviewColor     = "default";
    public String  hudAccentColor        = "default";
    public String  hudBgColor            = "default";
    /** Непрозрачность ленты, 10–100 %. */
    public int     routeOpacity          = 90;
    /** Мягкое свечение вокруг ленты (вторая, широкая и прозрачная). */
    public boolean routeGlow             = true;

    // ── Маршрут по точкам: редактор ──────────────────────────────────────────
    /** Как далеко редактор ставит/выбирает точки, блоков. */
    public double  routeEditReach        = 32;
    /** Сдвиг точки в блоке стрелками: шаг и мелкий шаг (с Shift), блоков. */
    public double  routeOffsetStep       = 0.05;
    public double  routeOffsetFineStep   = 0.01;
    /** «Змейка»: смещение всех точек внутри блока по X/Z при построении (−0.5…0.5). */
    public double  snakeOffsetX          = 0;
    public double  snakeOffsetZ          = 0;
    /** «Змейка»: шаг между рядами, блоков. */
    public int     snakeStep             = 3;
    /** auto | x | z — вдоль какой оси ряды. */
    public String  snakeAxis             = "auto";
    /** Действие на ряду и на переходе между рядами: none | attack | use. */
    public String  snakeRowAction        = "attack";
    public String  snakeTurnAction       = "none";
    /** Добавлять «змейку» в конец маршрута (иначе заменить). */
    public boolean snakeAppend           = false;
    /** «Змейка» по этажам: сколько этажей (1 — обычная) и на сколько блоков ниже каждый следующий. */
    public int     snakeFloors           = 1;
    public int     snakeFloorStep        = 3;
    /** Автоход: точка достигнута в этом радиусе по XZ, блоков. */
    public double  routeReachRadius      = 0.3;

    // ── Камера: пресеты yaw/pitch ────────────────────────────────────────────
    public static class CamPreset {
        /** Постоянный номер (на него ссылаются привязки камеры в макросах и маршрутах), 0 — ещё не выдан. */
        public int    id    = 0;
        /** Имя для списка; пустое — «Пресет N». */
        public String name  = "";
        /** id культуры из camera.CameraPresets.CROPS (только подпись). */
        public String crop  = "other";
        public float  yaw   = 0f;
        public float  pitch = 0f;
        public CamPreset() {}
        public CamPreset(String crop, float yaw, float pitch) { this.crop = crop; this.yaw = yaw; this.pitch = pitch; }
    }
    public static final int CAM_NAME_MAX = 32;
    public java.util.List<CamPreset> camPresets = new java.util.ArrayList<>();
    public int     camSelected           = 0;
    /** Следующий свободный id пресета. */
    public int     camNextId             = 1;

    /** Новый пресет с постоянным id (в список не добавляет). */
    public CamPreset newCamPreset(String crop, float yaw, float pitch) {
        CamPreset p = new CamPreset(crop, yaw, pitch);
        p.id = camNextId++;
        p.name = "Пресет " + p.id;
        return p;
    }
    /** Плавный поворот к пресету (выкл — мгновенно). */
    public boolean camSmooth             = true;
    /** Скорость плавного поворота, градусов в секунду. */
    public double  camTurnSpeed          = 120;

    // ── Клавиши по умолчанию (дальше их хранит меню «Управление» Minecraft) ──
    public int keyRecord  = 82;   // R
    public int keyPlay    = 80;   // P
    public int keyClear   = 261;  // Delete
    public int keyOpenGui = 344;  // Right Shift

    /** Приводит значения к допустимым диапазонам (на случай ручной правки json). */
    public void sanitize() {
        yawThreshold          = clamp(yawThreshold, 0.001f, 180f);
        pitchThreshold        = clamp(pitchThreshold, 0.001f, 180f);
        serverMoveThreshold   = clamp(serverMoveThreshold, 0.001, 1024);
        serverRotateThreshold = clamp(serverRotateThreshold, 0.001f, 180f);
        blockDetectRadius     = clamp(blockDetectRadius, 0, 8);
        stuckThresholdTicks   = clamp(stuckThresholdTicks, 1, 12000);
        driftThreshold        = clamp(driftThreshold, 0.01, 1024);
        knockbackThreshold    = clamp(knockbackThreshold, 0.001, 10);
        playerNearRadius      = clamp(playerNearRadius, 1, 128);
        if (chatKeywords == null) chatKeywords = "";
        if (chatKeywords.length() > 1000) chatKeywords = chatKeywords.substring(0, 1000);
        humanGlobal           = com.farmmacro.macro.HumanSettings.sanitize(humanGlobal);
        panicSoundVolume      = clamp(panicSoundVolume, 0f, 1f);
        panicSoundPitch       = clamp(panicSoundPitch, 0.5f, 2f);
        panicSoundRepeats     = clamp(panicSoundRepeats, 1, 100);
        panicSoundRepeatDelayTicks = clamp(panicSoundRepeatDelayTicks, 1, 1200);
        panicRedScreenTicks   = clamp(panicRedScreenTicks, 1, 1200);
        loopLimit             = clamp(loopLimit, 0, 1000000);
        timeLimitMinutes      = clamp(timeLimitMinutes, 0, 7 * 24 * 60);
        startCountdownSeconds = clamp(startCountdownSeconds, 0, 600);
        smoothTurnJitterPct   = clamp(smoothTurnJitterPct, 0, 90);
        startPointWarnDistance = clamp(startPointWarnDistance, 0.01, 1024);
        if (panicSound == null || panicSound.isBlank()) panicSound = "builtin:siren";
        routeWidth            = clamp(routeWidth, 0.005, 1);
        routeRadius           = clamp(routeRadius, 1, 1024);
        routeArrowSpacing     = clamp(routeArrowSpacing, 0.5, 64);
        if (!"always".equals(routeMode) && !"playing".equals(routeMode)) routeMode = "always";
        if (!"off".equals(routeSeeThrough) && !"dim".equals(routeSeeThrough) && !"full".equals(routeSeeThrough)) routeSeeThrough = "dim";
        if (!com.farmmacro.visual.HatColors.isPreset(routeColor)) routeColor = "cyan";
        routePointColor   = colorOrDefault(routePointColor);
        routeLabelColor   = colorOrDefault(routeLabelColor);
        routeStartColor   = colorOrDefault(routeStartColor);
        routeStopColor    = colorOrDefault(routeStopColor);
        routeArrowColor   = colorOrDefault(routeArrowColor);
        snakePreviewColor = colorOrDefault(snakePreviewColor);
        hudAccentColor    = colorOrDefault(hudAccentColor);
        hudBgColor        = colorOrDefault(hudBgColor);
        routeOpacity          = clamp(routeOpacity, 1, 100);
        if (camPresets == null) camPresets = new java.util.ArrayList<>();
        camPresets.removeIf(java.util.Objects::isNull);
        while (camPresets.size() > 16) camPresets.remove(camPresets.size() - 1);
        int maxId = 0;
        for (CamPreset p : camPresets) maxId = Math.max(maxId, p.id);
        camNextId = Math.max(Math.max(1, camNextId), maxId + 1);
        java.util.Set<Integer> ids = new java.util.HashSet<>();
        for (CamPreset p : camPresets) {
            if (p.id <= 0 || !ids.add(p.id)) { p.id = camNextId++; ids.add(p.id); }   // старые пресеты (до 1.6) — без id
            p.yaw = net.minecraft.util.Mth.wrapDegrees(Float.isFinite(p.yaw) ? p.yaw : 0f);
            p.pitch = clamp(Float.isFinite(p.pitch) ? p.pitch : 0f, -90f, 90f);
            if (p.crop == null) p.crop = "other";
            p.name = p.name == null ? "" : p.name.strip();
            if (p.name.length() > CAM_NAME_MAX) p.name = p.name.substring(0, CAM_NAME_MAX);
            if (p.name.isEmpty()) p.name = "Пресет " + p.id;
        }
        camSelected           = camPresets.isEmpty() ? 0 : clamp(camSelected, 0, camPresets.size() - 1);
        camTurnSpeed          = clamp(camTurnSpeed, 1, 3600);
        routeEditReach        = clamp(routeEditReach, 1, 128);
        routeOffsetStep       = clamp(routeOffsetStep, 0.001, 0.5);
        routeOffsetFineStep   = clamp(routeOffsetFineStep, 0.001, 0.5);
        snakeOffsetX          = clamp(snakeOffsetX, -0.5, 0.5);
        snakeOffsetZ          = clamp(snakeOffsetZ, -0.5, 0.5);
        snakeStep             = clamp(snakeStep, 1, 64);
        snakeFloors           = clamp(snakeFloors, 1, 64);
        snakeFloorStep        = clamp(snakeFloorStep, 1, 64);
        routeReachRadius      = clamp(routeReachRadius, 0.01, 2);
        if (!"auto".equals(snakeAxis) && !"x".equals(snakeAxis) && !"z".equals(snakeAxis)) snakeAxis = "auto";
        if (!java.util.List.of("none", "attack", "use").contains(snakeRowAction)) snakeRowAction = "attack";
        if (!java.util.List.of("none", "attack", "use").contains(snakeTurnAction)) snakeTurnAction = "none";
        hatOpacity            = clamp(hatOpacity, 0, 100);
        hatRadius             = clamp(hatRadius, 0.05, 3);
        hatHeight             = clamp(hatHeight, 0.01, 2);
        hatOffset             = clamp(hatOffset, -1, 2);
        hatSpeed              = clamp(hatSpeed, 0, 20);
        hatSegments           = clamp(hatSegments, 3, 256);
        if (!"gradient".equals(hatStyle) && !"solid".equals(hatStyle) && !"rainbow".equals(hatStyle)) hatStyle = "gradient";
        if (!com.farmmacro.visual.HatColors.isPreset(hatColor1)) hatColor1 = "purple";
        if (!com.farmmacro.visual.HatColors.isPreset(hatColor2)) hatColor2 = "orange";
    }

    /** Реальные пороги детекторов одной строкой (пишется в лог при загрузке и при старте макроса). */
    public String describeThresholds() {
        return String.format(java.util.Locale.ROOT,
                "паника=%s | поворот=%s yaw>%.2f° pitch>%.2f° | сервер=%s сдвиг>=%.2f бл поворот>=%.2f° | "
                        + "блок=%s %.2f бл | слот=%s окно=%s урон=%s эффекты=%s | застрял=%s %d т | сход=%s %.1f бл | "
                        + "повтор камеры=%s | звук=%s безопасный=%s системный=%s | любой телепорт=%s толчок=%s %.3f | "
                        + "чат=%s ник=%s титр=%s режим=%s игрок рядом=%s %.0f бл наблюдатель=%s вход/выход=%s предмет=%s",
                panicEnabled, detectRotation, yawThreshold, pitchThreshold,
                detectServerMove, serverMoveThreshold, serverRotateThreshold,
                detectBlockInFace, blockDetectRadius, detectSlotChange, detectGuiOpen, detectDamage, detectPotionEffect,
                detectStuck, stuckThresholdTicks, detectDrift, driftThreshold,
                replayCamera, panicSound, panicSoundSafe, panicSoundSystem, serverMoveAny, detectKnockback, knockbackThreshold,
                detectChat, chatMentionName, detectTitle, detectGameMode, detectPlayerNear, playerNearRadius,
                detectSpectator, detectPlayerJoin, detectHeldItem);
    }

    private static String colorOrDefault(String id) {
        return com.farmmacro.visual.HatColors.isPreset(id) ? id : com.farmmacro.visual.HatColors.DEFAULT;
    }

    private static int    clamp(int v, int lo, int hi)          { return Math.max(lo, Math.min(hi, v)); }
    private static float  clamp(float v, float lo, float hi)    { return Float.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v)); }
    private static double clamp(double v, double lo, double hi) { return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v)); }

    // ── Сохранение / загрузка ─────────────────────────────────────────────────
    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            INSTANCE = new ModConfig();
            save();
            return;
        }
        try (Reader r = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            ModConfig loaded = GSON.fromJson(r, ModConfig.class);
            INSTANCE = loaded != null ? loaded : new ModConfig();
        } catch (Exception e) {
            LOGGER.error("Не удалось прочитать farmmacro.json, беру настройки по умолчанию", e);
            INSTANCE = new ModConfig();
        }
        float yaw0 = INSTANCE.yawThreshold, pitch0 = INSTANCE.pitchThreshold;
        INSTANCE.sanitize();
        if (yaw0 != INSTANCE.yawThreshold || pitch0 != INSTANCE.pitchThreshold)
            LOGGER.warn("Пороги поворота в {} были вне диапазона ({} / {}), исправлены", CONFIG_PATH, yaw0, pitch0);
        LOGGER.info("Конфиг {} загружен: {}", CONFIG_PATH, INSTANCE.describeThresholds());
    }

    /** Пишет во временный файл и атомарно подменяет — конфиг не побьётся при вылете игры. */
    public static void save() {
        try {
            Path tmp = CONFIG_PATH.resolveSibling("farmmacro.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, w);
            }
            try {
                Files.move(tmp, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicNotSupported) {
                Files.move(tmp, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.error("Не удалось сохранить farmmacro.json", e);
        }
    }

    /** Папка мода: config/farmmacro/ */
    public static Path modDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("farmmacro");
    }
}
