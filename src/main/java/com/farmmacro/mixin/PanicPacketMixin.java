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

    @Inject(method = "handleParticleEvent", at = @At("HEAD"))
    private void farmmacro$pestTrail(ClientboundLevelParticlesPacket packet, CallbackInfo ci){
        if(farmmacro$main())com.farmmacro.pest.PestTracker.particle(Minecraft.getInstance(),packet);
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
        if (farmmacro$main() && packet.content() != null) {
            String text = packet.content().getString();
            if(packet.overlay())com.farmmacro.util.Guard.run("stats/xp",()->com.farmmacro.stats.Telemetry.actionbar(Minecraft.getInstance(),text));
            com.farmmacro.pest.PestHunter.INSTANCE.signal(Minecraft.getInstance(),text,packet.overlay());
            if(!packet.overlay()) {com.farmmacro.market.StashSales.INSTANCE.signal(text);com.farmmacro.visitor.Visitors.INSTANCE.signal(text);com.farmmacro.pest.PestPlots.INSTANCE.onServerText(text);com.farmmacro.combat.SpecialZealotHunt.INSTANCE.notice(Minecraft.getInstance(),text);}
            PanicDetector.INSTANCE.onSystemText(text, packet.overlay() ? "надпись над хотбаром" : "чат");
            PanicDetector.INSTANCE.onChat(text, null, null, packet.overlay());
        }
    }

    // v1.13 «Лаги сервера»: момент прихода пакета времени — в сетевом потоке (первый вызов, до перекидывания в главный)
    @Inject(method = "handleSetTime", at = @At("HEAD"))
    private void farmmacro$onSetTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
        if (!farmmacro$main()) com.farmmacro.panic.LagGuard.INSTANCE.onTime(System.currentTimeMillis(), packet.gameTime());
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
        if (farmmacro$main() && packet.text() != null) {
            String text=packet.text().getString();
            if(!com.farmmacro.combat.SpecialZealotHunt.INSTANCE.notice(Minecraft.getInstance(),text))PanicDetector.INSTANCE.onTitle(text,false);
        }
    }

    @Inject(method = "setSubtitleText", at = @At("HEAD"))
    private void farmmacro$onSubtitle(ClientboundSetSubtitleTextPacket packet, CallbackInfo ci) {
        if (farmmacro$main() && packet.text() != null) {
            String text=packet.text().getString();
            if(!com.farmmacro.combat.SpecialZealotHunt.INSTANCE.notice(Minecraft.getInstance(),text))PanicDetector.INSTANCE.onTitle(text,true);
        }
    }

    @Inject(method = "setActionBarText", at = @At("HEAD"))
    private void farmmacro$onActionBar(ClientboundSetActionBarTextPacket packet, CallbackInfo ci) {
        if (farmmacro$main() && packet.text() != null) {
            com.farmmacro.util.Guard.run("stats/xp",()->com.farmmacro.stats.Telemetry.actionbar(Minecraft.getInstance(),packet.text().getString()));
            PanicDetector.INSTANCE.onSystemText(packet.text().getString(), "надпись над хотбаром");
            PanicDetector.INSTANCE.onChat(packet.text().getString(), null, null, true);
        }
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
        if (farmmacro$main()&&!com.farmmacro.market.MarketTrip.INSTANCE.spawnAbilities(packet.isFlying(),packet.canFly(),packet.getWalkingSpeed(),packet.getFlyingSpeed()))
            PanicDetector.INSTANCE.onAbilities(packet.isFlying(), packet.canFly(), packet.getWalkingSpeed(), packet.getFlyingSpeed());
    }

    @Inject(method = "handlePlayerAbilities", at = @At("RETURN"))
    private void farmmacro$flightApplied(ClientboundPlayerAbilitiesPacket packet, CallbackInfo ci){
        if(farmmacro$main())com.farmmacro.route.FlightCapability.packet(Minecraft.getInstance(),packet.canFly(),packet.isFlying());
    }

    // v1.11: «Параметры движения» — атрибуты игрока от сервера (скорость, прыжок, гравитация, шаг, размер)
    @Inject(method = "handleUpdateAttributes", at = @At("HEAD"))
    private void farmmacro$onAttributes(ClientboundUpdateAttributesPacket packet, CallbackInfo ci) {
        if (farmmacro$main()&&!com.farmmacro.market.MarketTrip.INSTANCE.spawnAttributes(packet.getEntityId(),packet.getValues())) PanicDetector.INSTANCE.onAttributes(packet.getEntityId(), packet.getValues());
    }

    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void farmmacro$onRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        if (farmmacro$main()) {
            boolean own=com.farmmacro.market.MarketTrip.INSTANCE.claimRespawn(packet);
            if(!own){com.farmmacro.market.StashSales.INSTANCE.cancel(Minecraft.getInstance(),"незапрошенный respawn");PanicDetector.INSTANCE.onRespawn();}
        }
    }

    @Inject(method = "handleSetEntityPassengersPacket", at = @At("HEAD"))
    private void farmmacro$onPassengers(ClientboundSetPassengersPacket packet, CallbackInfo ci) {
        if (farmmacro$main()) PanicDetector.INSTANCE.onPassengers(packet.getVehicle(), packet.getPassengers());
    }

    @Inject(method = "handleSetHeldSlot", at = @At("HEAD"))
    private void farmmacro$onHeldSlot(ClientboundSetHeldSlotPacket packet, CallbackInfo ci) {
        if (farmmacro$main()){com.farmmacro.market.StashSales.INSTANCE.serverSlot(packet.slot());com.farmmacro.loadout.GlobalLoadouts.INSTANCE.cancel(Minecraft.getInstance(),"сервер задал слот руки");PanicDetector.INSTANCE.onServerSlot(packet.slot());}
    }

    // Inspect applied inventory, never cancel vanilla packets/acknowledgements.
    @Inject(method = "handleContainerSetSlot", at = @At("RETURN"))
    private void farmmacro$safetyhandleContainerSetSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        if(farmmacro$main()){com.farmmacro.visitor.VisitorMenu.slot(Minecraft.getInstance(),packet.getContainerId(),packet.getSlot(),packet.getItem());com.farmmacro.market.SaleEvidence.slot(Minecraft.getInstance(),packet.getContainerId(),packet.getSlot(),packet.getItem());com.farmmacro.visitor.VisitorMenu.inventory(Minecraft.getInstance());com.farmmacro.market.SaleEvidence.inventory(Minecraft.getInstance());}
        if(farmmacro$main())com.farmmacro.loadout.LoadoutEvidence.slot(Minecraft.getInstance(),packet.getContainerId(),packet.getSlot(),packet.getItem());
        if(farmmacro$main())com.farmmacro.pest.PestMenuEvidence.slot(Minecraft.getInstance(),packet.getContainerId(),packet.getSlot(),packet.getItem());
        if(farmmacro$main())com.farmmacro.panic.AutomationSafety.INSTANCE.tick(Minecraft.getInstance());
    }

    @Inject(method = "handleSetCursorItem", at = @At("RETURN"))
    private void farmmacro$pestCursor(ClientboundSetCursorItemPacket packet, CallbackInfo ci){
        if(farmmacro$main()){com.farmmacro.visitor.VisitorMenu.slot(Minecraft.getInstance(),-1,-1,packet.contents());com.farmmacro.market.SaleEvidence.slot(Minecraft.getInstance(),-1,-1,packet.contents());}
        if(farmmacro$main())com.farmmacro.loadout.LoadoutEvidence.slot(Minecraft.getInstance(),-1,-1,packet.contents());
        if(farmmacro$main())com.farmmacro.pest.PestMenuEvidence.slot(Minecraft.getInstance(),-1,-1,packet.contents());
    }

    @Inject(method = "handleContainerContent", at = @At("RETURN"))
    private void farmmacro$safetyhandleContainerContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        if(farmmacro$main())com.farmmacro.util.Guard.run("stats/pet",()->com.farmmacro.stats.Telemetry.petMenu(Minecraft.getInstance(),packet.containerId(),packet.items()));
        if(farmmacro$main()){com.farmmacro.visitor.VisitorMenu.content(Minecraft.getInstance(),packet.containerId(),packet.items(),packet.carriedItem());com.farmmacro.market.SaleEvidence.content(Minecraft.getInstance(),packet.containerId(),packet.items(),packet.carriedItem());com.farmmacro.visitor.VisitorMenu.inventory(Minecraft.getInstance());com.farmmacro.market.SaleEvidence.inventory(Minecraft.getInstance());}
        if(farmmacro$main())com.farmmacro.loadout.LoadoutEvidence.content(Minecraft.getInstance(),packet.containerId(),packet.items(),packet.carriedItem());
        if(farmmacro$main())com.farmmacro.pest.PestMenuEvidence.content(Minecraft.getInstance(),packet.containerId(),packet.items(),packet.carriedItem());
        if(farmmacro$main())com.farmmacro.panic.AutomationSafety.INSTANCE.tick(Minecraft.getInstance());
    }

    @Inject(method = "handleSetPlayerInventory", at = @At("RETURN"))
    private void farmmacro$safetyhandleSetPlayerInventory(ClientboundSetPlayerInventoryPacket packet, CallbackInfo ci) {
        if(farmmacro$main()){com.farmmacro.visitor.VisitorMenu.inventory(Minecraft.getInstance());com.farmmacro.market.SaleEvidence.inventory(Minecraft.getInstance());}
        if(farmmacro$main())com.farmmacro.panic.AutomationSafety.INSTANCE.tick(Minecraft.getInstance());
    }

    @Inject(method = "handleSetHeldSlot", at = @At("RETURN"))
    private void farmmacro$safetyhandleSetHeldSlot(ClientboundSetHeldSlotPacket packet, CallbackInfo ci) {
        if(farmmacro$main())com.farmmacro.panic.AutomationSafety.INSTANCE.tick(Minecraft.getInstance());
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
