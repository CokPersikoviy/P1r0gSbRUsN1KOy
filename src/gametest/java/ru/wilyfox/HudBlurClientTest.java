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
            verifyBlurPixels(context);
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
                    expect(target != null && target.width == Math.ceilDiv(window.width, 4) && target.height == Math.ceilDiv(window.height, 4),
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

    /** Read raw GPU RGBA, not Screenshot (which forces every pixel's alpha to 255). */
    private static void verifyBlurPixels(ClientGameTestContext context) {
        var result = new java.util.concurrent.CompletableFuture<byte[]>();
        int[] size = new int[2];
        context.runOnClient(client -> {
            var main = client.gameRenderer.mainRenderTarget();
            try (var pattern = new com.mojang.blaze3d.platform.NativeImage(main.width, main.height, false)) {
                for (int y = 0; y < main.height; y++) for (int x = 0; x < main.width; x++) {
                    int color = y < main.height / 2 ? x < main.width / 2 ? 0xffffffff : 0xff000000
                            : x < main.width / 2 ? 0xffcc2020 : 0xff2040cc;
                    pattern.setPixel(x, y, color);
                }
                com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder().writeToTexture(main.getColorTexture(), pattern);
            }
            var state = new GuiRenderState();
            var graphics = new GuiGraphicsExtractor(client, state, 0, 0);
            HudBlur.beginFrame(graphics); HudBlur.blurBehind(graphics, 0, 0, 100, 100, 6);
            HudBlur.captureBeforeGui();
            TextureTarget target = (TextureTarget) field("blurred"); size[0] = target.width; size[1] = target.height;
            int bytes = size[0] * size[1] * 4;
            var device = com.mojang.blaze3d.systems.RenderSystem.getDevice();
            var buffer = device.createBuffer(() -> "HUD blur regression readback",
                    com.mojang.blaze3d.buffers.GpuBuffer.USAGE_COPY_DST | com.mojang.blaze3d.buffers.GpuBuffer.USAGE_MAP_READ, bytes);
            device.createCommandEncoder().copyTextureToBuffer(target.getColorTexture(), buffer, 0, () -> {
                try (buffer; var mapped = buffer.map(true, false)) {
                    byte[] rgba = new byte[bytes]; mapped.data().get(rgba); result.complete(rgba);
                } catch (Throwable failure) { result.completeExceptionally(failure); }
            }, 0);
        });
        for (int i = 0; i < 100 && !result.isDone(); i++) context.waitTicks(1);
        expect(result.isDone(), "Blur GPU readback did not complete");
        byte[] pixels = result.join(); int w = size[0], h = size[1];
        // Alpha must survive the downsample; otherwise a successfully loaded blur is invisible.
        for (int[] point : new int[][]{{w / 4, h / 4}, {w * 3 / 4, h / 4}, {w / 4, h * 3 / 4}, {w * 3 / 4, h * 3 / 4}})
            expect((pixels[(point[1] * w + point[0]) * 4 + 3] & 255) >= 250, "HUD blur is transparent: downsample lost alpha");
        int nearBoundary = (pixels[(h / 4 * w + w / 2) * 4] & 255);
        expect(nearBoundary > 20 && nearBoundary < 235, "Blur did not soften the black/white edge");
        int a = (h * 3 / 4 * w + w / 4) * 4, b = (h * 3 / 4 * w + w * 3 / 4) * 4;
        expect((pixels[a] & 255) > (pixels[a + 2] & 255) + 80 && (pixels[b + 2] & 255) > (pixels[b] & 255) + 80,
                "Downsample lost RGB colour or screen alignment");
        System.out.println("HUD blur raw GPU pixels: opaque alpha, softened edge and correct RGB verified");
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
            expect(target != null && target.width == Math.ceilDiv(window.width, 4) && target.height == Math.ceilDiv(window.height, 4),
                    "Extraction must use this frame's window dimensions rather than the previous target");
            graphics.text(minecraft.font, "Boss Timers", 27, 27, 0xFFFFFFFF);
            graphics.text(minecraft.font, "No active timers", 27, 40, 0xFFFFFFFF);
        }
    }
}
