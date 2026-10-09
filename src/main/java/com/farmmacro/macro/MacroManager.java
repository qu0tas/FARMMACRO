package com.farmmacro.macro;

import com.farmmacro.config.ModConfig;
import com.farmmacro.gui.SaveMacroScreen;
import com.farmmacro.panic.PanicDetector;
import com.farmmacro.panic.PanicSound;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Запись и воспроизведение макроса.
 *
 * Запись: каждый клиентский тик сохраняем позицию, поворот, нажатые клавиши и слот (MacroFrame).
 * Воспроизведение: каждый тик «нажимаем» клавиши из очередного кадра (через реальные привязки игрока).
 *
 * Автоматизация (всё опционально, настраивается в GUI → «Запуск»):
 * обратный отсчёт, проверка точки старта, лимит кругов/времени, стоп при полном инвентаре,
 * повтор камеры, сигнал по окончании. Проверки «застрял» и «сошёл с маршрута» живут здесь,
 * потому что им нужна запись, но срабатывают как обычная паника.
 */
public class MacroManager {

    public static final MacroManager INSTANCE = new MacroManager();
    private static final Logger LOGGER = LoggerFactory.getLogger("FarmMacro/Macro");

    public enum State { IDLE, RECORDING, COUNTDOWN, PLAYING }

    private static final int RESUME_ROLLBACK = 2;

    private final List<MacroFrame> frames = new ArrayList<>();
    private String loadedName;          // null — несохранённая запись
    private State  state = State.IDLE;

    // воспроизведение
    private int    playbackIndex;
    private int    passStartIndex;      // с какого кадра начался текущий круг
    private int    loopsDone;           // завершённых кругов в этом запуске
    private long   runStartMs;
    private int    countdownTicks;
    private int    pendingStartIndex;
    private double offX, offY, offZ;    // сдвиг «реальная позиция − запись» на начало круга
    private double[] actualX, actualZ;  // реальные позиции по кадрам текущего круга (для «застрял»)

    // статистика сессии (с момента запуска игры)
    private long sessionStartMs;
    private int  sessionRuns;

    // точка остановки для «возобновить»
    private boolean hasSavedPosition;
    private int     savedIndex = -1;
    private double  savedX, savedY, savedZ;

    // ── Запись ───────────────────────────────────────────────────────────────

    public void toggleRecording(Minecraft mc) {
        if (state == State.PLAYING || state == State.COUNTDOWN) { msg(mc, "§cСначала останови воспроизведение"); return; }
        if (state != State.RECORDING) {
            frames.clear();
            loadedName = null;
            clearSavedPosition();
            state = State.RECORDING;
            msg(mc, "§c● Запись началась §7(" + keyName(com.farmmacro.FarmMacroMod.keyRecord) + " — стоп)");
        } else {
            state = State.IDLE;
            msg(mc, "§e■ Запись остановлена: " + frames.size() + " кадров (" + formatTicks(frames.size()) + ")");
            if (frames.size() >= 5 && mc.screen == null) {
                mc.setScreen(new SaveMacroScreen(null, new ArrayList<>(frames)));
            }
        }
    }

    public void tickRecord(Minecraft mc) {
        if (state != State.RECORDING || mc.player == null) return;
        Options o = mc.options;
        LocalPlayer p = mc.player;
        frames.add(new MacroFrame(
                p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot(),
                o.keyUp.isDown(), o.keyDown.isDown(), o.keyLeft.isDown(), o.keyRight.isDown(),
                o.keyJump.isDown(), o.keyShift.isDown(), o.keySprint.isDown(),
                o.keyAttack.isDown(), o.keyUse.isDown(),
                p.getInventory().getSelectedSlot()));
    }

    public void clearRecording(Minecraft mc) {
        if (state == State.PLAYING || state == State.COUNTDOWN) { msg(mc, "§cСначала останови воспроизведение"); return; }
        frames.clear();
        loadedName = null;
        state = State.IDLE;
        clearSavedPosition();
        msg(mc, "§7Буфер макроса очищен.");
    }

    /** Загрузить сохранённый макрос в буфер. */
    public boolean loadMacro(String name, List<MacroFrame> loaded, Minecraft mc) {
        if (state != State.IDLE) { msg(mc, "§cСначала останови запись/воспроизведение"); return false; }
        frames.clear();
        frames.addAll(loaded);
        loadedName = name;
        clearSavedPosition();
        msg(mc, "§aЗагружен «" + name + "»: " + formatTicks(frames.size()) + ". "
                + keyName(com.farmmacro.FarmMacroMod.keyPlay) + " — запуск");
        return true;
    }

    public void markSaved(String name) { loadedName = name; }

    // ── Воспроизведение ──────────────────────────────────────────────────────

    public void togglePlayback(Minecraft mc) {
        if (state == State.PLAYING || state == State.COUNTDOWN) {
            stopPlayback(mc, "§e■ Остановлено вручную");
        } else {
            startPlayback(mc, 0, false);
        }
    }

    public void resumeFromSaved(Minecraft mc) {
        if (state != State.IDLE)  { msg(mc, "§cСначала останови запись/воспроизведение"); return; }
        if (!hasSavedPosition)    { msg(mc, "§7Нет сохранённой точки остановки"); return; }
        startPlayback(mc, Math.max(0, savedIndex - RESUME_ROLLBACK), true);
    }

    private void startPlayback(Minecraft mc, int index, boolean resume) {
        if (state == State.RECORDING) { msg(mc, "§cСначала останови запись"); return; }
        if (frames.isEmpty())         { msg(mc, "§cНет макроса — запиши (" + keyName(com.farmmacro.FarmMacroMod.keyRecord) + ") или загрузи в меню"); return; }
        if (mc.player == null) return;
        ModConfig c = ModConfig.INSTANCE;
        index = Math.min(index, frames.size() - 1);

        double dist = resume ? distanceTo(mc.player, savedX, savedY, savedZ) : distanceToFrame(mc.player, index);
        String where = resume ? "точки остановки" : "точки старта";
        if (dist > c.startPointWarnDistance) {
            if (c.requireStartPoint) {
                msg(mc, String.format(Locale.ROOT, "§cДо %s %.1f бл — подойди ближе (стрелка в HUD)", where, dist));
                return;
            }
            msg(mc, String.format(Locale.ROOT, "§6Внимание: до %s %.1f бл", where, dist));
        }

        PanicDetector.INSTANCE.silence();
        if (resume) clearSavedPosition();
        pendingStartIndex = index;
        if (c.startCountdownSeconds > 0) {
            state = State.COUNTDOWN;
            countdownTicks = c.startCountdownSeconds * 20;
        } else {
            beginPlaying(mc);
        }
    }

    private void beginPlaying(Minecraft mc) {
        state = State.PLAYING;
        playbackIndex = pendingStartIndex;
        loopsDone = 0;
        runStartMs = System.currentTimeMillis();
        if (sessionStartMs == 0) sessionStartMs = runStartMs;
        startPass(mc, playbackIndex);
        msg(mc, "§a▶ Воспроизведение" + (playbackIndex > 0 ? " с кадра " + playbackIndex : "")
                + " §7(" + keyName(com.farmmacro.FarmMacroMod.keyPlay) + " — стоп)");
    }

    private void startPass(Minecraft mc, int index) {
        sessionRuns++;
        passStartIndex = index;
        LocalPlayer p = mc.player;
        MacroFrame f = frames.get(index);
        offX = p.getX() - f.x; offY = p.getY() - f.y; offZ = p.getZ() - f.z;
        if (actualX == null || actualX.length != frames.size()) {
            actualX = new double[frames.size()];
            actualZ = new double[frames.size()];
        }
        PanicDetector.INSTANCE.snapshot(mc);
    }

    public void tickPlayback(Minecraft mc) {
        if (state == State.COUNTDOWN) { tickCountdown(mc); return; }
        if (state != State.PLAYING) return;
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {          // вышли из мира — без следов
            state = State.IDLE;
            releaseAll(mc);
            return;
        }
        ModConfig c = ModConfig.INSTANCE;

        // ── лимиты ──
        if (c.timeLimitMinutes > 0 && System.currentTimeMillis() - runStartMs >= c.timeLimitMinutes * 60_000L) {
            finish(mc, "§a✔ Лимит времени (" + c.timeLimitMinutes + " мин) — стоп", true);
            return;
        }
        if (c.stopWhenInventoryFull && p.getInventory().getFreeSlot() == -1) {
            finish(mc, "§a✔ Инвентарь полон — стоп", true);
            return;
        }

        // ── конец записи ──
        if (playbackIndex >= frames.size()) {
            loopsDone++;
            if (!c.loopEnabled) { finish(mc, "§a✔ Макрос завершён", false); return; }
            if (c.loopLimit > 0 && loopsDone >= c.loopLimit) {
                finish(mc, "§a✔ Пройдено кругов: " + loopsDone, false);
                return;
            }
            playbackIndex = 0;
            startPass(mc, 0);
        }

        // ── застрял / сошёл с маршрута ──
        actualX[playbackIndex] = p.getX();
        actualZ[playbackIndex] = p.getZ();
        if (c.panicEnabled && checkRouteProblems(mc, p, c)) return;

        // ── применяем кадр ──
        MacroFrame f = frames.get(playbackIndex);
        p.getInventory().setSelectedSlot(f.selectedSlot);
        PanicDetector.INSTANCE.expectSlot(f.selectedSlot);
        if (c.replayCamera) {
            p.setYRot(f.yaw);
            p.setXRot(f.pitch);
            PanicDetector.INSTANCE.expectRotation(f.yaw, f.pitch);
        }
        Options o = mc.options;
        press(o.keyUp, f.forward);
        press(o.keyDown, f.back);
        press(o.keyLeft, f.left);
        press(o.keyRight, f.right);
        press(o.keyJump, f.jump);
        press(o.keyShift, f.sneak);
        press(o.keySprint, f.sprint);
        press(o.keyAttack, f.attackPressed);
        press(o.keyUse, f.usePressed);

        playbackIndex++;
    }

    /** @return true если сработала паника */
    private boolean checkRouteProblems(Minecraft mc, LocalPlayer p, ModConfig c) {
        int i = playbackIndex;
        if (c.detectStuck) {
            int n = c.stuckThresholdTicks;
            if (i - n >= passStartIndex) {
                MacroFrame a = frames.get(i - n), b = frames.get(i);
                double recorded = Math.hypot(b.x - a.x, b.z - a.z);
                double actual   = Math.hypot(actualX[i] - actualX[i - n], actualZ[i] - actualZ[i - n]);
                if (recorded >= 1.0 && actual < Math.min(0.3, recorded * 0.15)) {
                    PanicDetector.INSTANCE.triggerPanic(mc, String.format(Locale.ROOT,
                            "Застрял: по записи %.1f бл за %.1f с, прошёл %.2f", recorded, n / 20.0, actual));
                    return true;
                }
            }
        }
        if (c.detectDrift) {
            MacroFrame f = frames.get(i);
            double drift = Math.hypot(p.getX() - offX - f.x, p.getZ() - offZ - f.z);
            if (drift > c.driftThreshold) {
                PanicDetector.INSTANCE.triggerPanic(mc, String.format(Locale.ROOT,
                        "Сошёл с маршрута на %.1f бл", drift));
                return true;
            }
        }
        return false;
    }

    private void tickCountdown(Minecraft mc) {
        if (mc.player == null) { state = State.IDLE; return; }
        if (countdownTicks % 20 == 0) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), 1.6f, 0.8f));
        }
        if (--countdownTicks <= 0) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 2.0f, 0.8f));
            beginPlaying(mc);
        }
    }

    /** Штатное окончание (конец записи/лимит). keepResume — можно продолжить с этого места. */
    private void finish(Minecraft mc, String message, boolean keepResume) {
        boolean wasPlaying = state == State.PLAYING;
        stopInternal(mc, keepResume);
        msg(mc, message);
        if (wasPlaying && ModConfig.INSTANCE.finishSoundEnabled) PanicSound.playDone();
    }

    /** Остановка (вручную или паникой). Точка остановки запоминается для «возобновить». */
    public void stopPlayback(Minecraft mc, String message) {
        if (state != State.PLAYING && state != State.COUNTDOWN) return;
        stopInternal(mc, true);
        msg(mc, message);
    }

    private void stopInternal(Minecraft mc, boolean keepResume) {
        if (keepResume && state == State.PLAYING && mc.player != null && playbackIndex > 0) {
            savedIndex = Math.min(playbackIndex - 1, frames.size() - 1);
            savedX = mc.player.getX();
            savedY = mc.player.getY();
            savedZ = mc.player.getZ();
            hasSavedPosition = true;
        }
        state = State.IDLE;
        playbackIndex = 0;
        releaseAll(mc);
    }

    // ── Клавиши ──────────────────────────────────────────────────────────────

    private static void press(KeyMapping key, boolean down) {
        // жмём ту клавишу, на которую игрок реально назначил действие
        KeyMapping.set(KeyMappingHelper.getBoundKeyOf(key), down);
    }

    private static void releaseAll(Minecraft mc) {
        Options o = mc.options;
        if (o == null) return;
        for (KeyMapping k : new KeyMapping[]{o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump,
                o.keyShift, o.keySprint, o.keyAttack, o.keyUse}) press(k, false);
    }

    // ── Геттеры для GUI/HUD ──────────────────────────────────────────────────

    public State   getState()          { return state; }
    public boolean isRecording()       { return state == State.RECORDING; }
    public boolean isPlaying()         { return state == State.PLAYING; }
    public boolean isCountingDown()    { return state == State.COUNTDOWN; }
    public boolean isActive()          { return state == State.PLAYING || state == State.COUNTDOWN; }
    public int     getCountdownTicks() { return countdownTicks; }
    public int     getFrameCount()     { return frames.size(); }
    public int     getPlaybackIndex()  { return playbackIndex; }
    public int     getLoopsDone()      { return loopsDone; }
    public long    getRunStartMs()     { return runStartMs; }
    public String  getLoadedName()     { return loadedName; }
    public List<MacroFrame> getFrames() { return Collections.unmodifiableList(frames); }

    public long getSessionStartMs() { return sessionStartMs; }
    public int  getSessionRuns()    { return sessionRuns; }
    public void resetStats()        { sessionStartMs = 0; sessionRuns = 0; }

    public boolean hasSavedPosition()   { return hasSavedPosition; }
    public double  getSavedX()          { return savedX; }
    public double  getSavedY()          { return savedY; }
    public double  getSavedZ()          { return savedZ; }
    public void    clearSavedPosition() { hasSavedPosition = false; savedIndex = -1; }

    /** Позиция первого кадра (точка старта) или null. */
    public MacroFrame getStartFrame() { return frames.isEmpty() ? null : frames.get(0); }

    public double distanceToFrame(LocalPlayer p, int index) {
        MacroFrame f = frames.get(index);
        return distanceTo(p, f.x, f.y, f.z);
    }

    private static double distanceTo(LocalPlayer p, double x, double y, double z) {
        double dx = p.getX() - x, dy = p.getY() - y, dz = p.getZ() - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ── Утилиты ──────────────────────────────────────────────────────────────

    public static String formatTicks(int ticks) {
        long s = ticks / 20;
        return s >= 60 ? String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60) : String.format(Locale.ROOT, "%.1f с", ticks / 20.0);
    }

    public static String keyName(KeyMapping key) {
        return key == null ? "?" : key.getTranslatedKeyMessage().getString();
    }

    private static void msg(Minecraft mc, String text) {
        if (mc.player != null) mc.player.sendOverlayMessage(Component.literal("§8[§cFM§8] §r" + text));
        LOGGER.info(text.replaceAll("§.", ""));
    }
}
