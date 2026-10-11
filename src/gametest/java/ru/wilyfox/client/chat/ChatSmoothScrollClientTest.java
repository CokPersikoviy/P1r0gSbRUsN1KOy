package ru.wilyfox.client.chat;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.Client;
import ru.wilyfox.client.hud.config.ConfigManager;

public final class ChatSmoothScrollClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> ConfigManager.snapshot());
        var renderer = Client.getInstance().getHudRenderer();
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                ConfigManager.get().mainLayout.widgets.clear(); ConfigManager.get().locationLayouts.clear();
                ConfigManager.get().render.chatTimestamps = false;
                ConfigManager.get().render.fixedChatWidgetFont = false;
                renderer.setEditing(false); renderer.setSettings(false);
                ChatDock.select(ChatDock.key(ChatTab.ALL)); client.gui.hud.getChat().clearMessages(false);
                var key = ChatDock.key(ChatTab.CLAN);
                var settings = ChatDock.settings(key);
                settings.deleted = false; settings.textFilter = ""; settings.skipOthers = false;
                settings.width = 180; settings.rows = 4; settings.showTitle = true;
                ChatDock.detach(key, 20, 20);
                var widget = ChatDock.widget(key); widget.setScale(1);
                for (int i = 0; i < 80; i++) {
                    int message = i;
                    client.gui.hud.getChat().addServerSystemMessage(
                        Component.literal("C Fox: message " + i + " alpha beta gamma delta epsilon zeta eta theta iota kappa lambda")
                                .withStyle(style -> style.withClickEvent(new ClickEvent.SuggestCommand("/m Fox " + message))));
                }
                var view = widget.view();
                view.restoreScroll(6); view.lines(client.font, 166, 4);
                long now = 1_000_000_000L;
                view.smoothScroll(2, now);
                var start = view.presentation(client.font, 166, 4, now);
                var middle = view.presentation(client.font, 166, 4, now + 70_000_000L);
                check(view.scroll() == 8 && start.shift() == 0 && Math.abs(middle.shift() - .75f) < .001,
                        "Wheel must interpolate between wrapped rows instead of jumping to its target");
                check(middle.lines().size() == 5, "Animation must include one clipped extra row");
                check(view.presentation(client.font, 166, 4, now + 71_000_000L).lines() == middle.lines(),
                        "Fractional animation must not rebuild the viewport every frame");
                check(view.presentation(client.font, 166, 4, now + 140_000_000L).lines()
                        == view.lines(client.font, 166, 4), "Settled animation must reuse the normal viewport");
                var anchor = view.lines(client.font, 166, 4).getFirst();
                client.gui.hud.getChat().addServerSystemMessage(Component.literal("C Fox: incoming"));
                check(view.lines(client.font, 166, 4).getFirst().message() == anchor.message(), "Incoming message moved the reader");
                // Rewrap and resize, including the layout revision raised by the settings menu.
                view.restoreScroll(20); var narrow = view.lines(client.font, 80, 4).getFirst();
                ConfigManager.layoutChanged();
                var wide = view.lines(client.font, 300, 4).getFirst();
                check(wide.message() == narrow.message(), "Width change lost the message anchor");
                check(wide.index() < ChatTabManager.getInstance().wrappedLines(wide.message(), client.font, 300).size(),
                        "Rewrapped anchor uses an invalid old line index");
                check(view.lines(client.font, 300, 8).getFirst().message() == wide.message(), "Height change moved the reader");
                // Small windows use half their rows per notch; trackpad fractions accumulate.
                view.restoreScroll(0); widget.scroll(.2, false); widget.scroll(.2, false); widget.scroll(.2, false);
                check(view.scroll() == 1, "Fractional wheel input was discarded");
                view.restoreScroll(0); widget.scroll(1, false); check(view.scroll() == 2, "Small-window wheel step is not adaptive");
                view.restoreScroll(0); ConfigManager.get().render.fixedChatWidgetFont = true; widget.setScale(2);
                widget.scroll(1, false); check(view.scroll() == 5, "Fixed-font scroll used configured rows rather than actual viewport");
                view.restoreScroll(0); widget.scroll(1, true); check(view.scroll() == 1, "Shift must move one row regardless of scale");
                ConfigManager.get().render.fixedChatWidgetFont = false; widget.setScale(1);
                view.restoreScroll(6); view.lines(client.font, 166, 4);
                view.smoothScroll(2, System.nanoTime() - 50_000_000L);
            });
            context.setScreen(() -> new ChatScreen("", false));
            context.runOnClient(client -> {
                var widget = ChatDock.widget(ChatDock.key(ChatTab.CLAN));
                var state = new GuiRenderState();
                widget.render(new GuiGraphicsExtractor(client, state, 0, 0), net.minecraft.client.DeltaTracker.ZERO);
                long time;
                try {
                    var field = widget.getClass().getDeclaredField("presentationTime"); field.setAccessible(true); time = field.getLong(widget);
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                var frame = widget.view().presentation(client.font, 166, 4, time);
                double x = widget.getStartX() + 14;
                double y = widget.getStartY() + widget.getHeight() - 6 + frame.shift() * 10 - 15;
                check(widget.lineAt(x, y).message() == frame.lines().get(1).message(), "Hit test follows target rather than drawn rows");
                check(widget.styleAt(x, y) != null && widget.styleAt(x, y).getClickEvent() != null,
                        "Animated text lost clickable styles");
                check(widget.lineAt(x, widget.getStartY() + 8) == null, "Clipped text is clickable in the header");
            });
            context.takeScreenshot("chat-smooth-scroll");
        } finally {
            context.runOnClient(client -> {
                client.gui.setScreen(null); ConfigManager.restoreSnapshot(original); ConfigManager.layoutChanged(); renderer.refreshLayout();
                ChatDock.select(ChatDock.key(ChatTab.ALL)); client.gui.hud.getChat().clearMessages(false);
            });
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
