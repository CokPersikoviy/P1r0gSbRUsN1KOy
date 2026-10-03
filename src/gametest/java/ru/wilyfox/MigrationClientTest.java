package ru.wilyfox;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.hud.HudEditingScreen;
public final class MigrationClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                try { Class.forName("ru.wilyfox.client.hud.healthbar.PlayerHealthBarRenderer$SeeThroughType"); }
                catch (ClassNotFoundException exception) { throw new AssertionError(exception); }
                var other = new net.minecraft.client.player.RemotePlayer(client.level,
                        new com.mojang.authlib.GameProfile(java.util.UUID.fromString("00000000-0000-0000-0000-000000000002"), "MigrationTest"));
                other.setId(2000000);
                other.snapTo(client.player.getX() + 3, client.player.getY(), client.player.getZ() + 4);
                client.level.addEntity(other);
                var repository = new ru.wilyfox.boss.BossRepository();
                var tracker = new ru.wilyfox.boss.BossTracker(repository);
                var name = new net.minecraft.world.entity.decoration.ArmorStand(
                        client.level, client.player.getX(), client.player.getY(), client.player.getZ());
                name.setId(2000001);
                name.setCustomName(Component.literal("§cКРИГЕР"));
                var timer = new net.minecraft.world.entity.decoration.ArmorStand(
                        client.level, client.player.getX(), client.player.getY(), client.player.getZ());
                timer.setId(2000002);
                timer.setCustomName(Component.literal("§a01:05"));
                client.level.addEntity(name);
                client.level.addEntity(timer);
                tracker.onEntityLoad(name);
                tracker.onEntityLoad(timer);
                tracker.onWorldTick(client.level);
                if (repository.getAll().stream().noneMatch(boss -> boss.getName().equals("Кригер")
                        && boss.getRespawnAt() > System.currentTimeMillis() + 64_000L)) {
                    throw new AssertionError("Boss hologram colon times must survive name sanitization");
                }
                client.level.removeEntity(name.getId(), net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                client.level.removeEntity(timer.getId(), net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            });
            context.waitTicks(30);
            context.takeScreenshot("migration-world");
            context.runOnClient(client -> client.gui.hud.getChat().addClientSystemMessage(Component.literal("Migration 26.2: chat and HUD")));
            context.setScreen(() -> new InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
            context.waitTicks(5);
            context.takeScreenshot("migration-inventory");
            context.runOnClient(client -> ru.wilyfox.client.Client.getInstance().getHudRenderer().setEditing(true));
            context.setScreen(() -> new HudEditingScreen(ru.wilyfox.client.Client.getInstance().getHudRenderer()));
            context.waitTicks(5);
            context.takeScreenshot("migration-hud-editor");
            context.runOnClient(client -> net.minecraft.client.KeyMapping.click(
                    ru.wilyfox.client.keybinds.KeyBinds.SETTINGS_MENU.getDefaultKey()));
            context.waitTicks(2);
            context.runOnClient(client -> {
                if (!ru.wilyfox.client.Client.getInstance().getHudRenderer().isSettingsOpen()
                        || !(client.gui.screen() instanceof HudEditingScreen)) {
                    throw new AssertionError("Opening settings from the HUD editor must preserve the settings state");
                }
            });
            context.takeScreenshot("migration-hud-settings");
            context.setScreen(ru.wilyfox.client.quickaccess.QuickAccessScreen::new);
            context.waitTicks(5);
            context.takeScreenshot("migration-quick-access");
            context.setScreen(() -> net.minecraft.client.Minecraft.getInstance().gui.hud.getChat().createScreen(net.minecraft.client.gui.components.ChatComponent.ChatMethod.MESSAGE, ChatScreen::new));
            context.waitTicks(5);
            context.takeScreenshot("migration-chat");
            context.setScreen(() -> null);
        }
    }
}
