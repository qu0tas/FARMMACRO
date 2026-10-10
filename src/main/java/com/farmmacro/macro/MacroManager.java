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

    /** Что играет клавиша запуска: запись (кадры) или маршрут по точкам. Последнее загруженное/изменённое. */
    public enum SourceKind { RECORDING, ROUTE }
    private SourceKind sourceKind = SourceKind.RECORDING;

    public SourceKind getSourceKind() { return sourceKind; }
    /** Маршрут загружен или изменён — дальше играет он (пока идёт воспроизведение, источник не меняется). */
    public void useRouteSource()      { if (state == State.IDLE) sourceKind = SourceKind.ROUTE; }
    public void useRecordingSource()  { if (state == State.IDLE || state == State.RECORDING) sourceKind = SourceKind.RECORDING; }

    private static final int RESUME_ROLLBACK = 2;

    private final List<MacroFrame> frames = new ArrayList<>();
    private String loadedName;          // null — несохранённая запись
    /** Настройки текущего макроса (камера: from — индекс кадра). Сохраняются в файл макроса (v4). */
    private MacroSettings settings = new MacroSettings();
    /** Настройки поменяли в окне, а файл ещё старый. */
    private boolean settingsDirty;
    private final com.farmmacro.camera.CameraBinding.Tracker camTracker = new com.farmmacro.camera.CameraBinding.Tracker();
    private State  state = State.IDLE;

    // воспроизведение (общее)
    private PlaybackSource source;      // что играет сейчас (null — ничего)
    private final RecordingSource recordingSource = new RecordingSource();
    private int    loopsDone;           // завершённых кругов в этом запуске
    private long   runStartMs;
    private int    countdownTicks;
    private int    pendingStartIndex;
    private PlaybackSource pendingSource;
    // запись по кадрам
    private int    playbackIndex;
    private int    passStartIndex;      // с какого кадра начался текущий круг
    private double offX, offY, offZ;    // сдвиг «реальная позиция − запись» на начало круга
    private double[] actualX, actualZ;  // реальные позиции по кадрам текущего круга (для «застрял»)

    // статистика сессии (с момента запуска игры)
    private long sessionStartMs;
    private int  sessionRuns;

    // точка остановки для «возобновить»
    private boolean hasSavedPosition;
    private SourceKind savedKind;
    private int     savedIndex = -1;
    private double  savedX, savedY, savedZ;

    // ── Запись ───────────────────────────────────────────────────────────────

    public void toggleRecording(Minecraft mc) {
        if (state == State.PLAYING || state == State.COUNTDOWN) { msg(mc, "§cСначала останови воспроизведение"); return; }
        if (state != State.RECORDING) {
            frames.clear();
            loadedName = null;
            settings = new MacroSettings();
            settingsDirty = false;
            clearSavedPosition();
            state = State.RECORDING;
            sourceKind = SourceKind.RECORDING;
            navDismissed = false;
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
        settings = new MacroSettings();
        settingsDirty = false;
        state = State.IDLE;
        clearSavedPosition();
        msg(mc, "§7Буфер макроса очищен.");
    }

    /** Загрузить сохранённый макрос в буфер. */
    public boolean loadMacro(String name, List<MacroFrame> loaded, MacroSettings set, Minecraft mc) {
        if (state != State.IDLE) { msg(mc, "§cСначала останови запись/воспроизведение"); return false; }
        saveSettingsIfDirty();
        frames.clear();
        frames.addAll(loaded);
        loadedName = name;
        settings = set == null ? new MacroSettings() : set;
        settingsDirty = false;
        clearSavedPosition();
        sourceKind = SourceKind.RECORDING;
        navDismissed = false;
        msg(mc, "§aЗагружен «" + name + "»: " + formatTicks(frames.size()) + ". "
                + keyName(com.farmmacro.FarmMacroMod.keyPlay) + " — запуск");
        return true;
    }

    public void markSaved(String name) { loadedName = name; settingsDirty = false; }

    // ── Настройки макроса ────────────────────────────────────────────────────

    public MacroSettings getSettings() { return settings; }
    public com.farmmacro.camera.CameraBinding getCamera() { return settings.camera; }

    /** Окно настроек поменяло настройки (в файл уйдёт при закрытии окна/меню или загрузке другого макроса). */
    public void settingsChanged() { settingsDirty = true; }
    public boolean isSettingsDirty() { return settingsDirty; }

    /** Настройки записаны в файл загруженного макроса снаружи (⚙ у карточки) — взять копию, файл уже свежий. */
    public void replaceSettings(MacroSettings s) {
        if (state != State.IDLE || s == null) return;
        settings = s;
        settingsDirty = false;
    }

    /** Дописать изменённые настройки в файл загруженного макроса. */
    public void saveSettingsIfDirty() {
        if (!settingsDirty || loadedName == null || frames.isEmpty()) return;
        if (MacroStorage.INSTANCE.save(loadedName, frames, settings)) settingsDirty = false;
    }

    // ── Воспроизведение ──────────────────────────────────────────────────────

    public void togglePlayback(Minecraft mc) {
        if (state == State.PLAYING || state == State.COUNTDOWN) {
            stopPlayback(mc, "§e■ Остановлено вручную");
        } else {
            startPlayback(mc, 0, false);
        }
    }

    /** Источник для клавиши запуска: запись или маршрут по точкам. null — играть нечего. */
    private PlaybackSource selectSource() {
        if (sourceKind == SourceKind.ROUTE) {
            return com.farmmacro.route.RouteBuffer.INSTANCE.isEmpty() ? null : com.farmmacro.route.RouteWalker.INSTANCE;
        }
        return frames.isEmpty() ? null : recordingSource;
    }

    public void resumeFromSaved(Minecraft mc) {
        if (state != State.IDLE)  { msg(mc, "§cСначала останови запись/воспроизведение"); return; }
        if (!hasSavedPosition)    { msg(mc, "§7Нет сохранённой точки остановки"); return; }
        if (savedKind != sourceKind) { msg(mc, "§7Точка остановки от другого макроса/маршрута"); return; }
        int back = sourceKind == SourceKind.RECORDING ? RESUME_ROLLBACK : 0;
        startPlayback(mc, Math.max(0, savedIndex - back), true);
    }

    private void startPlayback(Minecraft mc, int index, boolean resume) {
        if (state == State.RECORDING) { msg(mc, "§cСначала останови запись"); return; }
        PlaybackSource src = selectSource();
        if (src == null) {
            msg(mc, sourceKind == SourceKind.ROUTE
                    ? "§cМаршрут пуст — поставь точки в редакторе (" + keyName(com.farmmacro.FarmMacroMod.keyEditor) + ")"
                    : "§cНет макроса — запиши (" + keyName(com.farmmacro.FarmMacroMod.keyRecord) + ") или загрузи в меню");
            return;
        }
        if (mc.player == null) return;
        if (src != recordingSource) {
            String dim = com.farmmacro.route.RouteBuffer.INSTANCE.route().dimension;
            if (dim != null && !dim.equals(com.farmmacro.route.RouteBuffer.dimensionId(mc))) {
                msg(mc, "§cМаршрут построен в другом измерении (" + dim + ")");
                return;
            }
        }
        ModConfig c = ModConfig.INSTANCE;
        index = Math.min(index, src.length() - 1);

        double[] at = src.position(index);
        double dist = resume ? distanceTo(mc.player, savedX, savedY, savedZ) : distanceTo(mc.player, at[0], at[1], at[2]);
        String where = resume ? "точки остановки" : "точки старта";
        if (dist > c.startPointWarnDistance) {
            if (c.requireStartPoint) {
                msg(mc, String.format(Locale.ROOT, "§cДо %s %.1f бл — подойди ближе (стрелка в HUD)", where, dist));
                return;
            }
            msg(mc, String.format(Locale.ROOT, "§6Внимание: до %s %.1f бл", where, dist));
        }

        PanicDetector.INSTANCE.silence();
        com.farmmacro.route.RouteEditor.setActive(mc, false);
        navDismissed = false;
        if (resume) clearSavedPosition();
        pendingStartIndex = index;
        pendingSource = src;
        if (c.startCountdownSeconds > 0) {
            state = State.COUNTDOWN;
            countdownTicks = c.startCountdownSeconds * 20;
        } else {
            beginPlaying(mc);
        }
    }

    private void beginPlaying(Minecraft mc) {
        state = State.PLAYING;
        source = pendingSource;
        loopsDone = 0;
        runStartMs = System.currentTimeMillis();
        if (sessionStartMs == 0) sessionStartMs = runStartMs;
        MacroSettings ms = source == recordingSource ? settings : com.farmmacro.route.RouteBuffer.INSTANCE.settings();
        Humanizer human = ModConfig.INSTANCE.humanMaster ? Humanizer.create(ModConfig.INSTANCE.humanUseGlobal ? ModConfig.INSTANCE.humanGlobal : ms.human, runStartMs ^ System.nanoTime()) : null;
        Humanizer.ACTIVE = human;
        MouseHold.INSTANCE.begin(human != null ? human.shapeHold(ms.hold) : ms.hold, source != recordingSource);
        if (human != null) LOGGER.info("Случайность: сид {}", human.seed);
        PanicDetector.INSTANCE.beginRun();
        lastMidStops = 0; lastAfks = 0;
        com.farmmacro.panic.EventLog.log("START", (source == recordingSource ? "запись " + (loadedName != null ? loadedName : "(буфер)")
                : "маршрут " + com.farmmacro.route.RouteBuffer.INSTANCE.route().name)
                + ", с " + (pendingStartIndex + 1) + (human != null ? ", случайность, сид " + human.seed : ", без случайности"));
        startPass(mc, pendingStartIndex);
        if (ModConfig.INSTANCE.mouseLockOnStart) com.farmmacro.camera.MouseLock.lock(mc, "старт макроса");
        LOGGER.info("Старт ({}), пороги: {}", source == recordingSource ? "запись" : "маршрут",
                ModConfig.INSTANCE.describeThresholds());
        String what = source == recordingSource ? (pendingStartIndex > 0 ? " с кадра " + pendingStartIndex : "")
                : " маршрута" + (pendingStartIndex > 0 ? " с точки " + (pendingStartIndex + 1) : "");
        msg(mc, "§a▶ Воспроизведение" + what + (human != null ? " §d· случайность" : "")
                + " §7(" + keyName(com.farmmacro.FarmMacroMod.keyPlay) + " — стоп)");
    }

    private void startPass(Minecraft mc, int index) {
        sessionRuns++;
        Humanizer human = Humanizer.ACTIVE;
        if (human != null) {
            boolean perfect = human.beginLap();
            LOGGER.debug("Круг {}: {}", human.laps, perfect ? "идеальный" : "со случайностью");
            com.farmmacro.panic.EventLog.log("LAP", "круг " + (loopsDone + 1) + ": " + (perfect ? "идеальный" : "со случайностью")
                    + " (всего: идеальных " + human.perfectLaps + ", встал " + human.midStops + ", протупил " + human.afks + ")");
        } else {
            com.farmmacro.panic.EventLog.log("LAP", "круг " + (loopsDone + 1) + ": без случайности");
        }
        MouseHold.INSTANCE.suspend(false);
        source.startPass(mc, index);
        PanicDetector.INSTANCE.snapshot(mc);
    }

    public void tickPlayback(Minecraft mc) {
        if (state == State.COUNTDOWN) { tickCountdown(mc); return; }
        if (state != State.PLAYING) return;
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {          // вышли из мира — без следов
            Humanizer.ACTIVE = null;
            state = State.IDLE;
            if (source != null) source.stop(mc);
            source = null;
            releaseAll(mc);
            clearTransient();
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

        // ── конец круга ──
        if (source.passDone()) {
            loopsDone++;
            if (!c.loopEnabled) { finish(mc, source == recordingSource ? "§a✔ Макрос завершён" : "§a✔ Маршрут пройден", false); return; }
            if (c.loopLimit > 0 && loopsDone >= c.loopLimit) {
                finish(mc, "§a✔ Пройдено кругов: " + loopsDone, false);
                return;
            }
            startPass(mc, 0);
        }

        MouseHold.INSTANCE.tickRun();
        source.tick(mc, c);          // паника из источника сама остановит макрос
        Humanizer h = Humanizer.ACTIVE;
        if (h != null) {
            if (h.midStops != lastMidStops) { lastMidStops = h.midStops; com.farmmacro.panic.EventLog.log("HUMAN", "встал посреди пути"); }
            if (h.afks != lastAfks) { lastAfks = h.afks; com.farmmacro.panic.EventLog.log("HUMAN", "протупить (долгая остановка)"); }
        }
    }

    /** Счётчики остановок «Случайности», уже записанные в журнал. */
    private int lastMidStops, lastAfks;

    /** Запись по кадрам: каждый тик — очередной кадр (логика 1.0–1.4 без изменений). */
    private final class RecordingSource implements PlaybackSource {
        public int length() { return frames.size(); }

        public double[] position(int index) {
            MacroFrame f = frames.get(Math.max(0, Math.min(frames.size() - 1, index)));
            return new double[]{f.x, f.y, f.z};
        }

        /** «Случайность» прохода записи (null — выкл). */
        private Humanizer.RecShaper rs;

        public void startPass(Minecraft mc, int index) {
            Humanizer h = Humanizer.lap();                    // null — выкл или идеальный круг (точно как запись)
            rs = h != null ? h.recShaper(frames.size()) : null;
            playbackIndex = index;
            passStartIndex = index;
            camTracker.reset();
            camTracker.update(mc, settings.camera, index, "старт, кадр " + (index + 1));
            LocalPlayer p = mc.player;
            MacroFrame f = frames.get(index);
            offX = p.getX() - f.x; offY = p.getY() - f.y; offZ = p.getZ() - f.z;
            if (actualX == null || actualX.length != frames.size()) {
                actualX = new double[frames.size()];
                actualZ = new double[frames.size()];
            }
        }

        public boolean passDone() { return playbackIndex >= frames.size(); }

        public int progress() { return playbackIndex; }

        public int resumeIndex() { return Math.max(0, Math.min(playbackIndex - 1, frames.size() - 1)); }

        /** Камеру крутит запись («Повторять камеру»), только если не выбран пресет камеры. */
        public boolean controlsCamera(ModConfig c) { return c.replayCamera && !settings.camera.active(); }

        public boolean tick(Minecraft mc, ModConfig c) {
            LocalPlayer p = mc.player;
            // ── застрял / сошёл с маршрута ──
            actualX[playbackIndex] = p.getX();
            actualZ[playbackIndex] = p.getZ();
            if (c.panicEnabled && checkRouteProblems(mc, p, c)) return true;

            // ── применяем кадр ──
            MacroFrame f = frames.get(playbackIndex);
            p.getInventory().setSelectedSlot(f.selectedSlot);
            PanicDetector.INSTANCE.expectSlot(f.selectedSlot);
            if (settings.camera.active()) {
                camTracker.update(mc, settings.camera, playbackIndex, "кадр " + (playbackIndex + 1));
            } else if (c.replayCamera) {
                float[] nz = rs != null ? rs.camNoise() : new float[]{0, 0};
                float yaw = f.yaw + nz[0], pitch = Math.max(-90f, Math.min(90f, f.pitch + nz[1]));
                p.setYRot(yaw);
                p.setXRot(pitch);
                PanicDetector.INSTANCE.expectRotation(yaw, pitch);
            }
            boolean stop = rs != null && rs.stopping();
            Humanizer hh = Humanizer.ACTIVE;
            if (hh != null) {
                hh.stopLeftTicks = stop ? rs.stopLeft() : 0;
                // «оглядеться»/шум pitch — только если камеру не крутит сама запись
                if (!(c.replayCamera && !settings.camera.active()))
                    com.farmmacro.camera.SmoothTurn.look(hh, p, stop ? rs.stopLeft() : 0);
            }
            MouseHold.INSTANCE.suspend(stop);
            MouseHold.INSTANCE.position(playbackIndex, false);
            Options o = mc.options;
            press(o.keyUp, f.forward);
            press(o.keyDown, f.back);
            press(o.keyLeft, f.left);
            press(o.keyRight, f.right);
            press(o.keyJump, f.jump);
            press(o.keyShift, f.sneak);
            press(o.keySprint, f.sprint);
            press(o.keyAttack, rs != null ? rs.attack(f.attackPressed && !stop) : f.attackPressed);
            press(o.keyUse, rs != null ? rs.use(f.usePressed && !stop) : f.usePressed);

            if (rs == null) { playbackIndex++; return false; }
            int step = rs.advance(idle(playbackIndex), idle(playbackIndex + 1), playbackIndex);
            if (step == 2 && playbackIndex + 1 < frames.size()) {   // пропущенный кадр — та же позиция для «застрял»
                actualX[playbackIndex + 1] = p.getX();
                actualZ[playbackIndex + 1] = p.getZ();
            }
            playbackIndex = Math.min(frames.size(), playbackIndex + step);
            return false;
        }

        /** Стоячий кадр: позиция как у предыдущего (до 0.005 бл), клавиш движения и прыжка нет. */
        private boolean idle(int i) {
            if (i <= 0 || i >= frames.size()) return false;
            MacroFrame f = frames.get(i), q = frames.get(i - 1);
            return !f.forward && !f.back && !f.left && !f.right && !f.jump
                    && Math.abs(f.x - q.x) < 0.005 && Math.abs(f.y - q.y) < 0.005 && Math.abs(f.z - q.z) < 0.005;
        }
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
        if (mc.player == null) { state = State.IDLE; releaseAll(mc); return; }
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
        com.farmmacro.panic.EventLog.log("STOP", stripCodes(message) + " (кругов " + loopsDone + ")");
        stopInternal(mc, keepResume);
        msg(mc, message);
        if (wasPlaying && ModConfig.INSTANCE.finishSoundEnabled) PanicSound.playDone();
    }

    /** Остановка (вручную или паникой). Точка остановки запоминается для «возобновить». */
    public void stopPlayback(Minecraft mc, String message) {
        if (state != State.PLAYING && state != State.COUNTDOWN) return;
        com.farmmacro.panic.EventLog.log("STOP", stripCodes(message) + " (кругов " + loopsDone + ")");
        stopInternal(mc, true);
        msg(mc, message);
    }

    /** Аварийная остановка без сообщений и точки возобновления (если обычная остановка упала). */
    private static String stripCodes(String s) { return s == null ? "" : s.replaceAll("§.", ""); }

    public void forceStop(Minecraft mc) {
        com.farmmacro.panic.EventLog.log("STOP", "аварийная остановка");
        Humanizer.ACTIVE = null;
        state = State.IDLE;
        playbackIndex = 0;
        source = null;
        releaseAll(mc);
        com.farmmacro.util.Guard.run("transient/clear", MacroManager::clearTransient);
    }

    /**
     * Убрать всё временное: выбранную/наведённую точку, выделение и превью «змейки» (если редактор закрыт).
     * Вызывается при стопе, панике, аварийной остановке, End, выходе из мира.
     */
    public static void clearTransient() {
        com.farmmacro.visual.RouteRenderer.editorHover = -1;
        if (!com.farmmacro.route.RouteEditor.isActive()) {
            com.farmmacro.route.SnakeTool.reset();
            if (com.farmmacro.route.RouteBuffer.INSTANCE.selected() >= 0) com.farmmacro.route.RouteBuffer.INSTANCE.select(-1);
        }
    }

    /** End: остановить (без точки возобновления), сбросить точку остановки, зажим мыши, всё временное и навигатор. */
    public void resetAll(Minecraft mc) {
        boolean wasActive = isActive();
        if (wasActive) stopInternal(mc, false);
        boolean held = MouseHold.INSTANCE.isManual() || MouseHold.INSTANCE.running() != null;
        boolean wasLocked = com.farmmacro.camera.MouseLock.isLocked();
        if (wasLocked) com.farmmacro.util.Guard.run("reset/unlock-mouse", () -> com.farmmacro.camera.MouseLock.unlock(mc, "End"));
        clearSavedPosition();
        MouseHold.INSTANCE.stopAll(mc);
        clearTransient();
        navDismissed = true;
        msg(mc, (wasActive ? "§e■ Стоп и сброс" : "§7Сброшено") + "§7: точка остановки, указатели"
                + (held ? ", зажим мыши" : "") + (wasLocked ? ", блокировка мыши" : "") + " · навигатор вернётся при запуске или загрузке");
    }

    /** Навигатор (HUD) скрыт клавишей End до следующего запуска/загрузки/записи. */
    private boolean navDismissed;
    public boolean isNavDismissed() { return navDismissed; }
    public void showNav() { navDismissed = false; }

    /** Точка остановки маршрута не относится к новому маршруту (загрузили другой / очистили). */
    public void routeUnloaded() {
        if (savedKind == SourceKind.ROUTE) clearSavedPosition();
        clearTransient();
    }

    private void stopInternal(Minecraft mc, boolean keepResume) {
        if (keepResume && state == State.PLAYING && source != null && mc.player != null
                && (source != recordingSource || playbackIndex > 0)) {
            savedIndex = source.resumeIndex();
            savedKind = source == recordingSource ? SourceKind.RECORDING : SourceKind.ROUTE;
            savedX = mc.player.getX();
            savedY = mc.player.getY();
            savedZ = mc.player.getZ();
            hasSavedPosition = true;
            navDismissed = false;
        }
        if (source != null) source.stop(mc);
        source = null;
        Humanizer.ACTIVE = null;
        state = State.IDLE;
        playbackIndex = 0;
        releaseAll(mc);
        clearTransient();
        if (ModConfig.INSTANCE.mouseUnlockOnStop)
            com.farmmacro.util.Guard.run("stop/unlock-mouse", () -> com.farmmacro.camera.MouseLock.unlock(mc, "стоп макроса"));
    }

    // ── Клавиши ──────────────────────────────────────────────────────────────

    /** Нажать/отпустить клавишу от источника. ЛКМ/ПКМ остаются зажатыми, если их держит «Зажим мыши». */
    public static void press(KeyMapping key, boolean down) {
        // жмём ту клавишу, на которую игрок реально назначил действие
        KeyMapping.set(KeyMappingHelper.getBoundKeyOf(key), down || MouseHold.INSTANCE.wants(Minecraft.getInstance(), key));
    }

    /** Отпустить всё — без учёта «Зажима мыши» (его выключают до этого). */
    private static void releaseAll(Minecraft mc) {
        MouseHold.INSTANCE.stopAll(mc);
        Options o = mc.options;
        if (o == null) return;
        for (KeyMapping k : new KeyMapping[]{o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump,
                o.keyShift, o.keySprint, o.keyAttack, o.keyUse}) KeyMapping.set(KeyMappingHelper.getBoundKeyOf(k), false);
    }

    // ── Геттеры для GUI/HUD ──────────────────────────────────────────────────

    public State   getState()          { return state; }
    public boolean isRecording()       { return state == State.RECORDING; }
    public boolean isPlaying()         { return state == State.PLAYING; }
    public boolean isCountingDown()    { return state == State.COUNTDOWN; }
    public boolean isActive()          { return state == State.PLAYING || state == State.COUNTDOWN; }
    /** Макрос сам крутит камеру — пресеты камеры в это время не применяются. */
    public boolean controlsCamera()    { return state == State.PLAYING && source != null && source.controlsCamera(ModConfig.INSTANCE); }
    /** Играет (или отсчитывает) маршрут по точкам. */
    public boolean isRoutePlaying()    { return isActive() && (state == State.PLAYING ? source : pendingSource) == com.farmmacro.route.RouteWalker.INSTANCE; }
    /** Прогресс: текущий кадр или точка (1-based для точек не применяется). */
    public int     getProgress()       { return state == State.PLAYING && source != null ? source.progress() : 0; }
    public int     getProgressTotal()  { return state == State.PLAYING && source != null ? source.length() : getFrameCount(); }
    public int     getCountdownTicks() { return countdownTicks; }
    public int     getFrameCount()     { return frames.size(); }
    public int     getPlaybackIndex()  { return playbackIndex; }
    /** Прогресс маршрута по точкам в «кадрах» RoutePath (точка × 1000 + доля отрезка), −1 — не играет маршрут. */
    public int     getRouteProgressFrame() {
        return state == State.PLAYING && source == com.farmmacro.route.RouteWalker.INSTANCE
                ? com.farmmacro.route.RouteWalker.INSTANCE.progressFrame() : -1;
    }
    public int     getLoopsDone()      { return loopsDone; }
    public long    getRunStartMs()     { return runStartMs; }
    public String  getLoadedName()     { return loadedName; }
    public List<MacroFrame> getFrames() { return Collections.unmodifiableList(frames); }

    /** Сдвиг «реальная позиция − запись» на начало текущего круга (для рисования маршрута от точки запуска). */
    public double getOffsetX() { return offX; }
    public double getOffsetY() { return offY; }
    public double getOffsetZ() { return offZ; }

    public long getSessionStartMs() { return sessionStartMs; }
    public int  getSessionRuns()    { return sessionRuns; }
    public void resetStats()        { sessionStartMs = 0; sessionRuns = 0; }

    public boolean hasSavedPosition()   { return hasSavedPosition; }
    public double  getSavedX()          { return savedX; }
    public double  getSavedY()          { return savedY; }
    public double  getSavedZ()          { return savedZ; }
    public void    clearSavedPosition() { hasSavedPosition = false; savedIndex = -1; savedKind = null; }
    /** Точка остановки относится к тому, что сейчас выбрано для запуска. */
    public boolean savedMatchesSource() { return hasSavedPosition && savedKind == sourceKind; }

    /** Позиция первого кадра (точка старта) или null. */
    public MacroFrame getStartFrame() { return frames.isEmpty() ? null : frames.get(0); }

    /** Точка старта того, что запустит клавиша (кадр 0 или точка 1), или null. */
    public double[] getStartPosition() {
        PlaybackSource s = selectSource();
        return s == null ? null : s.position(0);
    }

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
