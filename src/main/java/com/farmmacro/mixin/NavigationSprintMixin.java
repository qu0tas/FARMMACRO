package com.farmmacro.mixin;

import com.farmmacro.route.NavigationInput;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keyboard cruise/braking can resemble a double-W. Sprint belongs to the explicit sprint intent. */
@Mixin(LocalPlayer.class)
public abstract class NavigationSprintMixin {
    @Shadow protected int sprintTriggerTime;
    @Inject(method="aiStep",at=@At("HEAD"))
    private void farmmacro$explicitFlightSprint(CallbackInfo ci){
        var p=(LocalPlayer)(Object)this;
        if(NavigationInput.busy()&&p.getAbilities().flying)sprintTriggerTime=0;
    }
}
