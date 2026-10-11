package com.farmmacro.mixin;

import com.farmmacro.camera.Freelook;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** v1.12 Freelook: камера берёт yaw/pitch freelook вместо взгляда своего игрока (Camera.alignWithEntity, javap 26.1.2). */
@Mixin(Camera.class)
public class FreelookCameraMixin {

    @Redirect(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
    private float farmmacro$yaw(Entity e, float pt) {
        return mine(e) ? Freelook.yaw() : e.getViewYRot(pt);
    }

    @Redirect(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
    private float farmmacro$pitch(Entity e, float pt) {
        return mine(e) ? Freelook.pitch() : e.getViewXRot(pt);
    }

    private static boolean mine(Entity e) {
        try { return Freelook.active() && e == Minecraft.getInstance().player; }
        catch (Throwable t) { return false; }
    }
}
