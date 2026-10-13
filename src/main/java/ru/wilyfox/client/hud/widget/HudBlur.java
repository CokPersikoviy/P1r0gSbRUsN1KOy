package ru.wilyfox.client.hud.widget;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.resources.Identifier;
import ru.wilyfox.FrogHelper;
import ru.wilyfox.client.profiler.ModProfiler;

/** Captures the world before GUI drawing and composites only the rounded panel regions. */
public final class HudBlur {
    private static final Identifier BLUR_CHAIN = Identifier.fromNamespaceAndPath("froghelper", "hud_blur");
    // The vanilla outline blit only writes RGB: after a transparent clear it loses the entire
    // blur's alpha. This copy writes all RGBA channels without blending into the old frame.
    private static final com.mojang.blaze3d.pipeline.RenderPipeline DOWNSAMPLE_PIPELINE =
            com.mojang.blaze3d.pipeline.RenderPipeline.builder(net.minecraft.client.renderer.RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("froghelper", "pipeline/hud_downsample"))
                    .withVertexShader("core/screenquad")
                    .withFragmentShader("core/blit_screen")
                    .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.IN_SAMPLER)
                    .withColorTargetState(com.mojang.blaze3d.pipeline.ColorTargetState.DEFAULT)
                    .withDepthStencilState(java.util.Optional.empty()).withCull(false).build();
    private static TextureTarget blurred;
    private static CrossFrameResourcePool pool;
    private static boolean requested;
    private static boolean available;
    private static long lastFailureLog;
    private static GuiGraphicsExtractor preparedContext;
    private static int sourceWidth, sourceHeight;
    private static final int DOWNSAMPLE = 4;

    static int targetSize(int pixels) { return Math.max(1, Math.ceilDiv(pixels, DOWNSAMPLE)); }

    private HudBlur() {}

    /** Replayed frosted panels still sample this frame's world, using the same shared target. */
    public static void requestSnapshotCapture() { if (available && blurred != null && !HudSurface.nativeRenderer()) requested = true; }

    public static void beginFrame(GuiGraphicsExtractor context) {
        if (HudSurface.nativeRenderer()) {
            if (blurred != null || pool != null) close();
            return;
        }
        if (!HudSurface.shouldUseBlur(HudSurface.chrome(), HudSurface.nativeRenderer())) {
            return;
        }
        prepareTarget(context);
    }

    private static void prepareTarget(GuiGraphicsExtractor context) {
        if (preparedContext == context) return;
        preparedContext = context;
        try {
            var renderer = Minecraft.getInstance().gameRenderer;
            RenderTarget main = renderer.mainRenderTarget();
            // GUI extraction precedes GameRenderer.render(), which resizes the main target.
            // Extracted states must already reference a texture matching this frame's window.
            var window = renderer.gameRenderState().windowRenderState;
            if (window.width <= 0 || window.height <= 0) {
                available = false;
                return;
            }
            var format = main.getColorTexture().getFormat();
            int width = targetSize(window.width), height = targetSize(window.height);
            if (blurred == null || blurred.width != width || blurred.height != height
                    || blurred.getColorTexture().getFormat() != format) {
                TextureTarget replacement = new TextureTarget("FrogHelper HUD blur", width, height, false, format);
                if (blurred != null) blurred.destroyBuffers();
                blurred = replacement;
            }
            sourceWidth = window.width;
            sourceHeight = window.height;
            available = true;
        } catch (Exception exception) {
            fail(exception);
        }
    }

    /** Called on the render thread immediately before GuiRenderer draws the extracted GUI. */
    public static void captureBeforeGui() {
        if (!requested || !available || blurred == null) return;
        requested = false;
        try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("hud/blur/capture")) {
            Minecraft mc = Minecraft.getInstance();
            RenderTarget main = mc.gameRenderer.mainRenderTarget();
            if (main.width != sourceWidth || main.height != sourceHeight) {
                // Never replace the texture here: the extracted GUI already owns its view.
                throw new IllegalStateException("HUD blur target does not match the current framebuffer");
            }
            // Render a filtered copy: texture-to-texture copies cannot scale between resolutions.
            try (var downsample = ModProfiler.getInstance().scope("hud/blur/downsample");
                 var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                         () -> "FrogHelper HUD downsample", blurred.getColorTextureView(),
                         java.util.Optional.of(new org.joml.Vector4f(0)))) {
                pass.setPipeline(DOWNSAMPLE_PIPELINE);
                RenderSystem.bindDefaultUniforms(pass);
                pass.bindTexture("InSampler", main.getColorTextureView(),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                pass.draw(3, 1, 0, 0);
            }
            var chain = mc.getShaderManager().getPostChain(BLUR_CHAIN, LevelTargetBundle.MAIN_TARGETS);
            if (chain == null) return;
            if (pool == null) pool = new CrossFrameResourcePool(3);
            try (var filter = ModProfiler.getInstance().scope("hud/blur/filter")) { chain.process(blurred, pool); }
            pool.endFrame();
        } catch (Exception exception) {
            fail(exception);
        }
    }

    public static void blurBehind(GuiGraphicsExtractor context, int x, int y, int w, int h, int radius) {
        blurBehindIfAvailable(context, x, y, w, h, radius);
    }

    static boolean blurBehindIfAvailable(GuiGraphicsExtractor context, int x, int y, int w, int h, int radius) {
        if (HudSurface.nativeRenderer()) return false;
        // Screens can explicitly request FROST even when gameplay widgets use BARE/SOLID.
        // Preparing here also covers standalone screens without an explicit beginFrame call.
        prepareTarget(context);
        if (!available || blurred == null || w <= 0 || h <= 0) return false;
        requested = true;
        var window = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState;
        RoundedPanelRenderState.blit(context, blurred.getColorTextureView(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR), x, y, w, h, radius,
                (float) window.width / window.guiScale, (float) window.height / window.guiScale);
        return true;
    }

    public static void close() {
        ru.wilyfox.client.hud.internal.WidgetLayoutAnimation.invalidateResources();
        if (blurred != null) blurred.destroyBuffers();
        if (pool != null) pool.close();
        blurred = null;
        pool = null;
        requested = false;
        available = false;
        lastFailureLog = 0;
        preparedContext = null;
        sourceWidth = sourceHeight = 0;
    }

    private static void fail(Exception exception) {
        long now = System.nanoTime();
        if (lastFailureLog == 0 || now - lastFailureLog >= 10_000_000_000L) {
            FrogHelper.LOGGER.warn("HUD blur unavailable for this frame; retrying on the next frame", exception);
            lastFailureLog = now;
        }
        available = false;
        requested = false;
    }
}
