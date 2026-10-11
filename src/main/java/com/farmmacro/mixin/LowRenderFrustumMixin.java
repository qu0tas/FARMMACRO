package com.farmmacro.mixin;

import com.farmmacro.visual.LowRender;
import com.farmmacro.visual.LowRenderMath;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * v1.12 Low render: всё, что дальше радиуса от игрока, — «вне поля зрения». {@code isVisible(AABB)} — секции
 * (Octree$Leaf, SectionOcclusionGraph), сущности; {@code cubeInFrustum(BoundingBox)} — ветки октодерева секций:
 * −2 «целиком внутри» (дети без проверки), −1 «пересекает», ≥ 0 «снаружи» (javap Octree$Branch.visitNodes 26.1.2).
 */
@Mixin(Frustum.class)
public class LowRenderFrustumMixin {

    @Inject(method = "isVisible(Lnet/minecraft/world/phys/AABB;)Z", at = @At("HEAD"), cancellable = true)
    private void farmmacro$lowRenderAabb(AABB b, CallbackInfoReturnable<Boolean> cir) {
        if (!LowRender.active()) return;
        if (LowRender.classify(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ) == LowRenderMath.OUTSIDE) cir.setReturnValue(false);
    }

    @Inject(method = "cubeInFrustum(Lnet/minecraft/world/level/levelgen/structure/BoundingBox;)I", at = @At("RETURN"), cancellable = true)
    private void farmmacro$lowRenderBox(BoundingBox b, CallbackInfoReturnable<Integer> cir) {
        if (!LowRender.active()) return;
        int k = LowRender.classify(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
        if (k == LowRenderMath.OUTSIDE) cir.setReturnValue(0);
        else if (k == LowRenderMath.PARTIAL && cir.getReturnValue() == -2) cir.setReturnValue(-1);
    }
}
