package com.farmmacro.mixin;

import com.farmmacro.macro.MacroManager;
import com.farmmacro.panic.PanicDetector;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class PanicPacketMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("FarmMacro/PanicPacketMixin");

    // Ж: Server-forced rotation
    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void onPlayerPositionLook(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        boolean playing = MacroManager.INSTANCE.isPlaying();
        LOGGER.debug("[PanicPacketMixin] PlayerPositionLookS2CPacket получен, playing={}", playing);
        if (!playing) return;
        LOGGER.warn("[PanicPacketMixin] PlayerPositionLook пришёл ВО ВРЕМЯ макроса! -> notifyServerRotation()");
        PanicDetector.INSTANCE.notifyServerRotation();
    }

    // З: Potion add
    @Inject(method = "handleUpdateMobEffect", at = @At("HEAD"))
    private void onEntityStatusEffect(ClientboundUpdateMobEffectPacket packet, CallbackInfo ci) {
        boolean playing = MacroManager.INSTANCE.isPlaying();
        LOGGER.debug("[PanicPacketMixin] EntityStatusEffect (добавление): entityId={} playing={}",
                packet.getEntityId(), playing);
        if (!playing) return;
        LOGGER.warn("[PanicPacketMixin] Эффект добавлен во время макроса: entityId={}", packet.getEntityId());
        PanicDetector.INSTANCE.notifyPotionEffect();
    }

    // З: Potion remove
    @Inject(method = "handleRemoveMobEffect", at = @At("HEAD"))
    private void onRemoveEntityStatusEffect(ClientboundRemoveMobEffectPacket packet, CallbackInfo ci) {
        boolean playing = MacroManager.INSTANCE.isPlaying();
        LOGGER.debug("[PanicPacketMixin] RemoveEntityStatusEffect: playing={}", playing);
        if (!playing) return;
        LOGGER.warn("[PanicPacketMixin] Эффект снят во время макроса");
        PanicDetector.INSTANCE.notifyPotionEffect();
    }
}
