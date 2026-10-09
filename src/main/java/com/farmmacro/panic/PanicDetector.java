package com.farmmacro.panic;

import com.farmmacro.config.ModConfig;
import com.farmmacro.macro.MacroFrame;
import com.farmmacro.macro.MacroManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class PanicDetector {

    public static final PanicDetector INSTANCE = new PanicDetector();

    private static final Logger LOGGER = LoggerFactory.getLogger("FarmMacro/PanicDetector");

    private double  prevX, prevY, prevZ;
    private float   prevYaw, prevPitch;
    private float   expectedHealth;
    private int     expectedSlot;

    private volatile boolean guiOpenedExternally  = false;
    private volatile boolean blockAppearedInFace  = false;
    private volatile boolean serverForcedRotation = false;
    private volatile boolean potionEffectChanged  = false;

    private int ticksSinceMouseMove = 999;
    private int redScreenTicksLeft = 0;
    private int soundRepeatsLeft = 0;
    private int soundRepeatDelay = 0;
    private String lastPanicMoveFile = null;

    private int debugTickCounter = 0;
    private static final int DEBUG_LOG_INTERVAL = 100;

    public void updateExpected(double x, double y, double z, float yaw, float pitch) {}
    public void updateExpectedRotation(float yaw, float pitch) {}

    public void updateExpectedSlot(int slot) {
        if (slot != expectedSlot) {
            LOGGER.debug("[Слот] ожидаемый обновлён: {} -> {}", expectedSlot, slot);
        }
        expectedSlot = slot;
    }

    public void notifyGuiOpenedExternally() {
        LOGGER.warn("[Mixin/GUI] notifyGuiOpenedExternally() вызван, playing={}", MacroManager.INSTANCE.isPlaying());
        guiOpenedExternally = true;
    }

    public void notifyBlockAppearedInFace() {
        LOGGER.warn("[Mixin/Block] notifyBlockAppearedInFace() вызван, playing={}", MacroManager.INSTANCE.isPlaying());
        blockAppearedInFace = true;
    }

    public void notifyMouseInput() { ticksSinceMouseMove = 0; }

    public void notifyServerRotation() {
        LOGGER.warn("[Mixin/Packet] notifyServerRotation() вызван, playing={}, ticksSinceMouseMove={}",
                MacroManager.INSTANCE.isPlaying(), ticksSinceMouseMove);
        serverForcedRotation = true;
    }

    public void notifyPotionEffect() {
        LOGGER.warn("[Mixin/Packet] notifyPotionEffect() вызван, playing={}", MacroManager.INSTANCE.isPlaying());
        potionEffectChanged = true;
    }

    public void notifyStuck(MinecraftClient client) {
        LOGGER.warn("[Stuck] Застревание! pos=({}, {}, {})",
                client.player != null ? String.format("%.2f", client.player.getX()) : "?",
                client.player != null ? String.format("%.2f", client.player.getY()) : "?",
                client.player != null ? String.format("%.2f", client.player.getZ()) : "?");
        triggerPanic(client, "Застрял — координаты не меняются");
    }

    public void snapshot(MinecraftClient client) {
        if (client.player == null) return;
        prevX          = client.player.getX();
        prevY          = client.player.getY();
        prevZ          = client.player.getZ();
        prevYaw        = client.player.getYaw();
        prevPitch      = client.player.getPitch();
        expectedHealth = client.player.getHealth();
        expectedSlot   = client.player.getInventory().getSelectedSlot();
        redScreenTicksLeft  = 0;
        soundRepeatsLeft    = 0;
        soundRepeatDelay    = 0;
        ticksSinceMouseMove = 999;
        debugTickCounter    = 0;
        clearFlags();
        ModConfig cfg = ModConfig.INSTANCE;
        LOGGER.info("[Snapshot] pos=({},{},{}) yaw={} pitch={} health={} slot={}",
                String.format("%.2f", prevX), String.format("%.2f", prevY), String.format("%.2f", prevZ),
                String.format("%.2f", prevYaw), String.format("%.2f", prevPitch),
                String.format("%.1f", expectedHealth), expectedSlot);
        LOGGER.info("[Config] panicEnabled={} | A:rot({}/yaw={}/pitch={}) B:tp({}/thr={}) " +
                "C:block({}) D:slot({}) dmg({}) GUI({}) J:srvRot({}/win={}) Z:potion({})",
                cfg.panicEnabled,
                cfg.detectRotation, cfg.yawThreshold, cfg.pitchThreshold,
                cfg.detectTeleport, cfg.teleportThreshold,
                cfg.detectBlockInFace, cfg.detectSlotChange,
                cfg.detectDamage, cfg.detectGuiOpen,
                cfg.detectServerRotation, cfg.serverRotationMouseTickWindow,
                cfg.detectPotionEffect);
    }

    public void tick(MinecraftClient client) {
        if (client.player == null) return;
        ModConfig cfg = ModConfig.INSTANCE;

        if (redScreenTicksLeft > 0) redScreenTicksLeft--;
        if (soundRepeatsLeft > 0) {
            if (soundRepeatDelay > 0) {
                soundRepeatDelay--;
            } else {
                playPanicSound(client, cfg);
                soundRepeatsLeft--;
                soundRepeatDelay = cfg.panicSoundRepeatDelayTicks;
            }
        }

        if (ticksSinceMouseMove < 999) ticksSinceMouseMove++;

        if (!cfg.panicEnabled) { clearFlags(); updatePrev(client); return; }

        // Периодический дебаг
        debugTickCounter++;
        if (debugTickCounter >= DEBUG_LOG_INTERVAL) {
            debugTickCounter = 0;
            float dYaw = angleDiff(client.player.getYaw(), prevYaw);
            float dPitch = Math.abs(client.player.getPitch() - prevPitch);
            double dist = Math.sqrt(
                Math.pow(client.player.getX() - prevX, 2) +
                Math.pow(client.player.getY() - prevY, 2) +
                Math.pow(client.player.getZ() - prevZ, 2));
            LOGGER.debug("[Tick~5s] pos=({},{},{}) Dyaw={} Dpitch={} Ddist={} hp={} slot={} | flags: gui={} block={} srvRot={} potion={} | mouseTicks={}",
                    String.format("%.2f", client.player.getX()),
                    String.format("%.2f", client.player.getY()),
                    String.format("%.2f", client.player.getZ()),
                    String.format("%.2f", dYaw), String.format("%.2f", dPitch),
                    String.format("%.2f", dist),
                    String.format("%.1f", client.player.getHealth()),
                    client.player.getInventory().getSelectedSlot(),
                    guiOpenedExternally, blockAppearedInFace,
                    serverForcedRotation, potionEffectChanged,
                    ticksSinceMouseMove);
        }

        String reason = null;

        // Д: GUI снаружи
        if (cfg.detectGuiOpen && guiOpenedExternally) {
            reason = "GUI открылось снаружи";
            LOGGER.warn("[Детект-Д] GUI открылось снаружи. screen={}",
                    client.currentScreen != null ? client.currentScreen.getClass().getSimpleName() : "null");
        }

        // В: блок в лицо
        if (reason == null && cfg.detectBlockInFace && blockAppearedInFace) {
            reason = "Блок появился перед лицом";
            LOGGER.warn("[Детект-В] Блок в лицо. pos=({},{},{})",
                    String.format("%.2f", client.player.getX()),
                    String.format("%.2f", client.player.getY()),
                    String.format("%.2f", client.player.getZ()));
        }

        // А: поворот камеры
        if (reason == null && cfg.detectRotation) {
            float dy = angleDiff(client.player.getYaw(), prevYaw);
            float dp = Math.abs(client.player.getPitch() - prevPitch);
            if (dy > cfg.yawThreshold) {
                reason = "Камера повернулась (yaw +" + String.format("%.2f", dy) + ")";
                LOGGER.warn("[Детект-А] Yaw: было={} стало={} D={} порог={}",
                        String.format("%.2f", prevYaw),
                        String.format("%.2f", client.player.getYaw()),
                        String.format("%.2f", dy), cfg.yawThreshold);
            } else if (dp > cfg.pitchThreshold) {
                reason = "Камера повернулась (pitch +" + String.format("%.2f", dp) + ")";
                LOGGER.warn("[Детект-А] Pitch: было={} стало={} D={} порог={}",
                        String.format("%.2f", prevPitch),
                        String.format("%.2f", client.player.getPitch()),
                        String.format("%.2f", dp), cfg.pitchThreshold);
            }
        }

        // Б: телепорт
        if (reason == null && cfg.detectTeleport) {
            double d = Math.sqrt(
                Math.pow(client.player.getX() - prevX, 2) +
                Math.pow(client.player.getY() - prevY, 2) +
                Math.pow(client.player.getZ() - prevZ, 2));
            if (d > cfg.teleportThreshold) {
                reason = "Телепортация (" + String.format("%.1f", d) + " бл)";
                LOGGER.warn("[Детект-Б] Телепорт: было=({},{},{}) стало=({},{},{}) D={} порог={}",
                        String.format("%.2f", prevX), String.format("%.2f", prevY), String.format("%.2f", prevZ),
                        String.format("%.2f", client.player.getX()),
                        String.format("%.2f", client.player.getY()),
                        String.format("%.2f", client.player.getZ()),
                        String.format("%.2f", d), cfg.teleportThreshold);
            }
        }

        // Г: слот снаружи
        if (reason == null && cfg.detectSlotChange) {
            int real = client.player.getInventory().getSelectedSlot();
            if (real != expectedSlot) {
                reason = "Слот сменился снаружи";
                LOGGER.warn("[Детект-Г] Слот: ожидался={} реальный={}", expectedSlot, real);
                expectedSlot = real;
            }
        }

        // Урон
        if (reason == null && cfg.detectDamage) {
            float h = client.player.getHealth();
            if (h < expectedHealth - 0.01f) {
                reason = "Получен урон";
                LOGGER.warn("[Детект-Урон] HP: было={} стало={} потеря={}",
                        String.format("%.1f", expectedHealth), String.format("%.1f", h),
                        String.format("%.1f", expectedHealth - h));
            }
            expectedHealth = h;
        }

        // Ж: принудительная ротация сервером
        if (reason == null && cfg.detectServerRotation && serverForcedRotation) {
            if (ticksSinceMouseMove > cfg.serverRotationMouseTickWindow) {
                reason = "Сервер принудительно повернул камеру";
                LOGGER.warn("[Детект-Ж] Серверная ротация: ticksSinceMouseMove={} (окно={})",
                        ticksSinceMouseMove, cfg.serverRotationMouseTickWindow);
            } else {
                LOGGER.debug("[Детект-Ж] Серверная ротация проигнорирована: мышь={} тиков назад (окно={})",
                        ticksSinceMouseMove, cfg.serverRotationMouseTickWindow);
            }
        }

        // З: зелья
        if (reason == null && cfg.detectPotionEffect && potionEffectChanged) {
            reason = "Получен/снят эффект зелья";
            LOGGER.warn("[Детект-З] Эффект зелья изменился");
        }

        clearFlags();

        if (reason != null) {
            triggerPanic(client, reason);
            return;
        }

        updatePrev(client);
    }

    private void triggerPanic(MinecraftClient client, String reason) {
        ModConfig cfg = ModConfig.INSTANCE;

        LOGGER.error("╔═══════════════════════════════════════════════════");
        LOGGER.error("║  [ПАНИКА] {}", reason);
        if (client.player != null) {
            LOGGER.error("║  pos=({}, {}, {}) yaw={} pitch={}",
                    String.format("%.2f", client.player.getX()),
                    String.format("%.2f", client.player.getY()),
                    String.format("%.2f", client.player.getZ()),
                    String.format("%.2f", client.player.getYaw()),
                    String.format("%.2f", client.player.getPitch()));
            LOGGER.error("║  prevYaw={} prevPitch={}",
                    String.format("%.2f", prevYaw), String.format("%.2f", prevPitch));
            LOGGER.error("║  hp={} slot={} (ожидался {})",
                    String.format("%.1f", client.player.getHealth()),
                    client.player.getInventory().getSelectedSlot(), expectedSlot);
        }
        LOGGER.error("╚═══════════════════════════════════════════════════");

        MacroManager.INSTANCE.stopPlayback(client, "[FarmMacro] Паника: " + reason);

        if (cfg.panicMoveEnabled) {
            List<MacroFrame> panicFrames = PanicMoveStorage.INSTANCE.loadRandom(lastPanicMoveFile);
            if (panicFrames != null) {
                lastPanicMoveFile = PanicMoveStorage.INSTANCE.getLastLoadedFile();
                LOGGER.info("[Паника] Запускаю panic_move: {} ({} кадров)", lastPanicMoveFile, panicFrames.size());
                MacroManager.INSTANCE.startPanicMove(client, panicFrames);
            } else {
                LOGGER.warn("[Паника] Нет доступных panic_move файлов (last={})", lastPanicMoveFile);
            }
        }

        if (cfg.panicSoundEnabled) {
            playPanicSound(client, cfg);
            soundRepeatsLeft = Math.max(0, cfg.panicSoundRepeats - 1);
            soundRepeatDelay = cfg.panicSoundRepeatDelayTicks;
        }

        if (cfg.panicRedScreenEnabled)
            redScreenTicksLeft = cfg.panicRedScreenTicks;
    }

    private void playPanicSound(MinecraftClient client, ModConfig cfg) {
        if (cfg.panicSoundSystem) {
            playSystemSound(cfg.panicSoundVolume);
            return;
        }
        if (client.world == null || client.player == null) return;
        try {
            Identifier id    = Identifier.of(cfg.panicSoundId);
            SoundEvent sound = Registries.SOUND_EVENT.get(id);
            if (sound == null) {
                LOGGER.warn("[Звук] Звук не найден: {}", cfg.panicSoundId);
                return;
            }
            float pitch  = cfg.panicSoundPitch;
            float volume = cfg.panicSoundVolume;
            client.getSoundManager().play(new AbstractSoundInstance(sound, SoundCategory.MASTER, Random.create()) {
                { this.volume = volume; this.pitch = pitch; this.relative = true; }
            });
        } catch (Exception e) {
            LOGGER.error("[Звук] Ошибка: {}", e.getMessage());
        }
    }

    /**
     * Воспроизводит звук через javax.sound.sampled — полностью игнорирует
     * настройки громкости Minecraft.
     *
     * Сначала ищет .minecraft/config/farmmacro/panic.wav
     * Если файл не найден — играет встроенный программный бип.
     */
    private void playSystemSound(float volume) {
        Thread t = new Thread(() -> {
            try {
                java.io.File wavFile = net.fabricmc.loader.api.FabricLoader.getInstance()
                        .getConfigDir()
                        .resolve("farmmacro")
                        .resolve("panic.wav")
                        .toFile();

                if (wavFile.exists()) {
                    // Играем пользовательский .wav
                    try (javax.sound.sampled.AudioInputStream ais =
                                 javax.sound.sampled.AudioSystem.getAudioInputStream(wavFile)) {
                        javax.sound.sampled.AudioFormat fmt = ais.getFormat();
                        javax.sound.sampled.DataLine.Info info =
                                new javax.sound.sampled.DataLine.Info(javax.sound.sampled.SourceDataLine.class, fmt);
                        javax.sound.sampled.SourceDataLine line =
                                (javax.sound.sampled.SourceDataLine) javax.sound.sampled.AudioSystem.getLine(info);
                        line.open(fmt);
                        // Применяем громкость через FloatControl если доступен
                        if (line.isControlSupported(javax.sound.sampled.FloatControl.Type.MASTER_GAIN)) {
                            javax.sound.sampled.FloatControl gain =
                                    (javax.sound.sampled.FloatControl) line.getControl(javax.sound.sampled.FloatControl.Type.MASTER_GAIN);
                            float db = (float)(20.0 * Math.log10(Math.max(0.0001f, volume)));
                            gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
                        }
                        line.start();
                        byte[] buf = new byte[4096];
                        int read;
                        while ((read = ais.read(buf, 0, buf.length)) != -1) {
                            line.write(buf, 0, read);
                        }
                        line.drain();
                        line.close();
                    }
                } else {
                    // Фолбэк: программный бип 880 Гц, 300 мс
                    LOGGER.warn("[СистемныйЗвук] panic.wav не найден в config/farmmacro/, играю встроенный бип");
                    int sampleRate = 44100;
                    int samples    = sampleRate * 300 / 1000;
                    byte[] buf     = new byte[samples * 2];
                    float amp      = Math.min(1.0f, Math.max(0.0f, volume)) * 32767f;
                    for (int i = 0; i < samples; i++) {
                        double envelope = 1.0 - (double) i / samples;
                        short s = (short)(Math.sin(2 * Math.PI * 880.0 * i / sampleRate) * amp * envelope);
                        buf[i * 2]     = (byte)(s & 0xFF);
                        buf[i * 2 + 1] = (byte)((s >> 8) & 0xFF);
                    }
                    javax.sound.sampled.AudioFormat fmt = new javax.sound.sampled.AudioFormat(sampleRate, 16, 1, true, false);
                    javax.sound.sampled.DataLine.Info info =
                            new javax.sound.sampled.DataLine.Info(javax.sound.sampled.SourceDataLine.class, fmt);
                    javax.sound.sampled.SourceDataLine line =
                            (javax.sound.sampled.SourceDataLine) javax.sound.sampled.AudioSystem.getLine(info);
                    line.open(fmt);
                    line.start();
                    line.write(buf, 0, buf.length);
                    line.drain();
                    line.close();
                }
            } catch (Exception e) {
                LOGGER.error("[СистемныйЗвук] Ошибка: {}", e.getMessage());
            }
        }, "farmmacro-panic-sound");
        t.setDaemon(true);
        t.start();
    }

    public boolean isInPanic()         { return redScreenTicksLeft > 0; }
    public int     getRedScreenTicks() { return redScreenTicksLeft; }

    public void tickEffectsOnly(MinecraftClient client) {
        if (redScreenTicksLeft > 0) redScreenTicksLeft--;
        if (soundRepeatsLeft > 0) {
            if (soundRepeatDelay > 0) {
                soundRepeatDelay--;
            } else {
                playPanicSound(client, ModConfig.INSTANCE);
                soundRepeatsLeft--;
                soundRepeatDelay = ModConfig.INSTANCE.panicSoundRepeatDelayTicks;
            }
        }
    }

    private void updatePrev(MinecraftClient client) {
        if (client.player == null) return;
        prevX     = client.player.getX();
        prevY     = client.player.getY();
        prevZ     = client.player.getZ();
        prevYaw   = client.player.getYaw();
        prevPitch = client.player.getPitch();
    }

    private void clearFlags() {
        guiOpenedExternally  = false;
        blockAppearedInFace  = false;
        serverForcedRotation = false;
        potionEffectChanged  = false;
    }

    private float angleDiff(float a, float b) {
        float d = Math.abs(a - b) % 360f;
        return d > 180f ? 360f - d : d;
    }
}
