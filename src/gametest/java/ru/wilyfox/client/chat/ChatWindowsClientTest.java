package ru.wilyfox.client.chat;

import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.client.Client;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.HudConfig;
import ru.wilyfox.client.hud.config.HudConfigCodec;
import ru.wilyfox.client.hud.config.LocationWidgetLayoutConfig;
import ru.wilyfox.client.hud.config.WidgetLayoutConfig;
import ru.wilyfox.client.protocol.SocialProtocolFixture;
import ru.wilyfox.bridge.ChatComponentAccessor;
import java.lang.reflect.Field;

public final class ChatWindowsClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var renderer = Client.getInstance().getHudRenderer();
        HudConfig original = ConfigManager.get();
        int scale = context.computeOnClient(client -> client.options.guiScale().get());
        int width = context.computeOnClient(client -> client.getWindow().getScreenWidth());
        int height = context.computeOnClient(client -> client.getWindow().getScreenHeight());
        double chatScale = context.computeOnClient(client -> client.options.chatScale().get());
        double chatWidth = context.computeOnClient(client -> client.options.chatWidth().get());
        String clan = ChatDock.key(ChatTab.CLAN), trade = ChatDock.key(ChatTab.TRADE);
        String[] custom = {null};
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                swap(HudConfigCodec.decode(HudConfigCodec.createGson(), JsonParser.parseString("{\"mainLayout\":{\"widgets\":[]}}")));
                renderer.setSettings(false); renderer.setEditing(false); renderer.refreshLayout();
                client.options.guiScale().set(2); client.getWindow().setWindowed(854, 480); client.resizeGui();
                client.options.chatScale().set(1d); client.options.chatWidth().set(1d);
                ConfigManager.get().render.chatTimestamps = false;
                ChatTabManager.getInstance().setActiveTab(ChatTab.ALL);
                client.gui.hud.getChat().clearMessages(false);
                check(ChatDock.tabs().size() == 7 && renderer.getLayoutWidgetCount() == 0, "Tabs must initially live below main chat");
                for (int i = 0; i < 35; i++) client.gui.hud.getChat().addServerSystemMessage(Component.literal("C Fox: clan " + i));
                for (int i = 0; i < 12; i++) client.gui.hud.getChat().addServerSystemMessage(Component.literal("T Fox: trade " + i));
                client.gui.hud.getChat().addPlayerMessage(Component.literal("C Fox: clickable dragon").withStyle(style ->
                        style.withClickEvent(new ClickEvent.SuggestCommand("/m Fox dragon"))), new MessageSignature(new byte[256]), GuiMessageTag.system());
                ChatDock.settings(clan).width = 180; ChatDock.settings(clan).rows = 4;
            });
            context.setScreen(() -> new ChatScreen("draft survives", false));
            context.waitTicks(2); context.takeScreenshot("chat-tabs-aligned");
            checkTabReordering(context);
            context.runOnClient(client -> {
                var screen = (ChatScreen) client.gui.screen();
                var overlay = ChatTabOverlay.getInstance();
                int guiHeight = client.getWindow().getGuiScaledHeight();
                check(overlay.dockLeft() == 4 && overlay.dockRight() == 324 && overlay.rowTop(guiHeight) == guiHeight - 39,
                        "Tab strip must align with native chat, not screen width");
                int y = overlay.rowTop(guiHeight) + 6;
                int allX = tabX(ChatDock.key(ChatTab.ALL), y, client.getWindow().getGuiScaledWidth());
                screen.mouseClicked(mouse(allX, y, 1), false);
                ChatDock.edit(ChatDock.key(ChatTab.ALL)); ChatDock.delete(ChatDock.key(ChatTab.ALL));
                check(client.gui.screen() == screen && !ChatDock.canEdit(ChatDock.key(ChatTab.ALL))
                        && !ChatDock.settings(ChatDock.key(ChatTab.ALL)).deleted, "ALL must not open settings or be deleted");
                check(!new ru.wilyfox.client.hud.menu.HudSettingsPanel().openWidget(ChatDock.widget(ChatDock.key(ChatTab.ALL)), () -> {}),
                        "Widget editor must also protect ALL");
                client.options.chatScale().set(.5d); client.options.chatWidth().set(.4d);
                check(overlay.dockLeft() == 2 && overlay.dockRight() == 154 && overlay.rowTop(guiHeight) == guiHeight - 39,
                        "Tab strip did not follow chat scale/width");
                check(!overlay.isDockTarget(overlay.dockRight() + 1, y), "Drop target extends outside chat");
                client.options.chatWidth().set(0d); ChatDock.select(ChatDock.key(ChatTab.PRIVATE));
                check(tabX(ChatDock.key(ChatTab.PRIVATE), y, client.getWindow().getGuiScaledWidth()) < overlay.dockRight(),
                        "Keyboard selection must reveal tabs in a narrow dock");
                overlay.scrollTabs(overlay.dockLeft() + 5, y, 100);
                check(overlay.tabAt(overlay.dockLeft() + 1, y, guiHeight).equals(ChatDock.key(ChatTab.ALL)), "Tab wheel did not scroll left");
                client.options.chatScale().set(1d); client.options.chatWidth().set(1d); ChatDock.select(ChatDock.key(ChatTab.ALL));
                int x = tabX(clan, y, client.getWindow().getGuiScaledWidth());
                screen.mouseClicked(mouse(x, y, 0), false);
                screen.mouseDragged(mouse(x + 2, y, 0), 2, 0);
                screen.mouseReleased(mouse(x + 2, y, 0));
                check(!ChatDock.settings(clan).detached, "A click must not detach a tab");
                screen.mouseClicked(mouse(x, y, 0), false);
                screen.mouseDragged(mouse(24, 22, 0), 24 - x, 22 - y);
                screen.mouseReleased(mouse(24, 22, 0));
                var widget = ChatDock.widget(clan);
                check(widget.isVisible() && !ChatDock.tabs().contains(clan), "Pulling a tab out must create its window");
                check(ChatTabManager.getInstance().getMessages(ChatTab.ALL).size() == 48, "Detachment replayed or lost history");
                var lines = widget.view().lines(client.font, 166, 4);
                check(lines.size() == 4 && lines.stream().allMatch(line -> line.message().content().getString().startsWith("C ")), "Window channel or row bound wrong");
                check(lines.getFirst().message().signature() != null && lines.getFirst().message().tag() != null, "Window lost native metadata");
                check(widget.view().lines(client.font, 166, 4) == lines, "Unchanged window should reuse its viewport");
                double rowY = widget.getStartY() + widget.getHeight() - 12;
                var style = widget.styleAt(widget.getStartX() + 12, rowY);
                check(style != null && style.getClickEvent() != null, "Scaled widget text hit-testing lost click event");
                screen.mouseClicked(mouse(widget.getStartX() + 12, rowY, 0), false);
                check(((EditBox) screen.getFocused()).getValue().equals("/m Fox dragon"), "Native text click action not handled");
                check(ChatTabManager.getInstance().getActiveTab() == ChatTab.ALL && ChatDock.outgoingChannel() == ChatTab.CLAN, "Window focus must not replace main chat view");
                checkFixedFont(client, widget);
                screen.mouseScrolled(widget.getStartX() + 10, rowY, 0, 1);
                var scrolled = widget.view().lines(client.font, 166, 4).getFirst();
                check(widget.view().scroll() > 0, "Window scroll not routed");
                client.gui.hud.getChat().addServerSystemMessage(Component.literal("C Fox: arrives while scrolled"));
                check(widget.view().lines(client.font, 166, 4).getFirst().message() == scrolled.message(), "New message jumped the scrolled viewport");
                int otherScroll = ChatDock.widget(trade).view().scroll();
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_F, 0, GLFW.GLFW_MOD_CONTROL));
                "DRAGON".codePoints().forEach(value -> screen.charTyped(new CharacterEvent(value)));
                check(widget.view().matchingMessages() == 1 && ChatTabManager.getInstance().search().isEmpty(), "Widget search contaminated main chat");
                check(ChatDock.widget(trade).view().scroll() == otherScroll, "Independent window scroll changed");
            });
            context.waitTicks(2); context.takeScreenshot("chat-detached-search");
            context.runOnClient(client -> {
                var screen = (ChatScreen) client.gui.screen();
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                var widget = ChatDock.widget(clan);
                double hx = widget.getStartX() + 12, hy = widget.getStartY() + 7;
                int dockY = ChatTabOverlay.getInstance().rowTop(client.getWindow().getGuiScaledHeight()) + 6;
                screen.mouseClicked(mouse(hx, hy, 0), false);
                screen.mouseDragged(mouse(12, dockY, 0), 12 - hx, dockY - hy);
                screen.mouseReleased(mouse(12, dockY, 0));
                check(!widget.isVisible() && ChatDock.tabs().contains(clan), "Dragging header back must restore ordinary tab");
                check(ChatTabManager.getInstance().getActiveTab() == ChatTab.CLAN, "Returned tab not selected");
                client.gui.hud.getChat().scrollChat(9);
                int savedScroll = ((ChatComponentAccessor) client.gui.hud.getChat()).froghelper$getScroll();
                int tabX = tabX(clan, dockY, client.getWindow().getGuiScaledWidth());
                screen.mouseClicked(mouse(tabX, dockY, 0), false);
                screen.mouseDragged(mouse(24, 22, 0), 24 - tabX, 22 - dockY); screen.mouseReleased(mouse(24, 22, 0));
                widget.view().lines(client.font, 166, 4);
                check(widget.view().scroll() == savedScroll, "Detachment lost native tab scroll");
                widget.view().scroll(2); widget.view().lines(client.font, 166, 4);
                int windowScroll = widget.view().scroll();
                ChatDock.pressWindow(widget, widget.getStartX() + 12, widget.getStartY() + 7);
                ChatDock.drag(12, dockY); ChatDock.release(12, dockY);
                check(((ChatComponentAccessor) client.gui.hud.getChat()).froghelper$getScroll() == windowScroll, "Redocking lost window scroll");
                custom[0] = ChatDock.create();
                check(custom[0] != null && ChatDock.tabs().contains(custom[0]), "New tab must initially be docked");
                var config = ChatDock.settings(custom[0]); config.title = "Dragons"; config.channel = ChatTab.CLAN; config.textFilter = "DRAGON";
                ChatDock.select(custom[0]);
                check(ChatTabManager.getInstance().matchingMessages() == 1, "Custom native tab filter wrong");
                ChatDock.pressTab(custom[0], 50, dockY); ChatDock.drag(230, 30); ChatDock.release(230, 30);
                var customWidget = ChatDock.widget(custom[0]);
                check(customWidget.view().lines(client.font, config.width - 14, config.rows).size() == 1, "Custom filter lost on detachment");
                var locationLayout = new LocationWidgetLayoutConfig();
                locationLayout.widgets.add(custom[0]); locationLayout.locationVisibility.add("shaft_1");
                var placement = new WidgetLayoutConfig(); placement.x = 60; placement.y = 30; placement.scale = 1.25f;
                locationLayout.placements.put(custom[0], placement);
                ConfigManager.get().locationLayouts.put("chat-mine", locationLayout); ConfigManager.layoutChanged();
                SocialProtocolFixture.location("PRISONEVO1", 1, "shaft_1");
                renderer.refreshLayout();
                check(customWidget.getScale() == 1.25f && renderer.getLayoutWidgets().stream().filter(w -> w == customWidget).count() == 1,
                        "Location override duplicated the window or lost its scale");
                customWidget.view().lines(client.font, config.width - 14, config.rows);
                check(customWidget.styleAt(customWidget.getStartX() + 15, customWidget.getStartY() + customWidget.getHeight() - 15) != null,
                        "Scaled window text hit-test failed");
                check(customWidget.copyAt(customWidget.getStartX() + 15, customWidget.getStartY() + customWidget.getHeight() - 15)
                        && client.keyboardHandler.getClipboard().equals("clickable dragon"), "Window copy lost original message body");
                customWidget.setScreenAnchor(ru.wilyfox.client.hud.indicators.ScreenAnchor.TOP_RIGHT);
                int oldX = customWidget.getStartX(), oldY = customWidget.getStartY();
                ChatDock.pressWindow(customWidget, oldX + 12, oldY + 7); ChatDock.drag(oldX + 30, oldY + 20);
                check(customWidget.getScreenAnchor() == null, "Anchors fight window dragging");
                ChatDock.cancelDrag();
                check(customWidget.getStartX() == oldX && customWidget.getStartY() == oldY
                        && customWidget.getScreenAnchor() == ru.wilyfox.client.hud.indicators.ScreenAnchor.TOP_RIGHT, "Cancel did not restore anchored window");
                customWidget.setScreenAnchor(null);
            });
            context.waitTicks(2); context.takeScreenshot("chat-custom-window");
            var chrome = ConfigManager.get().render.widgetChrome;
            context.runOnClient(client -> {
                ConfigManager.get().render.fixedChatWidgetFont = true;
                ConfigManager.get().render.widgetChrome = ru.wilyfox.client.hud.config.WidgetChrome.BARE;
            });
            context.waitTicks(2); context.takeScreenshot("chat-fixed-font-bare");
            context.runOnClient(client -> {
                ConfigManager.get().render.fixedChatWidgetFont = false;
                ConfigManager.get().render.widgetChrome = chrome;
                ChatDock.edit(custom[0]);
                check(client.gui.screen() instanceof ChatTabSettingsScreen, "Tab settings screen unavailable");
            });
            context.waitTicks(2); context.takeScreenshot("chat-tab-settings");
            context.runOnClient(client -> {
                client.gui.screen().onClose(); ChatDock.delete(custom[0]); custom[0] = null;
                for (var tab : ChatTab.values()) ChatDock.detach(ChatDock.key(tab), 12, 20);
                check(ChatDock.tabs().equals(java.util.List.of(ChatDock.key(ChatTab.ALL), ChatDock.key(ChatTab.FH))), "Pinned tabs must remain docked");
                client.gui.hud.getChat().addServerSystemMessage(Component.literal("G Fox: empty dock still archives"));
                check(!((ChatComponentAccessor) client.gui.hud.getChat()).froghelper$getTrimmedMessages().isEmpty(), "Pinned ALL must keep native messages visible");
                ChatDock.dock(ChatDock.key(ChatTab.ALL)); ChatDock.select(ChatDock.key(ChatTab.ALL));
                check(!((ChatComponentAccessor) client.gui.hud.getChat()).froghelper$getTrimmedMessages().isEmpty(), "Returning a tab did not restore main chat");
                ChatDock.edit(clan);
                check(client.gui.screen() instanceof ChatTabSettingsScreen, "Built-in tab settings unavailable");
                client.gui.screen().onClose();
                int historySize = ChatTabManager.getInstance().getMessages(ChatTab.ALL).size();
                int librarySize = renderer.getLibraryWidgetCount();
                ChatDock.select(clan); ChatDock.delete(clan);
                check(ChatDock.settings(clan).deleted && !ChatDock.widget(clan).isVisible() && !ChatDock.tabs().contains(clan)
                        && ChatDock.selectedKey().equals(ChatDock.key(ChatTab.ALL)), "Deleting a built-in window failed or left its tab selected");
                check(renderer.getLibraryWidgetCount() == librarySize - 1, "Deleted window remains in widget library");
                ChatDock.dock(trade); ChatDock.select(trade); ChatDock.delete(trade);
                check(ChatDock.settings(trade).deleted && !ChatDock.tabs().contains(trade), "Deleting a docked built-in tab failed");
                check(ChatTabManager.getInstance().getMessages(ChatTab.ALL).size() == historySize, "Deleting tabs discarded shared messages");
                check(ChatDock.tabs().equals(java.util.List.of(ChatDock.key(ChatTab.ALL), ChatDock.key(ChatTab.FH))), "Deleted built-in tabs reappeared");
            });
        } finally {
            context.runOnClient(client -> {
                client.gui.setScreen(null); ChatDock.cancelDrag(); ChatDock.focusWindow(null);
                if (custom[0] != null && ChatDock.settings(custom[0]) != null) ChatDock.delete(custom[0]);
                swap(original); ConfigManager.setEditorLayout(null); renderer.refreshLayout(); ConfigManager.save();
                ChatTabManager.getInstance().setActiveTab(ChatTab.ALL); client.gui.hud.getChat().clearMessages(false);
                SocialProtocolFixture.clear(); client.options.chatScale().set(chatScale); client.options.chatWidth().set(chatWidth); client.options.guiScale().set(scale); client.getWindow().setWindowed(width, height); client.resizeGui();
            });
        }
    }
    private static void checkFixedFont(net.minecraft.client.Minecraft client, ru.wilyfox.client.hud.widget.ChatWidget widget) {
        var render = ConfigManager.get().render;
        var oldChrome = render.widgetChrome;
        float oldScale = widget.getScale();
        double oldOpacity = client.options.textBackgroundOpacity().get();
        try {
            widget.setScale(2); render.widgetChrome = ru.wilyfox.client.hud.config.WidgetChrome.BARE;
            client.options.textBackgroundOpacity().set(.6d);
            var scaled = new net.minecraft.client.renderer.state.gui.GuiRenderState();
            widget.render(new net.minecraft.client.gui.GuiGraphicsExtractor(client, scaled, 0, 0), net.minecraft.client.DeltaTracker.ZERO);
            var scaledTexts = new java.util.ArrayList<net.minecraft.client.renderer.state.gui.GuiTextRenderState>();
            scaled.forEachText(scaledTexts::add);
            check(!scaledTexts.isEmpty() && scaledTexts.stream().allMatch(t -> t.pose.m00() == 2), "Default mode stopped scaling chat text");
            int width = widget.getWidth(), height = widget.getHeight();
            render.fixedChatWidgetFont = true;
            var fixed = new net.minecraft.client.renderer.state.gui.GuiRenderState();
            widget.render(new net.minecraft.client.gui.GuiGraphicsExtractor(client, fixed, 0, 0), net.minecraft.client.DeltaTracker.ZERO);
            var fixedTexts = new java.util.ArrayList<net.minecraft.client.renderer.state.gui.GuiTextRenderState>();
            fixed.forEachText(fixedTexts::add);
            check(fixedTexts.size() > scaledTexts.size() && fixedTexts.stream().allMatch(t -> t.pose.m00() == 1),
                    "Fixed font must stay unscaled and fit more rows in a larger window");
            check(widget.getWidth() == width && widget.getHeight() == height, "Fixed font changed window bounds");
            var rectangles = new java.util.ArrayList<net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState>();
            fixed.forEachElement(e -> { if (e instanceof net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState rectangle) rectangles.add(rectangle); },
                    net.minecraft.client.renderer.state.gui.GuiRenderState.TraverseRange.ALL);
            check(rectangles.stream().anyMatch(r -> Math.min(r.x0(), r.x1()) == 0 && Math.min(r.y0(), r.y1()) == 0
                    && Math.abs(r.x1() - r.x0()) == width && Math.abs(r.y1() - r.y0()) == height
                    && r.col1() == (Math.round(client.options.textBackgroundOpacity().get().floatValue() * .5f * 255) << 24)),
                    "Bare chat background did not use vanilla text opacity");
            double x = widget.getStartX() + 12, y = widget.getStartY() + height - 12;
            check(widget.styleAt(x, y) != null && widget.styleAt(x, y).getClickEvent() != null,
                    "Fixed font lost clickable text coordinates");
            check(widget.copyAt(x, y) && client.keyboardHandler.getClipboard().equals("clickable dragon"), "Fixed font copy coordinates are wrong");
            widget.setScale(.5f);
            var small = new net.minecraft.client.renderer.state.gui.GuiRenderState();
            widget.render(new net.minecraft.client.gui.GuiGraphicsExtractor(client, small, 0, 0), net.minecraft.client.DeltaTracker.ZERO);
            small.forEachText(t -> check(t.pose.m00() == 1, "Small window shrank its fixed font"));
        } finally {
            render.fixedChatWidgetFont = false; render.widgetChrome = oldChrome; widget.setScale(oldScale);
            client.options.textBackgroundOpacity().set(oldOpacity);
        }
    }
    private static void checkTabReordering(ClientGameTestContext context) {
        var savedOrder = context.computeOnClient(client -> java.util.List.copyOf(ConfigManager.get().chatTabOrder));
        String clan = ChatDock.key(ChatTab.CLAN);
        String[] custom = {null};
        try {
            context.runOnClient(client -> {
                var overlay = ChatTabOverlay.getInstance();
                var screen = (ChatScreen) client.gui.screen();
                int y = overlay.rowTop(client.getWindow().getGuiScaledHeight()) + 6;
                int x = tabX(clan, y, client.getWindow().getGuiScaledWidth());
                long revision = ConfigManager.getLayoutRevision();
                screen.mouseClicked(mouse(x, y, 0), false);
                screen.mouseDragged(mouse(overlay.dockLeft() + 1, y, 0), overlay.dockLeft() + 1 - x, 0);
                check(ChatDock.tabs().get(2).equals(clan) && !ChatDock.settings(clan).detached, "Dragging along must reorder, not detach");
                check(ConfigManager.get().chatTabOrder.equals(savedOrder) && ConfigManager.getLayoutRevision() == revision,
                        "Reorder preview wrote settings or rebuilt layouts before mouse release");
            });
            context.waitTicks(2); context.takeScreenshot("chat-tab-reorder-preview");
            context.runOnClient(client -> {
                var overlay = ChatTabOverlay.getInstance();
                var screen = (ChatScreen) client.gui.screen();
                int y = overlay.rowTop(client.getWindow().getGuiScaledHeight()) + 6;
                screen.mouseReleased(mouse(overlay.dockLeft() + 1, y, 0));
                check(ConfigManager.get().chatTabOrder.get(2).equals(clan), "Reordered tabs not committed");
                try {
                    var source = java.nio.file.Files.readString(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("froghelper.json"));
                    check(HudConfigCodec.decode(HudConfigCodec.createGson(), JsonParser.parseString(source)).chatTabOrder.get(2).equals(clan),
                            "Tab order lost after config reload");
                } catch (java.io.IOException e) { throw new AssertionError(e); }
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_TAB, 0, GLFW.GLFW_MOD_CONTROL));
                check(ChatDock.selectedKey().equals(ChatDock.key(ChatTab.GLOBAL)), "Ctrl+Tab ignored visual order");
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_TAB, 0, GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT));
                check(ChatDock.selectedKey().equals(clan), "Reverse tab cycling ignored visual order");
                int x = tabX(clan, y, client.getWindow().getGuiScaledWidth());
                screen.mouseClicked(mouse(x, y, 0), false);
                screen.mouseDragged(mouse(24, 22, 0), 24 - x, 22 - y); screen.mouseReleased(mouse(24, 22, 0));
                check(ChatDock.settings(clan).detached && ChatDock.tabs().getFirst().equals(ChatDock.key(ChatTab.ALL)), "Reordered tab cannot detach");
                ChatDock.dock(clan); ChatDock.select(clan);
                check(ChatDock.tabs().get(2).equals(clan), "Detachment forgot a tab's saved slot");
                x = tabX(clan, y, client.getWindow().getGuiScaledWidth());
                screen.mouseClicked(mouse(x, y, 0), false);
                screen.mouseDragged(mouse(overlay.dockRight() + 30, y, 0), overlay.dockRight() + 30 - x, 0);
                check(ChatDock.tabs().getLast().equals(clan) && !ChatDock.settings(clan).detached, "Horizontal exit detached a tab instead of placing it last");
                ChatDock.cancelDrag();
                check(ChatDock.tabs().get(2).equals(clan) && ConfigManager.get().chatTabOrder.get(2).equals(clan), "Cancelling reorder did not restore saved order");
                client.options.chatWidth().set(0d); ChatDock.select(clan);
                overlay.scrollTabs(overlay.dockLeft() + 5, y, 1000);
                x = tabX(clan, y, client.getWindow().getGuiScaledWidth());
                screen.mouseClicked(mouse(x, y, 0), false);
                int edge = overlay.dockRight() - 48;
                screen.mouseDragged(mouse(edge, y, 0), edge - x, 0);
            });
            context.waitTicks(10); context.takeScreenshot("chat-tab-reorder-scroll");
            context.runOnClient(client -> {
                check(tabScroll() > 0 && !ChatDock.settings(clan).detached, "Holding a tab at the edge did not scroll the dock");
                var screen = (ChatScreen) client.gui.screen();
                var narrowOverlay = ChatTabOverlay.getInstance();
                int narrowY = narrowOverlay.rowTop(client.getWindow().getGuiScaledHeight()) + 6;
                screen.mouseDragged(mouse(narrowOverlay.dockRight() + 30, narrowY, 0), 30, 0);
                screen.mouseReleased(mouse(narrowOverlay.dockRight() + 30, narrowY, 0));
                check(ChatDock.tabs().getLast().equals(clan) && tabX(clan, narrowY, client.getWindow().getGuiScaledWidth()) >= narrowOverlay.dockLeft(),
                        "Committing reorder left the selected tab outside the viewport");
                client.options.chatWidth().set(1d);
                custom[0] = ChatDock.create();
                var overlay = ChatTabOverlay.getInstance();
                int y = overlay.rowTop(client.getWindow().getGuiScaledHeight()) + 6;
                int x = tabX(custom[0], y, client.getWindow().getGuiScaledWidth());
                ChatDock.pressTab(custom[0], x, y); ChatDock.drag(overlay.dockLeft() + 1, y); ChatDock.release(overlay.dockLeft() + 1, y);
                check(ChatDock.tabs().get(2).equals(custom[0]) && !ChatDock.settings(custom[0]).detached, "Custom tabs cannot be reordered");
                check(ChatTabManager.getInstance().getMessages(ChatTab.ALL).size() == 48, "Reordering replayed or discarded history");
            });
        } finally {
            context.runOnClient(client -> {
                ChatDock.cancelDrag();
                if (custom[0] != null && ChatDock.settings(custom[0]) != null) ChatDock.delete(custom[0]);
                ConfigManager.get().chatTabOrder = new java.util.ArrayList<>(savedOrder);
                ConfigManager.layoutChanged(); ConfigManager.save();
                client.options.chatWidth().set(1d); ChatDock.select(ChatDock.key(ChatTab.ALL));
                Client.getInstance().getHudRenderer().refreshLayout();
            });
        }
    }
    private static int tabScroll() {
        try { Field field = ChatTabOverlay.class.getDeclaredField("stripScroll"); field.setAccessible(true); return field.getInt(ChatTabOverlay.getInstance()); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static MouseButtonEvent mouse(double x, double y, int button) { return new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0)); }
    private static int tabX(String key, int y, int width) {
        for (int x = 4; x < width; x++) if (key.equals(ChatTabOverlay.getInstance().tabAt(x, y, net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight()))) return x + 2;
        throw new AssertionError("Tab not visible: " + key);
    }
    private static void swap(HudConfig config) {
        try { Field field = ConfigManager.class.getDeclaredField("CONFIG"); field.setAccessible(true); field.set(null, config); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
