package ru.wilyfox.client.chat;

import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.client.Client;
import ru.wilyfox.client.hud.config.ConfigManager;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;

/** Exercise vanilla suggestions with a deterministic server response, including hit boxes. */
public final class ChatSuggestionsClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> ConfigManager.snapshot());
        int oldScale = context.computeOnClient(client -> client.options.guiScale().get());
        int oldWidth = context.computeOnClient(client -> client.getWindow().getScreenWidth());
        int oldHeight = context.computeOnClient(client -> client.getWindow().getScreenHeight());
        double oldChatScale = context.computeOnClient(client -> client.options.chatScale().get());
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                ConfigManager.get().mainLayout.widgets.clear();
                ConfigManager.layoutChanged();
                var renderer = Client.getInstance().getHudRenderer();
                renderer.setSettings(false); renderer.setEditing(false); renderer.refreshLayout();
                ChatDock.select(ChatDock.key(ChatTab.ALL));
                client.getWindow().setWindowed(1280, 720);
            });
            for (int guiScale : new int[]{2, 3}) {
                for (double chatScale : new double[]{1d, .6d}) {
                    context.runOnClient(client -> {
                        client.options.guiScale().set(guiScale); client.resizeGui();
                        client.options.chatScale().set(chatScale);
                    });
                    context.setScreen(() -> new ChatScreen("", false));
                    context.runOnClient(client -> {
                        var screen = (ChatScreen) client.gui.screen();
                        var input = (EditBox) get(ChatScreen.class, screen, "input");
                        var commands = (CommandSuggestions) get(ChatScreen.class, screen, "commandSuggestions");
                        for (int count : new int[]{2, 20}) {
                            show(commands, input, count);
                            var popup = get(CommandSuggestions.class, commands, "suggestions");
                            var rect = (Rect2i) get(popup.getClass(), popup, "rect");
                            int tabTop = ChatTabOverlay.getInstance().rowTop(screen.height);
                            check(rect.getY() >= 0 && rect.getY() + rect.getHeight() + 1 < tabTop,
                                    "Suggestions overlap the tabs or leave the screen");
                            int lastRow = rect.getHeight() / 12 - 1;
                            check(screen.mouseClicked(new MouseButtonEvent(rect.getX() + 2,
                                    rect.getY() + lastRow * 12 + 5, new MouseButtonInfo(0, 0)), false),
                                    "Moved suggestion does not receive mouse clicks");
                            check(input.getValue().equals("/warp fish_%02d".formatted(lastRow)),
                                    "Click picked the wrong suggestion after relocation");
                        }
                        show(commands, input, 2);
                        check(screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_TAB, 0, 0))
                                        && input.getValue().equals("/warp fish_00"),
                                "Tab completion stopped working");
                        show(commands, input, 20);
                        var popup = get(CommandSuggestions.class, commands, "suggestions");
                        var rect = (Rect2i) get(popup.getClass(), popup, "rect");
                        // Vanilla scrolling reads the actual pointer, unlike click events.
                        double oldX = client.mouseHandler.xpos(), oldY = client.mouseHandler.ypos();
                        try {
                            set(client.mouseHandler, "xpos", (rect.getX() + 2d) * client.getWindow().getScreenWidth() / screen.width);
                            set(client.mouseHandler, "ypos", (rect.getY() + 5d) * client.getWindow().getScreenHeight() / screen.height);
                            check(screen.mouseScrolled(rect.getX() + 2, rect.getY() + 5, 0, -1), "Long suggestion list does not scroll");
                        } finally {
                            set(client.mouseHandler, "xpos", oldX); set(client.mouseHandler, "ypos", oldY);
                        }
                        check((int) get(popup.getClass(), popup, "offset") > 0, "Suggestion scroll did not advance");
                    });
                    context.waitTicks(2);
                    context.takeScreenshot("chat-completion-" + guiScale + "-" + (int) (chatScale * 100));
                }
            }
            context.runOnClient(client -> {
                Screen screen = new Screen(Component.literal("Other command screen")) {};
                screen.height = client.getWindow().getGuiScaledHeight();
                var input = new EditBox(client.font, 4, screen.height - 12, 200, 12, Component.empty());
                var commands = new CommandSuggestions(client, screen, input, client.font, false, false, 1, 10, true, 0xCC000000);
                show(commands, input, 2);
                var popup = get(CommandSuggestions.class, commands, "suggestions");
                var rect = (Rect2i) get(popup.getClass(), popup, "rect");
                check(rect.getY() + rect.getHeight() == screen.height - 15,
                        "Non-chat command screen was moved");
            });
        } finally {
            context.runOnClient(client -> {
                ConfigManager.restoreSnapshot(original);
                Client.getInstance().getHudRenderer().refreshLayout();
                client.options.guiScale().set(oldScale);
                client.options.chatScale().set(oldChatScale);
                client.getWindow().setWindowed(oldWidth, oldHeight); client.resizeGui();
            });
        }
    }

    private static void show(CommandSuggestions commands, EditBox input, int count) {
        input.setValue("/warp fi");
        var builder = new SuggestionsBuilder(input.getValue(), 6);
        for (int i = 0; i < count; i++) builder.suggest("fish_%02d".formatted(i));
        try {
            Field pending = CommandSuggestions.class.getDeclaredField("pendingSuggestions");
            pending.setAccessible(true); pending.set(commands, CompletableFuture.completedFuture(builder.build()));
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        commands.showSuggestions(false);
        check(commands.isVisible(), "Native suggestions did not open");
    }

    private static Object get(Class<?> type, Object target, String name) {
        try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void set(Object target, String name, Object value) {
        try { Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
