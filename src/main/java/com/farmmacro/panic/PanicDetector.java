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
    /** v1.14.2-pre: id детектора (реестр {@link Detectors}) у pendingReason и у причины этого тика — для своего звука. */
    private String pendingId, lastHitId, tickReason, tickId;
    /** Звук текущей паники (повторы) — свой у детектора или null (общий). */
    private String soundId;
    private boolean lagWas;

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
    /** v1.13: детекторы мира и сервера (горение, паутина, клетка, BPS, рассинхрон, табло, банвейв…). */
    private final WorldGuards world = new WorldGuards(this);
    /** v1.14: игроки (staff, двойник, массовый телепорт, без имени, ЛС, AFK) и друзья. */
    private final PlayerGuards players = new PlayerGuards(this);
    /** v1.14 «Игрок рядом: задержка»: новые (не стартовые) игроки в радиусе и сколько стоят. */
    private Set<UUID> nearFresh = new HashSet<>();
    private final DwellTimer<UUID> nearDwell = new DwellTimer<>();
    /** v1.14 диагностика «стоит без дела»: с какого момента нет нажатых клавиш. */
    private long idleSinceMs;
    private boolean idleLogged;
    /** Игроки, уже засчитанные «за радиусом» в этом запуске (null — ещё не запомнили стартовых). */
    private Set<UUID> farSeen;
    /** Надписи над хотбаром (цифры → #), уже засчитанные в этом запуске. */
    private final java.util.LinkedHashSet<String> actionSeen = new java.util.LinkedHashSet<>();

    private int redScreenTicksLeft;
    private int soundRepeatsLeft;
    private int soundRepeatDelay;

    private String lastReason;
    /** v1.16.2: id детектора последней паники (null — общая). */
    private String lastPanicId;
    private long   lastPanicMs;
    private String overlayText;

    // ── Состояние ────────────────────────────────────────────────────────────

    private static boolean armed() {
        return MacroManager.INSTANCE.isPlaying() && ModConfig.INSTANCE.panicEnabled;
    }

    /** Срабатывание из пакета/GUI: через реестр; паника — в следующем тике. */
    void flag(String id, String reason) {
        if (hit(id, reason) == null) return;
        EventLog.log("FLAG", reason);
        if (pendingReason == null) {
            pendingReason = reason;
            pendingId = id;
            LOGGER.warn("[Флаг] {}", reason);
        }
    }

    /**
     * v1.14.2-pre: срабатывание детектора id через реестр {@link Detectors}.
     * @return причина, если действие — паника (или своё действие); null — очки/журнал/выключен/повтор
     */
    String hit(String id, String reason) {
        if (reason == null) return null;
        Detectors.Action a = Detectors.record(id, reason, System.currentTimeMillis());
        if (a == null) return null;
        switch (a) {
            case PANIC -> { lastHitId = id; return reason; }
            case SUSP -> suspectRaw(Detectors.name(id), reason, Detectors.weight(id));
            case LOG -> EventLog.log("DETECT", Detectors.name(id) + " (только журнал): " + reason);
            default -> {}
        }
        return null;
    }

    /** Причина этого тика: первая непустая (как раньше «if (reason == null) reason = …»). */
    private void offer(String r) {
        if (tickReason == null && r != null) { tickReason = r; tickId = lastHitId; }
    }

    /** Срабатывание вне тика детекторов (маршрут, запись): через реестр и сразу паника. @return была ли паника */
    public boolean fire(Minecraft mc, String id, String reason) {
        String r = hit(id, reason);
        if (r == null) return false;
        triggerPanic(mc, id, r);
        return true;
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

    /**
     * Старт запуска (не круга): кто уже рядом — не паника, паника — только кто подойдёт.
     * v1.16.1: сразу новая точка отсчёта (камера, слот, здоровье, предмет в руке) — как стоишь в момент старта.
     * Раньше при старте издалека (возврат к точке старта / переподключение) снимок делался только на первом круге,
     * а до него детекторы сравнивали с концом прошлого запуска: другой слот или камера — паника сразу после P.
     */
    public void beginRun() {
        snapshot(Minecraft.getInstance());
        nearSeen = null; fallGraceTicks = 0;
        farSeen = null; actionSeen.clear(); suspicion.reset();
        world.beginRun(Minecraft.getInstance().player);
        players.beginRun();
        nearFresh = new HashSet<>(); nearDwell.clear();
        idleSinceMs = System.currentTimeMillis(); idleLogged = false;
    }

    /** v1.13: BPS для HUD (NaN — мало данных). */
    public double bps() { return world.lastBps(); }

    /** v1.13: игрок сломал блок (MultiPlayerGameMode.destroyBlock). state — что было до ломания. */
    public void onPlayerBroke(BlockPos pos, BlockState state) {
        if (armed()) world.onBroke(pos, state);
    }

    /** v1.13: системный чат / надпись над хотбаром / титр — перезагрузка, лимбо, гость на ферме. */
    public void onSystemText(String text, String where) {
        if (armed() && player() != null) {
            world.onServerText(text, where);
            if (!"надпись над хотбаром".equals(where)) players.onSystemText(text, ModConfig.INSTANCE);
        }
    }

    /** v1.14: каждый тик, даже без макроса (возраст мира, свои телепорты — для «Массового телепорта»). */
    public void tickAlways(Minecraft mc) { players.tickAlways(mc); }

    /** v1.14: друг из белого списка («Паника → Друзья»). */
    public boolean isFriend(String name) { return players.isFriend(name); }

    /**
     * v1.13 «Отключение от сервера»: вызывается из события Fabric (поток может быть любым).
     * wasPlaying — играл ли макрос в момент отключения (потом MacroManager молча остановится без игрока).
     */
    public void onDisconnect(Minecraft mc, boolean wasPlaying) {
        com.farmmacro.panic.LagGuard.INSTANCE.reset();
        if (!wasPlaying) return;
        mc.execute(() -> {
            ModConfig c = ModConfig.INSTANCE;
            if (c.panicEnabled && c.detectDisconnect) fire(mc, "disconnect", "Отключение от сервера");
        });
    }

    /**
     * v1.11: мелкое событие детектора id ниже порогов → очки подозрительности (и строка журнала).
     * v1.14.2-pre: считается в реестре; действие детектора «Журнал» — только строка журнала, без очков.
     */
    void suspect(String id, String type, String detail, double weight) {
        if (!armed()) return;
        if (Detectors.soft(id, type + ": " + detail, System.currentTimeMillis())) suspectRaw(type, detail, weight);
        else EventLog.log("DETECT", Detectors.name(id) + " (только журнал): " + type + " (" + detail + ")");
    }

    private void suspectRaw(String type, String detail, double weight) {
        ModConfig c = ModConfig.INSTANCE;
        if (!armed() || !c.suspicionEnabled || !(weight > 0)) return;
        double s = suspicion.add(type, detail, weight, System.currentTimeMillis(), c.suspicionHalfLifeSec);
        EventLog.log("SUSP", String.format(Locale.ROOT, "%s (%s) +%.1f → %.1f/%.0f", type, detail, weight, s, c.suspicionLimit));
    }

    /** Сумма очков подозрительности (для HUD; считается в tick). */
    public double suspicionScore() { return suspicion.lastScore; }

    /** Автоход в спуске: урон от падения ещё N тиков не паника (падение — часть маршрута). */
    /** v1.15.1: возврат сам включает/выключает полёт — «Сервер сменил полёт» по флагу «летит» до этого момента не считаем. */
    private long flightGraceUntil;
    public void allowFlightToggle(int ticks) { flightGraceUntil = Math.max(flightGraceUntil, System.currentTimeMillis() + ticks * 50L); }

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
                || screen instanceof com.farmmacro.gui.MacroSettingsScreen
                || screen instanceof com.farmmacro.hud.HudEditorScreen) return;
        flag("gui", "Открылось окно: " + screen.getTitle().getString()
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
        boolean lag = c.lagGuard && LagGuard.INSTANCE.lagging();
        if (dist >= c.serverMoveThreshold && c.lagbackSoft && dist < 4 && world.onRecentPath(p.getX(), p.getY(), p.getZ(), 0.5)) {
            // v1.13 «Откат назад»: вернули на место, где игрок был за последние ~3 с — похоже на лаг-откат античита
            String d = String.format(Locale.ROOT, "%.2f бл%s", dist, lag ? ", сервер лагает" : "");
            EventLog.log("SYNC", "откат назад: " + d);
            suspect("lagback", "откат назад", d, c.suspWeightLagback);
        } else if (dist >= c.serverMoveThreshold) {
            flag("serverMove", String.format(Locale.ROOT, "Сервер телепортировал (%.1f бл)", dist));
        } else if (rot >= c.serverRotateThreshold) {
            flag("serverMove", String.format(Locale.ROOT, "Сервер повернул камеру (%.3f° ≥ %.3f°)", rot, c.serverRotateThreshold));
        } else if (lag && (syncOver || c.serverMoveAny)) {
            // v1.13 «Лаги сервера»: во время лага поправка позиции — норма, только подозрительность
            String d = String.format(Locale.ROOT, "%.3f бл, %.3f°, сервер лагает", dist, rot);
            EventLog.log("SYNC", "лаг: пакет " + mvKind + " " + d);
            suspect("serverMove", "откат при лаге", d, syncOver ? c.suspWeightRollback : c.suspWeightSync);
        } else if (c.detectServerSync && syncOver) {
            flag("serverSync", SyncMath.syncReason(mvKind, dist, c.serverPosEpsilon, dYaw, dPitch, c.serverRotEpsilon));
        } else if (c.serverMoveAny) {
            flag("serverMove", String.format(Locale.ROOT, "Пакет %s от сервера (сдвиг %.3f бл, поворот %.3f°)", mvKind, dist, rot));
        } else if (syncOver) {
            // «Синхронизация» выключена: сдвиг больше эпсилона, но меньше порогов — похоже на лаг-откат
            String d = String.format(Locale.ROOT, "%.3f бл, %.3f°", dist, rot);
            EventLog.log("SYNC", "откат: пакет " + mvKind + " " + d);
            suspect("serverMove", "лаг-откат", d, c.suspWeightRollback);
        } else {
            String d = String.format(Locale.ROOT, "%.4f бл, yaw %.4f° pitch %.4f°", dist, dYaw, dPitch);
            LOGGER.info("[Синхр] пакет {} ниже эпсилона: {} (≤ {} бл / {}°)", mvKind, d, c.serverPosEpsilon, c.serverRotEpsilon);
            EventLog.log("SYNC", "ниже эпсилона: пакет " + mvKind + " " + d);
            suspect("serverMove", "пакет " + mvKind + " ниже эпсилона", String.format(Locale.ROOT, "%.3f бл", dist), c.suspWeightSync);
        }
        // Поворот от сервера — не движение мыши: детектор мыши сравнивает с уже повёрнутой камерой,
        // иначе мелкий серверный поворот (ниже порога сервера) засчитывался бы как «Камера повернулась».
        updatePrev(p);
    }

    /** Блок из пакета сервера. Вызывается ДО применения, поэтому в мире ещё старое состояние. */
    public void onServerBlockChange(BlockPos pos, BlockState newState) {
        ModConfig c = ModConfig.INSTANCE;
        if (!armed()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null) return;
        BlockState old = level.getBlockState(pos);
        world.onBlock(p, level, pos, old, newState);
        if (!c.detectBlockInFace && !c.detectObstacle) return;

        // Свои постановки блоков клиент уже предсказал — там старое состояние уже твёрдое.
        if (!old.getCollisionShape(level, pos).isEmpty()) return;
        VoxelShape shape = newState.getCollisionShape(level, pos);
        if (shape.isEmpty()) return;

        if (c.detectBlockInFace) {
            AABB zone = p.getBoundingBox().inflate(c.blockDetectRadius);
            for (AABB box : shape.toAabbs()) {
                if (box.move(pos).intersects(zone)) {
                    flag("blockNear", "Рядом появился блок: " + newState.getBlock().getName().getString()
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
    static boolean grownOrMoved(BlockState st) {
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
        flag("effects", "Эффект: " + effect.value().getDisplayName().getString() + " " + (amplifier + 1));
    }

    public void onEffectRemoved(int entityId, Holder<MobEffect> effect) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || entityId != p.getId() || !armed() || !ModConfig.INSTANCE.detectPotionEffect) return;
        MobEffectInstance cur = p.getEffect(effect);
        if (cur == null) return;
        // Естественное окончание — норма; снятие задолго до конца (молоко, /effect clear) — нет.
        if (cur.isInfiniteDuration() || cur.getDuration() > 40) {
            flag("effects", "Сняли эффект: " + effect.value().getDisplayName().getString());
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
            flag("knockback", String.format(Locale.ROOT, "Сервер толкнул (скорость %.2f бл/т)", v));
        else if (v > 0.001)
            suspect("knockback", "слабый толчок", String.format(Locale.ROOT, "%.3f бл/т", v), ModConfig.INSTANCE.suspWeightKnock);
    }

    public void onExplosion(java.util.Optional<net.minecraft.world.phys.Vec3> knockback) {
        if (!armed() || knockback == null || knockback.isEmpty()) return;
        markServerPush();
        if (!ModConfig.INSTANCE.detectKnockback) return;
        double v = knockback.get().length();
        if (v >= ModConfig.INSTANCE.knockbackThreshold)
            flag("knockback", String.format(Locale.ROOT, "Отдача взрыва (%.2f бл/т)", v));
        else if (v > 0.001)
            suspect("knockback", "слабый толчок", String.format(Locale.ROOT, "взрыв %.3f бл/т", v), ModConfig.INSTANCE.suspWeightKnock);
    }

    /** Чат: system — сообщение сервера/плагина; sender — автор сообщения игрока (null — неизвестен). */
    public void onChat(String text, UUID sender, String senderName, boolean overlay) {
        LocalPlayer p = player();
        ModConfig c = ModConfig.INSTANCE;
        if (p == null || text == null || !armed()) return;
        if (sender != null && sender.equals(p.getUUID())) return;           // своё сообщение
        if (c.friendsIgnoreChat && !overlay) {                               // v1.14: друг пишет — не считаем
            String who = senderName != null ? senderName : PlayerRules.chatSender(text);
            if (players.isFriend(who)) { EventLog.log("FRIEND", "чат от друга " + who); return; }
        }
        String hit = c.detectChat ? ChatMatch.hit(text, p.getGameProfile().name(), c.chatMentionName, c.chatKeywords) : null;
        if (hit == null) {
            // новая надпись над хотбаром без ника/слова — немного подозрительности (цифры не различаем: таймеры)
            if (overlay && !text.isBlank()) {
                String key = clip(text, 80).replaceAll("\\d", "#");
                if (actionSeen.add(key)) {
                    if (actionSeen.size() > 64) actionSeen.remove(actionSeen.iterator().next());
                    suspect("title", "надпись над хотбаром", "«" + clip(text, 30) + "»", c.suspWeightActionBar);
                }
            }
            return;
        }
        EventLog.log("CHAT", hit + (senderName != null ? " от " + senderName : "") + ": «" + clip(text, 120) + "»");
        if (c.chatWordsSoft && !"ник".equals(hit)) {
            suspect("chat", "слово в чате", hit, c.suspWeightChatWord);
            return;
        }
        flag("chat", (overlay ? "Надпись над хотбаром" : "Чат") + " (" + hit + ")" + (senderName != null ? " от " + senderName : "")
                + ": «" + clip(text, 60) + "»");
    }

    public void onTitle(String text, boolean subtitle) {
        if (!armed() || text == null || text.isBlank()) return;
        world.onServerText(text, subtitle ? "подзаголовок" : "титр");
        if (!ModConfig.INSTANCE.detectTitle) return;
        flag("title", (subtitle ? "Подзаголовок" : "Титр") + " на экране: «" + clip(text, 60) + "»");
    }

    public void onGameModeChanged(String to) {
        if (armed() && ModConfig.INSTANCE.detectGameMode) flag("gameMode", "Сервер сменил режим игры" + (to != null ? ": " + to : ""));
    }

    public void onAbilities(boolean flying, boolean canFly, float walkSpeed, float flySpeed) {
        LocalPlayer p = player();
        ModConfig c = ModConfig.INSTANCE;
        if (p == null || !armed()) return;
        var a = p.getAbilities();
        boolean flyOwn = System.currentTimeMillis() < flightGraceUntil;
        if (c.detectGameMode && ((a.flying != flying && !flyOwn) || a.mayfly != canFly))
            flag("gameMode", "Сервер сменил полёт (летит: " + yes(flying) + ", может летать: " + yes(canFly) + ")");
        if (c.detectMoveAttrs) {
            if (SyncMath.changed(a.getWalkingSpeed(), walkSpeed, c.attrEpsilon))
                flag("moveAttrs", attrReason("скорость ходьбы (abilities)", a.getWalkingSpeed(), walkSpeed));
            else if (SyncMath.changed(a.getFlyingSpeed(), flySpeed, c.attrEpsilon))
                flag("moveAttrs", attrReason("скорость полёта (abilities)", a.getFlyingSpeed(), flySpeed));
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
                flag("moveAttrs", attrReason(SyncMath.attrLabel(MOVE_ATTR_PATHS[i]), before, after));
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
        if (armed() && ModConfig.INSTANCE.detectGameMode) flag("gameMode", "Респавн / смена мира");
    }

    public void onPassengers(int vehicle, int[] passengers) {
        LocalPlayer p = player();
        if (p == null || passengers == null || !armed() || !ModConfig.INSTANCE.detectGameMode) return;
        for (int id : passengers)
            if (id == p.getId() && (p.getVehicle() == null || p.getVehicle().getId() != vehicle)) {
                flag("gameMode", "Посадили на сущность #" + vehicle);
                return;
            }
    }

    public void onServerSlot(int slot) {
        LocalPlayer p = player();
        if (p == null || !armed() || !ModConfig.INSTANCE.detectSlotChange) return;
        int cur = p.getInventory().getSelectedSlot();
        if (slot != cur) flag("slot", "Сервер сменил слот: " + (cur + 1) + " → " + (slot + 1));
    }

    /** Событие урона (приходит и на урон 0, и при поглощении — здоровье может не измениться). */
    public void onDamageEvent(int entityId, boolean fall) {
        LocalPlayer p = player();
        if (p == null || entityId != p.getId() || !armed() || !ModConfig.INSTANCE.detectDamage) return;
        if (fall && fallGraceTicks > 0) return;
        flag("damage", fall ? "Урон от падения" : "Получен урон (событие сервера)");
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
            boolean friend = players.isFriend(name);
            if (add && old == null) {
                if (c.detectSpectator && mode && e.gameMode() == net.minecraft.world.level.GameType.SPECTATOR
                        && !(friend && c.friendsIgnoreSpectator)) {
                    flag("spectator", "Зашёл игрок наблюдателем: " + name); return;
                }
                if (c.detectPlayerJoin && !(friend && c.friendsIgnoreJoin)) { flag("playerJoin", "Зашёл игрок: " + name); return; }
            } else if (mode && c.detectSpectator && !(friend && c.friendsIgnoreSpectator) && e.gameMode() == net.minecraft.world.level.GameType.SPECTATOR
                    && old != null && old.getGameMode() != net.minecraft.world.level.GameType.SPECTATOR) {
                flag("spectator", "Игрок стал наблюдателем: " + name); return;
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
            if (old != null && ModConfig.INSTANCE.friendsIgnoreJoin && players.isFriend(old.getProfile().name())) continue;
            flag("playerJoin", "Игрок пропал из списка (вышел или ваниш): " + (old != null ? old.getProfile().name() : id.toString()));
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

        tickReason = pendingReason; tickId = pendingId;
        pendingReason = null; pendingId = null;

        if (pushGraceTicks > 0) pushGraceTicks--;
        if (!c.panicEnabled) { updatePrev(p); obstacles.clear(); return; }

        // v1.14.2-pre: «Лаги сервера» — защита, в реестре считаются эпизоды лага
        boolean lagNow = c.lagGuard && LagGuard.INSTANCE.lagging();
        if (lagNow && !lagWas) {
            double tps = LagGuard.INSTANCE.tps();
            Detectors.record("lagGuard", Double.isNaN(tps) ? "сервер лагает" : String.format(Locale.ROOT, "сервер лагает, TPS %.1f", tps),
                    System.currentTimeMillis());
        }
        lagWas = lagNow;

        if (c.detectObstacle && mc.level != null) {
            String o = checkObstacles(p, mc.level, c.obstacleRange);
            if (tickReason == null) offer(hit("obstacle", o));
        } else obstacles.clear();

        if (tickReason == null && c.detectRotation) {
            float dy = angleDiff(p.getYRot(), prevYaw);
            float dp = Math.abs(p.getXRot() - prevPitch);
            if (dy > c.yawThreshold)
                offer(hit("rotation", String.format(Locale.ROOT, "Камера повернулась (yaw %.3f° > %.3f°)", dy, c.yawThreshold)));
            else if (dp > c.pitchThreshold)
                offer(hit("rotation", String.format(Locale.ROOT, "Камера повернулась (pitch %.3f° > %.3f°)", dp, c.pitchThreshold)));
        }

        if (tickReason == null && c.detectSlotChange) {
            int real = p.getInventory().getSelectedSlot();
            if (real != expectedSlot) offer(hit("slot", "Слот сменился: " + (expectedSlot + 1) + " → " + (real + 1)));
        }

        // предмет в руке сменился/пропал сам — слот тот же (смену слота макросом не считаем)
        var stack = p.getMainHandItem();
        int sel = p.getInventory().getSelectedSlot();
        if (tickReason == null && c.detectHeldItem && sel == heldSlot && heldItem != null) {
            if (stack.isEmpty()) {
                if (!heldStackable) offer(hit("heldItem", "Предмет в руке пропал: " + heldItem.getName(heldItem.getDefaultInstance()).getString()));
            } else if (stack.getItem() != heldItem) {
                offer(hit("heldItem", "Предмет в руке сменился: " + heldItem.getName(heldItem.getDefaultInstance()).getString()
                        + " → " + stack.getHoverName().getString()));
            }
        }
        heldSlot = sel;
        heldItem = stack.isEmpty() ? null : stack.getItem();
        heldStackable = !stack.isEmpty() && stack.isStackable();

        // игрок подошёл: кто был рядом на старте — не считаем, считаем только новых в радиусе
        // v1.14: друзья не считаются (по галочке); «задержка N с» — паника, только если чужой стоит рядом N с
        long nowMs = System.currentTimeMillis();
        if (mc.level != null) {
            double r = c.playerNearRadius;
            Set<UUID> now = new HashSet<>(), fresh = new HashSet<>();
            java.util.Map<UUID, String> info = new java.util.HashMap<>();
            boolean firstFar = farSeen == null;
            if (firstFar) farSeen = new HashSet<>();
            for (var other : mc.level.players()) {
                if (other == p || other.getUUID().equals(p.getUUID())) continue;
                String nm = other.getGameProfile().name();
                if (c.friendsIgnoreNear && players.isFriend(nm)) continue;
                double d = other.distanceTo(p);
                if (d > r) {
                    // v1.11: виден, но дальше радиуса — раз за запуск на игрока (кто был на старте — нет)
                    if (farSeen.add(other.getUUID()) && !firstFar)
                        suspect("playerNear", "игрок за радиусом", String.format(Locale.ROOT, "%s %.0f бл", nm, d),
                                c.suspWeightPlayerFar);
                    continue;
                }
                now.add(other.getUUID());
                if (nearSeen != null && (!nearSeen.contains(other.getUUID()) || nearFresh.contains(other.getUUID()))) {
                    fresh.add(other.getUUID());
                    info.put(other.getUUID(), String.format(Locale.ROOT, "%s (%.1f бл", nm, d));
                }
            }
            if (nearSeen == null && !now.isEmpty()) LOGGER.info("Рядом на старте (не паника): {}", now.size());
            nearSeen = now;
            nearFresh = fresh;
            long delayMs = Math.round(c.playerNearDelaySec * 1000);
            UUID near = nearDwell.update(nowMs, fresh, delayMs);
            if (tickReason == null && c.detectPlayerNear && near != null)
                offer(hit("playerNear", "Рядом игрок: " + info.get(near) + (delayMs > 0 ? String.format(Locale.ROOT, ", стоит %.1f с)", nearDwell.elapsed(near, nowMs) / 1000.0) : ")")));
        }

        // v1.14 диагностика (только журнал): нет нажатых клавиш дольше обычного
        var ko = mc.options;
        if (ko.keyUp.isDown() || ko.keyDown.isDown() || ko.keyLeft.isDown() || ko.keyRight.isDown() || ko.keyJump.isDown()
                || ko.keyAttack.isDown() || ko.keyUse.isDown() || ko.keyShift.isDown()) { idleSinceMs = nowMs; idleLogged = false; }
        else if (c.diagIdle && !idleLogged && nowMs - idleSinceMs >= c.diagIdleSec * 1000L) {
            idleLogged = true;
            EventLog.log("DIAG", "стоит без дела " + c.diagIdleSec + " с: ни одна клавиша не нажата (точка-пауза, лаг или макрос завис?)");
        }

        float hp = p.getHealth();
        boolean fallOk = fallGraceTicks > 0;
        if (fallGraceTicks > 0) fallGraceTicks--;
        if (tickReason == null && c.detectDamage && !fallOk && hp < expectedHealth - 0.01f) {
            offer(hit("damage", String.format(Locale.ROOT, "Получен урон (−%.1f ❤)", (expectedHealth - hp) / 2f)));
        }
        expectedHealth = hp;

        offer(world.tick(mc, p, c));                     // v1.13: мир и сервер (состояние обновляется всегда)
        offer(players.tick(mc, p, c));                   // v1.14: staff, двойник, массовый телепорт, без имени
        if (!MacroManager.INSTANCE.isPlaying()) return;    // Jacob's Contest: обычный стоп по лимиту

        if (c.suspicionEnabled) {
            String s = suspicion.reasonIfOver(System.currentTimeMillis(), c.suspicionHalfLifeSec, c.suspicionLimit);
            if (tickReason == null && s != null) {
                offer(hit("suspicion", s));
                if (tickReason == null) suspicion.reset();     // «Журнал»: записали и начали копить заново
            }
        }

        String reason = tickReason, id = tickId;
        tickReason = null; tickId = null;
        if (reason != null) triggerPanic(mc, id, reason);
        else updatePrev(p);
    }

    /** Красный экран и повторы звука — каждый тик, независимо от макроса. */
    public void tickEffects() {
        if (redScreenTicksLeft > 0) redScreenTicksLeft--;
        if (soundRepeatsLeft > 0) {
            if (soundRepeatDelay > 0) soundRepeatDelay--;
            else {
                soundRepeatsLeft--;
                Guard.run("panic/repeat-sound", () -> PanicSound.playPanic(soundId));
                soundRepeatDelay = ModConfig.INSTANCE.panicSoundRepeatDelayTicks;
            }
        }
    }

    /**
     * Аварийный стоп. Только останавливает макрос и подаёт сигнал.
     * Каждый шаг защищён отдельно: ошибка в логе/звуке/экране не мешает остановке и не роняет игру.
     */
    public void triggerPanic(Minecraft mc, String reason) { triggerPanic(mc, null, reason); }

    /** v1.14.2-pre: id — детектор из реестра (свой звук); null — общий звук. */
    public void triggerPanic(Minecraft mc, String id, String reason) {
        ModConfig c = ModConfig.INSTANCE;
        soundId = Detectors.sound(id);
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
        lastPanicId = id;
        overlayText = reason;
        lastPanicMs = System.currentTimeMillis();
        pendingReason = null;

        // 0. Мышь — сразу свободна (блокировка мыши), чтобы игрок мог реагировать сам.
        Guard.run("panic/unlock-mouse", () -> com.farmmacro.camera.MouseLock.unlock(mc, "паника"));
        // v1.13.1: курсор — обратно в игру, графика — как была (чтобы видеть и реагировать)
        Guard.run("panic/ungrab-off", () -> com.farmmacro.camera.Ungrab.off(mc, "паника"));
        Guard.run("panic/perf-off", () -> com.farmmacro.visual.PerformanceMode.off(mc, "паника"));
        // v1.12: freelook — обратно в свой вид, чтобы видеть то же, что и сервер (по настройке)
        if (c.freelookOffOnPanic) Guard.run("panic/freelook-off", () -> com.farmmacro.camera.Freelook.off(mc, "паника"));

        // 1. Остановка — главное. Если обычная остановка упала, отпускаем клавиши аварийно.
        if (!Guard.run("panic/stop", () -> MacroManager.INSTANCE.stopPlayback(mc, "§c⚠ Паника: " + reason)))
            Guard.run("panic/force-stop", () -> MacroManager.INSTANCE.forceStop(mc));

        // 2. Звук: только постановка в очередь, без декодирования и файлов.
        if (c.panicSoundEnabled) {
            soundRepeatsLeft = Math.max(0, c.panicSoundRepeats - 1);
            soundRepeatDelay = c.panicSoundRepeatDelayTicks;
            Guard.run("panic/sound", () -> PanicSound.playPanic(soundId));
        }
        // 3. Красный экран.
        if (c.panicRedScreenEnabled) redScreenTicksLeft = c.panicRedScreenTicks;
        // 4. v1.14: скриншот последнего кадра (до красного экрана) и сообщение в Discord — файлы/сеть в фоне
        Guard.run("panic/screenshot", () -> PanicShot.onPanic(mc, reason));
    }

    /** Проверка из меню: те же звук и красный экран, но без остановки и без записи в «последнюю панику». */
    public void preview(Minecraft mc) { preview(mc, "Проверка — так выглядит паника", null); }

    /** v1.14.2-pre «Тест ▶» в таблице детекторов: экран, звук детектора и пример причины — без остановки макроса. */
    public void preview(Minecraft mc, String text, String sound) {
        ModConfig c = ModConfig.INSTANCE;
        overlayText = text;
        soundId = sound;
        EventLog.log("TEST", text);
        if (c.panicSoundEnabled) {
            soundRepeatsLeft = Math.max(0, c.panicSoundRepeats - 1);
            soundRepeatDelay = c.panicSoundRepeatDelayTicks;
            Guard.run("panic/sound", () -> PanicSound.playPanic(sound));
        }
        redScreenTicksLeft = c.panicRedScreenEnabled ? c.panicRedScreenTicks : 0;
    }

    /** Заглушить повторы сирены (кнопка в GUI / новый старт макроса). */
    public void silence() { soundRepeatsLeft = 0; }

    public int     getRedScreenTicks() { return redScreenTicksLeft; }
    public String  getLastReason()     { return lastReason; }
    public String  getLastPanicId()    { return lastPanicId; }
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
