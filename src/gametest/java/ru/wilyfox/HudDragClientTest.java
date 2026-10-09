package ru.wilyfox;

import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.HudRenderer;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.hud.menu.HudSettingsPanel;
import ru.wilyfox.client.hud.widget.AbstractWidget;

import java.io.IOException;
import java.nio.file.Files;

public final class HudDragClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            HudRenderer renderer = new HudRenderer(new HudSettingsPanel());
            DragTestWidget dragged = new DragTestWidget();
            DragTestWidget companion = new DragTestWidget();
            renderer.registerWidget(dragged);
            renderer.registerWidget(companion);
            dragged.setConfigKey("HudDragTestPrimary");
            companion.setConfigKey("HudDragTestCompanion");
            dragged.clearWidgetSnap();
            companion.clearWidgetSnap();
            dragged.setScreenAnchor(null);
            companion.setScreenAnchor(null);
            renderer.setEditing(true);
            HudEditingScreen screen = new HudEditingScreen(renderer);
            client.gui.setScreen(screen);
            int panelWidth = Math.min(198, Math.max(146, screen.width / 3));
            screen.mouseClicked(mouse(8 + panelWidth - 15, 18, 0), false);

            int startX = Math.max(110, screen.width / 3);
            int startY = screen.height / 3;
            dragged.setStartX(startX);
            dragged.setStartY(startY);
            companion.setStartX(startX + 90);
            companion.setStartY(startY + 40);
            screen.mouseClicked(mouse(startX + 5, startY + 5, 0), false);

            // All six mouse moves happen in the same client callback, without a tick.
            // The old GLFW tick poller left the widget at its initial position here.
            for (int step = 1; step <= 6; step++) {
                screen.mouseDragged(mouse(startX + 5 + step * 3, startY + 5 + step * 2, 0), 3, 2);
                expect(dragged.getStartX() == startX + step * 3, "Drag x must update before the next tick");
                expect(dragged.getStartY() == startY + step * 2, "Drag y must update before the next tick");
            }
            screen.mouseDragged(mouse(startX + 50, startY + 50, 1), 20, 20);
            expect(dragged.getStartX() == startX + 18, "Other mouse buttons must not move the widget");

            screen.mouseReleased(mouse(startX + 29, startY + 21, 0));
            expect(dragged.getStartX() == startX + 24, "Release must keep the final pointer x");
            expect(dragged.getStartY() == startY + 16, "Release must keep the final pointer y");
            screen.mouseDragged(mouse(startX + 60, startY + 60, 0), 20, 20);
            expect(dragged.getStartX() == startX + 24, "Released widgets must stop following the pointer");

            try {
                var saved = JsonParser.parseString(Files.readString(
                        FabricLoader.getInstance().getConfigDir().resolve("froghelper.json")))
                        .getAsJsonObject().getAsJsonObject("widgetLayouts");
                var savedDragged = saved.getAsJsonObject(dragged.getConfigKey());
                var savedCompanion = saved.getAsJsonObject(companion.getConfigKey());
                expect(savedDragged.get("x").getAsInt() == startX + 24, "Batch save must persist the dropped widget");
                expect(savedDragged.get("y").getAsInt() == startY + 16, "Batch save must persist both axes");
                expect(savedDragged.get("xFraction").getAsDouble() == (startX + 24) / (double) screen.width,
                        "Batch save must persist resolution independent positions");
                expect(savedCompanion.get("x").getAsInt() == startX + 90, "Batch save must persist the complete layout");
            } catch (IOException exception) {
                throw new AssertionError("Could not inspect saved widget layouts", exception);
            } finally {
                client.gui.setScreen(null);
                ConfigManager.get().widgetLayouts.remove(dragged.getConfigKey());
                ConfigManager.get().widgetLayouts.remove(companion.getConfigKey());
                ConfigManager.save();
            }
        });
    }

    private static MouseButtonEvent mouse(double x, double y, int button) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0));
    }

    private static void expect(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class DragTestWidget extends AbstractWidget {
        private DragTestWidget() {
            super(0, 0, HudLayer.CONTENT);
            width = 20;
            height = 12;
        }

        @Override
        public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        }
    }
}
