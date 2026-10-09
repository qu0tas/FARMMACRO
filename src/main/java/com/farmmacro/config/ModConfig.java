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
    /** true — играть через звуковую систему ОС (не зависит от громкости Minecraft). */
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
        INSTANCE.sanitize();
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
