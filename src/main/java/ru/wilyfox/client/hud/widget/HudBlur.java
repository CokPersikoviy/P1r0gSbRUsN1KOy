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
    private static TextureTarget blurred;
    private static CrossFrameResourcePool pool;
    private static boolean requested;
    private static boolean available;
    private static long lastFailureLog;
    private static GuiGraphicsExtractor preparedContext;

    private HudBlur() {}

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
            if (blurred == null || blurred.width != window.width || blurred.height != window.height
                    || blurred.getColorTexture().getFormat() != format) {
                TextureTarget replacement = new TextureTarget("FrogHelper HUD blur", window.width, window.height, false, format);
                if (blurred != null) blurred.destroyBuffers();
                blurred = replacement;
            }
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
            if (main.width != blurred.width || main.height != blurred.height) {
                // Never replace the texture here: the extracted GUI already owns its view.
                throw new IllegalStateException("HUD blur target does not match the current framebuffer");
            }
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                    main.getColorTexture(), blurred.getColorTexture(), 0, 0, 0, 0, 0, blurred.width, blurred.height);
            var chain = mc.getShaderManager().getPostChain(BLUR_CHAIN, LevelTargetBundle.MAIN_TARGETS);
            if (chain == null) return;
            if (pool == null) pool = new CrossFrameResourcePool(3);
            chain.process(blurred, pool);
            pool.endFrame();
        } catch (Exception exception) {
            fail(exception);
        }
    }

    public static void blurBehind(GuiGraphicsExtractor context, int x, int y, int w, int h, int radius) {
        if (HudSurface.nativeRenderer()) return;
        // Screens can explicitly request FROST even when gameplay widgets use BARE/SOLID.
        // Preparing here also covers standalone screens without an explicit beginFrame call.
        prepareTarget(context);
        if (!available || blurred == null || w <= 0 || h <= 0) return;
        requested = true;
        var window = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState;
        RoundedPanelRenderState.blit(context, blurred.getColorTextureView(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR), x, y, w, h, radius,
                (float) window.width / window.guiScale, (float) window.height / window.guiScale);
    }

    public static void close() {
        if (blurred != null) blurred.destroyBuffers();
        if (pool != null) pool.close();
        blurred = null;
        pool = null;
        requested = false;
        available = false;
        lastFailureLog = 0;
        preparedContext = null;
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
