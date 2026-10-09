package com.farmmacro.macro;

import com.farmmacro.panic.PanicDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;

public class MacroManager {

    public static final MacroManager INSTANCE = new MacroManager();

    private static final Logger LOGGER = LoggerFactory.getLogger("FarmMacro/MacroManager");

    private final List<MacroFrame> frames      = new ArrayList<>();

    private boolean recording     = false;
    private boolean playing       = false;
    private int     playbackIndex = 0;
    private boolean loopEnabled   = com.farmmacro.config.ModConfig.INSTANCE.loopEnabled;

    // ── Статистика сессии ─────────────────────────────────────────────────────
    private long sessionStartMs  = 0;   // System.currentTimeMillis() при первом запуске
    private int  sessionRuns     = 0;   // сколько раз запущен макрос за сессию

    private double lastX = 0, lastZ = 0;
    private int    stuckTicks = 0;
    private static final int STUCK_TIMEOUT_TICKS = 30;

    // ── Сохранённая позиция для возобновления ─────────────────────────────────
    private static final int RESUME_ROLLBACK = 2;

    private int     savedIndex = -1;
    private double  savedX, savedY, savedZ;
    private boolean hasSavedPosition = false;

    // ── Кеш XZ-позиций макроса для фильтрации стен фермы ─────────────────────
    private java.util.Set<Long> macroXZCache = null;

    public void toggleRecording(Minecraft client) {
        if (playing) { msg(client, "§cСначала останови воспроизведение (P)"); return; }
        if (!recording) {
            frames.clear();
            recording = true;
            msg(client, "§a● Запись началась (нажми R чтобы остановить)");
        } else {
            recording = false;
            msg(client, "§e■ Запись остановлена. Кадров: " + frames.size());
            if (!frames.isEmpty() && client.screen == null) {
                client.setScreen(new com.farmmacro.gui.SaveMacroScreen(null, new java.util.ArrayList<>(frames)));
            }
        }
    }

    public void tickRecord(Minecraft client) {
        if (!recording || client.player == null) return;
        Options opt = client.options;
        MacroFrame frame = new MacroFrame(
                client.player.getX(), client.player.getY(), client.player.getZ(),
                client.player.getYRot(), client.player.getXRot(),
                opt.keyUp.isDown(), opt.keyDown.isDown(),
                opt.keyLeft.isDown(), opt.keyRight.isDown(),
                opt.keyJump.isDown(), opt.keyShift.isDown(), opt.keySprint.isDown(),
                opt.keyAttack.isDown(), opt.keyUse.isDown(),
                client.player.getInventory().getSelectedSlot()
        );
        frames.add(frame);
    }

    public void clearRecording(Minecraft client) {
        if (playing) { msg(client, "§cСначала останови воспроизведение (P)"); return; }
        frames.clear();
        recording = false;
        msg(client, "§7Запись очищена.");
    }

    public void togglePlayback(Minecraft client) {
        if (recording) { msg(client, "§cСначала останови запись (R)"); return; }
        if (frames.isEmpty()) { msg(client, "§cНет записи! Сначала запиши макрос (R)"); return; }
        if (!playing) {
            macroXZCache = null;

            playing = true;
            playbackIndex = 0;
            stuckTicks = 0;
            lastX = client.player.getX();
            lastZ = client.player.getZ();
            PanicDetector.INSTANCE.snapshot(client);

            // Статистика
            if (sessionStartMs == 0) sessionStartMs = System.currentTimeMillis();
            sessionRuns++;

            msg(client, "§a▶ Воспроизведение началось (P чтобы остановить)");
        } else {
            stopPlayback(client, "§e■ Воспроизведение остановлено вручную.");
        }
    }

    public void tickPlayback(Minecraft client) {
        if (!playing || client.player == null) return;

        double curX = client.player.getX();
        double curZ = client.player.getZ();
        {
            com.farmmacro.config.ModConfig c = com.farmmacro.config.ModConfig.INSTANCE;
            double moved = Math.sqrt((curX - lastX) * (curX - lastX) + (curZ - lastZ) * (curZ - lastZ));

            // Макрос должен сейчас двигаться (есть нажатая клавиша движения)
            List<MacroFrame> checkFrames = this.frames;
            if (c.stuckBlockDetectEnabled) {
                // Умная проверка: считаем тики только когда макрос хочет идти
                boolean macroWantsMove = false;
                if (playbackIndex > 0 && playbackIndex <= checkFrames.size()) {
                    MacroFrame cf = checkFrames.get(playbackIndex - 1);
                    macroWantsMove = cf.forward || cf.back || cf.left || cf.right;
                }
                if (macroWantsMove && moved < 0.02) {
                    stuckTicks++;
                    if (stuckTicks >= c.stuckThresholdTicks) {
                        if (isBlockedByWall(client, checkFrames)) {
                            PanicDetector.INSTANCE.notifyStuck(client);
                            return;
                        }
                    }
                } else {
                    stuckTicks = 0;
                }
            } else {
                // Старая логика: просто таймаут без проверки блока
                if (moved < 0.02) {
                    stuckTicks++;
                    if (stuckTicks >= STUCK_TIMEOUT_TICKS) {
                        PanicDetector.INSTANCE.notifyStuck(client);
                        return;
                    }
                } else {
                    stuckTicks = 0;
                }
            }
        }
        lastX = curX;
        lastZ = curZ;

        List<MacroFrame> currentFrames = this.frames;

        if (playbackIndex >= currentFrames.size()) {
            if (!loopEnabled) {
                stopPlayback(client, "§e■ Макрос завершён (цикл выключен).");
                return;
            }
            playbackIndex = 0;
            sessionRuns++;
            PanicDetector.INSTANCE.snapshot(client);
        }
        MacroFrame f = currentFrames.get(playbackIndex);

        client.player.getInventory().setSelectedSlot(f.selectedSlot);
        PanicDetector.INSTANCE.updateExpectedSlot(f.selectedSlot);

        applyKey(client.options.keyUp,  f.forward);
        applyKey(client.options.keyDown,     f.back);
        applyKey(client.options.keyLeft,     f.left);
        applyKey(client.options.keyRight,    f.right);
        applyKey(client.options.keyJump,     f.jump);
        applyKey(client.options.keyShift,    f.sneak);
        applyKey(client.options.keySprint,   f.sprint);
        applyKey(client.options.keyAttack,   f.attackPressed);
        applyKey(client.options.keyUse,      f.usePressed);

        playbackIndex++;
    }

    /**
     * Определяет вектор движения из текущего кадра макроса и проверяет,
     * есть ли твёрдый блок по этому вектору на высоте ног (Y) и головы (Y+1).
     *
     * Используется исключительно BlockPos + world.getBlockState — без raycast,
     * чтобы не зависеть от угла камеры.
     */
    private boolean isBlockedByWall(Minecraft client, List<MacroFrame> frames) {
        if (client.player == null || client.level == null) return false;
        if (playbackIndex <= 0 || playbackIndex > frames.size()) return false;

        MacroFrame cf = frames.get(playbackIndex - 1);

        // Вычисляем нормализованный вектор движения из флагов кадра
        double dx = 0, dz = 0;
        // forward/back в Minecraft — движение вдоль -Z / +Z в мировых координатах
        // но реальное направление зависит от yaw игрока.
        // Используем yaw из кадра для корректного пересчёта.
        float yawRad = (float) Math.toRadians(cf.yaw);
        if (cf.forward)  { dx -= Math.sin(yawRad); dz += Math.cos(yawRad); }
        if (cf.back)     { dx += Math.sin(yawRad); dz -= Math.cos(yawRad); }
        if (cf.left)     { dx -= Math.cos(yawRad); dz -= Math.sin(yawRad); }
        if (cf.right)    { dx += Math.cos(yawRad); dz += Math.sin(yawRad); }

        if (dx == 0 && dz == 0) return false;

        // Нормализуем и смотрим на 0.6 блока вперёд (ширина хитбокса игрока)
        double len = Math.sqrt(dx * dx + dz * dz);
        dx = dx / len * 0.6;
        dz = dz / len * 0.6;

        double px = client.player.getX() + dx;
        double pz = client.player.getZ() + dz;
        double py = client.player.getY();

        // Проверяем ноги (Y) и голову (Y+1)
        net.minecraft.core.BlockPos feet = new net.minecraft.core.BlockPos(
                (int) Math.floor(px), (int) Math.floor(py), (int) Math.floor(pz));
        net.minecraft.core.BlockPos head = new net.minecraft.core.BlockPos(
                (int) Math.floor(px), (int) Math.floor(py + 1), (int) Math.floor(pz));

        boolean feetBlocked = !client.level.getBlockState(feet).getCollisionShape(
                client.level, feet).isEmpty();
        boolean headBlocked = !client.level.getBlockState(head).getCollisionShape(
                client.level, head).isEmpty();

        if (!feetBlocked && !headBlocked) return false;

        // Блок есть — но лежит ли он на пути макроса?
        // Если эта XZ-координата НИКОГДА не встречалась в кадрах — это стена фермы, игнорим
        java.util.Set<Long> xzSet = getMacroXZSet();
        long blockKey = ((long)(int) Math.floor(px) << 32) | ((int) Math.floor(pz) & 0xFFFFFFFFL);
        if (!xzSet.contains(blockKey)) return false;

        return true;
    }

    /**
     * Строит и кеширует Set всех XZ-координат (в блоках), которые посещает макрос.
     * Стены фермы по определению не входят в этот набор — игрок доходит до них,
     * разворачивается и уходит, не наступая на них.
     */
    private java.util.Set<Long> getMacroXZSet() {
        if (macroXZCache != null) return macroXZCache;
        macroXZCache = new java.util.HashSet<>();
        for (MacroFrame f : frames) {
            int fx = (int) Math.floor(f.x);
            int fz = (int) Math.floor(f.z);
            // +1 блок вокруг — покрывает хитбокс игрока (0.6 шириной)
            for (int ddx = -1; ddx <= 1; ddx++)
                for (int ddz = -1; ddz <= 1; ddz++)
                    macroXZCache.add(((long)(fx + ddx) << 32) | ((fz + ddz) & 0xFFFFFFFFL));
        }
        return macroXZCache;
    }

    private void applyKey(net.minecraft.client.KeyMapping key, boolean pressed) {
        // Жмём ту клавишу, на которую игрок реально назначил действие (а не дефолтную)
        net.minecraft.client.KeyMapping.set(
                net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.getBoundKeyOf(key), pressed);
    }

    public void stopPlayback(Minecraft client, String reason) {
        if (playing && client.player != null && playbackIndex > 0) {
            savedIndex       = playbackIndex - 1;
            savedX           = client.player.getX();
            savedY           = client.player.getY();
            savedZ           = client.player.getZ();
            hasSavedPosition = true;
        }
        playing      = false;
        playbackIndex = 0;
        macroXZCache  = null;
        releaseAll(client);
        msg(client, reason);
    }

    public void resumeFromSaved(Minecraft client) {
        if (recording)         { msg(client, "§cСначала останови запись (R)"); return; }
        if (playing)           { msg(client, "§cМакрос уже играет"); return; }
        if (!hasSavedPosition) { msg(client, "§7Нет сохранённой позиции"); return; }
        if (frames.isEmpty())  { msg(client, "§cНет загруженного макроса"); return; }

        int resumeIndex = Math.max(0, savedIndex - RESUME_ROLLBACK);
        playing       = true;
        playbackIndex = resumeIndex;
        stuckTicks    = 0;
        if (client.player != null) {
            lastX = client.player.getX();
            lastZ = client.player.getZ();
        }
        PanicDetector.INSTANCE.snapshot(client);

        // Сбрасываем сохранённую позицию — только после успешного запуска
        hasSavedPosition = false;
        savedIndex       = -1;

        msg(client, "§a▶ Возобновлено с кадра " + resumeIndex + " (P чтобы остановить)");
    }

    public boolean hasSavedPosition()   { return hasSavedPosition; }
    public double  getSavedX()          { return savedX; }
    public double  getSavedY()          { return savedY; }
    public double  getSavedZ()          { return savedZ; }
    public void    clearSavedPosition() { hasSavedPosition = false; savedIndex = -1; }

    private void releaseAll(Minecraft client) {
        if (client.options == null) return;
        applyKey(client.options.keyUp,  false);
        applyKey(client.options.keyDown,     false);
        applyKey(client.options.keyLeft,     false);
        applyKey(client.options.keyRight,    false);
        applyKey(client.options.keyJump,     false);
        applyKey(client.options.keyShift,    false);
        applyKey(client.options.keySprint,   false);
        applyKey(client.options.keyAttack,   false);
        applyKey(client.options.keyUse,      false);
    }

    public boolean isRecording()    { return recording; }
    public boolean isPlaying()      { return playing; }
    public boolean isLoopEnabled()  { return loopEnabled; }

    public long getSessionStartMs() { return sessionStartMs; }
    public int  getSessionRuns()    { return sessionRuns; }
    public void resetStats()        { sessionStartMs = 0; sessionRuns = 0; }
    public void setLoopEnabled(boolean v) {
        loopEnabled = v;
        com.farmmacro.config.ModConfig.INSTANCE.loopEnabled = v;
        com.farmmacro.config.ModConfig.save();
    }

    public void loadMacro(java.util.List<MacroFrame> loadedFrames, Minecraft client) {
        if (playing)    { msg(client, "§cСначала останови воспроизведение (P)"); return; }
        if (recording)  { msg(client, "§cСначала останови запись (R)"); return; }
        frames.clear();
        frames.addAll(loadedFrames);
        hasSavedPosition = false;   // старая позиция относится к другому макросу
        savedIndex       = -1;
        msg(client, "§aМакрос загружен. Кадров: " + frames.size() + "  Нажми P для запуска.");
    }

    private void msg(Minecraft client, String text) {
        if (client.player != null)
            client.player.sendOverlayMessage(Component.literal("[FarmMacro] " + text));
    }
}
