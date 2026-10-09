package ru.wilyfox.client.hud.menu;

import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.widget.HudBlur;
import ru.wilyfox.utils.InputModifiers;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class DragNumberClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        int original = ConfigManager.get().bossWidget.maxBosses;
        int oldScale = context.computeOnClient(client -> client.options.guiScale().get());
        int oldWidth = context.computeOnClient(client -> client.getWindow().getScreenWidth());
        int oldHeight = context.computeOnClient(client -> client.getWindow().getScreenHeight());
        var panel = new HudSettingsPanel();
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                var boss = ru.wilyfox.client.Client.getInstance().getHudRenderer().getWidgets().stream()
                        .filter(widget -> widget.getClass().getSimpleName().equals("BossHudWidget")).findFirst().orElseThrow();
                panel.openWidget(boss, panel::clearWidget);
                ConfigManager.get().bossWidget.maxBosses = 10;
                client.options.guiScale().set(2);
                client.getWindow().setWindowed(1280, 720);
                client.resizeGui();
            });
            context.setScreen(() -> new NumberScreen(panel));
            context.waitTicks(4);
            context.takeScreenshot("settings-drag-numbers");
            context.runOnClient(client -> {
                var manager = client.getSoundManager();
                var sound = Identifier.parse("froghelper:ui.scroll");
                expect(manager.getSoundEvent(sound) != null, "Scroll sound event was not loaded");
                expect(client.getResourceManager().getResource(Identifier.parse("froghelper:sounds/ui/scroll.ogg")).isPresent(),
                        "Scroll sample is missing from resources");
                var ticks = new AtomicInteger();
                net.minecraft.client.sounds.SoundEventListener listener = (instance, event, distance) -> {
                    if (instance.getIdentifier().equals(sound)) ticks.incrementAndGet();
                };
                manager.addListener(listener);
                try {
                    var value = new AtomicInteger(1000);
                    var number = new DragNumberSettingsComponent(0, 0, 200, 22, "Burst", value::get, value::set, 0, 10000);
                    expect(!number.mouseClicked(10, 10, 0), "Label must not start changing the number");
                    number.mouseClicked(180, 10, 0);
                    expect(value.get() == 1000 && ticks.get() == 0, "Pressing the number changed it or played sound");
                    long started = System.nanoTime();
                    for (int i = 1; i <= 1000; i++) number.mouseDragged(180 + i * 4, 10, 0, 4, 0);
                    long elapsed = System.nanoTime() - started;
                    expect(value.get() == 2000, "Relative drag lost changes");
                    expect(ticks.get() > 0 && ticks.get() <= 1 + elapsed / 25_000_000L, "Scroll ticks ignored the shared rate limit");
                    System.out.println("Numeric drag burst: 1000 value changes, " + ticks.get() + " sound ticks in " + elapsed / 1_000_000 + " ms");
                    number.onClickOutside();
                } finally { manager.removeListener(listener); }

                var number = number(panel);
                int mouseX = number.getX() + number.getWidth() - 35, mouseY = number.getY() + 11;
                panel.mousePressed(mouseX, mouseY, 0);
                expect(ConfigManager.get().bossWidget.maxBosses == 10, "Mouse press jumped to an absolute value");
                panel.mouseDragged(mouseX + 8, -1000, 0);
                expect(ConfigManager.get().bossWidget.maxBosses == 12, "Captured number stopped receiving drag outside the panel");
                int scroll = (int) field(panel, "scrollOffset");
                panel.mouseScrolled(mouseX, mouseY, -3);
                expect((int) field(panel, "scrollOffset") == scroll, "Scrolling changed the layout during number dragging");
                panel.mouseReleased(mouseX + 8, -1000, 0);
                expect(!number.isDragging(), "Outside release left a number dragging");
                expectSaved(12);
            });
            context.getInput().holdShift();
            context.runOnClient(client -> {
                expect(InputModifiers.hasShiftDown(), "Test Shift key was not held");
                var number = number(panel);
                int mouseX = number.getX() + number.getWidth() - 35, mouseY = number.getY() + 11;
                panel.mousePressed(mouseX, mouseY, 0);
                panel.mouseDragged(mouseX + 4, mouseY, 0);
                expect(ConfigManager.get().bossWidget.maxBosses == 12, "Shift did not reduce drag sensitivity");
                panel.mouseDragged(mouseX + 40, mouseY, 0);
                expect(ConfigManager.get().bossWidget.maxBosses == 13, "Fine drag did not change after forty GUI pixels");
                panel.mouseReleased(mouseX + 40, mouseY, 0);
                expectSaved(13);
            });
            context.getInput().releaseShift();
            context.runOnClient(client -> {
                var number = number(panel);
                int mouseX = number.getX() + number.getWidth() - 35, mouseY = number.getY() + 11;
                panel.mousePressed(mouseX, mouseY, 0);
                panel.mouseDragged(mouseX - 8, mouseY, 0);
                panel.finishInteraction();
                expect(!number.isDragging(), "Closing settings left an active number drag");
                expectSaved(11);
            });
            var times = new java.util.ArrayList<Long>();
            net.minecraft.client.sounds.SoundEventListener cadenceListener = (instance, event, distance) -> {
                if (instance.getIdentifier().equals(Identifier.parse("froghelper:ui.scroll"))) times.add(System.nanoTime());
            };
            var value = new AtomicInteger(1000);
            var number = new DragNumberSettingsComponent(0, 0, 200, 22, "Cadence", value::get, value::set, 0, 10000);
            context.waitTicks(3);
            long cadenceStarted = System.nanoTime();
            context.runOnClient(client -> {
                client.getSoundManager().addListener(cadenceListener);
                number.mouseClicked(180, 10, 0);
            });
            try {
                for (int i = 1; i <= 40; i++) {
                    int mouseX = 180 + i * 4;
                    context.runOnClient(client -> number.mouseDragged(mouseX, 10, 0, 4, 0));
                    context.waitTick();
                }
                long duration = System.nanoTime() - cadenceStarted;
                context.runOnClient(client -> {
                    number.onClickOutside();
                    expect(value.get() == 1040, "Sustained drag lost changes");
                    expect(times.size() >= duration / 1_000_000_000.0 * 8.5,
                            "Sustained playback was too sparse: " + times.size() + " in " + duration / 1_000_000 + " ms");
                    for (long start : times) expect(times.stream().filter(t -> t >= start && t < start + 1_000_000_000L).count() <= 40,
                            "Actual playback exceeded forty scroll sounds in one second");
                    System.out.println("Sustained numeric drag: " + times.size() + " sound ticks in " + duration / 1_000_000 + " ms");
                });
            } finally {
                context.runOnClient(client -> {
                    number.onClickOutside();
                    client.getSoundManager().removeListener(cadenceListener);
                });
            }
        } finally {
            context.getInput().releaseShift();
            context.setScreen(() -> null);
            context.runOnClient(client -> {
                panel.finishInteraction();
                ConfigManager.get().bossWidget.maxBosses = original;
                ConfigManager.save();
                client.options.guiScale().set(oldScale);
                client.getWindow().setWindowed(oldWidth, oldHeight);
                client.resizeGui();
            });
        }
    }

    private static DragNumberSettingsComponent number(HudSettingsPanel panel) {
        return ((List<?>) field(panel, "widgetComponents")).stream().map(SettingsComponent.class::cast)
                .filter(component -> component.label.equals("Max bosses")).map(DragNumberSettingsComponent.class::cast)
                .findFirst().orElseThrow();
    }
    private static Object field(Object target, String name) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }
    private static void expectSaved(int value) {
        try {
            var saved = JsonParser.parseString(Files.readString(FabricLoader.getInstance().getConfigDir().resolve("froghelper.json")));
            expect(saved.getAsJsonObject().getAsJsonObject("bossWidget").get("maxBosses").getAsInt() == value,
                    "Number change was not saved");
        } catch (java.io.IOException failure) { throw new AssertionError(failure); }
    }
    private static void expect(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private static final class NumberScreen extends Screen {
        private final HudSettingsPanel panel;
        private NumberScreen(HudSettingsPanel panel) { super(Component.literal("Numeric settings")); this.panel = panel; }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {}
        @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
            HudBlur.beginFrame(context);
            panel.render(context, mouseX, mouseY);
        }
        @Override public void removed() { panel.finishInteraction(); super.removed(); }
    }
}
