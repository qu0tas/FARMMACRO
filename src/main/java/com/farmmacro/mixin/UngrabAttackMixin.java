package com.farmmacro.mixin;

import com.farmmacro.camera.Ungrab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * v1.13.1: зажатая ЛКМ ломает блоки и при отпущенном курсоре. В 26.1.2 в handleKeybinds ровно один вызов
 * isMouseGrabbed — условие continueAttack (проверено javap -c).
 */
@Mixin(Minecraft.class)
public class UngrabAttackMixin {
    // javap 26.1.2: one local hotbar setSelectedSlot invocation in handleKeybinds.
    @Redirect(method="handleKeybinds",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"))
    private void farmmacro$manualHotbarSlot(net.minecraft.world.entity.player.Inventory inventory,int slot){
        com.farmmacro.panic.AutomationSafety.INSTANCE.selectLocalSlot(inventory,slot,false);
    }

    @Redirect(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;isMouseGrabbed()Z"))
    private boolean farmmacro$grabbedForAttack(MouseHandler mouse) {
        return mouse.isMouseGrabbed() || Ungrab.attackWithoutGrab();
    }
}
