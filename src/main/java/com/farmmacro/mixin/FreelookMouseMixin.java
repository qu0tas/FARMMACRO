package com.farmmacro.mixin;

import com.farmmacro.camera.Freelook;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * v1.12 Freelook: в {@code turnPlayer} поворот игрока ({@code LocalPlayer.turn(dx, dy)}, javap 26.1.2) уходит в камеру
 * freelook — чувствительность, инверсия и сглаживание игры уже учтены. Игрок не поворачивается.
 */
@Mixin(MouseHandler.class)
public class FreelookMouseMixin {

    @Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void farmmacro$freelook(LocalPlayer player, double dx, double dy) {
        boolean fl;
        try { fl = Freelook.active(); } catch (Throwable t) { fl = false; }
        if (fl) Freelook.turn(dx, dy);
        else player.turn(dx, dy);
    }
}
