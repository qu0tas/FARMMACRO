package com.farmmacro.mixin;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Only local block-breaking visual debris. Never suppresses server particles or Tracker evidence. */
@Mixin(ClientLevel.class)
public class PerformanceParticlesMixin {
 @Inject(method="addDestroyBlockEffect",at=@At("HEAD"),cancellable=true)
 private void kaelix$destroy(BlockPos at,BlockState state,CallbackInfo ci){if(com.farmmacro.visual.PerformanceMode.hideBlockParticles())ci.cancel();}
 @Inject(method="addBreakingBlockEffect",at=@At("HEAD"),cancellable=true)
 private void kaelix$crack(BlockPos at,Direction face,CallbackInfo ci){if(com.farmmacro.visual.PerformanceMode.hideBlockParticles())ci.cancel();}
}
