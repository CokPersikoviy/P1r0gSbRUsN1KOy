package ru.wilyfox.client.hud.menu;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.client.chat.LowHpMessageFormatter;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.LowHpMessageElement;
import ru.wilyfox.client.hud.config.LowHpMessageFormatConfig;
import ru.wilyfox.client.hud.widget.HudBlur;

import java.util.List;
import java.util.Map;

public final class LowHpMessageBuilderClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var original = ConfigManager.get().bossRespawnMessages.lowHealthFormat;
        int oldScale = context.computeOnClient(client -> client.options.guiScale().get());
        int oldWidth = context.computeOnClient(client -> client.getWindow().getScreenWidth());
        int oldHeight = context.computeOnClient(client -> client.getWindow().getScreenHeight());
        var panel = new HudSettingsPanel();
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                ConfigManager.get().bossRespawnMessages.lowHealthFormat = new LowHpMessageFormatConfig();
                client.options.guiScale().set(2);
                client.getWindow().setWindowed(1280, 720);
                client.resizeGui();
                setField(panel, "activeCategory", SettingsCategory.LOW_HP_MESSAGES);
            });
            context.setScreen(() -> new BuilderScreen(panel));
            context.waitTicks(4);
            context.takeScreenshot("low-hp-message-builder");
            context.runOnClient(client -> {
                List<SettingsComponent> components = components(panel);
                if (components.stream().filter(component -> component instanceof LowHpMessagePreviewComponent).count() != 1
                        || components.stream().filter(component -> component instanceof TextInputSettingsComponent).count() != 7) {
                    fail("Low HP constructor has missing preview or color fields");
                }
                var server = components.stream().filter(component -> component.label.equals(LowHpMessageElement.SERVER.title()))
                        .findFirst().orElseThrow();
                setField(panel, "scrollOffset", server.getY() - ((int) field(panel, "y") + 60));
            });
            context.waitTicks(2);
            context.runOnClient(client -> {
                var components = components(panel);
                var server = components.stream().filter(component -> component.label.equals(LowHpMessageElement.SERVER.title()))
                        .findFirst().orElseThrow();
                if (!panel.mousePressed(server.getX() + 8, server.getY() + 8, 0)) fail("Visibility toggle did not receive click");
                var format = ConfigManager.get().bossRespawnMessages.lowHealthFormat;
                if (format.element(LowHpMessageElement.SERVER).visible
                        || LowHpMessageFormatter.preview(format).plainText().contains("PE1.2")) fail("Hiding subserver did not update preview");
                panel.mousePressed(server.getX() + 8, server.getY() + 8, 0);
            });
            context.waitTicks(2);
            context.runOnClient(client -> {
                var color = components(panel).stream().filter(component -> component instanceof TextInputSettingsComponent)
                        .findFirst().orElseThrow();
                panel.mousePressed(color.getX() + color.getWidth() - 12, color.getY() + 8, 0);
                panel.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0);
                panel.keyPressed(GLFW.GLFW_KEY_DELETE, 0, 0);
                panel.keyPressed(GLFW.GLFW_KEY_DELETE, 0, 0);
                panel.charTyped('§', 0);
                if (!ConfigManager.get().bossRespawnMessages.lowHealthFormat.element(LowHpMessageElement.SERVER).colorCode.equals("&")) {
                    fail("Config saving discarded incomplete color input");
                }
                panel.charTyped('c', 0);
                panel.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                var format = ConfigManager.get().bossRespawnMessages.lowHealthFormat;
                if (!format.element(LowHpMessageElement.SERVER).colorCode.equals("&c")
                        || !LowHpMessageFormatter.preview(format).clanText().startsWith("&c[PE1.2]")) fail("Color editor did not update preview and message");
            });
            context.waitTicks(2);
            context.takeScreenshot("low-hp-message-builder-colors");
        } finally {
            context.setScreen(() -> null);
            context.runOnClient(client -> {
                ConfigManager.get().bossRespawnMessages.lowHealthFormat = original;
                ConfigManager.save();
                client.options.guiScale().set(oldScale);
                client.getWindow().setWindowed(oldWidth, oldHeight);
                client.resizeGui();
            });
        }
    }

    private static List<SettingsComponent> components(HudSettingsPanel panel) {
        var map = (Map<?, ?>) field(panel, "componentsByCategory");
        return ((List<?>) map.get(SettingsCategory.LOW_HP_MESSAGES)).stream().map(SettingsComponent.class::cast).toList();
    }

    private static Object field(Object target, String name) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }

    private static void fail(String message) { throw new AssertionError(message); }

    private static final class BuilderScreen extends Screen {
        private final HudSettingsPanel panel;
        private BuilderScreen(HudSettingsPanel panel) {
            super(Component.literal("Low HP message builder"));
            this.panel = panel;
        }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {}
        @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
            HudBlur.beginFrame(context);
            panel.render(context, mouseX, mouseY);
        }
    }
}
