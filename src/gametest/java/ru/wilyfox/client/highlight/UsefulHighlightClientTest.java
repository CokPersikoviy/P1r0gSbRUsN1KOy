package ru.wilyfox.client.highlight;

import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import ru.wilyfox.client.chat.ChatDispatchQueue;
import ru.wilyfox.client.audio.UiSound;
import ru.wilyfox.client.audio.UiSounds;
import ru.wilyfox.client.chat.ChatTab;
import ru.wilyfox.client.chat.ChatTabManager;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.popup.PopUpManager;
import ru.wilyfox.client.popup.PopUpSource;
import ru.wilyfox.client.protocol.SocialProtocolFixture;

import java.lang.reflect.Method;
import java.util.List;

public final class UsefulHighlightClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                var config = ConfigManager.get();
                boolean enabled = config.render.usefulItemsHighlight;
                boolean thanks = config.render.autoThanks;
                boolean timestamps = config.render.chatTimestamps;
                boolean barrelPopups = config.popUps.barrelFoundEvent;
                boolean crystalPopups = config.popUps.goldenCrystalFoundEvent;
                long originalTime = client.level.getGameTime();
                double uiVolume = client.options.getSoundSourceOptionInstance(SoundSource.UI).get();
                double masterVolume = client.options.getSoundSourceOptionInstance(SoundSource.MASTER).get();
                int[] discoverySounds = {0};
                SoundEventListener soundListener = (instance, event, distance) -> {
                    if (instance.getIdentifier().toString().equals(UiSound.DISCOVERY.eventId())) discoverySounds[0]++;
                };
                ChatTab oldTab = ChatTabManager.getInstance().getActiveTab();
                BlockPos barrel = new BlockPos(client.player.chunkPosition().getMiddleBlockX(),
                        client.player.blockPosition().getY(), client.player.chunkPosition().getMiddleBlockZ());
                ArmorStand crystal = null;
                try {
                    config.render.usefulItemsHighlight = true;
                    config.render.autoThanks = true;
                    config.render.chatTimestamps = true;
                    config.popUps.barrelFoundEvent = true;
                    config.popUps.goldenCrystalFoundEvent = true;
                    verifyThanks(client);
                    SocialProtocolFixture.location("PRISONEVO1", 2, "shaft_12");
                    invoke("shouldWaitForWorldContext");
                    UsefulWorldHighlightRenderHook.onPlayerTeleport();
                    if (!(boolean) invoke("shouldWaitForWorldContext")) fail("Teleport should briefly wait for world context");
                    // A same-location teleport sends no new DW location packet.
                    setField("worldContextWaitStartedAt", System.nanoTime() - 1_100_000_000L);
                    if ((boolean) invoke("shouldWaitForWorldContext")) fail("Same-mine teleport stalled highlighting forever");

                    var barrelState = Blocks.NOTE_BLOCK.defaultBlockState()
                            .setValue(NoteBlock.INSTRUMENT, NoteBlockInstrument.FLUTE)
                            .setValue(NoteBlock.NOTE, 19);
                    client.getConnection().handleBlockUpdate(new ClientboundBlockUpdatePacket(barrel, barrelState));
                    refresh(client);
                    if (UsefulWorldHighlightRenderHook.diagnosticSnapshot().blockBoxes() != 1) fail("Nearest chunk barrel was not detected on first scan");
                    if (findNotifications(PopUpSource.BARREL_FOUND) != 1) fail("Barrel discovery notification missing");
                    client.getConnection().handleBlockUpdate(new ClientboundBlockUpdatePacket(barrel, barrelState.setValue(NoteBlock.POWERED, true)));
                    refresh(client);
                    if (findNotifications(PopUpSource.BARREL_FOUND) != 1) fail("Detonation duplicated barrel discovery");
                    client.getConnection().handleBlockUpdate(new ClientboundBlockUpdatePacket(barrel, Blocks.AIR.defaultBlockState()));
                    refresh(client);
                    if (UsefulWorldHighlightRenderHook.diagnosticSnapshot().blockBoxes() != 0) fail("Destroyed barrel highlight stayed cached");

                    for (int i = 0; i < 30; i++) refresh(client);
                    var chunk = client.level.getChunkAt(barrel);
                    // A chunk arriving after the scan must be queued even while the 5-second cache is fresh.
                    client.level.setBlock(barrel, barrelState, 0);
                    ClientChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(client.level, chunk);
                    refresh(client);
                    if (UsefulWorldHighlightRenderHook.diagnosticSnapshot().blockBoxes() != 1) fail("Late-loaded chunk waited for periodic refresh");

                    client.getConnection().handleBlockUpdate(new ClientboundBlockUpdatePacket(barrel, Blocks.AIR.defaultBlockState()));
                    refresh(client);
                    client.options.getSoundSourceOptionInstance(SoundSource.UI).set(1d);
                    client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(1d);
                    client.getSoundManager().addListener(soundListener);
                    allowDiscoverySound();
                    crystal = new ArmorStand(client.level, client.player.getX() + 3, client.player.getY(), client.player.getZ());
                    crystal.setId(2_900_001);
                    client.level.addEntity(crystal);
                    setField("lastEntityScanTick", client.level.getGameTime() - 1L);
                    setField("entityScanDirty", false);
                    var model = new ItemStack(Items.LEATHER_HORSE_ARMOR);
                    model.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(271f), List.of(), List.of(), List.of()));
                    client.getConnection().handleSetEquipment(new ClientboundSetEquipmentPacket(crystal.getId(), List.of(Pair.of(EquipmentSlot.HEAD, model))));
                    refresh(client);
                    if (UsefulWorldHighlightRenderHook.diagnosticSnapshot().entityBoxes() != 1) fail("Equipment update waited for periodic entity scan");
                    if (findNotifications(PopUpSource.GOLDEN_CRYSTAL_FOUND) != 1) fail("Golden crystal notification missing");
                    if (discoverySounds[0] != 1) fail("First crystal discovery did not play one sound");

                    PopUpManager.getInstance().removeSources(PopUpSource.GOLDEN_CRYSTAL_FOUND);
                    client.level.removeEntity(crystal.getId(), Entity.RemovalReason.DISCARDED);
                    client.level.setTimeFromServer(originalTime + 201L);
                    setField("entityScanDirty", true);
                    refresh(client);
                    if (UsefulWorldHighlightRenderHook.diagnosticSnapshot().entityBoxes() != 0) fail("Unloaded crystal stayed highlighted");
                    // Chunk reloading assigns a new entity ID, but the crystal position is unchanged.
                    crystal = new ArmorStand(client.level, client.player.getX() + 3, client.player.getY(), client.player.getZ());
                    crystal.setId(2_900_002);
                    client.level.addEntity(crystal);
                    client.level.setTimeFromServer(originalTime + 202L);
                    allowDiscoverySound();
                    client.getConnection().handleSetEquipment(new ClientboundSetEquipmentPacket(crystal.getId(), List.of(Pair.of(EquipmentSlot.HEAD, model))));
                    refresh(client);
                    if (UsefulWorldHighlightRenderHook.diagnosticSnapshot().entityBoxes() != 1) fail("Reloaded crystal lost its highlight");
                    if (findNotifications(PopUpSource.GOLDEN_CRYSTAL_FOUND) != 0 || discoverySounds[0] != 1) fail("Reloading a crystal after ten seconds repeated the notification or sound");

                    allowDiscoverySound();
                    UsefulWorldHighlightRenderHook.onPlayerTeleport();
                    setField("worldContextWaitStartedAt", System.nanoTime() - 1_100_000_000L);
                    if ((boolean) invoke("shouldWaitForWorldContext")) fail("Same-mine teleport did not release the cache");
                    refresh(client);
                    if (UsefulWorldHighlightRenderHook.diagnosticSnapshot().entityBoxes() != 1
                            || findNotifications(PopUpSource.GOLDEN_CRYSTAL_FOUND) != 0 || discoverySounds[0] != 1) {
                        fail("Same-location teleport reset crystal discovery memory");
                    }

                    // Renderer rebuilds and toggling highlighting must also preserve discovery memory.
                    allowDiscoverySound();
                    invoke("clearCache");
                    refresh(client);
                    if (findNotifications(PopUpSource.GOLDEN_CRYSTAL_FOUND) != 0 || discoverySounds[0] != 1) fail("Render cache rebuild repeated crystal discovery");

                    SocialProtocolFixture.location("PRISONEVO1", 2, "shaft_13");
                    invoke("shouldWaitForWorldContext");
                    allowDiscoverySound();
                    invoke("refreshEntityCacheIfNeeded", new Class<?>[]{Minecraft.class}, client);
                    if (findNotifications(PopUpSource.GOLDEN_CRYSTAL_FOUND) != 1 || discoverySounds[0] != 2) fail("A different location did not reset crystal discovery");
                    PopUpManager.getInstance().removeSources(PopUpSource.GOLDEN_CRYSTAL_FOUND);
                    SocialProtocolFixture.location("PRISONEVO1", 3, "shaft_13");
                    invoke("shouldWaitForWorldContext");
                    allowDiscoverySound();
                    invoke("refreshEntityCacheIfNeeded", new Class<?>[]{Minecraft.class}, client);
                    if (findNotifications(PopUpSource.GOLDEN_CRYSTAL_FOUND) != 1 || discoverySounds[0] != 3) fail("A different mirror did not reset crystal discovery");
                    UsefulWorldHighlightRenderHook.onPlayerTeleport();
                    if (findNotifications(PopUpSource.BARREL_FOUND) != 0 || findNotifications(PopUpSource.GOLDEN_CRYSTAL_FOUND) != 0) {
                        fail("Previous-world discovery notifications survived teleport");
                    }
                } finally {
                    client.getSoundManager().removeListener(soundListener);
                    client.options.getSoundSourceOptionInstance(SoundSource.UI).set(uiVolume);
                    client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(masterVolume);
                    client.level.setTimeFromServer(originalTime);
                    if (crystal != null) client.level.removeEntity(crystal.getId(), Entity.RemovalReason.DISCARDED);
                    client.level.setBlock(barrel, Blocks.AIR.defaultBlockState(), 0);
                    ChatDispatchQueue.removeQueuedCommandsContaining("thx");
                    SocialProtocolFixture.clear();
                    invoke("shouldWaitForWorldContext");
                    invoke("clearCache");
                    config.render.usefulItemsHighlight = enabled;
                    config.render.autoThanks = thanks;
                    config.render.chatTimestamps = timestamps;
                    config.popUps.barrelFoundEvent = barrelPopups;
                    config.popUps.goldenCrystalFoundEvent = crystalPopups;
                    ChatTabManager.getInstance().setActiveTab(oldTab);
                }
            });
        }
    }

    private static void verifyThanks(Minecraft client) {
        ChatDispatchQueue.removeQueuedCommandsContaining("thx");
        int before = ChatDispatchQueue.getDebugSnapshot().size();
        ChatTabManager.getInstance().setActiveTab(ChatTab.CLAN);
        client.gui.hud.getChat().addClientSystemMessage(Component.literal("Fox активировал глобальный бустер опыта"));
        if (ChatDispatchQueue.getDebugSnapshot().size() != before) fail("Local chat triggered a server-only thank-you");
        ClientReceiveMessageEvents.GAME.invoker().onReceiveGameMessage(Component.literal("Fox активировал глобальный бустер опыта"), false);
        ClientReceiveMessageEvents.GAME.invoker().onReceiveGameMessage(Component.literal("Other activated the global money booster"), false);
        if (ChatDispatchQueue.getDebugSnapshot().size() != before + 2
                || !ChatDispatchQueue.getDebugSnapshot().preview().equals("/thx")) fail("Booster burst dropped a priority thank-you");
        ChatDispatchQueue.removeQueuedCommandsContaining("thx");
        ClientReceiveMessageEvents.GAME.invoker().onReceiveGameMessage(Component.literal("Fox активировал глобальный бустер опыта"), false);
        if (ChatDispatchQueue.getDebugSnapshot().size() != before + 1) fail("Recent thank-you suppressed a new activation");
        ChatDispatchQueue.removeQueuedCommandsContaining("thx");
    }

    private static long findNotifications(String source) {
        return PopUpManager.getInstance().getVisibleNotifications(32).stream().filter(p -> p.source().equals(source)).count();
    }

    private static void refresh(Minecraft client) {
        invoke("refreshCacheIfNeeded", new Class<?>[]{Minecraft.class}, client);
    }

    private static void allowDiscoverySound() {
        // These transitions run in one client task; bypass only the real-time audio limiter
        // so it cannot mask a duplicate notification or suppress a valid new discovery.
        try {
            var field = UiSounds.class.getDeclaredField("ALERT_LIMITS");
            field.setAccessible(true);
            ((java.util.Map<?, ?>) field.get(null)).remove(UiSound.DISCOVERY);
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }

    private static Object invoke(String name) { return invoke(name, new Class<?>[0]); }

    private static Object invoke(String name, Class<?>[] parameters, Object... arguments) {
        try {
            Method method = UsefulWorldHighlightRenderHook.class.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method.invoke(null, arguments);
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }

    private static void setField(String name, Object value) {
        try {
            var field = UsefulWorldHighlightRenderHook.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, value);
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }

    private static void fail(String message) { throw new AssertionError(message); }
}
