package ru.wilyfox.client.hud.menu;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.Client;
import ru.wilyfox.client.chat.*;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.*;
import java.nio.file.*;
import java.util.*;

public final class ChatPlusImportClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var snapshot = context.computeOnClient(client -> ConfigManager.snapshot());
        int scale = context.computeOnClient(client -> client.options.guiScale().get());
        int width = context.computeOnClient(client -> client.getWindow().getScreenWidth());
        int height = context.computeOnClient(client -> client.getWindow().getScreenHeight());
        Path file;
        try {
            file = Files.createTempFile("chatplus-native-import-", ".json");
            try (var input = getClass().getResourceAsStream("/chatplus-import.json")) { Files.write(file, input.readAllBytes()); }
        } catch (Exception failure) { throw new AssertionError(failure); }
        String[] adapted = {null}; ChatPlusImportScreen[] screen = {null};
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                ConfigManager.restoreSnapshot(com.google.gson.JsonParser.parseString("{}"));
                ChatTabManager.getInstance().clearAll();
                client.options.guiScale().set(1); client.getWindow().setWindowed(854, 480); client.resizeGui();
                adapted[0] = ChatDock.create(); ChatDock.settings(adapted[0]).title = "Adapted";
                ChatDock.settings(adapted[0]).textFilter = ".*original.*"; ChatDock.settings(adapted[0]).width = 333;
                ConfigManager.save();
                client.gui.hud.getChat().addServerSystemMessage(Component.literal("Existing message"));
                var renderer = Client.getInstance().getHudRenderer();
                client.gui.setScreen(new HudEditingScreen(renderer)); renderer.setSettings(true);
                var sections = new EnumMap<SettingsCategory, List<SettingsComponent>>(SettingsCategory.class);
                for (var category : SettingsCategory.values()) sections.put(category, new ArrayList<>());
                HudSettingsCoreSections.populate(sections);
                var action = sections.get(SettingsCategory.RENDER).stream().filter(c -> c.getLabel().equals("Import ChatPlus tabs")).findFirst().orElseThrow();
                action.setPosition(0, 0); action.setSize(300, 22); action.mouseClicked(220, 10, 0);
                check(client.gui.screen() instanceof ChatPlusImportScreen, "Settings button did not open the importer");
                screen[0] = (ChatPlusImportScreen) client.gui.screen();
            });
            awaitIdle(context, screen[0]);
            context.runOnClient(client -> screen[0].onFilesDrop(List.of(file)));
            awaitIdle(context, screen[0]);
            context.runOnClient(client -> {
                check(((List<?>) get(screen[0], "tabs")).size() == 5, "Server profiles or invalid tabs missing from preview");
                check(!((Button)get(screen[0], "importButton")).active, "Loading a config selected or imported tabs automatically");
                int x = (int)get(screen[0], "left") + 16;
                screen[0].mouseClicked(mouse(x, 90), false);
                check(((Set<?>)get(screen[0], "selected")).size() == 1, "Checkbox did not select a tab");
                screen[0].mouseClicked(mouse(x, 117), false);
                check(((Set<?>)get(screen[0], "selected")).size() == 1, "Two server profiles of the same tab were selected");
                screen[0].mouseClicked(mouse(x, 90), false);
                screen[0].mouseClicked(mouse(x, 144), false);
                screen[0].mouseClicked(mouse(x, 171), false);
                check(((Set<?>)get(screen[0], "selected")).size() == 2, "Color-based filter was selectable");
                screen[0].mouseClicked(mouse(x + 45, 90), false);
            });
            context.waitTicks(2); context.takeScreenshot("chatplus-import-preview");
            context.runOnClient(client -> {
                client.options.guiScale().set(2); client.resizeGui();
                check(((Set<?>)get(screen[0], "selected")).size() == 2, "Resize discarded selection");
                check((int)get(screen[0], "listBottom") > 113, "Importer does not fit a small GUI");
            });
            context.waitTicks(2); context.takeScreenshot("chatplus-import-small");
            context.runOnClient(client -> {
                var button = (Button)get(screen[0], "importButton");
                check(button.active, "Valid selected tabs could not be imported");
                screen[0].mouseClicked(mouse(button.getX() + 3, button.getY() + 3), false);
                check(ConfigManager.get().chatWidgets.keySet().stream().filter(WidgetCatalog::isCustomChatKey).count() == 3, "Wrong number of imported tabs");
                check(ChatDock.settings(adapted[0]).title.equals("Adapted") && ChatDock.settings(adapted[0]).width == 333, "Import overwrote an adapted tab");
                var joined = ConfigManager.get().chatWidgets.entrySet().stream().filter(e -> e.getValue().title.equals("Joined")).findFirst().orElseThrow();
                check(ChatDock.widget(joined.getKey()) != null, "Imported tab did not register a detachable widget");
                check(ChatDock.tabs().contains(joined.getKey()) && !joined.getValue().detached, "Imported tab is missing from the dock");
                check(ChatTabManager.getInstance().getMessages(ChatTab.ALL).size() == 1, "Import erased chat history");
                try {
                    var backup = com.google.gson.JsonParser.parseString(Files.readString(FabricLoader.getInstance().getConfigDir().resolve("froghelper-before-chatplus-import.json"))).getAsJsonObject();
                    check(backup.getAsJsonObject("chatWidgets").has(adapted[0]) && !backup.getAsJsonObject("chatWidgets").has(joined.getKey()), "Backup is not the pre-import config");
                } catch (Exception failure) { throw new AssertionError(failure); }
                ConfigManager.restoreSnapshot(ConfigManager.snapshot());
                ChatDock.select(joined.getKey());
                client.gui.hud.getChat().addServerSystemMessage(Component.literal("Fox ЗАШЁЛ на сервер"));
                client.gui.hud.getChat().addServerSystemMessage(Component.literal("Fox: я зашел в шахту"));
                check(ChatTabManager.getInstance().matchingMessages() == 1, "Imported regex did not filter actual server messages");
                screen[0].onClose();
                check(Client.getInstance().getHudRenderer().isSettingsOpen(), "Returning from import lost the settings menu");
                check(ChatDock.tabs().getFirst().equals(ChatDock.key(ChatTab.ALL)) && ChatDock.tabs().get(1).equals(ChatDock.key(ChatTab.FH)), "Import changed pinned tabs");
            });
        } finally {
            context.runOnClient(client -> {
                client.gui.setScreen(null); ConfigManager.restoreSnapshot(snapshot);
                Client.getInstance().getHudRenderer().refreshConfiguration(); ChatTabManager.getInstance().clearAll(); ChatDock.select(ChatDock.key(ChatTab.ALL));
                client.options.guiScale().set(scale); client.getWindow().setWindowed(width, height); client.resizeGui();
            });
            try { Files.deleteIfExists(file); } catch (Exception ignored) {}
        }
    }
    private static void awaitIdle(ClientGameTestContext context, ChatPlusImportScreen screen) {
        for (int i = 0; i < 40; i++) {
            context.waitTicks(2);
            if (context.computeOnClient(client -> !((Boolean)get(screen, "busy")))) return;
        }
        throw new AssertionError("Async config read did not complete");
    }
    private static Object get(Object target, String name) {
        try { var f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static MouseButtonEvent mouse(double x, double y) { return new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0)); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
