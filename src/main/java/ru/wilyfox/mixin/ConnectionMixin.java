package ru.wilyfox.mixin;

import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.performance.EstimatedTpsMonitor;
import ru.wilyfox.client.profiler.ModProfiler;

@Mixin(Connection.class)
public class ConnectionMixin {
    @Inject(method = "channelRead0", at = @At("HEAD"))
    private void froghelper$trackEstimatedTps(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        EstimatedTpsMonitor.onClientboundPacket(packet);
        ModProfiler.getInstance().recordNetworkPacket("clientbound", packet);
    }

    @Inject(
            method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Z)V",
            at = @At("HEAD")
    )
    private void froghelper$trackServerboundPacket(
            Packet<?> packet,
            net.minecraft.network.PacketSendListener listener,
            boolean flush,
            CallbackInfo ci
    ) {
        ModProfiler.getInstance().recordNetworkPacket("serverbound", packet);
    }
}
