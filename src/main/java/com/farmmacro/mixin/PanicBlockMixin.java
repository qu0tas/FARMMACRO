package com.farmmacro.mixin;

import com.farmmacro.macro.MacroManager;
import com.farmmacro.panic.PanicDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class PanicBlockMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("FarmMacro/PanicBlockMixin");

    @Inject(method = "setBlocksDirty", at = @At("HEAD"))
    private void onBlockUpdate(BlockPos pos, BlockState old, BlockState updated, CallbackInfo ci) {
        if (!MacroManager.INSTANCE.isPlaying()) return;
        if (!old.isAir() || updated.isAir()) return;
        if (!updated.isRedstoneConductor((ClientLevel)(Object)this, pos)) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        Vec3 eyes = client.player.getEyePosition();
        double dx = pos.getX() + 0.5 - eyes.x;
        double dy = pos.getY() + 0.5 - eyes.y;
        double dz = pos.getZ() + 0.5 - eyes.z;
        double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);

        double radius = com.farmmacro.config.ModConfig.INSTANCE.blockDetectRadius;
        LOGGER.debug("[PanicBlockMixin] Твёрдый блок появился: pos={} dist={} (порог {}) block={}",
                pos, String.format("%.2f", dist), radius, updated.getBlock());

        if (dist < radius) {
            LOGGER.warn("[PanicBlockMixin] Блок В ЛИЦО! pos={} dist={} block={} | глаза=({},{},{})",
                    pos, String.format("%.2f", dist), updated.getBlock(),
                    String.format("%.2f", eyes.x),
                    String.format("%.2f", eyes.y),
                    String.format("%.2f", eyes.z));
            PanicDetector.INSTANCE.notifyBlockAppearedInFace();
        }
    }
}
