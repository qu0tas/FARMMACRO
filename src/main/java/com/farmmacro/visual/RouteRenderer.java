package com.farmmacro.visual;

import com.farmmacro.config.ModConfig;
import com.farmmacro.macro.MacroFrame;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.util.Guard;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Маршрут текущего макроса в мире: светящаяся лента по кадрам, стрелки, маркеры старта/остановки/текущей позиции,
 * участки ЛКМ/ПКМ, отметки прыжка и приседания.
 *
 * Рендер (26.1.2, см. CONTEXT.md 4.0):
 *  • {@code LevelRenderEvents.COLLECT_SUBMITS} + {@code submitCustomGeometry};
 *  • ширина GL-линий не используется — лента из квадов, повёрнутых к камере;
 *  • обычный проход: {@code RenderTypes.debugQuads()} (тест глубины, без записи, обе стороны);
 *  • «сквозь стены»: {@code RenderTypes.textBackgroundSeeThrough()} — POSITION_COLOR_LIGHTMAP, QUADS, без теста глубины,
 *    отсечение граней включено, поэтому каждый квад пишется в обоих порядках обхода; шейдер отбрасывает alpha &lt; 0.1.
 *
 * Геометрия ({@link RoutePath}) кешируется и пересчитывается только при смене макроса;
 * во время записи — дёшево (только прореживание) раз в 10 тиков, после записи — полное упрощение.
 */
public final class RouteRenderer {
    private RouteRenderer() {}

    private static final String GUARD = "visual/route";
    private static final float LIFT = 0.06f;            // над землёй
    private static final int FULL_LIGHT = 0xF000F0;
    private static final int MAX_SEGMENTS = 12_000;

    // ── кеш ──
    private static RoutePath path;
    private static MacroFrame keyFirst, keyLast;
    private static int keySize = -1;
    private static boolean keyLive;
    private static double keyArrowSpacing = -1;
    private static long lastLiveBuildMs;
    /** Измерение, в котором маршрут появился (в файле макроса его нет). */
    private static ResourceKey<Level> routeDim;

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
            if (Guard.isDisabled(GUARD)) return;
            Guard.runOrDisable(GUARD, () -> collect(ctx));
        });
    }

    /** Текущая упрощённая геометрия (для отладки/статистики). */
    public static RoutePath currentPath() { return path; }

    private static void updateCache(MacroManager m, Minecraft mc, ModConfig c) {
        List<MacroFrame> frames = m.getFrames();
        int n = frames.size();
        boolean live = m.isRecording();
        if (n == 0) { path = null; keySize = 0; keyFirst = keyLast = null; return; }
        MacroFrame first = frames.get(0), last = frames.get(n - 1);
        boolean sameMacro = first == keyFirst;
        boolean unchanged = sameMacro && n == keySize && last == keyLast && live == keyLive
                && keyArrowSpacing == c.routeArrowSpacing;
        if (unchanged) return;
        if (live && sameMacro && System.currentTimeMillis() - lastLiveBuildMs < 500) return;   // запись: не чаще 2 раз/с
        if (!sameMacro && mc.level != null) routeDim = mc.level.dimension();
        long t0 = System.nanoTime();
        path = RoutePath.build(frames, 0.25, 0.08, 4.0, c.routeArrowSpacing, !live);
        lastLiveBuildMs = System.currentTimeMillis();
        keyFirst = first; keyLast = last; keySize = n; keyLive = live; keyArrowSpacing = c.routeArrowSpacing;
        if (!live) com.farmmacro.FarmMacroMod.LOGGER.info("Маршрут: {} кадров → {} точек, {} стрелок, {} отметок за {} мс",
                n, path.size, path.arrows, path.marks, (System.nanoTime() - t0) / 1_000_000);
    }

    private static void collect(LevelRenderContext ctx) {
        ModConfig c = ModConfig.INSTANCE;
        if (!c.routeEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        MacroManager m = MacroManager.INSTANCE;
        boolean playing = m.isPlaying() || m.isCountingDown();
        if ("playing".equals(c.routeMode) && !playing) return;

        updateCache(m, mc, c);
        RoutePath p = path;
        if (p == null || p.size == 0) return;
        if (routeDim != null && !routeDim.equals(mc.level.dimension())) return;

        Vec3 cam = ctx.levelState().cameraRenderState.pos;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 me = player.getPosition(pt);

        // сдвиг «относительно точки запуска» (как у детектора схода с маршрута)
        double ox = 0, oy = 0, oz = 0;
        if (c.routeRelative && m.isPlaying()) { ox = m.getOffsetX(); oy = m.getOffsetY(); oz = m.getOffsetZ(); }
        final float sx = (float) (ox - cam.x), sy = (float) (oy - cam.y) + LIFT, sz = (float) (oz - cam.z);
        final float px0 = (float) (me.x - ox), pz0 = (float) (me.z - oz), py0 = (float) (me.y - oy);
        final float r2 = (float) (c.routeRadius * c.routeRadius);
        // камера в координатах маршрута (для поворота ленты к камере)
        final float cx = (float) (cam.x - ox), cy = (float) (cam.y - oy) - LIFT, cz = (float) (cam.z - oz);

        final boolean recording = m.isRecording();
        final int cur = playing ? m.getPlaybackIndex() : -1;
        final float w = (float) c.routeWidth * 0.5f;
        final boolean actions = c.routeShowActions;
        final String xray = c.routeSeeThrough;
        long ms = System.currentTimeMillis();
        final float pulse = (float) (0.65 + 0.35 * Math.sin(ms / 220.0));

        PoseStack ps = ctx.poseStack();
        ps.pushPose();
        try {
            ps.translate(sx, sy, sz);
            Builder normal = new Builder(false, 1f);
            ctx.submitNodeCollector().submitCustomGeometry(ps, RenderTypes.debugQuads(), (pose, vc) ->
                    emitAll(normal.with(pose, vc), p, c, px0, py0, pz0, r2, cx, cy, cz, cur, recording, w, actions, pulse, m));
            if (!"off".equals(xray)) {
                Builder see = new Builder(true, "full".equals(xray) ? 0.85f : 0.32f);
                ctx.submitNodeCollector().submitCustomGeometry(ps, RenderTypes.textBackgroundSeeThrough(), (pose, vc) ->
                        emitAll(see.with(pose, vc), p, c, px0, py0, pz0, r2, cx, cy, cz, cur, recording, w, actions, pulse, m));
            }
        } finally {
            ps.popPose();
        }
    }

    // ── цвета ──
    private static final int C_AHEAD = 0x6DD3FF, C_PASSED = 0x8A8F99, C_REC = 0xFF4545;
    private static final int C_ATTACK = 0xFFA23A, C_USE = 0xC77DFF;
    private static final int C_START = 0x4DFF88, C_STOP = 0xFF4D4D, C_CUR = 0xFFF6B0;
    private static final int C_JUMP = 0xFFE14D, C_SNEAK = 0xFF6FCF;

    private static void emitAll(Builder b, RoutePath p, ModConfig c, float px, float py, float pz, float r2,
                                float cx, float cy, float cz, int cur, boolean rec, float w, boolean actions,
                                float pulse, MacroManager m) {
        // лента (не больше MAX_SEGMENTS за кадр — защита от огромных хаотичных маршрутов)
        int drawn = 0;
        for (int i = 1; i < p.size && drawn < MAX_SEGMENTS; i++) {
            float mx = (p.x[i] + p.x[i - 1]) * 0.5f - px, mz = (p.z[i] + p.z[i - 1]) * 0.5f - pz;
            if (mx * mx + mz * mz > r2) continue;
            drawn++;
            int f0 = p.frame[i - 1], f1 = p.frame[i];
            int base = segColor(p.flags[i], rec, actions);
            if (cur < 0 || f1 <= cur) {
                boolean passed = cur >= 0 && !rec;
                ribbon(b, p.x[i - 1], p.y[i - 1], p.z[i - 1], p.x[i], p.y[i], p.z[i], cx, cy, cz, w,
                        passed ? dim(base) : base, passed ? 0.40f : 0.90f);
            } else if (f0 >= cur) {
                ribbon(b, p.x[i - 1], p.y[i - 1], p.z[i - 1], p.x[i], p.y[i], p.z[i], cx, cy, cz, w, base, 0.90f);
            } else {
                // отрезок, на котором сейчас воспроизведение: делим
                float t = (float) (cur - f0) / Math.max(1, f1 - f0);
                float qx = p.x[i - 1] + (p.x[i] - p.x[i - 1]) * t, qy = p.y[i - 1] + (p.y[i] - p.y[i - 1]) * t,
                        qz = p.z[i - 1] + (p.z[i] - p.z[i - 1]) * t;
                ribbon(b, p.x[i - 1], p.y[i - 1], p.z[i - 1], qx, qy, qz, cx, cy, cz, w, dim(base), 0.40f);
                ribbon(b, qx, qy, qz, p.x[i], p.y[i], p.z[i], cx, cy, cz, w, base, 0.90f);
            }
        }
        // стрелки
        if (c.routeArrows && !rec) {
            float s = Math.max(0.18f, w * 3.2f);
            for (int i = 0; i < p.arrows; i++) {
                float dx = p.ax[i] - px, dz = p.az[i] - pz;
                if (dx * dx + dz * dz > r2) continue;
                boolean passed = cur >= 0 && p.aFrame[i] <= cur;
                chevron(b, p.ax[i], p.ay[i] + 0.01f, p.az[i], p.adx[i], p.adz[i], s,
                        passed ? dim(C_AHEAD) : 0xFFFFFF, passed ? 0.35f : 0.85f);
            }
        }
        // прыжок / приседание
        if (actions) {
            for (int i = 0; i < p.marks; i++) {
                float dx = p.mx[i] - px, dz = p.mz[i] - pz;
                if (dx * dx + dz * dz > r2) continue;
                boolean passed = cur >= 0 && p.mFrame[i] <= cur;
                billboard(b, p.mx[i], p.my[i] + 0.12f, p.mz[i], cx, cy, cz, 0.07f,
                        p.mType[i] == 0 ? C_JUMP : C_SNEAK, passed ? 0.35f : 0.9f);
            }
        }
        // маркеры: старт, точка остановки, текущая позиция
        if (!rec) {
            beacon(b, p.x[0], p.y[0], p.z[0], cx, cy, cz, C_START, 0.9f);
        }
        if (m.hasSavedPosition() && !rec) {
            // точка остановки хранится в мировых координатах — вернуть в координаты маршрута
            float ox = (float) (c.routeRelative && m.isPlaying() ? m.getOffsetX() : 0);
            float oy = (float) (c.routeRelative && m.isPlaying() ? m.getOffsetY() : 0);
            float oz = (float) (c.routeRelative && m.isPlaying() ? m.getOffsetZ() : 0);
            beacon(b, (float) m.getSavedX() - ox, (float) m.getSavedY() - oy, (float) m.getSavedZ() - oz,
                    cx, cy, cz, C_STOP, 0.9f);
        }
        if (cur >= 0 && cur < m.getFrameCount()) {
            MacroFrame f = m.getFrames().get(cur);
            billboard(b, (float) f.x, (float) f.y + 0.15f, (float) f.z, cx, cy, cz, 0.14f + 0.04f * pulse, C_CUR, 0.95f);
        }
    }

    private static int segColor(byte flags, boolean rec, boolean actions) {
        if (rec) return C_REC;
        if (actions && (flags & RoutePath.F_ATTACK) != 0) return C_ATTACK;
        if (actions && (flags & RoutePath.F_USE) != 0) return C_USE;
        return C_AHEAD;
    }

    private static int dim(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, bl = rgb & 255;
        int gray = (r * 3 + g * 6 + bl) / 10;
        return HatColors.mix(rgb, (gray << 16) | (gray << 8) | gray, 0.75f) & 0xFFFFFF;
    }

    // ── примитивы ──

    /** Лента из одного квада, повёрнутого к камере. */
    private static void ribbon(Builder b, float x0, float y0, float z0, float x1, float y1, float z1,
                               float cx, float cy, float cz, float w, int rgb, float a) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float mx = (x0 + x1) * 0.5f, my = (y0 + y1) * 0.5f, mz = (z0 + z1) * 0.5f;
        float vx = cx - mx, vy = cy - my, vz = cz - mz;
        // side = d × v
        float sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx;
        float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1e-5f) { sx = -dz; sy = 0; sz = dx; sl = (float) Math.sqrt(sx * sx + sz * sz); if (sl < 1e-6f) return; }
        sx = sx / sl * w; sy = sy / sl * w; sz = sz / sl * w;
        b.quad(x0 - sx, y0 - sy, z0 - sz, x0 + sx, y0 + sy, z0 + sz,
               x1 + sx, y1 + sy, z1 + sz, x1 - sx, y1 - sy, z1 - sz, rgb, a);
    }

    /** Плоская «галочка» на земле по направлению движения. */
    private static void chevron(Builder b, float x, float y, float z, float dx, float dz, float s, int rgb, float a) {
        float lx = -dz, lz = dx;               // влево
        float tipX = x + dx * s * 0.6f, tipZ = z + dz * s * 0.6f;
        float bx = x - dx * s * 0.4f, bz = z - dz * s * 0.4f;
        float t = s * 0.22f;                    // толщина
        // левое крыло
        b.quad(bx + lx * s * 0.6f, y, bz + lz * s * 0.6f, tipX, y, tipZ,
               tipX - dx * t, y, tipZ - dz * t, bx + lx * s * 0.6f - dx * t, y, bz + lz * s * 0.6f - dz * t, rgb, a);
        // правое крыло
        b.quad(tipX, y, tipZ, bx - lx * s * 0.6f, y, bz - lz * s * 0.6f,
               bx - lx * s * 0.6f - dx * t, y, bz - lz * s * 0.6f - dz * t, tipX - dx * t, y, tipZ - dz * t, rgb, a);
    }

    /** Квадрат, повёрнутый к камере. */
    private static void billboard(Builder b, float x, float y, float z, float cx, float cy, float cz, float r, int rgb, float a) {
        float vx = cx - x, vy = cy - y, vz = cz - z;
        float l = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (l < 1e-4f) return;
        vx /= l; vy /= l; vz /= l;
        // right = up × v, up' = v × right
        float rx = vz, ry = 0, rz = -vx;
        float rl = (float) Math.sqrt(rx * rx + rz * rz);
        if (rl < 1e-4f) { rx = 1; rz = 0; rl = 1; }
        rx = rx / rl * r; rz = rz / rl * r;
        float ux = vy * rz - vz * ry, uy = vz * rx - vx * rz, uz = vx * ry - vy * rx;
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux = ux / ul * r; uy = uy / ul * r; uz = uz / ul * r;
        b.quad(x - rx - ux, y - ry - uy, z - rz - uz, x + rx - ux, y + ry - uy, z + rz - uz,
               x + rx + ux, y + ry + uy, z + rz + uz, x - rx + ux, y - ry + uy, z - rz + uz, rgb, a);
    }

    /** Маркер: вертикальный столбик + ромб сверху. */
    private static void beacon(Builder b, float x, float y, float z, float cx, float cy, float cz, int rgb, float a) {
        ribbon(b, x, y, z, x, y + 1.6f, z, cx, cy, cz, 0.05f, rgb, a * 0.8f);
        billboard(b, x, y + 1.75f, z, cx, cy, cz, 0.18f, rgb, a);
        billboard(b, x, y + 0.05f, z, cx, cy, cz, 0.22f, rgb, a * 0.6f);
    }

    /** Пишет вершины в нужном формате; для прохода «сквозь стены» — оба порядка обхода и свет. */
    private static final class Builder {
        private final boolean seeThrough;
        private final float alphaMul;
        private PoseStack.Pose pose;
        private VertexConsumer vc;

        Builder(boolean seeThrough, float alphaMul) { this.seeThrough = seeThrough; this.alphaMul = alphaMul; }

        Builder with(PoseStack.Pose pose, VertexConsumer vc) { this.pose = pose; this.vc = vc; return this; }

        void quad(float x0, float y0, float z0, float x1, float y1, float z1,
                  float x2, float y2, float z2, float x3, float y3, float z3, int rgb, float a) {
            float al = a * alphaMul;
            if (seeThrough) al = Math.max(0.11f, al);       // шейдер отбрасывает alpha < 0.1
            int argb = (Math.min(255, Math.round(al * 255)) << 24) | (rgb & 0xFFFFFF);
            v(x0, y0, z0, argb); v(x1, y1, z1, argb); v(x2, y2, z2, argb); v(x3, y3, z3, argb);
            if (seeThrough) { v(x3, y3, z3, argb); v(x2, y2, z2, argb); v(x1, y1, z1, argb); v(x0, y0, z0, argb); }
        }

        private void v(float x, float y, float z, int argb) {
            VertexConsumer c = vc.addVertex(pose, x, y, z).setColor(argb);
            if (seeThrough) c.setLight(FULL_LIGHT);
        }
    }
}
