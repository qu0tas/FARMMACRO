package com.farmmacro.panic;

import com.farmmacro.config.ModConfig;
import com.farmmacro.gui.FarmMacroScreen;
import com.farmmacro.gui.SaveMacroScreen;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.util.Guard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Детекторы паники. Паника = аварийный стоп макроса + красный экран + звук. Больше ничего.
 *
 * Как устроено:
 *  • События от сервера (телепорт/поворот, блок рядом, эффекты) приходят из {@code PanicPacketMixin}
 *    уже в главном потоке и складываются в {@link #pendingReason}.
 *  • Открытие чужого GUI приходит из {@code PanicGuiMixin}.
 *  • {@link #tick} (каждый клиентский тик, пока макрос играет) проверяет мышь, слот, урон
 *    и, если есть причина, вызывает {@link #triggerPanic}.
 *  • Застревание и сход с маршрута проверяет MacroManager (ему нужна запись) и тоже зовёт triggerPanic.
 */
public class PanicDetector {

    public static final PanicDetector INSTANCE = new PanicDetector();
    private static final Logger LOGGER = LoggerFactory.getLogger("FarmMacro/Panic");

    private float prevYaw, prevPitch;
    private float expectedHealth;
    private int   expectedSlot;

    /** Первая причина паники, пришедшая из пакета/GUI за этот тик. Только главный поток. */
    private String pendingReason;

    // захват состояния перед обработкой пакета движения
    private boolean moveCaptured;
    private double  mvX, mvY, mvZ;
    private float   mvYaw, mvPitch;

    private int redScreenTicksLeft;
    private int soundRepeatsLeft;
    private int soundRepeatDelay;

    private String lastReason;
    private long   lastPanicMs;
    private String overlayText;

    // ── Состояние ────────────────────────────────────────────────────────────

    private static boolean armed() {
        return MacroManager.INSTANCE.isPlaying() && ModConfig.INSTANCE.panicEnabled;
    }

    private void flag(String reason) {
        if (pendingReason == null) {
            pendingReason = reason;
            LOGGER.warn("[Флаг] {}", reason);
        }
    }

    /** Запомнить исходное состояние при старте/новом круге макроса. */
    public void snapshot(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        prevYaw        = p.getYRot();
        prevPitch      = p.getXRot();
        expectedHealth = p.getHealth();
        expectedSlot   = p.getInventory().getSelectedSlot();
        pendingReason  = null;
        moveCaptured   = false;
    }

    /** Макрос сам поставил этот слот — это не «внешняя» смена. */
    public void expectSlot(int slot) { expectedSlot = slot; }

    /** Макрос сам повернул камеру (replayCamera) — это не «чужой» поворот. */
    public void expectRotation(float yaw, float pitch) { prevYaw = yaw; prevPitch = pitch; }

    /**
     * Макрос сам довернул камеру на (dYaw, dPitch) — плавный поворот (пресет, автоход).
     * Учитывается как приращение, а не абсолютное значение: мышь, сдвинутая в тот же кадр, всё равно заметна.
     */
    public void expectTurn(float dYaw, float dPitch) { prevYaw += dYaw; prevPitch += dPitch; }

    // ── Хуки из миксинов (главный поток) ─────────────────────────────────────

    public void onScreenOpening(Screen screen) {
        if (!armed() || !ModConfig.INSTANCE.detectGuiOpen || screen == null) return;
        if (screen instanceof PauseScreen || screen instanceof ChatScreen
                || screen instanceof FarmMacroScreen || screen instanceof SaveMacroScreen
                || screen instanceof com.farmmacro.gui.PointEditScreen
                || screen instanceof com.farmmacro.gui.MacroSettingsScreen) return;
        flag("Открылось окно: " + screen.getTitle().getString()
                + " (" + screen.getClass().getSimpleName() + ")");
    }

    public void beforeServerMove() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !armed()) { moveCaptured = false; return; }
        mvX = p.getX(); mvY = p.getY(); mvZ = p.getZ();
        mvYaw = p.getYRot(); mvPitch = p.getXRot();
        moveCaptured = true;
    }

    public void afterServerMove() {
        if (!moveCaptured) return;
        moveCaptured = false;
        LocalPlayer p = Minecraft.getInstance().player;
        ModConfig c = ModConfig.INSTANCE;
        if (p == null || !armed()) return;
        if (!c.detectServerMove) { updatePrev(p); return; }
        double dx = p.getX() - mvX, dy = p.getY() - mvY, dz = p.getZ() - mvZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float rot = Math.max(angleDiff(p.getYRot(), mvYaw), Math.abs(p.getXRot() - mvPitch));
        if (dist >= c.serverMoveThreshold) {
            flag(String.format(Locale.ROOT, "Сервер телепортировал (%.1f бл)", dist));
        } else if (rot >= c.serverRotateThreshold) {
            flag(String.format(Locale.ROOT, "Сервер повернул камеру (%.3f° ≥ %.3f°)", rot, c.serverRotateThreshold));
        }
        // Поворот от сервера — не движение мыши: детектор мыши сравнивает с уже повёрнутой камерой,
        // иначе мелкий серверный поворот (ниже порога сервера) засчитывался бы как «Камера повернулась».
        updatePrev(p);
    }

    /** Блок из пакета сервера. Вызывается ДО применения, поэтому в мире ещё старое состояние. */
    public void onServerBlockChange(BlockPos pos, BlockState newState) {
        if (!armed() || !ModConfig.INSTANCE.detectBlockInFace) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null) return;

        // Свои постановки блоков клиент уже предсказал — там старое состояние уже твёрдое.
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return;
        VoxelShape shape = newState.getCollisionShape(level, pos);
        if (shape.isEmpty()) return;

        AABB zone = p.getBoundingBox().inflate(ModConfig.INSTANCE.blockDetectRadius);
        for (AABB box : shape.toAabbs()) {
            if (box.move(pos).intersects(zone)) {
                flag("Рядом появился блок: " + newState.getBlock().getName().getString()
                        + " " + pos.toShortString());
                return;
            }
        }
    }

    public void onEffectAdded(int entityId, Holder<MobEffect> effect, int amplifier) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || entityId != p.getId() || !armed() || !ModConfig.INSTANCE.detectPotionEffect) return;
        MobEffectInstance cur = p.getEffect(effect);
        if (cur != null && cur.getAmplifier() == amplifier) return; // продление (маяк/проводник) — норма
        flag("Эффект: " + effect.value().getDisplayName().getString() + " " + (amplifier + 1));
    }

    public void onEffectRemoved(int entityId, Holder<MobEffect> effect) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || entityId != p.getId() || !armed() || !ModConfig.INSTANCE.detectPotionEffect) return;
        MobEffectInstance cur = p.getEffect(effect);
        if (cur == null) return;
        // Естественное окончание — норма; снятие задолго до конца (молоко, /effect clear) — нет.
        if (cur.isInfiniteDuration() || cur.getDuration() > 40) {
            flag("Сняли эффект: " + effect.value().getDisplayName().getString());
        }
    }

    // ── Тик ──────────────────────────────────────────────────────────────────

    /** Вызывается каждый тик, пока макрос играет, ДО применения очередного кадра. */
    public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        ModConfig c = ModConfig.INSTANCE;

        String reason = pendingReason;
        pendingReason = null;

        if (!c.panicEnabled) { updatePrev(p); return; }

        if (reason == null && c.detectRotation) {
            float dy = angleDiff(p.getYRot(), prevYaw);
            float dp = Math.abs(p.getXRot() - prevPitch);
            if (dy > c.yawThreshold)
                reason = String.format(Locale.ROOT, "Камера повернулась (yaw %.3f° > %.3f°)", dy, c.yawThreshold);
            else if (dp > c.pitchThreshold)
                reason = String.format(Locale.ROOT, "Камера повернулась (pitch %.3f° > %.3f°)", dp, c.pitchThreshold);
        }

        if (reason == null && c.detectSlotChange) {
            int real = p.getInventory().getSelectedSlot();
            if (real != expectedSlot) reason = "Слот сменился: " + (expectedSlot + 1) + " → " + (real + 1);
        }

        float hp = p.getHealth();
        if (reason == null && c.detectDamage && hp < expectedHealth - 0.01f) {
            reason = String.format(Locale.ROOT, "Получен урон (−%.1f ❤)", (expectedHealth - hp) / 2f);
        }
        expectedHealth = hp;

        if (reason != null) triggerPanic(mc, reason);
        else updatePrev(p);
    }

    /** Красный экран и повторы звука — каждый тик, независимо от макроса. */
    public void tickEffects() {
        if (redScreenTicksLeft > 0) redScreenTicksLeft--;
        if (soundRepeatsLeft > 0) {
            if (soundRepeatDelay > 0) soundRepeatDelay--;
            else {
                soundRepeatsLeft--;
                Guard.run("panic/repeat-sound", PanicSound::playPanic);
                soundRepeatDelay = ModConfig.INSTANCE.panicSoundRepeatDelayTicks;
            }
        }
    }

    /**
     * Аварийный стоп. Только останавливает макрос и подаёт сигнал.
     * Каждый шаг защищён отдельно: ошибка в логе/звуке/экране не мешает остановке и не роняет игру.
     */
    public void triggerPanic(Minecraft mc, String reason) {
        ModConfig c = ModConfig.INSTANCE;
        Guard.run("panic/log", () -> {
            LocalPlayer p = mc.player;
            LOGGER.error("[ПАНИКА] {} | pos={} yaw={} pitch={} hp={}", reason,
                    p != null ? String.format(Locale.ROOT, "%.2f %.2f %.2f", p.getX(), p.getY(), p.getZ()) : "?",
                    p != null ? String.format(Locale.ROOT, "%.1f", p.getYRot()) : "?",
                    p != null ? String.format(Locale.ROOT, "%.1f", p.getXRot()) : "?",
                    p != null ? String.format(Locale.ROOT, "%.1f", p.getHealth()) : "?");
        });

        lastReason  = reason;
        overlayText = reason;
        lastPanicMs = System.currentTimeMillis();
        pendingReason = null;

        // 1. Остановка — главное. Если обычная остановка упала, отпускаем клавиши аварийно.
        if (!Guard.run("panic/stop", () -> MacroManager.INSTANCE.stopPlayback(mc, "§c⚠ Паника: " + reason)))
            Guard.run("panic/force-stop", () -> MacroManager.INSTANCE.forceStop(mc));

        // 2. Звук: только постановка в очередь, без декодирования и файлов.
        if (c.panicSoundEnabled) {
            soundRepeatsLeft = Math.max(0, c.panicSoundRepeats - 1);
            soundRepeatDelay = c.panicSoundRepeatDelayTicks;
            Guard.run("panic/sound", PanicSound::playPanic);
        }
        // 3. Красный экран.
        if (c.panicRedScreenEnabled) redScreenTicksLeft = c.panicRedScreenTicks;
    }

    /** Проверка из меню: те же звук и красный экран, но без остановки и без записи в «последнюю панику». */
    public void preview(Minecraft mc) {
        ModConfig c = ModConfig.INSTANCE;
        overlayText = "Проверка — так выглядит паника";
        if (c.panicSoundEnabled) {
            soundRepeatsLeft = Math.max(0, c.panicSoundRepeats - 1);
            soundRepeatDelay = c.panicSoundRepeatDelayTicks;
            Guard.run("panic/sound", PanicSound::playPanic);
        }
        redScreenTicksLeft = c.panicRedScreenEnabled ? c.panicRedScreenTicks : 0;
    }

    /** Заглушить повторы сирены (кнопка в GUI / новый старт макроса). */
    public void silence() { soundRepeatsLeft = 0; }

    public int     getRedScreenTicks() { return redScreenTicksLeft; }
    public String  getLastReason()     { return lastReason; }
    public String  getOverlayText()    { return overlayText; }
    public long    getLastPanicMs()    { return lastPanicMs; }

    private void updatePrev(LocalPlayer p) {
        prevYaw   = p.getYRot();
        prevPitch = p.getXRot();
    }

    private static float angleDiff(float a, float b) {
        float d = Math.abs(a - b) % 360f;
        return d > 180f ? 360f - d : d;
    }
}
