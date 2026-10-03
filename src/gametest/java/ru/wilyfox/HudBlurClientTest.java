package ru.wilyfox;

import com.mojang.blaze3d.pipeline.TextureTarget;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.WidgetChrome;
import ru.wilyfox.client.hud.widget.HudBlur;
import ru.wilyfox.client.hud.widget.HudSurface;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

public final class HudBlurClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        var oldChrome = ConfigManager.get().render.widgetChrome;
        boolean oldNative = ConfigManager.get().render.nativeRenderer;
        int oldScale = context.computeOnClient(client -> client.options.guiScale().get());
        int oldWidth = context.computeOnClient(client -> client.getWindow().getScreenWidth());
        int oldHeight = context.computeOnClient(client -> client.getWindow().getScreenHeight());
        ConfigManager.get().render.widgetChrome = WidgetChrome.FROST;
        ConfigManager.get().render.nativeRenderer = false;
        try (var world = context.worldBuilder().create()) {
            context.setScreen(BlurScreen::new);
            context.waitTicks(5);
            verifyBatching(context);
            // Odd dimensions also exercise UV alignment with a rounded GUI size.
            for (int[] size : new int[][]{{1280, 720, 2}, {763, 521, 2}, {1101, 751, 3},
                    {701, 501, 1}, {1280, 720, 2}, {1001, 751, 3}}) {
                context.runOnClient(client -> {
                    client.options.guiScale().set(size[2]);
                    client.getWindow().setWindowed(size[0], size[1]);
                    client.resizeGui();
                });
                context.waitTicks(5);
                context.runOnClient(client -> {
                    var window = client.gameRenderer.gameRenderState().windowRenderState;
                    TextureTarget target = (TextureTarget) field("blurred");
                    expect(target != null && target.width == window.width && target.height == window.height,
                            "Blur must follow the current window after shrinking or growing");
                    expect((boolean) field("available"), "Resize must leave blur available");
                    expect((long) field("lastFailureLog") == 0, "Resize must not fail texture capture");
                    expect(client.getShaderManager().getPostChain(
                            net.minecraft.resources.Identifier.fromNamespaceAndPath("froghelper", "hud_blur"),
                            net.minecraft.client.renderer.LevelTargetBundle.MAIN_TARGETS) != null,
                            "The blur shader chain must load successfully");
                });
            }
            context.runOnClient(client -> expect(((BlurScreen) client.gui.screen()).observedDeferredResize,
                    "Regression must exercise extraction before the main framebuffer resize"));
            context.takeScreenshot("hud-blur-resized-scale3");
            context.runOnClient(client -> {
                ConfigManager.get().render.widgetChrome = WidgetChrome.BARE;
                client.getWindow().setWindowed(763, 523);
            });
            context.waitTicks(3);
            context.runOnClient(client -> {
                expect((boolean) field("available") && (long) field("lastFailureLog") == 0,
                        "An explicitly frosted screen must prepare blur with global BARE chrome");
                ConfigManager.get().render.widgetChrome = WidgetChrome.FROST;
            });
            for (int toggle = 0; toggle < 2; toggle++) {
                context.runOnClient(client -> client.getWindow().toggleFullScreen());
                context.waitTicks(5);
                context.runOnClient(client -> expect((boolean) field("available") && (long) field("lastFailureLog") == 0,
                        "Fullscreen switching must preserve blur without capture errors"));
            }
            context.runOnClient(client -> {
                try {
                    var fail = HudBlur.class.getDeclaredMethod("fail", Exception.class);
                    fail.setAccessible(true);
                    fail.invoke(null, new IllegalStateException("Expected transient failure in HUD blur regression test"));
                } catch (ReflectiveOperationException exception) {
                    throw new AssertionError(exception);
                }
                expect(!(boolean) field("available"), "A failed frame must stop requesting blur");
            });
            context.waitTicks(3);
            context.runOnClient(client -> expect((boolean) field("available"),
                    "A transient failure must recover without restarting Minecraft"));
            context.takeScreenshot("hud-blur-recovered");
            context.setScreen(() -> null);
        } finally {
            context.runOnClient(client -> {
                ConfigManager.get().render.widgetChrome = oldChrome;
                ConfigManager.get().render.nativeRenderer = oldNative;
                client.options.guiScale().set(oldScale);
                client.getWindow().setWindowed(oldWidth, oldHeight);
                client.resizeGui();
                HudBlur.close();
            });
        }
    }

    private static void verifyBatching(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var state = new GuiRenderState();
            var graphics = new GuiGraphicsExtractor(client, state, 0, 0);
            graphics.pose().translate(20, 30);
            graphics.pose().scale(1.25f);
            graphics.enableScissor(0, 0, 100, 40);
            HudSurface.fillRounded(graphics, 0, 0, 120, 44, 5, 0x80445566);
            var count = new AtomicInteger();
            state.forEachElement(element -> {
                count.incrementAndGet();
                expect(element.scissorArea() != null && element.bounds() != null, "Batch must preserve clipping");
                expect(element.bounds().right() <= element.scissorArea().right(), "Batch bounds must intersect the scissor");
            }, GuiRenderState.TraverseRange.ALL);
            expect(count.get() == 1, "A rounded panel must submit one GUI element");
            graphics.fill(5, 5, 10, 10, 0xFFFFFFFF);
            count.set(0);
            state.forEachElement(element -> count.incrementAndGet(), GuiRenderState.TraverseRange.ALL);
            expect(count.get() == 2, "Batch submission must leave ordinary Minecraft fills intact");

            state.reset();
            HudBlur.blurBehind(graphics, 0, 0, 120, 44, 5);
            count.set(0);
            state.forEachElement(element -> count.incrementAndGet(), GuiRenderState.TraverseRange.ALL);
            expect(count.get() == 1, "Rounded blur must submit one GUI element");
        });
    }

    private static Object field(String name) {
        try {
            Field field = HudBlur.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class BlurScreen extends Screen {
        private boolean observedDeferredResize;
        private BlurScreen() {
            super(Component.literal("HUD blur regression"));
        }

        @Override public boolean isPauseScreen() { return false; }
        @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            HudBlur.beginFrame(graphics);
            var window = minecraft.gameRenderer.gameRenderState().windowRenderState;
            var main = minecraft.gameRenderer.mainRenderTarget();
            observedDeferredResize |= main.width != window.width || main.height != window.height;
            HudSurface.drawPanel(graphics, 20, 20, 170, 44, WidgetChrome.FROST, false);
            TextureTarget target = (TextureTarget) field("blurred");
            // This runs before GameRenderer resizes the main target. The old implementation fails here.
            expect(target != null && target.width == window.width && target.height == window.height,
                    "Extraction must use this frame's window dimensions rather than the previous target");
            graphics.text(minecraft.font, "Boss Timers", 27, 27, 0xFFFFFFFF);
            graphics.text(minecraft.font, "No active timers", 27, 40, 0xFFFFFFFF);
        }
    }
}
