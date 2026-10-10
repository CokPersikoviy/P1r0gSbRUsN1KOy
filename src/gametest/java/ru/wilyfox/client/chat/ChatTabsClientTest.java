package ru.wilyfox.client.chat;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.bridge.ChatComponentAccessor;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.protocol.SocialProtocolFixture;
import java.util.List;

public final class ChatTabsClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        int oldExtra = ConfigManager.get().render.extraChatHistoryLines;
        boolean oldTimestamps = ConfigManager.get().render.chatTimestamps;
        var tabs = ChatTabManager.getInstance();
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                ConfigManager.get().render.extraChatHistoryLines = 0;
                ConfigManager.get().render.chatTimestamps = false;
                var chat = client.gui.hud.getChat();
                tabs.setActiveTab(ChatTab.ALL);
                chat.clearMessages(false);
                for (int i = 0; i < 108; i++) chat.addServerSystemMessage(Component.literal("G Fox: " + i));
                check(((ChatComponentAccessor) chat).froghelper$getAllMessages().size() == 100, "Native zero-extra history limit");
                check(tabs.getMessages(ChatTab.ALL).size() == 100, "Archive zero-extra history limit");
                tabs.setActiveTab(ChatTab.GLOBAL);
                chat.addServerSystemMessage(Component.literal("C Fox: hidden clan"));
                check(tabs.unread(ChatTab.CLAN) == 1, "Hidden channel unread badge");
                check(tabs.getMessages(ChatTab.CLAN).size() == 1, "Hidden message lost from archive");
                check(((ChatComponentAccessor) chat).froghelper$getTrimmedMessages().stream().noneMatch(line -> line.parent().content().getString().contains("hidden clan")), "Hidden message displayed");
                tabs.setActiveTab(ChatTab.CLAN);
                check(tabs.unread(ChatTab.CLAN) == 0 && ((ChatComponentAccessor) chat).froghelper$getTrimmedMessages().size() == 1, "Hidden message not restored");
                tabs.setActiveTab(ChatTab.ALL);
                chat.clearMessages(false);
                var signature = new MessageSignature(new byte[256]);
                chat.addPlayerMessage(Component.literal("G Fox: Красный Дракон"), signature, GuiMessageTag.system());
                for (int i = 0; i < 25; i++) chat.addServerSystemMessage(Component.literal("G Fox: global " + i));
                for (int i = 0; i < 8; i++) chat.addServerSystemMessage(Component.literal("C Fox: clan " + i));
                var original = ((ChatComponentAccessor) chat).froghelper$getAllMessages().stream().filter(message -> message.signature() == signature).findFirst().orElseThrow();
                tabs.setActiveTab(ChatTab.GLOBAL);
                chat.scrollChat(9);
                int scroll = ((ChatComponentAccessor) chat).froghelper$getScroll();
                for (int i = 0; i < 10; i++) { tabs.setActiveTab(ChatTab.CLAN); tabs.setActiveTab(ChatTab.GLOBAL); }
                check(((ChatComponentAccessor) chat).froghelper$getScroll() == scroll && scroll > 0, "Per-tab scroll lost or inflated by refresh");
                var restored = ((ChatComponentAccessor) chat).froghelper$getAllMessages().stream().filter(message -> message.signature() == signature).findFirst().orElseThrow();
                check(restored == original && restored.source() == GuiMessageSource.PLAYER && restored.addedTime() == original.addedTime(), "Native metadata replaced");
                check(tabs.getMessages(ChatTab.ALL).size() == 34, "Switching replayed messages into archive");
                int width = (int) Math.floor(ChatComponent.getWidth(client.options.chatWidth().get()) / client.options.chatScale().get());
                var lines = tabs.wrappedLines(original, client.font, width);
                tabs.setActiveTab(ChatTab.CLAN); tabs.setActiveTab(ChatTab.GLOBAL);
                check(tabs.wrappedLines(original, client.font, width) == lines, "Wrapping cache discarded on tab switch");
            });
            context.setScreen(() -> new ChatScreen("draft stays here", false));
            context.runOnClient(client -> {
                var screen = (ChatScreen) client.gui.screen();
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_F, 0, GLFW.GLFW_MOD_CONTROL));
                "ДРАКОН".codePoints().forEach(value -> screen.charTyped(new CharacterEvent(value)));
                check(tabs.matchingMessages() == 1, "Case-insensitive native search");
                check(((ChatComponentAccessor) client.gui.hud.getChat()).froghelper$getTrimmedMessages().size() == 1, "Search did not filter visible messages");
                check(((EditBox) screen.getFocused()).getValue().equals("ДРАКОН"), "Search input not focused");
            });
            context.waitTicks(2);
            context.takeScreenshot("chat-search");
            context.runOnClient(client -> {
                var screen = (ChatScreen) client.gui.screen();
                int before = client.gui.hud.getChat().getRecentChat().size();
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                check(client.gui.screen() == screen && tabs.search().isEmpty(), "Enter closed chat instead of search");
                check(((EditBox) screen.getFocused()).getValue().equals("draft stays here"), "Search replaced chat input");
                check(client.gui.hud.getChat().getRecentChat().size() == before, "Search query sent as chat");
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_TAB, 0, GLFW.GLFW_MOD_CONTROL));
                check(tabs.getActiveTab() == ChatTab.TRADE, "Ctrl+Tab failed");
                SocialProtocolFixture.location("PRISONEVO1", 1, "shaft_1");
                tabs.setActiveTab(ChatTab.CLAN);
                screen.handleChatInput("channel test", true);
                check(client.gui.hud.getChat().getRecentChat().peekLast().equals("@channel test"), "Native manual send bypassed channel routing");
                tabs.setActiveTab(ChatTab.PRIVATE);
                var input = (EditBox) screen.getFocused();
                input.setValue("private text");
                before = client.gui.hud.getChat().getRecentChat().size();
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                check(client.gui.screen() == screen && client.gui.hud.getChat().getRecentChat().size() == before, "Unsupported PM leaked into public chat");
                tabs.setActiveTab(ChatTab.ALL);
                client.gui.hud.getChat().addServerSystemMessage(Component.literal("C Fox: unread test"));
            });
            context.waitTicks(2);
            context.takeScreenshot("chat-tabs");
            context.runOnClient(client -> {
                var chat = client.gui.hud.getChat();
                var signature = new MessageSignature(new byte[256]);
                var aged = new GuiMessage(client.gui.hud.getGuiTicks() - 61, Component.literal("G Fox: delete me"), signature, GuiMessageSource.PLAYER, GuiMessageTag.system());
                chat.restoreState(new ChatComponent.State(List.of(aged), List.of(), List.of()));
                chat.deleteMessage(signature);
                tabs.setActiveTab(ChatTab.CLAN); tabs.setActiveTab(ChatTab.ALL);
                check(tabs.getMessages(ChatTab.ALL).stream().noneMatch(entry -> entry.component().getString().contains("delete me")), "Tab switch resurrected server-deleted message");
                SocialProtocolFixture.clear();
            });
        } finally {
            context.setScreen(() -> null);
            context.runOnClient(client -> {
                ConfigManager.get().render.extraChatHistoryLines = oldExtra;
                ConfigManager.get().render.chatTimestamps = oldTimestamps;
                SocialProtocolFixture.clear();
                tabs.setActiveTab(ChatTab.ALL);
                client.gui.hud.getChat().clearMessages(false);
            });
        }
    }
    private static void check(boolean passed, String reason) { if (!passed) throw new AssertionError(reason); }
}
