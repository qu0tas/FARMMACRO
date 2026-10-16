package com.farmmacro.mixin;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** MASTER (including panic alarms), PLAYER, UI and music are intentionally untouched. */
@Mixin(SoundEngine.class)
public class PerformanceSoundMixin {
 @Inject(method="calculateVolume(FLnet/minecraft/sounds/SoundSource;)F",at=@At("RETURN"),cancellable=true)
 private void kaelix$volume(float volume,SoundSource source,CallbackInfoReturnable<Float> ci){ci.setReturnValue(com.farmmacro.visual.PerformanceMode.worldVolume(ci.getReturnValueF(),source));}
}
