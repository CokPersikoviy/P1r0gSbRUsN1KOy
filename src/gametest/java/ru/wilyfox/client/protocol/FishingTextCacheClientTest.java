package ru.wilyfox.client.protocol;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.hud.config.*;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.hud.widget.*;
import java.lang.reflect.Field;
import java.util.*;

public final class FishingTextCacheClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var snapshot = context.computeOnClient(client -> ConfigManager.snapshot());
        var state = state(); var oldNames = state.fishingLocationNames; var oldNibbles = state.fishingNibbles;
        int scale = context.computeOnClient(client -> client.options.guiScale().get());
        int width = context.computeOnClient(client -> client.getWindow().getScreenWidth()), height = context.computeOnClient(client -> client.getWindow().getScreenHeight());
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                state.fishingLocationNames = new LinkedHashMap<>(); state.fishingNibbles = new LinkedHashMap<>(); state.fishingRevision++;
                ProtocolPayloadHandlers.applyFishingSpots(state, new DwFishingSpotsPacket(Map.of("bay", "Залив", "swamp", "Болото", "magma", "Магма")));
                ProtocolPayloadHandlers.applySpotNibbles(state, new DwSpotNibblesPacket(Map.of("bay", 34.5, "swamp", 73.25, "magma", 52.8)));
                ConfigManager.get().fishing.nibblesVisibility = FishingWidgetVisibility.ALWAYS;
                ConfigManager.get().fishing.nibblesSort = FishingNibblesSort.DIMENSION;
                ConfigManager.get().render.widgetChrome = WidgetChrome.SOLID;
                ConfigManager.get().render.nativeRenderer = false;
                ConfigManager.get().render.lightweightHud = false;
                ConfigManager.get().mainLayout.widgets.clear(); ConfigManager.get().locationLayouts.clear();
                ConfigManager.get().mainLayout.widgets.add(WidgetCatalog.FISHING_NIBBLES.key()); ConfigManager.layoutChanged();
                var widget = new FishingNibblesWidget(20, 45, HudLayer.CONTENT); widget.setConfigKey(WidgetCatalog.FISHING_NIBBLES.key()); widget.setScale(1);
                int firstWidth = widget.getWidth(); Object cached = field(widget, "cachedLines");
                for (int i = 0; i < 100; i++) { widget.getWidth(); widget.getHeight(); }
                check(field(widget, "cachedLines") == cached, "Unchanged nibbles rebuilt their text");
                ProtocolPayloadHandlers.applySpotNibbles(state, new DwSpotNibblesPacket(Map.of("bay", 91.2)));
                widget.getWidth(); check(field(widget, "cachedLines") != cached, "New packet did not invalidate nibbles");
                check(((List<?>) field(widget, "cachedLines")).contains("Залив - 91.2%"), "Updated value was stale");
                ConfigManager.get().fishing.nibblesSort = FishingNibblesSort.NIBBLE;
                widget.getWidth(); check(((List<?>) field(widget, "cachedLines")).getFirst().equals("Залив - 91.2%"), "Sort change did not invalidate nibbles");
                ProtocolPayloadHandlers.applyFishingSpots(state, new DwFishingSpotsPacket(Map.of("bay", "Очень длинное название залива")));
                check(widget.getWidth() > firstWidth, "Location name change did not invalidate widths");
                ProtocolPayloadHandlers.applyFishingSpots(state, new DwFishingSpotsPacket(Map.of("bay", "Залив")));
                ConfigManager.get().fishing.nibblesSort = FishingNibblesSort.DIMENSION;
                client.getWindow().setWindowed(1280, 720);
            });
            context.waitTicks(2);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); client.gui.setScreen(new CompareScreen()); });
            context.waitTicks(3); context.takeScreenshot("fishing-text-visual-parity");
            context.runOnClient(client -> {
                var widget = new FishingNibblesWidget(0, 0, HudLayer.CONTENT); widget.getWidth();
                state.resetRuntimeState(); widget.getWidth();
                check(((List<?>) field(widget, "cachedLines")).isEmpty(), "World reset retained the cached fishing rows");
            });
        } finally {
            context.runOnClient(client -> {
                client.gui.setScreen(null); state.fishingLocationNames = oldNames; state.fishingNibbles = oldNibbles; state.fishingRevision++;
                ConfigManager.restoreSnapshot(snapshot); client.getWindow().setWindowed(width, height); client.options.guiScale().set(scale); client.resizeGui();
            });
        }
    }
    private static final class CompareScreen extends Screen {
        final LegacyFishingNibblesWidget old = new LegacyFishingNibblesWidget(20, 45, HudLayer.CONTENT);
        final FishingNibblesWidget fresh = new FishingNibblesWidget(340, 45, HudLayer.CONTENT);
        CompareScreen() {
            super(Component.literal("Fishing cache parity")); old.setConfigKey(WidgetCatalog.FISHING_NIBBLES.key()); fresh.setConfigKey(WidgetCatalog.FISHING_NIBBLES.key()); old.setScale(1); fresh.setScale(1);
        }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void extractBackground(GuiGraphicsExtractor g, int x, int y, float delta) { }
        @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
            g.fill(0, 0, width, height, 0xFF18232B);
            g.text(font, "Previous", 20, 16, -1); g.text(font, "Optimized", 340, 16, -1);
            old.render(g, DeltaTracker.ZERO); fresh.render(g, DeltaTracker.ZERO);
            check(old.getWidth() == fresh.getWidth() && old.getHeight() == fresh.getHeight(), "Fishing layout differs from its reference");
        }
    }
    private static ProtocolState state() {
        try { var f = DiamondWorldProtocolClient.class.getDeclaredField("STATE"); f.setAccessible(true); return (ProtocolState) f.get(null); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static Object field(Object object, String name) {
        try { Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
