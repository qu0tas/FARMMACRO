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

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

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
 *  • v1.10 — то, что видно только по пакетам сервера: любой пакет телепорта (даже без сдвига), взгляд (look at),
 *    толчок (скорость/взрыв), чат с ником или словом, титры, режим игры/полёт/респавн/посадка, игроки рядом,
 *    наблюдатель и вход/выход в Tab, слот от сервера, событие урона, предмет в руке сменился сам.
 */
public class PanicDetector {

    public static final PanicDetector INSTANCE = new PanicDetector();
    private static final Logger LOGGER = LoggerFactory.getLogger("FarmMacro/Panic");

    private float prevYaw, prevPitch;
    private float expectedHealth;
    private int   expectedSlot;

    /** Первая причина паники, пришедшая из пакета/GUI за этот тик. Только главный поток. */
    private volatile String pendingReason;

    // захват состояния перед обработкой пакета движения
    private boolean moveCaptured;
    private double  mvX, mvY, mvZ;
    private float   mvYaw, mvPitch;
    private String  mvKind = "телепорта";

    // игроки рядом (null — ещё не запомнили, кто был рядом на старте), предмет в руке, падение со спуска
    private Set<UUID> nearSeen;
    private net.minecraft.world.item.Item heldItem;
    private int     heldSlot = -1;
    private boolean heldStackable;
    private int     fallGraceTicks;

    /** v1.11: тиков после пакета толчка/телепорта, когда «Аномалия скорости» не считается (скорость задал сервер). */
    private int pushGraceTicks;
    /** v1.11: твёрдые блоки, поставленные сервером на месте проходимых, ждут проверки лучом (pos → кандидат). */
    private final java.util.Map<Long, Obstacle> obstacles = new java.util.HashMap<>();
    private record Obstacle(String name, int[] ttl) {}

    /** v1.11 «Подозрительность» (главный поток; lastScore — volatile для HUD). */
    public final Suspicion suspicion = new Suspicion();
    /** Игроки, уже засчитанные «за радиусом» в этом запуске (null — ещё не запомнили стартовых). */
    private Set<UUID> farSeen;
    /** Надписи над хотбаром (цифры → #), уже засчитанные в этом запуске. */
    private final java.util.LinkedHashSet<String> actionSeen = new java.util.LinkedHashSet<>();

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
        EventLog.log("FLAG", reason);
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
        heldSlot       = -1;
        obstacles.clear();
    }

    /** Старт запуска (не круга): кто уже рядом — не паника, паника — только кто подойдёт. */
    public void beginRun() {
        nearSeen = null; fallGraceTicks = 0;
        farSeen = null; actionSeen.clear(); suspicion.reset();
    }

    /** v1.11: мелкое событие ниже порогов → очки подозрительности (и строка журнала). */
    private void suspect(String type, String detail, double weight) {
        ModConfig c = ModConfig.INSTANCE;
        if (!armed() || !c.suspicionEnabled || !(weight > 0)) return;
        double s = suspicion.add(type, detail, weight, System.currentTimeMillis(), c.suspicionHalfLifeSec);
        EventLog.log("SUSP", String.format(Locale.ROOT, "%s (%s) +%.1f → %.1f/%.0f", type, detail, weight, s, c.suspicionLimit));
    }

    /** Сумма очков подозрительности (для HUD; считается в tick). */
    public double suspicionScore() { return suspicion.lastScore; }

    /** Автоход в спуске: урон от падения ещё N тиков не паника (падение — часть маршрута). */
    public void allowFall(int ticks) { fallGraceTicks = Math.max(fallGraceTicks, ticks); }

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

    public void beforeServerMove() { beforeServerMove("телепорта"); }

    public void beforeServerMove(String kind) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !armed()) { moveCaptured = false; return; }
        mvKind = kind;
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
        markServerPush();                                     // позицию задал сервер — не «аномалия скорости»
        if (!c.detectServerMove) { updatePrev(p); return; }
        double dx = p.getX() - mvX, dy = p.getY() - mvY, dz = p.getZ() - mvZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float dYaw = angleDiff(p.getYRot(), mvYaw), dPitch = Math.abs(p.getXRot() - mvPitch);
        float rot = Math.max(dYaw, dPitch);
        boolean syncOver = SyncMath.posOver(dist, c.serverPosEpsilon) || SyncMath.rotOver(rot, c.serverRotEpsilon);
        if (dist >= c.serverMoveThreshold) {
            flag(String.format(Locale.ROOT, "Сервер телепортировал (%.1f бл)", dist));
        } else if (rot >= c.serverRotateThreshold) {
            flag(String.format(Locale.ROOT, "Сервер повернул камеру (%.3f° ≥ %.3f°)", rot, c.serverRotateThreshold));
        } else if (c.detectServerSync && syncOver) {
            flag(SyncMath.syncReason(mvKind, dist, c.serverPosEpsilon, dYaw, dPitch, c.serverRotEpsilon));
        } else if (c.serverMoveAny) {
            flag(String.format(Locale.ROOT, "Пакет %s от сервера (сдвиг %.3f бл, поворот %.3f°)", mvKind, dist, rot));
        } else if (syncOver) {
            // «Синхронизация» выключена: сдвиг больше эпсилона, но меньше порогов — похоже на лаг-откат
            String d = String.format(Locale.ROOT, "%.3f бл, %.3f°", dist, rot);
            EventLog.log("SYNC", "откат: пакет " + mvKind + " " + d);
            suspect("лаг-откат", d, c.suspWeightRollback);
        } else {
            String d = String.format(Locale.ROOT, "%.4f бл, yaw %.4f° pitch %.4f°", dist, dYaw, dPitch);
            LOGGER.info("[Синхр] пакет {} ниже эпсилона: {} (≤ {} бл / {}°)", mvKind, d, c.serverPosEpsilon, c.serverRotEpsilon);
            EventLog.log("SYNC", "ниже эпсилона: пакет " + mvKind + " " + d);
            suspect("пакет " + mvKind + " ниже эпсилона", String.format(Locale.ROOT, "%.3f бл", dist), c.suspWeightSync);
        }
        // Поворот от сервера — не движение мыши: детектор мыши сравнивает с уже повёрнутой камерой,
        // иначе мелкий серверный поворот (ниже порога сервера) засчитывался бы как «Камера повернулась».
        updatePrev(p);
    }

    /** Блок из пакета сервера. Вызывается ДО применения, поэтому в мире ещё старое состояние. */
    public void onServerBlockChange(BlockPos pos, BlockState newState) {
        ModConfig c = ModConfig.INSTANCE;
        if (!armed() || (!c.detectBlockInFace && !c.detectObstacle)) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null) return;

        // Свои постановки блоков клиент уже предсказал — там старое состояние уже твёрдое.
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return;
        VoxelShape shape = newState.getCollisionShape(level, pos);
        if (shape.isEmpty()) return;

        if (c.detectBlockInFace) {
            AABB zone = p.getBoundingBox().inflate(c.blockDetectRadius);
            for (AABB box : shape.toAabbs()) {
                if (box.move(pos).intersects(zone)) {
                    flag("Рядом появился блок: " + newState.getBlock().getName().getString()
                            + " " + pos.toShortString());
                    return;
                }
            }
        }
        // v1.11: кандидат в «Препятствие впереди» — проверим лучом в tick (рост урожая и поршни — не считаем)
        if (c.detectObstacle && !grownOrMoved(newState) && obstacles.size() < 256)
            obstacles.put(pos.asLong(), new Obstacle(newState.getBlock().getName().getString(), new int[]{3}));
    }

    /** Блоки, которые появляются сами: растут на ферме или их двигает поршень фермы. */
    private static boolean grownOrMoved(BlockState st) {
        var b = st.getBlock();
        return b == net.minecraft.world.level.block.Blocks.PUMPKIN || b == net.minecraft.world.level.block.Blocks.MELON
                || b == net.minecraft.world.level.block.Blocks.CACTUS || b == net.minecraft.world.level.block.Blocks.BAMBOO
                || b == net.minecraft.world.level.block.Blocks.BAMBOO_SAPLING || b == net.minecraft.world.level.block.Blocks.COCOA
                || b == net.minecraft.world.level.block.Blocks.SUGAR_CANE || b == net.minecraft.world.level.block.Blocks.KELP
                || b == net.minecraft.world.level.block.Blocks.CHORUS_PLANT || b == net.minecraft.world.level.block.Blocks.CHORUS_FLOWER
                || b == net.minecraft.world.level.block.Blocks.MOVING_PISTON || b == net.minecraft.world.level.block.Blocks.PISTON_HEAD;
    }

    /** v1.11: «оправдание» для «Аномалии скорости» — сервер сам задал скорость/позицию. */
    private void markServerPush() { pushGraceTicks = Math.max(pushGraceTicks, 4); }

    /** Сервер недавно толкнул/переставил игрока (пакет скорости, взрыва, телепорта) — скорость не наша. */
    public boolean pushExcused() { return pushGraceTicks > 0; }

    /**
     * Луч по взгляду и по ходу автохода: не лежит ли на нём блок, который сервер только что сделал твёрдым.
     * @return причина паники или null
     */
    private String checkObstacles(LocalPlayer p, ClientLevel level, double range) {
        if (obstacles.isEmpty()) return null;
        String[] hit = new String[1];
        var eye = p.getEyePosition();
        var look = p.getViewVector(1f);
        SyncMath.cells(eye.x, eye.y, eye.z, look.x, look.y, look.z, range, (x, y, z, d) -> {
            hit[0] = obstacleAt(level, x, y, z, d, "по взгляду");
            return hit[0] == null;
        });
        double[] mv = com.farmmacro.route.RouteWalker.INSTANCE.moveDir();
        if (hit[0] == null && mv != null) {
            double lx = -mv[1] * 0.3, lz = mv[0] * 0.3;            // ширина хитбокса: центр и ±0.3 вбок
            outer:
            for (double h : new double[]{0.1, 1.0, 1.6})
                for (int side = -1; side <= 1; side++) {
                    SyncMath.cells(p.getX() + side * lx, p.getY() + h, p.getZ() + side * lz, mv[0], 0, mv[1], range, (x, y, z, d) -> {
                        hit[0] = obstacleAt(level, x, y, z, d, "по ходу");
                        return hit[0] == null;
                    });
                    if (hit[0] != null) break outer;
                }
        }
        obstacles.values().removeIf(o -> --o.ttl()[0] <= 0);
        return hit[0];
    }

    private String obstacleAt(ClientLevel level, int x, int y, int z, double d, String how) {
        Obstacle o = obstacles.get(BlockPos.asLong(x, y, z));
        if (o == null) return null;
        BlockPos pos = new BlockPos(x, y, z);
        if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return null;   // уже убрали
        return String.format(Locale.ROOT, "Препятствие впереди (%s): %s в %.1f бл", how, o.name(), d);
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

    // ── v1.10: пакеты, которые видит только клиент (и сервер в логах) ─────────

    private static LocalPlayer player() { return Minecraft.getInstance().player; }

    /** Скорость игрока от сервера: отдача, удочка, плагин «толкнуть». */
    public void onServerMotion(int entityId, net.minecraft.world.phys.Vec3 mv) {
        LocalPlayer p = player();
        if (p == null || entityId != p.getId() || !armed()) return;
        markServerPush();
        if (!ModConfig.INSTANCE.detectKnockback || mv == null) return;
        double v = mv.length();
        if (v >= ModConfig.INSTANCE.knockbackThreshold)
            flag(String.format(Locale.ROOT, "Сервер толкнул (скорость %.2f бл/т)", v));
        else if (v > 0.001)
            suspect("слабый толчок", String.format(Locale.ROOT, "%.3f бл/т", v), ModConfig.INSTANCE.suspWeightKnock);
    }

    public void onExplosion(java.util.Optional<net.minecraft.world.phys.Vec3> knockback) {
        if (!armed() || knockback == null || knockback.isEmpty()) return;
        markServerPush();
        if (!ModConfig.INSTANCE.detectKnockback) return;
        double v = knockback.get().length();
        if (v >= ModConfig.INSTANCE.knockbackThreshold)
            flag(String.format(Locale.ROOT, "Отдача взрыва (%.2f бл/т)", v));
        else if (v > 0.001)
            suspect("слабый толчок", String.format(Locale.ROOT, "взрыв %.3f бл/т", v), ModConfig.INSTANCE.suspWeightKnock);
    }

    /** Чат: system — сообщение сервера/плагина; sender — автор сообщения игрока (null — неизвестен). */
    public void onChat(String text, UUID sender, String senderName, boolean overlay) {
        LocalPlayer p = player();
        ModConfig c = ModConfig.INSTANCE;
        if (p == null || text == null || !armed()) return;
        if (sender != null && sender.equals(p.getUUID())) return;           // своё сообщение
        String hit = c.detectChat ? ChatMatch.hit(text, p.getGameProfile().name(), c.chatMentionName, c.chatKeywords) : null;
        if (hit == null) {
            // новая надпись над хотбаром без ника/слова — немного подозрительности (цифры не различаем: таймеры)
            if (overlay && !text.isBlank()) {
                String key = clip(text, 80).replaceAll("\\d", "#");
                if (actionSeen.add(key)) {
                    if (actionSeen.size() > 64) actionSeen.remove(actionSeen.iterator().next());
                    suspect("надпись над хотбаром", "«" + clip(text, 30) + "»", c.suspWeightActionBar);
                }
            }
            return;
        }
        EventLog.log("CHAT", hit + (senderName != null ? " от " + senderName : "") + ": «" + clip(text, 120) + "»");
        if (c.chatWordsSoft && !"ник".equals(hit)) {
            suspect("слово в чате", hit, c.suspWeightChatWord);
            return;
        }
        flag((overlay ? "Надпись над хотбаром" : "Чат") + " (" + hit + ")" + (senderName != null ? " от " + senderName : "")
                + ": «" + clip(text, 60) + "»");
    }

    public void onTitle(String text, boolean subtitle) {
        if (!armed() || !ModConfig.INSTANCE.detectTitle || text == null || text.isBlank()) return;
        flag((subtitle ? "Подзаголовок" : "Титр") + " на экране: «" + clip(text, 60) + "»");
    }

    public void onGameModeChanged(String to) {
        if (armed() && ModConfig.INSTANCE.detectGameMode) flag("Сервер сменил режим игры" + (to != null ? ": " + to : ""));
    }

    public void onAbilities(boolean flying, boolean canFly, float walkSpeed, float flySpeed) {
        LocalPlayer p = player();
        ModConfig c = ModConfig.INSTANCE;
        if (p == null || !armed()) return;
        var a = p.getAbilities();
        if (c.detectGameMode && (a.flying != flying || a.mayfly != canFly))
            flag("Сервер сменил полёт (летит: " + yes(flying) + ", может летать: " + yes(canFly) + ")");
        if (c.detectMoveAttrs) {
            if (SyncMath.changed(a.getWalkingSpeed(), walkSpeed, c.attrEpsilon))
                flag(attrReason("скорость ходьбы (abilities)", a.getWalkingSpeed(), walkSpeed));
            else if (SyncMath.changed(a.getFlyingSpeed(), flySpeed, c.attrEpsilon))
                flag(attrReason("скорость полёта (abilities)", a.getFlyingSpeed(), flySpeed));
        }
    }

    private static final java.util.List<Holder<net.minecraft.world.entity.ai.attributes.Attribute>> MOVE_ATTRS = java.util.List.of(
            net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
            net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH,
            net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY,
            net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT,
            net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
    private static final String[] MOVE_ATTR_PATHS = {"movement_speed", "jump_strength", "gravity", "step_height", "scale"};

    /** v1.11 «Параметры движения»: пакет атрибутов своего игрока (вызывается ДО применения — у игрока старые значения). */
    public void onAttributes(int entityId, java.util.List<net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket.AttributeSnapshot> values) {
        LocalPlayer p = player();
        ModConfig c = ModConfig.INSTANCE;
        if (p == null || values == null || entityId != p.getId() || !armed() || !c.detectMoveAttrs) return;
        for (var snap : values) {
            int i = MOVE_ATTRS.indexOf(snap.attribute());
            if (i < 0) continue;
            var inst = p.getAttribute(snap.attribute());
            if (inst == null) continue;
            double before = SyncMath.value(inst.getBaseValue(), mods(inst.getModifiers()));
            double after = SyncMath.value(snap.base(), mods(snap.modifiers()));
            if (SyncMath.changed(before, after, c.attrEpsilon)) {
                flag(attrReason(SyncMath.attrLabel(MOVE_ATTR_PATHS[i]), before, after));
                return;
            }
        }
    }

    private static java.util.List<SyncMath.Mod> mods(java.util.Collection<net.minecraft.world.entity.ai.attributes.AttributeModifier> ms) {
        java.util.List<SyncMath.Mod> out = new java.util.ArrayList<>();
        if (ms != null) for (var m : ms) out.add(new SyncMath.Mod(m.id().toString(), m.amount(), m.operation().ordinal()));
        return out;
    }

    private static String attrReason(String what, double before, double after) {
        return String.format(Locale.ROOT, "Сервер изменил %s: %.4f → %.4f (%+.1f %%)", what, before, after,
                (after - before) / Math.max(Math.abs(before), 1e-6) * 100);
    }

    public void onRespawn() {
        if (armed() && ModConfig.INSTANCE.detectGameMode) flag("Респавн / смена мира");
    }

    public void onPassengers(int vehicle, int[] passengers) {
        LocalPlayer p = player();
        if (p == null || passengers == null || !armed() || !ModConfig.INSTANCE.detectGameMode) return;
        for (int id : passengers)
            if (id == p.getId() && (p.getVehicle() == null || p.getVehicle().getId() != vehicle)) {
                flag("Посадили на сущность #" + vehicle);
                return;
            }
    }

    public void onServerSlot(int slot) {
        LocalPlayer p = player();
        if (p == null || !armed() || !ModConfig.INSTANCE.detectSlotChange) return;
        int cur = p.getInventory().getSelectedSlot();
        if (slot != cur) flag("Сервер сменил слот: " + (cur + 1) + " → " + (slot + 1));
    }

    /** Событие урона (приходит и на урон 0, и при поглощении — здоровье может не измениться). */
    public void onDamageEvent(int entityId, boolean fall) {
        LocalPlayer p = player();
        if (p == null || entityId != p.getId() || !armed() || !ModConfig.INSTANCE.detectDamage) return;
        if (fall && fallGraceTicks > 0) return;
        flag(fall ? "Урон от падения" : "Получен урон (событие сервера)");
    }

    /** Список игроков (Tab): кто стал наблюдателем, кто зашёл. Вызывается до применения — в списке старое состояние. */
    public void onPlayerInfo(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ModConfig c = ModConfig.INSTANCE;
        if (p == null || mc.getConnection() == null || !armed()) return;
        var actions = packet.actions();
        boolean add = actions.contains(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER);
        boolean mode = actions.contains(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE);
        for (var e : packet.entries()) {
            if (e.profileId() == null || e.profileId().equals(p.getUUID())) continue;
            var old = mc.getConnection().getPlayerInfo(e.profileId());
            String name = e.profile() != null ? e.profile().name() : old != null ? old.getProfile().name() : "?";
            if (add && old == null) {
                if (c.detectSpectator && mode && e.gameMode() == net.minecraft.world.level.GameType.SPECTATOR) {
                    flag("Зашёл игрок наблюдателем: " + name); return;
                }
                if (c.detectPlayerJoin) { flag("Зашёл игрок: " + name); return; }
            } else if (mode && c.detectSpectator && e.gameMode() == net.minecraft.world.level.GameType.SPECTATOR
                    && old != null && old.getGameMode() != net.minecraft.world.level.GameType.SPECTATOR) {
                flag("Игрок стал наблюдателем: " + name); return;
            }
        }
    }

    public void onPlayerRemoved(java.util.List<UUID> ids) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || ids == null || mc.getConnection() == null || !armed() || !ModConfig.INSTANCE.detectPlayerJoin) return;
        for (UUID id : ids) {
            if (id.equals(p.getUUID())) continue;
            var old = mc.getConnection().getPlayerInfo(id);
            flag("Игрок пропал из списка (вышел или ваниш): " + (old != null ? old.getProfile().name() : id.toString()));
            return;
        }
    }

    private static String yes(boolean b) { return b ? "да" : "нет"; }

    private static String clip(String s, int n) {
        s = s.replace('\n', ' ').strip();
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }

    // ── Тик ──────────────────────────────────────────────────────────────────

    /** Вызывается каждый тик, пока макрос играет, ДО применения очередного кадра. */
    public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        ModConfig c = ModConfig.INSTANCE;

        String reason = pendingReason;
        pendingReason = null;

        if (pushGraceTicks > 0) pushGraceTicks--;
        if (!c.panicEnabled) { updatePrev(p); obstacles.clear(); return; }

        if (c.detectObstacle && mc.level != null) {
            String o = checkObstacles(p, mc.level, c.obstacleRange);
            if (reason == null) reason = o;
        } else obstacles.clear();

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

        // предмет в руке сменился/пропал сам — слот тот же (смену слота макросом не считаем)
        var stack = p.getMainHandItem();
        int sel = p.getInventory().getSelectedSlot();
        if (reason == null && c.detectHeldItem && sel == heldSlot && heldItem != null) {
            if (stack.isEmpty()) {
                if (!heldStackable) reason = "Предмет в руке пропал: " + heldItem.getName(heldItem.getDefaultInstance()).getString();
            } else if (stack.getItem() != heldItem) {
                reason = "Предмет в руке сменился: " + heldItem.getName(heldItem.getDefaultInstance()).getString()
                        + " → " + stack.getHoverName().getString();
            }
        }
        heldSlot = sel;
        heldItem = stack.isEmpty() ? null : stack.getItem();
        heldStackable = !stack.isEmpty() && stack.isStackable();

        // игрок подошёл: кто был рядом на старте — не считаем, считаем только новых в радиусе
        if (mc.level != null) {
            double r = c.playerNearRadius;
            Set<UUID> now = new HashSet<>();
            String who = null;
            boolean firstFar = farSeen == null;
            if (firstFar) farSeen = new HashSet<>();
            for (var other : mc.level.players()) {
                if (other == p || other.getUUID().equals(p.getUUID())) continue;
                double d = other.distanceTo(p);
                if (d > r) {
                    // v1.11: виден, но дальше радиуса — раз за запуск на игрока (кто был на старте — нет)
                    if (farSeen.add(other.getUUID()) && !firstFar)
                        suspect("игрок за радиусом", String.format(Locale.ROOT, "%s %.0f бл", other.getGameProfile().name(), d),
                                c.suspWeightPlayerFar);
                    continue;
                }
                now.add(other.getUUID());
                if (who == null && nearSeen != null && !nearSeen.contains(other.getUUID()))
                    who = String.format(Locale.ROOT, "Рядом игрок: %s (%.1f бл)", other.getGameProfile().name(), d);
            }
            if (nearSeen == null && !now.isEmpty()) LOGGER.info("Рядом на старте (не паника): {}", now.size());
            nearSeen = now;
            if (reason == null && c.detectPlayerNear && who != null) reason = who;
        }

        float hp = p.getHealth();
        boolean fallOk = fallGraceTicks > 0;
        if (fallGraceTicks > 0) fallGraceTicks--;
        if (reason == null && c.detectDamage && !fallOk && hp < expectedHealth - 0.01f) {
            reason = String.format(Locale.ROOT, "Получен урон (−%.1f ❤)", (expectedHealth - hp) / 2f);
        }
        expectedHealth = hp;

        if (c.suspicionEnabled) {
            String s = suspicion.reasonIfOver(System.currentTimeMillis(), c.suspicionHalfLifeSec, c.suspicionLimit);
            if (reason == null) reason = s;
        }

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
        Guard.run("panic/event-log", () -> EventLog.log("PANIC", reason));
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

        // 0. Мышь — сразу свободна (блокировка мыши), чтобы игрок мог реагировать сам.
        Guard.run("panic/unlock-mouse", () -> com.farmmacro.camera.MouseLock.unlock(mc, "паника"));

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
