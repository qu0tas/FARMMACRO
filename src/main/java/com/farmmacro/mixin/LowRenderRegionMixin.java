package com.farmmacro.mixin;

import com.farmmacro.visual.LowRender;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * v1.12.2 Low render поблочно: RenderSectionRegion — копия мира только для сборки секций (javap 26.1.2:
 * SectionCompiler.compile берёт блоки через getBlockState, жидкость — BlockState.getFluidState, грани соседей —
 * через тот же регион). За радиусом — воздух: блок не строится, а у краевых блоков рисуются боковые грани.
 */
@Mixin(RenderSectionRegion.class)
public class LowRenderRegionMixin {

    @Inject(method = "getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"), cancellable = true)
    private void farmmacro$lowRenderBlock(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (LowRender.hides(pos.getX(), pos.getY(), pos.getZ())) cir.setReturnValue(Blocks.AIR.defaultBlockState());
    }

    @Inject(method = "getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;",
            at = @At("HEAD"), cancellable = true)
    private void farmmacro$lowRenderFluid(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        if (LowRender.hides(pos.getX(), pos.getY(), pos.getZ())) cir.setReturnValue(Fluids.EMPTY.defaultFluidState());
    }
}
