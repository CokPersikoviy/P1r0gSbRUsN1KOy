package ru.wilyfox.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.alchemy.AlchemyIngredientTracker;
import ru.wilyfox.client.chat.ServerEmojiRegistry;
import ru.wilyfox.client.dungeon.DungeonMapTracker;
import ru.wilyfox.client.highlight.UsefulWorldHighlightRenderHook;
import ru.wilyfox.client.profiler.ModProfiler;
import net.minecraft.core.particles.ParticleTypes;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @ModifyVariable(method = "sendChat", at = @At("HEAD"), argsOnly = true)
    private String froghelper$replaceEmojiSymbolsInChat(String message) {
        return ServerEmojiRegistry.replaceSymbolsWithKeys(message);
    }

    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    private void froghelper$markUsefulHighlightChunkDirty(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        UsefulWorldHighlightRenderHook.markBlockDirty(packet.getPos());
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("TAIL"))
    private void froghelper$markUsefulHighlightChunksDirty(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        packet.runUpdates((blockPos, blockState) -> UsefulWorldHighlightRenderHook.markBlockDirty(blockPos));
    }

    @Inject(method = "handleMapItemData", at = @At("TAIL"))
    private void froghelper$trackDungeonMapId(ClientboundMapItemDataPacket packet, CallbackInfo ci) {
        DungeonMapTracker.getInstance().updateMapId(packet.mapId());
    }

    @Inject(method = "handleParticleEvent", at = @At("HEAD"))
    private void froghelper$trackAlchemyIngredientParticlePacket(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        if (packet.getParticle().getType() == ParticleTypes.HAPPY_VILLAGER) {
            AlchemyIngredientTracker.getInstance().addParticle(packet.getX(), packet.getY(), packet.getZ());
        }
    }

    @Inject(method = "handleLogin", at = @At("HEAD"))
    private void froghelper$resetUsefulHighlightOnLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
        ModProfiler.getInstance().recordClientEvent("login", packet.getClass().getSimpleName());
        UsefulWorldHighlightRenderHook.onPlayerTeleport();
    }

    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void froghelper$resetUsefulHighlightOnRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        ModProfiler.getInstance().recordClientEvent("respawn", packet.getClass().getSimpleName());
        UsefulWorldHighlightRenderHook.onPlayerTeleport();
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void froghelper$resetUsefulHighlightOnTeleport(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        ModProfiler.getInstance().recordClientEvent("teleport", packet.getClass().getSimpleName());
        UsefulWorldHighlightRenderHook.onPlayerTeleport();
    }
}
