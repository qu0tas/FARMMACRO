package com.farmmacro.visual;

import com.farmmacro.config.ModConfig;
import com.farmmacro.util.Guard;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * «China Hat»: полупрозрачный плоский конус над головой игрока. Только клиент, видна только мне.
 *
 * Как рисуется в 26.1.2:
 *  • событие Fabric {@code LevelRenderEvents.COLLECT_SUBMITS} (после того как сущности сдали свою геометрию,
 *    до отрисовки) — добавляем свой «submit» через {@code SubmitNodeCollector.submitCustomGeometry};
 *  • {@code RenderTypes.debugQuads()}: пайплайн debug_quads = шейдер core/position_color, POSITION_COLOR + QUADS,
 *    смешивание TRANSLUCENT, без отсечения граней (видно обе стороны), тест глубины LEQUAL без записи глубины,
 *    сортировка квадов при загрузке. Своя RenderType не нужна (RenderType.create в 26.1.2 не публичный).
 *  • PoseStack из контекста — единичный, камера уже в матрице вида, поэтому координаты = мир − позиция камеры.
 *  • Треугольники конуса — вырожденные квады (вершина повторена дважды).
 */
public final class ChinaHatRenderer {
    private ChinaHatRenderer() {}

    private static final String GUARD = "visual/china_hat";

    /** Сглаженная высота макушки (приседание/плавание меняют её скачком). */
    private static final Map<AbstractClientPlayer, float[]> SMOOTH = new WeakHashMap<>();
    private static long lastNs;

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
            if (Guard.isDisabled(GUARD)) return;
            Guard.runOrDisable(GUARD, () -> collect(ctx));
        });
    }

    private static void collect(LevelRenderContext ctx) {
        ModConfig c = ModConfig.INSTANCE;
        if (!c.hatEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = ctx.levelState().cameraRenderState.pos;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.25f, (now - lastNs) / 1e9f);
        lastNs = now;
        double time = (System.currentTimeMillis() % 3_600_000L) / 1000.0;

        for (AbstractClientPlayer p : level.players()) {
            boolean self = p == mc.player;
            if (!self && !c.hatAllPlayers) continue;
            if (!self && p.isInvisible()) continue;
            if (p == camera.entity() && !camera.isDetached() && !c.hatFirstPerson) continue;
            submitHat(ctx, p, cam, pt, dt, time, c);
        }
    }

    private static void submitHat(LevelRenderContext ctx, AbstractClientPlayer p, Vec3 cam, float pt, float dt,
                                  double time, ModConfig c) {
        // плавная высота макушки
        float target = p.getBbHeight();
        float[] s = SMOOTH.computeIfAbsent(p, k -> new float[]{target});
        s[0] += (target - s[0]) * (1f - (float) Math.exp(-dt * 14f));

        Vec3 pos = p.getPosition(pt);
        float x = (float) (pos.x - cam.x);
        float y = (float) (pos.y - cam.y) + s[0] + (float) c.hatOffset;
        float z = (float) (pos.z - cam.z);

        float headYaw = Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot);
        double spin = time * c.hatSpeed * 0.6;                 // оборотов… условно: 1.0 ≈ 1 круг за ~10 с
        double baseAngle = Math.toRadians(-headYaw) + spin;

        int segments = Math.max(8, Math.min(96, c.hatSegments));
        float r = (float) c.hatRadius, h = (float) c.hatHeight;
        float alpha = Math.max(0f, Math.min(1f, c.hatOpacity / 100f));
        if (alpha <= 0.003f) return;
        int a = Math.round(alpha * 255);
        int edgeA = Math.min(255, Math.round((alpha * 1.6f + 0.18f) * 255));
        String style = c.hatStyle;
        int c1 = HatColors.rgb(c.hatColor1), c2 = HatColors.rgb(c.hatColor2);
        double hueShift = time * c.hatSpeed * 0.05;

        PoseStack ps = ctx.poseStack();
        ps.pushPose();
        try {
            ps.translate(x, y, z);
            ctx.submitNodeCollector().submitCustomGeometry(ps, RenderTypes.debugQuads(), (pose, vc) -> {
                float lip = 0.93f;    // где начинается светлая кромка (доля радиуса)
                for (int i = 0; i < segments; i++) {
                    double t0 = (double) i / segments, t1 = (double) (i + 1) / segments;
                    double a0 = baseAngle + t0 * Math.PI * 2, a1 = baseAngle + t1 * Math.PI * 2;
                    float cx0 = (float) Math.cos(a0), sz0 = (float) Math.sin(a0);
                    float cx1 = (float) Math.cos(a1), sz1 = (float) Math.sin(a1);
                    int col0 = HatColors.at(style, c1, c2, t0, hueShift);
                    int col1 = HatColors.at(style, c1, c2, t1, hueShift);
                    int apex = HatColors.lighten(HatColors.mix(col0, col1, 0.5f), 0.35f);

                    // скат конуса: вершина → кромка (вырожденный квад = треугольник)
                    float lr = r * lip, ly = h * (1 - lip);
                    v(vc, pose, 0, h, 0, apex, a);
                    v(vc, pose, 0, h, 0, apex, a);
                    v(vc, pose, cx1 * lr, ly, sz1 * lr, col1, a);
                    v(vc, pose, cx0 * lr, ly, sz0 * lr, col0, a);

                    // светлая кромка по краю полей
                    int e0 = HatColors.lighten(col0, 0.65f), e1 = HatColors.lighten(col1, 0.65f);
                    v(vc, pose, cx0 * lr, ly, sz0 * lr, e0, edgeA);
                    v(vc, pose, cx1 * lr, ly, sz1 * lr, e1, edgeA);
                    v(vc, pose, cx1 * r, 0, sz1 * r, e1, edgeA);
                    v(vc, pose, cx0 * r, 0, sz0 * r, e0, edgeA);
                }
            });
        } finally {
            ps.popPose();
        }
    }

    private static void v(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, int rgb, int alpha) {
        vc.addVertex(pose, x, y, z).setColor((alpha << 24) | (rgb & 0xFFFFFF));
    }
}
