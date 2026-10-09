package com.farmmacro.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;

public class ModConfig {

    public static ModConfig INSTANCE = new ModConfig();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("farmmacro.json");

    // ── Panic Detection ───────────────────────────────────────────────────────
    public boolean panicEnabled          = true;
    public boolean detectRotation        = true;
    public boolean detectTeleport        = true;
    public boolean detectBlockInFace     = true;
    public boolean detectSlotChange      = true;
    public boolean detectGuiOpen         = true;
    public boolean detectDamage          = true;

    public float   yawThreshold          = 5.0f;
    public float   pitchThreshold        = 5.0f;
    public double  teleportThreshold     = 6.0;
    public double  blockDetectRadius     = 1.5;

    public boolean detectServerRotation        = true;
    public int     serverRotationMouseTickWindow = 3;

    public boolean detectPotionEffect          = true;

    // ── Panic Reaction ────────────────────────────────────────────────────────
    public boolean panicMoveEnabled       = true;

    public boolean panicSoundEnabled           = true;
    public String  panicSoundId                = "minecraft:entity.ghast.scream";
    public float   panicSoundVolume            = 1.0f;
    public float   panicSoundPitch             = 1.0f;
    public int     panicSoundRepeats           = 1;
    public int     panicSoundRepeatDelayTicks  = 20;
    // Если true — звук играет через систему (не зависит от громкости Minecraft)
    public boolean panicSoundSystem            = true;

    public boolean panicRedScreenEnabled  = true;
    public int     panicRedScreenTicks    = 40;

    /** Детектор застревания по блоку:
     *  если игрок не двигается N тиков И по вектору движения есть твёрдый блок — паника */
    public boolean stuckBlockDetectEnabled = true;
    /** Сколько тиков без движения считается застреванием */
    public int     stuckThresholdTicks     = 30;

    // ── Humanize ─────────────────────────────────────────────────────────────
    /** Включить рандомизацию при каждом запуске макроса */
    public boolean humanizeEnabled        = true;

    /** Задержка старта: мин/макс в секундах */
    public float   humanizeStartDelayMin  = 0.3f;
    public float   humanizeStartDelayMax  = 2.5f;

    /** Паузы на поворотах: мин/макс в тиках */
    public int     humanizeDelayMin       = 5;
    public int     humanizeDelayMax       = 18;

    /** Тиков плавного замедления перед точкой (0 = выкл) */
    public int     humanizeSlowdownTicks  = 6;

    /** Смена стены на прямых отрезках */
    public boolean humanizeWallShift      = true;
    /** Максимальное смещение к стене в блоках */
    public float   humanizeWallOffset     = 0.15f;

    /** Редкие микро-паузы — раз в N запусков */
    public boolean humanizeMicroPauses       = true;
    /** Минимальная длина микро-паузы в секундах */
    public float   humanizeMicroPauseDurMin  = 0.5f;
    /** Максимальная длина микро-паузы в секундах */
    public float   humanizeMicroPauseDurMax  = 2.0f;
    /** Микро-пауза срабатывает раз в N запусков (мин) */
    public int     humanizeMicroPauseEveryMin = 20;
    /** Микро-пауза срабатывает раз в N запусков (макс) */
    public int     humanizeMicroPauseEveryMax = 25;

    // ── Crop Macros ───────────────────────────────────────────────────────────
    /**
     * Cooldown для рандомного выбора макроса культуры.
     * Один и тот же файл не может быть выбран повторно пока не сыграют N других.
     * Диапазон 1–5.
     */
    public int cropCooldown = 2;

    // ── Playback ──────────────────────────────────────────────────────────────
    /** Зациклить макрос (сохраняется между сессиями) */
    public boolean loopEnabled            = false;
    /** Показывать HUD статистики (сессия / кол-во запусков) */
    public boolean statsHudEnabled        = true;

    // ── Keybinds ──────────────────────────────────────────────────────────────
    public int     keyRecord             = 82;
    public int     keyPlay               = 80;
    public int     keyClear              = 261;
    public int     keyOpenGui            = 292;

    // ── Сохранение / загрузка ─────────────────────────────────────────────────
    public static void load() {
        File file = CONFIG_PATH.toFile();
        if (!file.exists()) {
            INSTANCE = new ModConfig();
            save();
            return;
        }
        try (Reader r = new FileReader(file)) {
            ModConfig loaded = GSON.fromJson(r, ModConfig.class);
            if (loaded != null) INSTANCE = loaded;
        } catch (Exception e) {
            INSTANCE = new ModConfig();
        }
    }

    public static void save() {
        try (Writer w = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(INSTANCE, w);
        } catch (Exception e) {
            // ignore
        }
    }
}
