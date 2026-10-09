package com.farmmacro.mixin;

import com.farmmacro.macro.MacroManager;
import com.farmmacro.panic.PanicDetector;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityStatusEffectS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.RemoveEntityStatusEffectS2CPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class PanicPacketMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("FarmMacro/PanicPacketMixin");

    // Ж: Server-forced rotation
    @Inject(method = "onPlayerPositionLook", at = @At("HEAD"))
    private void onPlayerPositionLook(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
        boolean playing = MacroManager.INSTANCE.isPlaying();
        LOGGER.debug("[PanicPacketMixin] PlayerPositionLookS2CPacket получен, playing={}", playing);
        if (!playing) return;
        LOGGER.warn("[PanicPacketMixin] PlayerPositionLook пришёл ВО ВРЕМЯ макроса! -> notifyServerRotation()");
        PanicDetector.INSTANCE.notifyServerRotation();
    }

    // З: Potion add
    @Inject(method = "onEntityStatusEffect", at = @At("HEAD"))
    private void onEntityStatusEffect(EntityStatusEffectS2CPacket packet, CallbackInfo ci) {
        boolean playing = MacroManager.INSTANCE.isPlaying();
        LOGGER.debug("[PanicPacketMixin] EntityStatusEffect (добавление): entityId={} playing={}",
                packet.getEntityId(), playing);
        if (!playing) return;
        LOGGER.warn("[PanicPacketMixin] Эффект добавлен во время макроса: entityId={}", packet.getEntityId());
        PanicDetector.INSTANCE.notifyPotionEffect();
    }

    // З: Potion remove
    @Inject(method = "onRemoveEntityStatusEffect", at = @At("HEAD"))
    private void onRemoveEntityStatusEffect(RemoveEntityStatusEffectS2CPacket packet, CallbackInfo ci) {
        boolean playing = MacroManager.INSTANCE.isPlaying();
        LOGGER.debug("[PanicPacketMixin] RemoveEntityStatusEffect: playing={}", playing);
        if (!playing) return;
        LOGGER.warn("[PanicPacketMixin] Эффект снят во время макроса");
        PanicDetector.INSTANCE.notifyPotionEffect();
    }
}
