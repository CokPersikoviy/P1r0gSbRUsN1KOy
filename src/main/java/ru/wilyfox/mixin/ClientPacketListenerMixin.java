package ru.wilyfox.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.alchemy.AlchemyIngredientTracker;
import ru.wilyfox.client.hud.fishing.FishingSpotTracker;
import ru.wilyfox.client.chat.ServerEmojiRegistry;
import ru.wilyfox.client.dungeon.DungeonMapTracker;
import ru.wilyfox.client.highlight.UsefulWorldHighlightRenderHook;
import ru.wilyfox.client.profiler.ModProfiler;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;
import net.minecraft.core.particles.ParticleTypes;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(method = "sendCommand", at = @At("HEAD"))
    private void froghelper$trackCommandSend(String command, CallbackInfo ci) {
        // Timeline marker only; command arguments can contain private messages or other secrets.
        ModProfiler.getInstance().recordClientEvent("command/send", "command");
    }

    @WrapMethod(method = "handleLogin")
    private void froghelper$timeLogin(ClientboundLoginPacket packet, Operation<Void> original) {
        if (!Minecraft.getInstance().isSameThread()) { original.call(packet); return; }
        try (var scope = ModProfiler.getInstance().transitionPacketScope(packet)) { original.call(packet); }
    }

    @WrapMethod(method = "handleRespawn")
    private void froghelper$timeRespawn(ClientboundRespawnPacket packet, Operation<Void> original) {
        if (!Minecraft.getInstance().isSameThread()) { original.call(packet); return; }
        try (var scope = ModProfiler.getInstance().transitionPacketScope(packet)) { original.call(packet); }
    }

    @WrapMethod(method = "handleMovePlayer")
    private void froghelper$timeTeleport(ClientboundPlayerPositionPacket packet, Operation<Void> original) {
        if (!Minecraft.getInstance().isSameThread()) { original.call(packet); return; }
        try (var scope = ModProfiler.getInstance().transitionPacketScope(packet)) { original.call(packet); }
    }

    @WrapMethod(method = "handleTabListCustomisation")
    private void froghelper$timeTab(ClientboundTabListPacket packet, Operation<Void> original) {
        if (!Minecraft.getInstance().isSameThread()) { original.call(packet); return; }
        try (var scope = ModProfiler.getInstance().transitionPacketScope(packet)) { original.call(packet); }
    }

    @Inject(method = "handleTabListCustomisation", at = @At("TAIL"))
    private void froghelper$updateServerDisplay(ClientboundTabListPacket packet, CallbackInfo ci) {
        DiamondWorldProtocolClient.onTabFooter(packet.footer());
    }

    @ModifyVariable(method = "sendChat", at = @At("HEAD"), argsOnly = true)
    private String froghelper$replaceEmojiSymbolsInChat(String message) {
        return ServerEmojiRegistry.replaceSymbolsWithKeys(message);
    }

    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    private void froghelper$markUsefulHighlightChunkDirty(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        UsefulWorldHighlightRenderHook.markBlockDirty(packet.getPos());
    }

    @Inject(method = "handleBlockEntityData", at = @At("TAIL"))
    private void froghelper$markUsefulHighlightProfileDirty(ClientboundBlockEntityDataPacket packet, CallbackInfo ci) {
        UsefulWorldHighlightRenderHook.markBlockDirty(packet.getPos());
    }

    @Inject(method = "handleSetEntityData", at = @At("TAIL"))
    private void froghelper$markUsefulHighlightEntityDataDirty(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        UsefulWorldHighlightRenderHook.markEntityDirty(packet.id());
    }

    @Inject(method = "handleSetEquipment", at = @At("TAIL"))
    private void froghelper$markUsefulHighlightEquipmentDirty(ClientboundSetEquipmentPacket packet, CallbackInfo ci) {
        UsefulWorldHighlightRenderHook.markEntityDirty(packet.getEntity());
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("TAIL"))
    private void froghelper$markUsefulHighlightChunksDirty(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        packet.runUpdates((blockPos, blockState) -> UsefulWorldHighlightRenderHook.markBlockDirty(blockPos));
    }

    @Inject(method = "handleMapItemData", at = @At("TAIL"))
    private void froghelper$trackDungeonMapId(ClientboundMapItemDataPacket packet, CallbackInfo ci) {
        DungeonMapTracker.getInstance().updateMapId(packet.mapId());
    }

    @Inject(method = "handleParticleEvent", at = @At("TAIL"))
    private void froghelper$trackIngredientAndFishingParticlePacket(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        FishingSpotTracker.getInstance().onParticlePacket(packet);
        if (packet.getParticle().getType() == ParticleTypes.HAPPY_VILLAGER) {
            AlchemyIngredientTracker.getInstance().addParticle(packet.getX(), packet.getY(), packet.getZ());
        }
    }

    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void froghelper$resetUsefulHighlightOnLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
        FishingSpotTracker.getInstance().clear();
        ModProfiler.getInstance().recordClientEvent("login", packet.getClass().getSimpleName());
        UsefulWorldHighlightRenderHook.onPlayerTeleport();
    }

    @Inject(method = "handleRespawn", at = @At("TAIL"))
    private void froghelper$resetUsefulHighlightOnRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        FishingSpotTracker.getInstance().clear();
        ModProfiler.getInstance().recordClientEvent("respawn", packet.getClass().getSimpleName());
        UsefulWorldHighlightRenderHook.onPlayerTeleport();
    }

    @Inject(method = "handleMovePlayer", at = @At("TAIL"))
    private void froghelper$resetUsefulHighlightOnTeleport(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        FishingSpotTracker.getInstance().clear();
        ModProfiler.getInstance().recordClientEvent("teleport", packet.getClass().getSimpleName());
        UsefulWorldHighlightRenderHook.onPlayerTeleport();
    }
}
