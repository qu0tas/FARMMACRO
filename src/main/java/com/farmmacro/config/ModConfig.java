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

    // ── Playback ──────────────────────────────────────────────────────────────
    /** Зациклить макрос (сохраняется между сессиями) */
    public boolean loopEnabled            = false;
    /** Показывать HUD статистики (сессия / кол-во запусков) */
    public boolean statsHudEnabled        = true;

    // ── Keybinds ──────────────────────────────────────────────────────────────
    public int     keyRecord             = 82;
    public int     keyPlay               = 80;
    public int     keyClear              = 261;
    public int     keyOpenGui            = 344; // Right Shift (292 = F3 — конфликт с debug-экраном)

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
            org.slf4j.LoggerFactory.getLogger("FarmMacro/Config")
                    .error("Не удалось прочитать farmmacro.json, беру настройки по умолчанию", e);
            INSTANCE = new ModConfig();
        }
    }

    public static void save() {
        try (Writer w = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(INSTANCE, w);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("FarmMacro/Config").error("Не удалось сохранить farmmacro.json", e);
        }
    }
}
