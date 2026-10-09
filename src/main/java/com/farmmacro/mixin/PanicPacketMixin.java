package com.farmmacro.mixin;

import com.farmmacro.panic.PanicDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Пакеты сервера для детекторов паники.
 *
 * Каждый обработчик сначала вызывается в сетевом потоке и сразу перекидывает себя в главный
 * (PacketUtils.ensureRunningOnSameThread). Поэтому на HEAD мы пропускаем вызов из сетевого потока
 * и работаем только во втором, «настоящем» вызове — там можно спокойно читать мир и игрока.
 */
@Mixin(ClientPacketListener.class)
public class PanicPacketMixin {

    private static boolean farmmacro$main() {
        return Minecraft.getInstance().isSameThread();
    }

    // ── Телепорт / поворот сервером: сравниваем состояние до и после применения ──

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void farmmacro$beforeMove(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        if (farmmacro$main()) PanicDetector.INSTANCE.beforeServerMove();
    }

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void farmmacro$afterMove(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        if (farmmacro$main()) PanicDetector.INSTANCE.afterServerMove();
    }

    @Inject(method = "handleRotatePlayer", at = @At("HEAD"))
    private void farmmacro$beforeRotate(ClientboundPlayerRotationPacket packet, CallbackInfo ci) {
        if (farmmacro$main()) PanicDetector.INSTANCE.beforeServerMove();
    }

    @Inject(method = "handleRotatePlayer", at = @At("RETURN"))
    private void farmmacro$afterRotate(ClientboundPlayerRotationPacket packet, CallbackInfo ci) {
        if (farmmacro$main()) PanicDetector.INSTANCE.afterServerMove();
    }

    // ── Блоки рядом с игроком (до применения — в мире ещё старое состояние) ──

    @Inject(method = "handleBlockUpdate", at = @At("HEAD"))
    private void farmmacro$onBlock(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        if (farmmacro$main()) PanicDetector.INSTANCE.onServerBlockChange(packet.getPos(), packet.getBlockState());
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("HEAD"))
    private void farmmacro$onSectionBlocks(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        if (farmmacro$main()) packet.runUpdates(PanicDetector.INSTANCE::onServerBlockChange);
    }

    // ── Эффекты ──

    @Inject(method = "handleUpdateMobEffect", at = @At("HEAD"))
    private void farmmacro$onEffect(ClientboundUpdateMobEffectPacket packet, CallbackInfo ci) {
        if (farmmacro$main())
            PanicDetector.INSTANCE.onEffectAdded(packet.getEntityId(), packet.getEffect(), packet.getEffectAmplifier());
    }

    @Inject(method = "handleRemoveMobEffect", at = @At("HEAD"))
    private void farmmacro$onEffectRemoved(ClientboundRemoveMobEffectPacket packet, CallbackInfo ci) {
        if (farmmacro$main()) PanicDetector.INSTANCE.onEffectRemoved(packet.entityId(), packet.effect());
    }
}
