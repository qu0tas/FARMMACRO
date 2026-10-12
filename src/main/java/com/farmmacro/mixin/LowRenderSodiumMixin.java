package com.farmmacro.mixin;

import com.farmmacro.visual.LowRender;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * v1.14.1 Low render с Sodium: Sodium собирает секции сам (ChunkBuilderMeshingTask → LevelSlice), ванильный
 * RenderSectionRegion не вызывается — поэтому раньше с Sodium режим только перезагружал чанки.
 * javap sodium-fabric 0.9.2+mc26.1.2: LevelSlice.getBlockState(III) — единственный источник блоков,
 * getBlockState(BlockPos) и getFluidState(BlockPos) вызывают его. Без Sodium класса нет — @Pseudo, require = 0.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice", remap = false)
public class LowRenderSodiumMixin {

    @Inject(method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void farmmacro$lowRenderSodium(int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
        if (LowRender.hides(x, y, z)) cir.setReturnValue(Blocks.AIR.defaultBlockState());
    }
}
