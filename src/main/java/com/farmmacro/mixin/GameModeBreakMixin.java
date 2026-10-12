package com.farmmacro.mixin;

import com.farmmacro.panic.PanicDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * v1.13: игрок сломал блок (клиентское предсказание ломания). Для «Урожай не ломается» (BPS),
 * «Рассинхрон» (блок вернулся) и «Блок на ферме» (свои сломанные блоки не считаются).
 */
@Mixin(MultiPlayerGameMode.class)
public class GameModeBreakMixin {
    @Unique private BlockState farmmacro$before;

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void farmmacro$beforeDestroy(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        var level = Minecraft.getInstance().level;
        farmmacro$before = level != null ? level.getBlockState(pos) : null;
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void farmmacro$afterDestroy(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockState before = farmmacro$before;
        farmmacro$before = null;
        if (Boolean.TRUE.equals(cir.getReturnValue())) PanicDetector.INSTANCE.onPlayerBroke(pos.immutable(), before);
    }
}
