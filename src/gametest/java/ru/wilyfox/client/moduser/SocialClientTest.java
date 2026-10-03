package ru.wilyfox.client.moduser;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import ru.wilyfox.client.chat.ChatMessageDecorator;
import ru.wilyfox.client.chat.ChatMessageSanitizer;
import ru.wilyfox.client.chat.ChatPrefixRouter;
import ru.wilyfox.client.chat.ChatTab;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.utility.PlayerNameFormatter;
import java.util.List;

public final class SocialClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                var config = ConfigManager.get();
                boolean clean = config.render.cleanPlayerNames;
                boolean badge = config.render.modUserBadge;
                boolean timestamps = config.render.chatTimestamps;
                boolean toned = config.render.toneDownChat;
                try {
                    config.render.modUserBadge = true;
                    config.render.cleanPlayerNames = true;
                    config.render.chatTimestamps = false;
                    config.render.toneDownChat = false;
                    PresenceStore.replace(List.of("Fox"));
                    Component original = Component.literal("[VIP] OtherNickname");
                    Component display = PlayerNameFormatter.apply(original, "Fox");
                    if (!display.getString().contains("\uf8ff") || !ModUserBadge.strip(display).getString().equals("Fox")) {
                        throw new AssertionError("Clean names must retain the visual backend badge");
                    }
                    if (!original.getString().equals("[VIP] OtherNickname")) throw new AssertionError("Player identity component mutated");
                    Component chat = ChatMessageDecorator.decorate(Component.literal("C Fox: helloⒻ"));
                    if (!chat.getString().contains("\uf8ff") || ChatPrefixRouter.resolve(chat.getString()) != ChatTab.CLAN
                            || !ChatMessageSanitizer.forLogic(chat).getString().equals("C Fox: helloⒻ")) {
                        throw new AssertionError("Visual badge must preserve chat routing and ordinary text");
                    }
                    PresenceStore.clear();
                    if (PlayerNameFormatter.apply(original, "Fox").getString().contains("\uf8ff")) {
                        throw new AssertionError("Offline players retain a stale badge");
                    }
                } finally {
                    PresenceStore.clear();
                    config.render.cleanPlayerNames = clean;
                    config.render.modUserBadge = badge;
                    config.render.chatTimestamps = timestamps;
                    config.render.toneDownChat = toned;
                }
                ItemStack fish = new ItemStack(Items.COD, 4);
                fish.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Pet experience: 125"))));
                client.player.getInventory().setItem(9, fish);
                config.fishing.showPetExperienceOverlay = true;
            });
            context.setScreen(() -> new InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
            context.waitTicks(5);
            context.takeScreenshot("pet-experience-on");
            context.runOnClient(client -> ConfigManager.get().fishing.showPetExperienceOverlay = false);
            context.waitTicks(5);
            context.takeScreenshot("pet-experience-off");
            context.setScreen(() -> null);
            context.runOnClient(client -> ConfigManager.get().fishing.showPetExperienceOverlay = true);
        }
    }
}
