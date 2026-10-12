package com.farmmacro.mixin;

import com.farmmacro.panic.SilenceWatch;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * v1.14 «Тишина сервера»: момент прихода любого пакета от сервера (сетевой поток, только запись времени).
 * Соединение встроенного сервера (одиночная игра) принимает пакеты клиента — его не считаем (getReceiving).
 */
@Mixin(Connection.class)
public class ConnectionSilenceMixin {
    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
    private void farmmacro$onRead(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        if (((Connection) (Object) this).getReceiving() == PacketFlow.CLIENTBOUND) SilenceWatch.INSTANCE.onPacket(System.currentTimeMillis());
    }
}
