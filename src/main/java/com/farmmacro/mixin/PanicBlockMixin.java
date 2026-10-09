package com.farmmacro.mixin;

import com.farmmacro.macro.MacroManager;
import com.farmmacro.panic.PanicDetector;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.MinecraftClient;

@Mixin(ClientWorld.class)
public class PanicBlockMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("FarmMacro/PanicBlockMixin");

    @Inject(method = "scheduleBlockRerenderIfNeeded", at = @At("HEAD"))
    private void onBlockUpdate(BlockPos pos, BlockState old, BlockState updated, CallbackInfo ci) {
        if (!MacroManager.INSTANCE.isPlaying()) return;
        if (!old.isAir() || updated.isAir()) return;
        if (!updated.isSolidBlock((ClientWorld)(Object)this, pos)) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        Vec3d eyes = client.player.getEyePos();
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
