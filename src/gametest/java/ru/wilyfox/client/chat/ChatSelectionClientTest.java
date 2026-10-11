package ru.wilyfox.client.chat;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.client.Client;
import ru.wilyfox.client.hud.config.ChatTimestampConfig;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.bridge.ChatComponentAccessor;
import java.time.Instant;
import java.util.List;

public final class ChatSelectionClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> ConfigManager.snapshot());
        double oldScale = context.computeOnClient(client -> client.options.chatScale().get());
        double oldWidth = context.computeOnClient(client -> client.options.chatWidth().get());
        String oldClipboard = context.computeOnClient(client -> client.keyboardHandler.getClipboard());
        var renderer = Client.getInstance().getHudRenderer();
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                ConfigManager.get().mainLayout.widgets.clear(); ConfigManager.get().locationLayouts.clear();
                ConfigManager.get().render.copyChatMessages = true; ConfigManager.get().render.fullMessageCopy = false;
                ConfigManager.get().render.toneDownChat = false; ConfigManager.get().render.chatTimestamps = true;
                var timestamp = ConfigManager.get().render.timestamp;
                timestamp.format = ChatTimestampConfig.Format.TIME_12;
                timestamp.brackets = ChatTimestampConfig.Brackets.ROUND;
                timestamp.themeColor = false; timestamp.color = 0xFF3399; timestamp.bold = true;
                renderer.setEditing(false); renderer.setSettings(false); ConfigManager.layoutChanged(); renderer.refreshLayout();
                client.options.chatScale().set(.75d); client.options.chatWidth().set(.5d);
                ChatDock.select(ChatDock.key(ChatTab.ALL)); client.gui.hud.getChat().clearMessages(false);
                for (String text : List.of("first", "second", "third"))
                    client.gui.hud.getChat().addServerSystemMessage(Component.literal("G Fox: " + text));
                var entries = ChatTabManager.getInstance().getMessages(ChatTab.GLOBAL);
                check(entries.size() == 3, "Custom timestamps broke channel routing");
                var prefix = entries.getFirst().component().getSiblings().getFirst();
                check(prefix.getStyle().getColor().getValue() == 0xFF3399 && prefix.getStyle().isBold(), "Timestamp color/emphasis lost");
            });
            context.setScreen(() -> new ChatScreen("draft stays", false));
            context.waitTicks(2);
            context.runOnClient(client -> {
                var screen = (ChatScreen) client.gui.screen();
                int first = nativeY(client, "first"), third = nativeY(client, "third");
                check(screen.mouseClicked(mouse(8, third), false), "Native selection press not consumed");
                check(screen.mouseDragged(mouse(8, first), 0, first - third), "Native selection drag not consumed");
            });
            context.takeScreenshot("chat-multiple-selection");
            context.runOnClient(client -> {
                var screen = (ChatScreen) client.gui.screen();
                screen.mouseReleased(mouse(8, nativeY(client, "first")));
                check(client.keyboardHandler.getClipboard().equals("first\nsecond\nthird"), "Native multi-copy ordering/separator wrong");
                client.keyboardHandler.setClipboard("unchanged");
                check(screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_C, 0, GLFW.GLFW_MOD_CONTROL)), "Ctrl+C not consumed");
                check(client.keyboardHandler.getClipboard().equals("first\nsecond\nthird"), "Ctrl+C did not copy selection");
                var clan = ChatDock.key(ChatTab.CLAN);
                ChatDock.detach(clan, 85, 20);
                var widget = ChatDock.widget(clan);
                widget.settings().width = 250; widget.settings().rows = 5; widget.settings().showTitle = false;
                widget.setScale(1.25f);
                for (String text : List.of("clan first", "clan second", "clan third"))
                    client.gui.hud.getChat().addServerSystemMessage(Component.literal("C Fox: " + text));
            });
            context.waitTicks(3);
            context.runOnClient(client -> {
                var screen = (ChatScreen) client.gui.screen(); var widget = ChatDock.widget(ChatDock.key(ChatTab.CLAN));
                check(widget.isVisible(), "Detached window missing");
                double x = widget.getStartX() + 12;
                double last = widget.getStartY() + widget.getHeight() - 12;
                double first = last - 20 * widget.getScale();
                check(widget.lineAt(x, last).message().content().getString().endsWith("clan third"), "Widget test target wrong");
                screen.mouseClicked(mouse(x, last), false);
                screen.mouseDragged(mouse(x, first), 0, first - last);
                screen.mouseReleased(mouse(x, first));
                check(client.keyboardHandler.getClipboard().equals("clan first\nclan second\nclan third"), "Scaled window copied dock or duplicated wrapped messages");
                ConfigManager.get().render.fullMessageCopy = true;
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_C, 0, GLFW.GLFW_MOD_CONTROL));
                check(client.keyboardHandler.getClipboard().contains(") C Fox: clan first\n("), "Full copy omitted timestamp or separator");
                // FH history must use the exact same formatter and original server time.
                var sentAt = Instant.now().minusSeconds(3600).toEpochMilli();
                var store = ChatTabManager.getInstance(); store.clearBackendMessages();
                store.acceptBackendMessages(List.of(new BackendChatMessage(1, "Fox", "backend timestamp", sentAt)), false);
                var backend = store.getMessages(ChatTab.FH).getFirst().component();
                check(backend.getString().startsWith(ChatTimestampFormatter.createTimestampPrefix(Instant.ofEpochMilli(sentAt)).getString()),
                        "FH uses a different timestamp format or current time");
            });
            context.takeScreenshot("chat-window-multiple-selection");
            context.setScreen(() -> null);
            context.runOnClient(client -> check(!ChatMessageSelection.getInstance().hasSelection(), "Closing chat retained selection"));
        } finally {
            context.setScreen(() -> null);
            context.runOnClient(client -> {
                ChatMessageSelection.getInstance().clear(); ConfigManager.restoreSnapshot(original);
                ConfigManager.layoutChanged(); renderer.refreshLayout(); renderer.setEditing(false); renderer.setSettings(false);
                client.options.chatScale().set(oldScale); client.options.chatWidth().set(oldWidth);
                client.keyboardHandler.setClipboard(oldClipboard);
                ChatDock.select(ChatDock.key(ChatTab.ALL)); client.gui.hud.getChat().clearMessages(false);
                ChatTabManager.getInstance().clearBackendMessages();
            });
        }
    }

    private static int nativeY(Minecraft client, String body) {
        for (int y = 0; y < client.getWindow().getGuiScaledHeight() - 40; y++) {
            var message = ChatMessageCopyExtractor.hoveredMessage(client.gui.hud.getChat(), 8, y);
            if (message != null && message.content().getString().endsWith(body)) return y;
        }
        throw new AssertionError("Cannot locate native message: " + body);
    }
    private static MouseButtonEvent mouse(double x, double y) { return new MouseButtonEvent(x, y, new MouseButtonInfo(1, 0)); }
    private static void check(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
}
