package ru.wilyfox.client.moduser;

import com.sun.net.httpserver.HttpServer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.bridge.ChatInputHost;
import ru.wilyfox.client.Client;
import ru.wilyfox.client.chat.*;
import ru.wilyfox.client.hud.config.ConfigManager;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Real native editor, focus/drafts, scaling, and HTTP delivery through an isolated local backend. */
public final class ChatInputClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> ConfigManager.snapshot());
        int oldScale = context.computeOnClient(client -> client.options.guiScale().get());
        int oldWidth = context.computeOnClient(client -> client.getWindow().getScreenWidth());
        int oldHeight = context.computeOnClient(client -> client.getWindow().getScreenHeight());
        var delivered = new AtomicReference<String>();
        var previousBackend = context.computeOnClient(client -> field(BackendSocialClient.class, null, "DEFAULT"));
        HttpServer server;
        try { server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); }
        catch (java.io.IOException failure) { throw new AssertionError(failure); }
        server.createContext("/v1/chat", exchange -> {
            try {
                expect(exchange.getRequestMethod().equals("POST"), "Input did not use backend POST");
                expect("Bearer unit-test-session".equals(exchange.getRequestHeaders().getFirst("Authorization")), "Session was not attached");
                String value = SocialWire.JSON.fromJson(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                        com.google.gson.JsonObject.class).get("text").getAsString();
                delivered.set(value);
                byte[] body = SocialWire.JSON.toJson(new BackendChatMessage(901, "Player0", value, System.currentTimeMillis())).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body);
            } finally { exchange.close(); }
        });
        server.start();
        var service = new BackendSocialClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                client.options.guiScale().set(2); client.getWindow().setWindowed(1280, 720); client.resizeGui();
                var renderer = Client.getInstance().getHudRenderer();
                renderer.setEditing(false); renderer.setSettings(false);
                ConfigManager.get().mainLayout.widgets.clear(); ConfigManager.get().locationLayouts.clear(); ConfigManager.layoutChanged();
                ChatDock.dock(ChatDock.key(ChatTab.FH)); ChatDock.select(ChatDock.key(ChatTab.ALL));
                ChatTabManager.getInstance().clearBackendMessages();
                ChatTabManager.getInstance().acceptBackendMessages(List.of(new BackendChatMessage(900, "Fox", "FH input preview", System.currentTimeMillis())), false);
                ChatDock.detach(ChatDock.key(ChatTab.FH), 320, 30); renderer.refreshLayout();
                var widget = ChatDock.widget(ChatDock.key(ChatTab.FH));
                widget.setScreenAnchor(null); widget.clearWidgetSnap(); widget.setStartX(320); widget.setStartY(30); widget.setScale(1);
                widget.settings().width = 280; widget.settings().rows = 5;
                ConfigManager.captureWidgetLayout(widget);
                widget.inputDraft("");
                client.gui.setScreen(new ChatScreen("game draft", false));
                var screen = (ChatScreen) client.gui.screen();
                expect(ChatInputMode.current(false) == ChatInputMode.CHAT, "Ordinary chat indicator is wrong");
                var input = (EditBox) field(ChatScreen.class, screen, "input");
                expect(input.getX() > ChatInputMode.CHAT.width(client.font), "Indicator overlaps ordinary input");
                screen.mouseClicked(mouse(widget.inputX() + 2, widget.inputY() + 3), false);
                expect(((ChatInputHost) screen).froghelper$inlineInputKey().equals(widget.getConfigKey()), "FH input did not acquire focus");
                expect(ChatInputMode.current(false) == ChatInputMode.FH && input.getY() < screen.height - 16, "FH editor remained below main chat");
                expect(input.getValue().isEmpty(), "Game draft leaked into FH input");
                input.setValue("FH draft");
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_F, 0, GLFW.GLFW_MOD_CONTROL));
                expect(ChatInputMode.current(true) == ChatInputMode.REGEX && ((ChatInputHost) screen).froghelper$inlineInputKey() == null,
                        "Search did not replace the editor or indicator");
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
                expect(input.getValue().equals("FH draft") && input.getY() == widget.inputY(), "Search lost FH draft or editor position");
                screen.mouseClicked(mouse(100, screen.height - 9), false);
                expect(input.getValue().equals("game draft") && ChatInputMode.current(false) == ChatInputMode.CHAT, "FH draft leaked into Minecraft input");
                screen.mouseClicked(mouse(widget.inputX() + 2, widget.inputY() + 3), false);
                expect(input.getValue().equals("FH draft"), "Switching input lost FH draft");
                widget.setScale(1.5f); ConfigManager.captureWidgetLayout(widget);
            });
            context.waitTicks(8); context.takeScreenshot("chat-input-fh");
            context.runOnClient(client -> {
                var screen = (ChatScreen) client.gui.screen();
                var widget = ChatDock.widget(ChatDock.key(ChatTab.FH));
                var input = (EditBox) field(ChatScreen.class, screen, "input");
                expect(input.getX() == widget.inputX() && input.getY() == widget.inputY(), "Editor did not follow widget scaling");
                input.setValue("ABCDEFGHIJ");
                screen.mouseClicked(mouse(input.getX() + client.font.width("ABCD") * widget.inputScale(), input.getY() + 3), false);
                expect(input.getCursorPosition() == 4, "Scaled editor did not place the cursor at the clicked character");
                ConfigManager.get().render.fixedChatWidgetFont = true;
                screen.mouseClicked(mouse(widget.inputX() + 2, widget.inputY() + 3), false);
                expect(widget.inputScale() == 1 && input.getX() == widget.inputX(), "Editor did not honor fixed chat font");
                ConfigManager.get().render.fixedChatWidgetFont = false;
                widget.settings().showInput = false;
                screen.mouseClicked(mouse(100, screen.height - 9), false);
                expect(!widget.hasInputBar(), "Input opt-out did not hide the bar");
                widget.settings().showInput = true;
                screen.mouseClicked(mouse(widget.inputX() + 2, widget.inputY() + 3), false);
                input.setValue("/say fh-backend-only");
                set(BackendSocialClient.class, service, "accessToken", "unit-test-session");
                set(BackendChatClient.class, field(BackendSocialClient.class, service, "chat"), "enabled", true);
                set(BackendSocialClient.class, null, "DEFAULT", service);
                try { screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)); }
                finally { set(BackendSocialClient.class, null, "DEFAULT", previousBackend); }
                expect(client.gui.screen() == screen && input.getValue().isEmpty(), "Inline send closed chat or retained sent text");
            });
            for (int i = 0; i < 200 && delivered.get() == null; i++) context.waitTick();
            expect("/say fh-backend-only".equals(delivered.get()), "FH text was not delivered intact to backend");
            context.runOnClient(client -> {
                var widget = ChatDock.widget(ChatDock.key(ChatTab.FH));
                var screen = (ChatScreen) client.gui.screen();
                var input = (EditBox) field(ChatScreen.class, screen, "input"); input.setValue("pending FH draft");
                client.gui.setScreen(null);
                expect(widget.inputDraft().equals("pending FH draft"), "Closing chat lost FH draft");
                client.gui.setScreen(new ChatScreen("another game draft", false));
                screen = (ChatScreen) client.gui.screen();
                screen.mouseClicked(mouse(widget.inputX() + 2, widget.inputY() + 3), false);
                expect(((EditBox) screen.getFocused()).getValue().equals("pending FH draft"), "Reopening chat lost FH draft");
                ChatDock.dock(widget.getConfigKey());
            });
            context.waitTicks(2);
            context.runOnClient(client -> expect(((ChatInputHost) client.gui.screen()).froghelper$inlineInputKey() == null,
                    "Reattaching FH left an invisible editor focused"));
            System.out.println("Chat input: CHAT/FH/REGEX indicators, inline editing, separate drafts, scaling, search, opt-out, backend delivery and redocking passed");
        } finally {
            context.runOnClient(client -> {
                set(BackendSocialClient.class, null, "DEFAULT", previousBackend); client.gui.setScreen(null);
                ChatTabManager.getInstance().clearBackendMessages(); ConfigManager.restoreSnapshot(original);
                Client.getInstance().getHudRenderer().refreshLayout();
                client.options.guiScale().set(oldScale); client.getWindow().setWindowed(oldWidth, oldHeight); client.resizeGui();
            });
            server.stop(0);
        }
    }
    private static MouseButtonEvent mouse(double x, double y) { return new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0)); }
    private static Object field(Class<?> type, Object object, String name) {
        try { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void set(Class<?> type, Object object, String name, Object value) {
        try { var field = type.getDeclaredField(name); field.setAccessible(true); field.set(object, value); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void expect(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
}
