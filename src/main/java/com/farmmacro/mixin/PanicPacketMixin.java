package com.farmmacro.mixin;

import com.farmmacro.panic.PanicDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
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
        if (farmmacro$main()) PanicDetector.INSTANCE.beforeServerMove("поворота");
    }

    // «посмотреть на» (/tp … facing, плагины): тоже поворот сервером
    @Inject(method = "handleLookAt", at = @At("HEAD"))
    private void farmmacro$beforeLookAt(ClientboundPlayerLookAtPacket packet, CallbackInfo ci) {
        if (farmmacro$main()) PanicDetector.INSTANCE.beforeServerMove("взгляда (look at)");
    }

    @Inject(method = "handleLookAt", at = @At("RETURN"))
    private void farmmacro$afterLookAt(ClientboundPlayerLookAtPacket packet, CallbackInfo ci) {
        if (farmmacro$main()) PanicDetector.INSTANCE.afterServerMove();
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

    // ── v1.10: толчки, чат, титры, режим, игроки, слот, урон ──

    @Inject(method = "handleSetEntityMotion", at = @At("HEAD"))
    private void farmmacro$onMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        if (farmmacro$main()) PanicDetector.INSTANCE.onServerMotion(packet.id(), packet.movement());
    }

    @Inject(method = "handleExplosion", at = @At("HEAD"))
    private void farmmacro$onExplosion(ClientboundExplodePacket packet, CallbackInfo ci) {
        if (farmmacro$main()) PanicDetector.INSTANCE.onExplosion(packet.playerKnockback());
    }

    @Inject(method = "handleSystemChat", at = @At("HEAD"))
    private void farmmacro$onSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (farmmacro$main() && packet.content() != null)
            PanicDetector.INSTANCE.onChat(packet.content().getString(), null, null, packet.overlay());
    }

    @Inject(method = "handlePlayerChat", at = @At("HEAD"))
    private void farmmacro$onPlayerChat(ClientboundPlayerChatPacket packet, CallbackInfo ci) {
        if (!farmmacro$main()) return;
        String text = packet.unsignedContent() != null ? packet.unsignedContent().getString()
                : packet.body() != null ? packet.body().content() : null;
        var conn = Minecraft.getInstance().getConnection();
        var info = conn != null ? conn.getPlayerInfo(packet.sender()) : null;
        PanicDetector.INSTANCE.onChat(text, packet.sender(), info != null ? info.getProfile().name() : null, false);
    }

    @Inject(method = "handleDisguisedChat", at = @At("HEAD"))
    private void farmmacro$onDisguisedChat(ClientboundDisguisedChatPacket packet, CallbackInfo ci) {
        if (farmmacro$main() && packet.message() != null)
            PanicDetector.INSTANCE.onChat(packet.message().getString(), null, null, false);
    }

    @Inject(method = "setTitleText", at = @At("HEAD"))
    private void farmmacro$onTitle(ClientboundSetTitleTextPacket packet, CallbackInfo ci) {
        if (farmmacro$main() && packet.text() != null) PanicDetector.INSTANCE.onTitle(packet.text().getString(), false);
    }

    @Inject(method = "setSubtitleText", at = @At("HEAD"))
    private void farmmacro$onSubtitle(ClientboundSetSubtitleTextPacket packet, CallbackInfo ci) {
        if (farmmacro$main() && packet.text() != null) PanicDetector.INSTANCE.onTitle(packet.text().getString(), true);
    }

    @Inject(method = "setActionBarText", at = @At("HEAD"))
    private void farmmacro$onActionBar(ClientboundSetActionBarTextPacket packet, CallbackInfo ci) {
        if (farmmacro$main() && packet.text() != null)
            PanicDetector.INSTANCE.onChat(packet.text().getString(), null, null, true);
    }

    @Inject(method = "handleGameEvent", at = @At("HEAD"))
    private void farmmacro$onGameEvent(ClientboundGameEventPacket packet, CallbackInfo ci) {
        if (farmmacro$main() && packet.getEvent() == ClientboundGameEventPacket.CHANGE_GAME_MODE) {
            var t = net.minecraft.world.level.GameType.byId((int) packet.getParam());
            PanicDetector.INSTANCE.onGameModeChanged(t != null ? t.getShortDisplayName().getString() : null);
        }
    }

    @Inject(method = "handlePlayerAbilities", at = @At("HEAD"))
    private void farmmacro$onAbilities(ClientboundPlayerAbilitiesPacket packet, CallbackInfo ci) {
        if (farmmacro$main())
            PanicDetector.INSTANCE.onAbilities(packet.isFlying(), packet.canFly(), packet.getWalkingSpeed(), packet.getFlyingSpeed());
    }

    // v1.11: «Параметры движения» — атрибуты игрока от сервера (скорость, прыжок, гравитация, шаг, размер)
    @Inject(method = "handleUpdateAttributes", at = @At("HEAD"))
    private void farmmacro$onAttributes(ClientboundUpdateAttributesPacket packet, CallbackInfo ci) {
        if (farmmacro$main()) PanicDetector.INSTANCE.onAttributes(packet.getEntityId(), packet.getValues());
    }

    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void farmmacro$onRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        if (farmmacro$main()) PanicDetector.INSTANCE.onRespawn();
    }

    @Inject(method = "handleSetEntityPassengersPacket", at = @At("HEAD"))
    private void farmmacro$onPassengers(ClientboundSetPassengersPacket packet, CallbackInfo ci) {
        if (farmmacro$main()) PanicDetector.INSTANCE.onPassengers(packet.getVehicle(), packet.getPassengers());
    }

    @Inject(method = "handleSetHeldSlot", at = @At("HEAD"))
    private void farmmacro$onHeldSlot(ClientboundSetHeldSlotPacket packet, CallbackInfo ci) {
        if (farmmacro$main()) PanicDetector.INSTANCE.onServerSlot(packet.slot());
    }

    @Inject(method = "handleDamageEvent", at = @At("HEAD"))
    private void farmmacro$onDamage(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        if (farmmacro$main())
            PanicDetector.INSTANCE.onDamageEvent(packet.entityId(),
                    packet.sourceType() != null && packet.sourceType().is(net.minecraft.world.damagesource.DamageTypes.FALL));
    }

    @Inject(method = "handlePlayerInfoUpdate", at = @At("HEAD"))
    private void farmmacro$onPlayerInfo(ClientboundPlayerInfoUpdatePacket packet, CallbackInfo ci) {
        if (farmmacro$main()) PanicDetector.INSTANCE.onPlayerInfo(packet);
    }

    @Inject(method = "handlePlayerInfoRemove", at = @At("HEAD"))
    private void farmmacro$onPlayerRemove(ClientboundPlayerInfoRemovePacket packet, CallbackInfo ci) {
        if (farmmacro$main()) PanicDetector.INSTANCE.onPlayerRemoved(packet.profileIds());
    }

    @Inject(method = "handleRemoveMobEffect", at = @At("HEAD"))
    private void farmmacro$onEffectRemoved(ClientboundRemoveMobEffectPacket packet, CallbackInfo ci) {
        if (farmmacro$main()) PanicDetector.INSTANCE.onEffectRemoved(packet.entityId(), packet.effect());
    }
}
