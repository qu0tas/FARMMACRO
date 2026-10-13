package com.farmmacro.visual;

import com.farmmacro.config.ModConfig;
import com.farmmacro.util.Guard;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * v1.16.1 «Анимешные щёчки»: румянец на лице модели игрока. Только клиент: видишь только ты (F5, Freelook, инвентарь).
 *
 * Как рисуется в 26.1.2 (javap):
 *  • слой модели через Fabric {@code LivingEntityRenderLayerRegistrationCallback} для каждого {@code AvatarRenderer}
 *    (игроки, широкая и тонкая модели) — щёки двигаются вместе с головой (поворот, наклон, присед, плавание, элитры);
 *  • {@code RenderLayer.submit(PoseStack, SubmitNodeCollector, light, AvatarRenderState, yRot, xRot)}; поза уже в
 *    координатах модели (после scale(-1,-1,1)), {@code head.translateAndRotate} переносит в голову: пиксель = 1/16,
 *    передняя грань — z = −4/16, верх лица — y = −8/16, левый для смотрящего край — x = −4/16 (правая рука модели — −X);
 *  • {@code RenderTypes.entityTranslucent(tex)}: пайплайн entity_translucent — без отсечения граней, ALPHA_CUTOUT 0.1,
 *    формат сущности (цвет, UV, overlay, свет, нормаль);
 *  • свой игрок — {@code AvatarRenderState.id == player.getId()}.
 */
public final class CheeksLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final String GUARD = "visual/cheeks";
    private static final Map<String, Identifier> TEX = new HashMap<>();

    public CheeksLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    public static void register() {
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
            if (renderer instanceof AvatarRenderer<?> avatar) helper.register(new CheeksLayer(avatar));
        });
    }

    private static Identifier tex(String name) {
        return TEX.computeIfAbsent(name, n -> Identifier.fromNamespaceAndPath("farmmacro", "textures/entity/cheeks/" + n + ".png"));
    }

    @Override
    public void submit(PoseStack ps, SubmitNodeCollector out, int light, AvatarRenderState st, float yRot, float xRot) {
        ModConfig c = ModConfig.INSTANCE;
        if (!c.cheeksEnabled || Guard.isDisabled(GUARD)) return;
        if (st.isInvisible || st.isSpectator) return;
        Minecraft mc = Minecraft.getInstance();
        boolean self = mc.player != null && st.id == mc.player.getId();
        if (!self && !c.cheeksAllPlayers) return;
        ModelPart head = getParentModel().head;
        if (!head.visible) return;
        Guard.runOrDisable(GUARD, () -> draw(ps, out, light, st, head, c));
    }

    private static void draw(PoseStack ps, SubmitNodeCollector out, int light, AvatarRenderState st, ModelPart head, ModConfig c) {
        float alpha = CheekMath.alpha(c.cheeksOpacity, c.cheeksPulse, st.ageInTicks);
        if (alpha <= 0.003f) return;
        int a = Math.round(alpha * 255);
        int tint = (a << 24) | (CheekMath.rgb(c.cheeksColor) & 0xFFFFFF);
        int white = (a << 24) | 0xFFFFFF;
        int overlay = LivingEntityRenderer.getOverlayCoords(st, 0f);
        // перед лицом; «поверх второго слоя» — перед слоем шляпы скина (он на 0.5 px дальше)
        float z = -(c.cheeksOverHat ? 4.56f : 4.06f) / 16f;
        float[] l = CheekMath.rect(true, c.cheeksHeight, c.cheeksSpread, c.cheeksSize);
        float[] r = CheekMath.rect(false, c.cheeksHeight, c.cheeksSpread, c.cheeksSize);

        ps.pushPose();
        try {
            head.translateAndRotate(ps);
            submit(ps, out, RenderTypes.entityTranslucent(tex(CheekMath.base(c.cheeksStyle))), l, r, z, tint, overlay, light);
            String deco = CheekMath.deco(c.cheeksStyle);
            if (deco != null)
                submit(ps, out, RenderTypes.entityTranslucent(tex(deco)), l, r, z - 0.02f / 16f, white, overlay, light);
        } finally {
            ps.popPose();
        }
    }

    private static void submit(PoseStack ps, SubmitNodeCollector out, RenderType type, float[] l, float[] r,
                               float z, int argb, int overlay, int light) {
        out.submitCustomGeometry(ps, type, (pose, vc) -> {
            if (l != null) quad(vc, pose, l, z, argb, overlay, light);
            if (r != null) quad(vc, pose, r, z, argb, overlay, light);
        });
    }

    /** Пиксели лица → модель: x = (fx − 4)/16, y = (fy − 8)/16. */
    private static void quad(VertexConsumer vc, PoseStack.Pose pose, float[] q, float z, int argb, int overlay, int light) {
        float x0 = (q[0] - 4) / 16f, y0 = (q[1] - 8) / 16f, x1 = (q[2] - 4) / 16f, y1 = (q[3] - 8) / 16f;
        v(vc, pose, x0, y0, z, q[4], q[5], argb, overlay, light);
        v(vc, pose, x0, y1, z, q[4], q[7], argb, overlay, light);
        v(vc, pose, x1, y1, z, q[6], q[7], argb, overlay, light);
        v(vc, pose, x1, y0, z, q[6], q[5], argb, overlay, light);
    }

    private static void v(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float vv,
                          int argb, int overlay, int light) {
        vc.addVertex(pose, x, y, z).setColor(argb).setUv(u, vv).setOverlay(overlay).setLight(light).setNormal(pose, 0, 0, -1);
    }
}
