package com.farmmacro.route;

import com.farmmacro.config.ModConfig;
import com.farmmacro.visual.RouteRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.TallFlowerBlock;
import net.minecraft.world.level.block.WitherRoseBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * v1.11 «Авто-маршрут» в редакторе: Ctrl+Shift+ЛКМ по двум углам (или кнопка в меню, тогда и просто Ctrl+ЛКМ) →
 * сканирование области частями по тикам ({@code autoRouteColumnsPerTick} колонок за тик) → {@link AutoRoute#build} →
 * маршрут в буфер (Ctrl+Z откатит). Проблемы рельефа сразу видны красной лентой (редактор пересчитывает Terrain.check).
 */
public final class AutoRouteTool {
    private AutoRouteTool() {}

    private static final Logger LOG = LoggerFactory.getLogger("FarmMacro/AutoRoute");

    private static boolean armed;              // кнопка меню: следующий Ctrl+ЛКМ — авто-маршрут
    private static int[] a, hoverB;
    private static int[] lastA, lastB;
    private static Job job;

    private static final class Job {
        FarmGrid g;
        int ax, az, next;
        String dim;
        /** Разбор и маршрут — в фоновом потоке (большая ферма — секунды), игра не замирает. */
        java.util.concurrent.CompletableFuture<AutoRoute.Result> built;
        long t0;
    }

    public static void arm() { armed = true; }
    /** Из меню: следующий ЛКМ в редакторе — первый угол авто-маршрута. */
    public static void arm(Minecraft mc) {
        SnakeTool.reset();
        a = null; armed = true;
        RouteEditor.msg(mc, "§b⌖ Авто-маршрут: ЛКМ по первому углу фермы, потом по второму §7· Ctrl+Z — отмена");
    }
    /** Ждём угол (взведён из меню или выбран первый угол) — обычный ЛКМ ставит угол, а не точку. */
    public static boolean pending() { return armed || a != null; }
    public static boolean wantsCorner(boolean shift) { return shift || armed || a != null; }
    public static boolean hasLast() { return lastA != null && lastB != null; }
    public static boolean scanning() { return job != null; }

    public static void reset() {
        boolean had = a != null || job != null;
        a = null; hoverB = null; job = null; armed = false;
        if (had) { RouteRenderer.editorCornerA = null; RouteRenderer.editorCornerB = null; RouteRenderer.editorPreview = null; }
    }

    public static boolean cancelIfPending() {
        if (a == null && job == null && !armed) return false;
        reset();
        return true;
    }

    private static int[] block(Vec3 top) {
        return new int[]{Mth.floor(top.x), Mth.floor(top.y - 0.01), Mth.floor(top.z)};
    }

    public static void corner(Minecraft mc, Vec3 at) {
        if (job != null) { RouteEditor.msg(mc, "§7Идёт сканирование — подожди или Ctrl+Z"); return; }
        SnakeTool.reset();
        int[] b = block(at);
        if (a == null) {
            a = b;
            RouteRenderer.editorCornerA = new double[]{a[0] + 0.5, at.y, a[2] + 0.5};
            RouteEditor.msg(mc, "§b⌖ Авто-маршрут, угол 1: " + a[0] + " " + a[2] + " §7· второй угол — ЛКМ, Ctrl+Z — отмена");
            return;
        }
        int[] first = a;
        a = null; hoverB = null; armed = false;
        start(mc, first, b);
    }

    /** Ещё раз по прошлой области (после смены настроек). */
    public static void rerun(Minecraft mc) {
        if (!hasLast() || job != null) return;
        start(mc, lastA, lastB);
    }

    private static void start(Minecraft mc, int[] p, int[] q) {
        ModConfig c = ModConfig.INSTANCE;
        int x0 = Math.min(p[0], q[0]), x1 = Math.max(p[0], q[0]), z0 = Math.min(p[2], q[2]), z1 = Math.max(p[2], q[2]);
        int w = x1 - x0 + 1, d = z1 - z0 + 1;
        int top = Math.max(p[1], q[1]) + 4;
        int bottom = Math.min(p[1], q[1]) - c.autoRouteDepth;
        if (mc.level != null) { bottom = Math.max(bottom, mc.level.getMinY()); top = Math.min(top, mc.level.getMaxY()); }
        int h = top - bottom + 1;
        if (h < 2) { RouteEditor.msg(mc, "§cНе та высота области"); reset(); return; }
        // предела по размеру нет — только память: снимок 3 байта на блок + разбор и поиск пути ≈ 100 байт на колонку
        long need = (long) w * d * h * 3 + (long) w * d * 100;
        Runtime rt = Runtime.getRuntime();
        long avail = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory());
        if ((long) w * d * h > Integer.MAX_VALUE - 8 || need > avail * 0.8) {
            RouteEditor.msg(mc, String.format(Locale.ROOT, "§cОбласть %d×%d×%d: нужно ≈ %d МБ памяти, свободно %d МБ — дай игре больше памяти (-Xmx) "
                    + "или уменьши «Глубину поиска этажей»", w, d, h, need >> 20, avail >> 20));
            reset();
            return;
        }
        Job j = new Job();
        j.g = new FarmGrid(x0, top - h + 1, z0, w, h, d);
        j.ax = p[0]; j.az = p[2];
        j.dim = mc.level != null ? mc.level.dimension().identifier().toString() : "";
        job = j;
        lastA = p; lastB = q;
        RouteRenderer.editorCornerA = new double[]{p[0] + 0.5, p[1] + 1, p[2] + 0.5};
        RouteRenderer.editorCornerB = new double[]{q[0] + 0.5, q[1] + 1, q[2] + 0.5};
        RouteEditor.msg(mc, String.format(Locale.ROOT, "§b⌖ Сканирую %d×%d×%d…", w, d, h));
    }

    /** Из тика редактора: живой второй угол и сканирование частями. */
    public static void tick(Minecraft mc) {
        if (a != null) {
            Vec3 at = RouteEditor.targetPoint(mc);
            if (at != null) {
                int[] b = block(at);
                if (hoverB == null || hoverB[0] != b[0] || hoverB[2] != b[2]) {
                    hoverB = b;
                    RouteRenderer.editorCornerB = new double[]{b[0] + 0.5, at.y, b[2] + 0.5};
                }
            }
        }
        Job j = job;
        if (j == null) return;
        Level level = mc.level;
        if (level == null || !level.dimension().identifier().toString().equals(j.dim)) { reset(); return; }
        if (j.built != null) {
            if (!j.built.isDone()) return;
            job = null;
            AutoRoute.Result res;
            try { res = j.built.join(); }
            catch (RuntimeException e) {
                LOG.warn("Авто-маршрут: ошибка разбора", e);
                RouteEditor.msg(mc, "§c⌖ Авто-маршрут: ошибка разбора — " + e.getMessage());
                RouteRenderer.editorCornerA = null; RouteRenderer.editorCornerB = null;
                return;
            }
            finish(mc, j, res, (System.nanoTime() - j.t0) / 1_000_000);
            return;
        }
        int total = j.g.w * j.g.d;
        // большие фермы: не дольше ≈ 10 с (200 тиков), даже если «Колонок за тик» мало
        int budget = Math.max(Math.max(16, ModConfig.INSTANCE.autoRouteColumnsPerTick), (total + 199) / 200);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int n = 0; n < budget && j.next < total; n++, j.next++) {
            int x = j.g.x0 + j.next / j.g.d, z = j.g.z0 + j.next % j.g.d;
            if (!level.hasChunkAt(x, z)) { j.g.unloaded++; continue; }
            for (int y = j.g.y0; y < j.g.y0 + j.g.h; y++) {
                pos.set(x, y, z);
                BlockState st = level.getBlockState(pos);
                VoxelShape sh = st.getCollisionShape(level, pos);
                double t = sh.isEmpty() ? -1 : Math.min(1.5, sh.max(Direction.Axis.Y));
                j.g.set(x, y, z, t, tagOf(st, t));
            }
        }
        if (j.next >= total) {
            AutoRoute.Params p = params();
            j.t0 = System.nanoTime();
            j.built = java.util.concurrent.CompletableFuture.supplyAsync(() -> AutoRoute.build(j.g, p, j.ax, j.az));
        }
    }

    static byte tagOf(BlockState st, double top) {
        Block b = st.getBlock();
        if (b instanceof WitherRoseBlock) return FarmGrid.DANGER;
        if (b instanceof CropBlock || b instanceof NetherWartBlock || b instanceof MushroomBlock || b instanceof PitcherCropBlock)
            return FarmGrid.CROP;
        if (b instanceof SugarCaneBlock || b instanceof CactusBlock || b instanceof StemBlock || b instanceof AttachedStemBlock
                || b instanceof CocoaBlock || b instanceof FlowerBlock || b instanceof TallFlowerBlock || b instanceof FlowerBedBlock
                || st.is(Blocks.PUMPKIN) || st.is(Blocks.CARVED_PUMPKIN) || st.is(Blocks.MELON)) return FarmGrid.CROP_TALL;
        FluidState fl = st.getFluidState();
        if (!fl.isEmpty() && fl.is(FluidTags.LAVA)) return FarmGrid.DANGER;
        if (st.is(BlockTags.FIRE) || st.is(Blocks.MAGMA_BLOCK) || st.is(Blocks.CAMPFIRE) || st.is(Blocks.SOUL_CAMPFIRE)
                || st.is(Blocks.POWDER_SNOW) || st.is(Blocks.SWEET_BERRY_BUSH) || st.is(Blocks.COBWEB)) return FarmGrid.DANGER;
        if (top < 0 && !fl.isEmpty() && fl.is(FluidTags.WATER)) return FarmGrid.WATER;
        if (st.is(BlockTags.CLIMBABLE)) return FarmGrid.CLIMB;
        if (st.is(Blocks.FARMLAND)) return FarmGrid.FARMLAND;
        if (st.is(BlockTags.SAND)) return FarmGrid.SAND;
        if (st.is(Blocks.SOUL_SAND) || st.is(Blocks.SOUL_SOIL)) return FarmGrid.SOUL;
        return FarmGrid.NONE;
    }

    public static AutoRoute.Params params() {
        ModConfig c = ModConfig.INSTANCE;
        AutoRoute.Params p = new AutoRoute.Params();
        p.axis = c.snakeAxis;
        p.stepAuto = c.autoStepAuto;
        p.step = c.snakeStep;
        p.surface = c.autoSurface;
        p.walk = c.autoWalk;
        p.returnToStart = c.autoReturnToStart;
        p.endPause = c.autoEndPause;
        p.rowAction = c.snakeRowAction;
        p.turnAction = c.snakeTurnAction;
        p.maxPoints = RouteBuffer.MAX_POINTS;
        return p;
    }

    private static void finish(Minecraft mc, Job j, AutoRoute.Result r, long ms) {
        ModConfig c = ModConfig.INSTANCE;
        RouteRenderer.editorCornerA = null; RouteRenderer.editorCornerB = null;
        if (r.analysis.error != null) {
            RouteEditor.msg(mc, "§c⌖ Авто-маршрут: " + r.analysis.error);
            LOG.info("Авто-маршрут: {} ({}×{}×{})", r.analysis.error, j.g.w, j.g.d, j.g.h);
            return;
        }
        List<RoutePoint> pts = r.points;
        if (c.snakeOffsetX != 0 || c.snakeOffsetZ != 0) for (RoutePoint p : pts) p.setOffset(c.snakeOffsetX, c.snakeOffsetZ);
        RouteBuffer rb = RouteBuffer.INSTANCE;
        List<RoutePoint> all = new ArrayList<>();
        if (c.snakeAppend && !rb.isEmpty()) for (RoutePoint p : rb.points()) all.add(p.copy());
        all.addAll(pts);
        if (all.size() > RouteBuffer.MAX_POINTS) { RouteEditor.msg(mc, "§cСлишком много точек (" + all.size() + " > " + RouteBuffer.MAX_POINTS + ")"); return; }
        // глубина спусков — по настоящему миру (как у «змейки»)
        if (mc.level != null) {
            TerrainLevel w = new TerrainLevel(mc.level);
            for (int i = 0; i < all.size(); i++) if (all.get(i).drop) Terrain.fillDepth(w, all, i);
        }
        rb.replaceAll(mc, all);
        String sum = r.analysis.summary();
        RouteEditor.msg(mc, "§a⌖ " + sum + " · точек " + pts.size() + (r.looped ? " · круг замкнут" : "") + " §7· Ctrl+Z — откатить");
        if (mc.player != null) {
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§b[FM] Авто-маршрут: §f" + sum
                    + "§7 · точек " + pts.size() + (r.looped ? ", последняя ведёт к старту" : "")));
            if (r.hint != null) mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7[FM] Совет: " + r.hint));
            for (String wmsg : r.warnings) mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e[FM] ⚠ " + wmsg));
            if (r.issues > 0) mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c[FM] Проверка рельефа: "
                    + r.issues + " проблем (красная лента) — " + r.firstIssue));
            if (j.g.unloaded > 0) mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e[FM] ⚠ не загружено колонок: "
                    + j.g.unloaded + " — подойди ближе и повтори"));
        }
        LOG.info("Авто-маршрут: {} | точек {}, предупреждений {}, проблем {}, {}×{}×{}, анализ {} мс",
                sum, pts.size(), r.warnings.size(), r.issues, j.g.w, j.g.d, j.g.h, ms);
    }

    /** Строка для HUD редактора. */
    public static String status() {
        if (job != null && job.built != null) return "⌖ строю маршрут…";
        if (job != null) return String.format(Locale.ROOT, "⌖ сканирую %d %%", (int) (100L * job.next / Math.max(1, job.g.w * job.g.d)));
        if (a != null) {
            if (hoverB == null) return "⌖ авто: угол 1 · Ctrl+ЛКМ — угол 2";
            return "⌖ авто: " + (Math.abs(hoverB[0] - a[0]) + 1) + "×" + (Math.abs(hoverB[2] - a[2]) + 1) + " · Ctrl+ЛКМ — сканировать";
        }
        if (armed) return "⌖ авто-маршрут: Ctrl+ЛКМ — угол 1";
        return null;
    }
}
