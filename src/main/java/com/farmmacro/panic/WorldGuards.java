package com.farmmacro.panic;

import com.farmmacro.config.ModConfig;
import com.farmmacro.macro.MacroManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * v1.13 «THE BIG PANIC UPDATE»: детекторы мира и сервера (идеи — из списка failsafe FarmHelper, код свой).
 * Горение, паутина/вязкие блоки, клетка из бедрока/барьера, блок на ферме, урожай не ломается (BPS), рассинхрон,
 * полный инвентарь, перезагрузка/лимбо (табло, чат, титры), банвейв, Jacob's Contest, гость на ферме, откат назад.
 * Всё в главном потоке. Паника — через {@link PanicDetector} (flag/triggerPanic): только стоп + экран + звук.
 */
final class WorldGuards {
    private final PanicDetector d;

    private boolean fireAtStart, stickyAtStart;
    private final Set<Long> cageBlocks = new HashSet<>();
    private final Map<Long, Long> farmBlocks = new HashMap<>();      // pos → до какого времени (мс) помним
    private final Map<Long, Long> recentBroken = new HashMap<>();    // pos → когда сломал игрок (мс)
    final BpsMeter bps = new BpsMeter();
    private record Broke(long pos, BlockState state, int due) {}
    private final ArrayDeque<Broke> pendingDesync = new ArrayDeque<>();
    private final ArrayDeque<Boolean> desyncResults = new ArrayDeque<>();
    private int tickNo, invFullTicks;
    /** Путь за последние ~3 с (x, y, z) — для «Откат назад». */
    private final ArrayDeque<double[]> path = new ArrayDeque<>();
    private static final int PATH_TICKS = 60, DESYNC_DELAY = 20, DESYNC_KEEP = 20, DESYNC_MIN = 10;
    private volatile double lastBps = Double.NaN;
    /** v1.14: бедрок очередью и клетка по числу блоков (идеи blazebot, код свой). */
    private final BurstWatch bedrockBurst = new BurstWatch(), cageBurst = new BurstWatch();
    /** v1.14 диагностика: с какого момента табло пустое/не читается (−1 — читается). */
    private long boardMissingSince = -1;
    private boolean boardLogged;

    WorldGuards(PanicDetector d) { this.d = d; }

    void beginRun(LocalPlayer p) {
        fireAtStart = p != null && p.isOnFire();
        stickyAtStart = p != null && stickyAt(p) != null;
        cageBlocks.clear(); farmBlocks.clear(); recentBroken.clear();
        bps.reset(); pendingDesync.clear(); desyncResults.clear(); path.clear();
        invFullTicks = 0; lastBps = Double.NaN;
        bedrockBurst.clear(); cageBurst.clear(); boardMissingSince = -1; boardLogged = false;
    }

    /** BPS для HUD (NaN — мало данных). */
    double lastBps() { return lastBps; }

    private static boolean lag() { return ModConfig.INSTANCE.lagGuard && LagGuard.INSTANCE.lagging(); }

    static boolean sticky(BlockState s) {
        Block b = s.getBlock();
        return b == Blocks.COBWEB || b == Blocks.SWEET_BERRY_BUSH || b == Blocks.POWDER_SNOW;
    }

    private static boolean cageBlock(BlockState s) {
        Block b = s.getBlock();
        return b == Blocks.BEDROCK || b == Blocks.BARRIER;
    }

    /** Вязкий блок в хитбоксе игрока (у паутины нет коллизии — «Блок рядом» её не видит). */
    private static String stickyAt(LocalPlayer p) {
        var level = p.level();
        AABB bb = p.getBoundingBox().deflate(0.001);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(bb.minX, bb.minY, bb.minZ), BlockPos.containing(bb.maxX, bb.maxY, bb.maxZ))) {
            BlockState s = level.getBlockState(pos);
            if (sticky(s)) return s.getBlock().getName().getString();
        }
        return p.isInPowderSnow ? Blocks.POWDER_SNOW.getName().getString() : null;
    }

    // ── События (главный поток) ──────────────────────────────────────────────

    /** Блок от сервера, ДО применения (old — то, что сейчас в мире). */
    void onBlock(LocalPlayer p, ClientLevel level, BlockPos pos, BlockState old, BlockState now) {
        ModConfig c = ModConfig.INSTANCE;
        double dist = Math.sqrt(p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        String name = now.getBlock().getName().getString();
        if (c.detectSticky && sticky(now) && !sticky(old) && dist <= c.stickyRadius) {
            d.flag(String.format(Locale.ROOT, "Сервер поставил %s рядом (%.1f бл)", name, dist));
            return;
        }
        if (c.detectCage && cageBlock(now) && !cageBlock(old) && dist <= 4 && cageBlocks.size() < 256) cageBlocks.add(pos.asLong());
        long ms = System.currentTimeMillis();
        // v1.14 «Бедрок очередью»: ≥ N блоков бедрока ближе R бл за 300 мс (клетку staff ставит одной пачкой)
        if (c.detectBedrockBurst && now.getBlock() == Blocks.BEDROCK && old.getBlock() != Blocks.BEDROCK && dist <= c.bedrockBurstRadius) {
            bedrockBurst.add(ms, pos.asLong());
            int n = bedrockBurst.count(ms, c.bedrockBurstMs);
            if (n >= c.bedrockBurstMin) {
                d.flag(String.format(Locale.ROOT, "Бедрок очередью: %d блоков ближе %.0f бл за %d мс", n, c.bedrockBurstRadius, c.bedrockBurstMs));
                return;
            }
        }
        // v1.14 «Клетка по числу блоков»: ≥ N любых твёрдых блоков у ног или головы за 1 с (рост урожая и поршни — нет)
        if (c.detectCageCount && old.getCollisionShape(level, pos).isEmpty() && !now.getCollisionShape(level, pos).isEmpty()
                && !PanicDetector.grownOrMoved(now)) {
            double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
            double feet = dist3(cx, cy, cz, p.getX(), p.getY() + 0.5, p.getZ()), head = dist3(cx, cy, cz, p.getX(), p.getY() + 1.5, p.getZ());
            if (Math.min(feet, head) <= c.cageCountRadius) {
                cageBurst.add(ms, pos.asLong());
                int n = cageBurst.count(ms, c.cageCountMs);
                if (n >= c.cageCountMin) {
                    d.flag(String.format(Locale.ROOT, "Клетка: %d блоков у ног/головы за %.1f с (последний %s)", n, c.cageCountMs / 1000.0, name));
                    return;
                }
            }
        }
        if (c.detectFarmBlock && dist <= c.farmBlockRadius && !lag()
                && old.getCollisionShape(level, pos).isEmpty() && now.isCollisionShapeFullBlock(level, pos)
                && !PanicDetector.grownOrMoved(now) && !brokenRecently(pos.asLong())) {
            if (farmBlocks.size() < 512) farmBlocks.put(pos.asLong(), ms + 60_000);
            EventLog.log("FARMBLOCK", name + " " + pos.toShortString() + String.format(Locale.ROOT, " (%.1f бл)", dist));
            d.suspect("блок на ферме", name + " " + pos.toShortString(), c.suspWeightFarmBlock);
        }
    }

    private boolean brokenRecently(long pos) {
        Long t = recentBroken.get(pos);
        return t != null && System.currentTimeMillis() - t < 10_000;
    }

    /** Игрок сломал блок (MultiPlayerGameMode.destroyBlock вернул true). */
    void onBroke(BlockPos pos, BlockState state) {
        ModConfig c = ModConfig.INSTANCE;
        bps.onBroken();
        long ms = System.currentTimeMillis();
        if (recentBroken.size() > 2048) recentBroken.values().removeIf(t -> ms - t > 10_000);
        recentBroken.put(pos.asLong(), ms);
        if (c.detectDesync && state != null && !state.isAir() && pendingDesync.size() < 256)
            pendingDesync.addLast(new Broke(pos.asLong(), state, tickNo + DESYNC_DELAY));
    }

    /** Системный чат, надпись над хотбаром или титр. */
    void onServerText(String text, String where) {
        ModConfig c = ModConfig.INSTANCE;
        if (text == null || text.isBlank()) return;
        if (c.detectRestart) {
            String w = ServerText.restartWord(text);
            if (w != null) { d.flag("Перезагрузка / лимбо (" + where + ", «" + w + "»): «" + clip(text) + "»"); return; }
        }
        if (c.detectVisitor && ServerText.isVisitor(text)) d.flag("Гость на ферме: «" + clip(text) + "»");
    }

    /** Новая позиция после пакета телепорта — на пути за последние ~3 с (откат назад)? */
    boolean onRecentPath(double x, double y, double z, double tol) {
        for (double[] q : path) {
            double dx = q[0] - x, dy = q[1] - y, dz = q[2] - z;
            if (dx * dx + dy * dy + dz * dz <= tol * tol) return true;
        }
        return false;
    }

    // ── Тик ──────────────────────────────────────────────────────────────────

    /** @return причина паники или null. Вызывается каждый тик, пока макрос играет и паника включена. */
    String tick(Minecraft mc, LocalPlayer p, ModConfig c) {
        tickNo++;
        ClientLevel level = mc.level;
        String r = null;
        boolean lag = lag();

        path.addLast(new double[]{p.getX(), p.getY(), p.getZ()});
        while (path.size() > PATH_TICKS) path.pollFirst();

        // горение
        if (!p.isOnFire()) fireAtStart = false;
        else if (c.detectFire && !fireAtStart) r = "Игрок горит";

        // паутина / ягоды / снег в хитбоксе
        String st = stickyAt(p);
        if (st == null) stickyAtStart = false;
        else if (r == null && c.detectSticky && !stickyAtStart) r = "Застрял в блоке: " + st;

        // клетка из бедрока/барьера (хотя бы один блок поставил сервер во время запуска)
        if (r == null && c.detectCage && level != null && !cageBlocks.isEmpty() && tickNo % 5 == 0) r = cage(p, level);

        // упёрся в блок, поставленный сервером на ферме
        if (!farmBlocks.isEmpty()) {
            long ms = System.currentTimeMillis();
            farmBlocks.values().removeIf(t -> t < ms);
            if (r == null && c.detectFarmBlock && level != null) {
                AABB bb = p.getBoundingBox().inflate(0.08);
                for (long lp : farmBlocks.keySet()) {
                    BlockPos pos = BlockPos.of(lp);
                    if (bb.intersects(new AABB(pos)) && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                        r = "Упёрся в блок, поставленный сервером: " + level.getBlockState(pos).getBlock().getName().getString()
                                + " " + pos.toShortString();
                        break;
                    }
                }
            }
        }

        // BPS: только «рабочие» тики (ЛКМ зажата и нажата клавиша движения), лаги не считаем
        var o = mc.options;
        boolean working = !lag && o.keyAttack.isDown()
                && (o.keyUp.isDown() || o.keyDown.isDown() || o.keyLeft.isDown() || o.keyRight.isDown());
        bps.tick(working, (int) Math.round(c.bpsWindowSec * 20));
        lastBps = bps.bps();
        if (r == null && c.detectBps && bps.ready() && bps.bps() < c.bpsMin)
            r = String.format(Locale.ROOT, "Урожай не ломается: %.1f блоков/с < %.1f", bps.bps(), c.bpsMin);

        // рассинхрон: сломанные блоки через 1 с снова на месте (ровно то же состояние — пересадка не считается)
        while (!pendingDesync.isEmpty() && pendingDesync.peekFirst().due() <= tickNo) {
            Broke b = pendingDesync.pollFirst();
            if (level == null) continue;
            desyncResults.addLast(level.getBlockState(BlockPos.of(b.pos())) == b.state());
            while (desyncResults.size() > DESYNC_KEEP) desyncResults.pollFirst();
        }
        if (c.detectDesync && desyncResults.size() >= DESYNC_MIN) {
            int back = 0;
            for (boolean x : desyncResults) if (x) back++;
            if (back * 100.0 / desyncResults.size() >= c.desyncPct) {
                String detail = back + " из " + desyncResults.size() + " сломанных блоков вернулись";
                desyncResults.clear();
                if (lag) d.suspect("рассинхрон при лаге", detail, c.suspWeightRollback);
                else if (r == null) r = "Рассинхрон: " + detail;
            }
        }

        // полный инвентарь ≈ 0.55 с подряд
        if (c.panicInventoryFull && p.getInventory().getFreeSlot() == -1) {
            if (++invFullTicks >= 11 && r == null) r = "Инвентарь полон";
        } else invFullTicks = 0;

        // табло: перезагрузка сервера, Jacob's Contest
        if (level != null && tickNo % 20 == 0 && (c.detectRestart || c.detectJacob)) {
            List<String> lines = sidebar(level);
            // v1.14 диагностика (только журнал): табло не читается минуту — «Перезагрузка» и Jacob по табло не сработают
            if (lines.size() <= 1) {
                long ms = System.currentTimeMillis();
                if (boardMissingSince < 0) boardMissingSince = ms;
                else if (c.diagScoreboard && !boardLogged && ms - boardMissingSince >= 60_000) {
                    boardLogged = true;
                    EventLog.log("DIAG", "табло (sidebar) не читается 60 с: перезагрузка по табло и Jacob's Contest не работают");
                }
            } else { boardMissingSince = -1; boardLogged = false; }
            if (r == null && c.detectRestart) {
                int sec = ServerText.closingSeconds(lines);
                if (sec >= 0 && sec < 30) r = "Сервер перезагружается через " + sec + " с (табло)";
            }
            if (c.detectJacob && c.jacobLimit > 0) {
                long got = ServerText.jacobCollected(lines);
                if (got >= c.jacobLimit) {
                    EventLog.log("JACOB", "собрано " + got + " ≥ " + c.jacobLimit);
                    MacroManager.INSTANCE.stopByLimit(mc, "§a✔ Jacob's Contest: собрано " + got + " ≥ " + c.jacobLimit + " — стоп");
                    return null;
                }
            }
        }

        // банвейв (данные собирает BanwaveWatch в фоне)
        if (r == null && c.detectBanwave) {
            long b = BanwaveWatch.bans();
            if (b >= c.banwaveThreshold)
                r = "Банвейв: " + b + " банов за " + c.banwaveWindowMin + " мин (порог " + c.banwaveThreshold + ")";
        }
        return r;
    }

    private static double dist3(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx, dy = ay - by, dz = az - bz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Над головой и ≥ 3 из 4 сторон (на уровне ног или головы) — бедрок/барьер. */
    private static String cage(LocalPlayer p, ClientLevel level) {
        BlockPos feet = p.blockPosition();
        boolean top = cageBlock(level.getBlockState(feet.above(2))) || cageBlock(level.getBlockState(feet.above(3)));
        if (!top) return null;
        int sides = 0;
        for (var dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            if (cageBlock(level.getBlockState(feet.relative(dir))) || cageBlock(level.getBlockState(feet.above().relative(dir)))) sides++;
        }
        return sides >= 3 ? "Клетка из бедрока/барьера вокруг игрока (" + sides + " из 4 сторон и сверху)" : null;
    }

    /** Строки табло (sidebar) сверху вниз, без кодов цвета. Заголовок — первой строкой. */
    static List<String> sidebar(ClientLevel level) {
        var sb = level.getScoreboard();
        var obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
        List<String> out = new ArrayList<>();
        if (obj == null) return out;
        out.add(ServerText.clean(obj.getDisplayName().getString()));
        List<PlayerScoreEntry> list = new ArrayList<>(sb.listPlayerScores(obj));
        list.removeIf(PlayerScoreEntry::isHidden);
        list.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed().thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER));
        for (int i = 0; i < list.size() && i < 15; i++) {
            var e = list.get(i);
            var text = e.display() != null ? e.display() : PlayerTeam.formatNameForTeam(sb.getPlayersTeam(e.owner()), e.ownerName());
            out.add(ServerText.clean(text.getString()));
        }
        return out;
    }

    private static String clip(String s) {
        s = ServerText.clean(s);
        return s.length() <= 60 ? s : s.substring(0, 59) + "…";
    }
}
