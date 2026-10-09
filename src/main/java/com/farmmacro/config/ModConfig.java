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
    /** Непрозрачность ленты, 10–100 %. */
    public int     routeOpacity          = 90;
    /** Мягкое свечение вокруг ленты (вторая, широкая и прозрачная). */
    public boolean routeGlow             = true;

    // ── Маршрут по точкам: редактор ──────────────────────────────────────────
    /** Как далеко редактор ставит/выбирает точки, блоков. */
    public double  routeEditReach        = 32;
    /** «Змейка»: шаг между рядами, блоков. */
    public int     snakeStep             = 3;
    /** auto | x | z — вдоль какой оси ряды. */
    public String  snakeAxis             = "auto";
    /** Действие на ряду и на переходе между рядами: none | attack | use. */
    public String  snakeRowAction        = "attack";
    public String  snakeTurnAction       = "none";
    /** Добавлять «змейку» в конец маршрута (иначе заменить). */
    public boolean snakeAppend           = false;
    /** Автоход: скорость поворота камеры к следующей точке, °/с. */
    public double  routeTurnSpeed        = 180;
    /** Автоход: точка достигнута в этом радиусе по XZ, блоков. */
    public double  routeReachRadius      = 0.3;

    // ── Камера: пресеты yaw/pitch ────────────────────────────────────────────
    public static class CamPreset {
        /** id культуры из camera.CameraPresets.CROPS (только подпись). */
        public String crop  = "other";
        public float  yaw   = 0f;
        public float  pitch = 0f;
        public CamPreset() {}
        public CamPreset(String crop, float yaw, float pitch) { this.crop = crop; this.yaw = yaw; this.pitch = pitch; }
    }
    public java.util.List<CamPreset> camPresets = new java.util.ArrayList<>();
    public int     camSelected           = 0;
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
        yawThreshold          = clamp(yawThreshold, 0.5f, 90f);
        pitchThreshold        = clamp(pitchThreshold, 0.5f, 90f);
        serverMoveThreshold   = clamp(serverMoveThreshold, 0.1, 64);
        serverRotateThreshold = clamp(serverRotateThreshold, 0.5f, 180f);
        blockDetectRadius     = clamp(blockDetectRadius, 0, 4);
        stuckThresholdTicks   = clamp(stuckThresholdTicks, 10, 400);
        driftThreshold        = clamp(driftThreshold, 1, 64);
        panicSoundVolume      = clamp(panicSoundVolume, 0f, 1f);
        panicSoundPitch       = clamp(panicSoundPitch, 0.5f, 2f);
        panicSoundRepeats     = clamp(panicSoundRepeats, 1, 20);
        panicSoundRepeatDelayTicks = clamp(panicSoundRepeatDelayTicks, 5, 200);
        panicRedScreenTicks   = clamp(panicRedScreenTicks, 10, 400);
        loopLimit             = clamp(loopLimit, 0, 10000);
        timeLimitMinutes      = clamp(timeLimitMinutes, 0, 24 * 60);
        startCountdownSeconds = clamp(startCountdownSeconds, 0, 30);
        startPointWarnDistance = clamp(startPointWarnDistance, 0.5, 64);
        if (panicSound == null || panicSound.isBlank()) panicSound = "builtin:siren";
        routeWidth            = clamp(routeWidth, 0.02, 0.4);
        routeRadius           = clamp(routeRadius, 8, 256);
        routeArrowSpacing     = clamp(routeArrowSpacing, 1, 32);
        if (!"always".equals(routeMode) && !"playing".equals(routeMode)) routeMode = "always";
        if (!"off".equals(routeSeeThrough) && !"dim".equals(routeSeeThrough) && !"full".equals(routeSeeThrough)) routeSeeThrough = "dim";
        if (!com.farmmacro.visual.HatColors.isPreset(routeColor)) routeColor = "cyan";
        routeOpacity          = clamp(routeOpacity, 10, 100);
        if (camPresets == null) camPresets = new java.util.ArrayList<>();
        camPresets.removeIf(java.util.Objects::isNull);
        while (camPresets.size() > 16) camPresets.remove(camPresets.size() - 1);
        for (CamPreset p : camPresets) {
            p.yaw = net.minecraft.util.Mth.wrapDegrees(Float.isFinite(p.yaw) ? p.yaw : 0f);
            p.pitch = clamp(Float.isFinite(p.pitch) ? p.pitch : 0f, -90f, 90f);
            if (p.crop == null) p.crop = "other";
        }
        camSelected           = camPresets.isEmpty() ? 0 : clamp(camSelected, 0, camPresets.size() - 1);
        camTurnSpeed          = clamp(camTurnSpeed, 20, 1080);
        routeEditReach        = clamp(routeEditReach, 4, 96);
        snakeStep             = clamp(snakeStep, 1, 16);
        routeTurnSpeed        = clamp(routeTurnSpeed, 30, 1080);
        routeReachRadius      = clamp(routeReachRadius, 0.1, 1.0);
        if (!"auto".equals(snakeAxis) && !"x".equals(snakeAxis) && !"z".equals(snakeAxis)) snakeAxis = "auto";
        if (!java.util.List.of("none", "attack", "use").contains(snakeRowAction)) snakeRowAction = "attack";
        if (!java.util.List.of("none", "attack", "use").contains(snakeTurnAction)) snakeTurnAction = "none";
        hatOpacity            = clamp(hatOpacity, 0, 100);
        hatRadius             = clamp(hatRadius, 0.3, 1.5);
        hatHeight             = clamp(hatHeight, 0.05, 0.8);
        hatOffset             = clamp(hatOffset, -0.3, 0.8);
        hatSpeed              = clamp(hatSpeed, 0, 5);
        hatSegments           = clamp(hatSegments, 8, 96);
        if (!"gradient".equals(hatStyle) && !"solid".equals(hatStyle) && !"rainbow".equals(hatStyle)) hatStyle = "gradient";
        if (!com.farmmacro.visual.HatColors.isPreset(hatColor1)) hatColor1 = "purple";
        if (!com.farmmacro.visual.HatColors.isPreset(hatColor2)) hatColor2 = "orange";
    }

    /** Реальные пороги детекторов одной строкой (пишется в лог при загрузке и при старте макроса). */
    public String describeThresholds() {
        return String.format(java.util.Locale.ROOT,
                "паника=%s | поворот=%s yaw>%.2f° pitch>%.2f° | сервер=%s сдвиг>=%.2f бл поворот>=%.2f° | "
                        + "блок=%s %.2f бл | слот=%s окно=%s урон=%s эффекты=%s | застрял=%s %d т | сход=%s %.1f бл | "
                        + "повтор камеры=%s | звук=%s безопасный=%s системный=%s",
                panicEnabled, detectRotation, yawThreshold, pitchThreshold,
                detectServerMove, serverMoveThreshold, serverRotateThreshold,
                detectBlockInFace, blockDetectRadius, detectSlotChange, detectGuiOpen, detectDamage, detectPotionEffect,
                detectStuck, stuckThresholdTicks, detectDrift, driftThreshold,
                replayCamera, panicSound, panicSoundSafe, panicSoundSystem);
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
