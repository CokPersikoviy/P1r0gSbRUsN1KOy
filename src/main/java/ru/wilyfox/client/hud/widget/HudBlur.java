package ru.wilyfox.client.hud.widget;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL30;
import ru.wilyfox.FrogHelper;
import ru.wilyfox.client.profiler.ModProfiler;

/**
 * Real frosted-glass backdrop blur for HUD panels, MC 1.21.4. No custom GLSL, no vertex buffers —
 * just framebuffer blits + the engine's own {@code minecraft:blur} post-effect.
 *
 * <p>Split into two phases to keep the per-panel path cheap and free of framebuffer-state churn (the
 * churn was the source of the one-frame, per-panel flicker):
 * <ul>
 *   <li>{@link #beginFrame} — once per frame, before any widget: copy the main target into a private
 *       {@link TextureTarget} and blur it. This is the only phase that rebinds the draw target /
 *       runs the post-chain.</li>
 *   <li>{@link #blurBehind} — per panel: copy the panel's rounded region from the blurred snapshot
 *       back into the main target. <b>Touches only the READ framebuffer</b>; the draw target stays the
 *       main target the HUD is already rendering into.</li>
 * </ul>
 *
 * Any failure disables the effect for the session and falls back to a flat tint — never crashes.
 */
public final class HudBlur {
    private static final ResourceLocation BLUR_CHAIN = ResourceLocation.withDefaultNamespace("blur");
    /** Blur strength passed to the box-blur uniform. Tunable; higher = softer, a bit more cost. */
    private static final float BLUR_RADIUS = 8.0f;

    /** Reuse the blurred backdrop for this many HUD frames between captures (so it re-blurs every
     *  Nth+1 frame). The backdrop is heavily low-pass, so a 2-frame lag is imperceptible, but it cuts
     *  the full-screen blur post-effect — the dominant Frost GPU cost — to a third. */
    private static final int BLUR_REUSE_FRAMES = 2;

    private static TextureTarget blurred;
    private static CrossFrameResourcePool pool; // reused across frames so the blur's swap target is stable
    private static boolean capturedOk;          // is a usable blurred backdrop currently available?
    private static boolean disabled;            // a hard failure switches the effect off for the session
    private static int reuseCountdown;          // frames left to reuse the current backdrop before recapturing
    private static int lastCaptureW;            // main-target size of the last capture (recapture on change)
    private static int lastCaptureH;

    private HudBlur() {
    }

    /**
     * Capture + blur the whole screen once, at the very start of HUD rendering (before any widget).
     * All the heavy, draw-target-rebinding work lives here so the per-panel composite stays trivial.
     */
    public static void beginFrame(GuiGraphics context) {
        if (disabled) {
            capturedOk = false;
            return;
        }
        // The blurred backdrop is ONLY consumed by FROST panels via blurBehind. In the native renderer
        // every panel takes the flat-tint path (blurBehind is never called), so capturing + blurring the
        // whole screen here was a full-screen post-effect burned every frame for nothing — the "slow even
        // on native renderer" cost. Skip it entirely when native is on.
        if (HudSurface.nativeRenderer()) {
            capturedOk = false;
            ModProfiler.getInstance().incrementCounter("hud/blur/capture/skippedNative");
            return;
        }

        try {
            // Flush pending draws EVERY frame, even when reusing the backdrop. blurBehind composites via
            // raw glBlitFramebuffer, so anything meant to sit behind a panel — e.g. a screen's full-screen
            // dim overlay batched right before this call — must already be in the main target. If it isn't,
            // it flushes LAST and paints over the frosted panels/widgets (a hard 3-frame flicker on the
            // reuse frames). Only the expensive capture is throttled, not this cheap flush.
            try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("hud/blur/flush")) {
                context.flush();
            }

            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            boolean sizeChanged = main.width != lastCaptureW || main.height != lastCaptureH;

            // Reuse only for the gameplay HUD. When a Screen is open (inventory / rune bag / QuickAccess),
            // the live world behind the menu is re-rendered every frame, so a big translucent frost panel
            // over a 3-frame-stale blurred backdrop flickers as the backdrop jumps between fresh capture
            // frames and stale reuse frames. Screens draw only 1-2 panels and aren't a gameplay-FPS path,
            // so capture fresh every frame there (the pre-reuse behaviour, which never flickered).
            boolean inScreen = Minecraft.getInstance().screen != null;

            // Reuse last frame's blurred backdrop for a few frames instead of re-blurring every frame — the
            // backdrop is heavily low-pass so a 2-frame lag is invisible, and this thirds the full-screen
            // blur pass. Always recapture on a size change so blurBehind never samples a stale-sized target.
            if (!inScreen && capturedOk && !sizeChanged && reuseCountdown > 0) {
                reuseCountdown--;
                ModProfiler.getInstance().incrementCounter("hud/blur/capture/reused");
                return;
            }

            capture();
            reuseCountdown = BLUR_REUSE_FRAMES;
        } catch (Throwable t) {
            fail("capturing/blurring the scene", t);
        }
    }

    /** Whether this frame's capture produced a usable blurred backdrop (pure read). */
    public static boolean isAvailable() {
        return capturedOk && !disabled;
    }

    /**
     * Paint the blurred backdrop into the panel rectangle (x,y,w,h in the current pose), rounded to
     * the same arc as the tint. READ-only: the draw target is left untouched (still the main target),
     * so this never disturbs the HUD's own rendering.
     */
    public static void blurBehind(GuiGraphics context, int x, int y, int w, int h, int radius) {
        if (!capturedOk || disabled || blurred == null) {
            return;
        }
        try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("hud/blur/blurBehind")) {
            ModProfiler.getInstance().incrementCounter("hud/blur/blurBehind/panels");
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();

            // Panel rect: local pose coords -> gui coords -> framebuffer pixels (y flipped).
            Matrix4f m = context.pose().last().pose();
            Vector3f a = m.transformPosition(new Vector3f(x, y, 0));
            Vector3f b = m.transformPosition(new Vector3f(x + w, y + h, 0));
            double sx = main.width / (double) context.guiWidth();
            double sy = main.height / (double) context.guiHeight();
            int fx0 = clamp((int) Math.round(Math.min(a.x, b.x) * sx), 0, main.width);
            int fx1 = clamp((int) Math.round(Math.max(a.x, b.x) * sx), 0, main.width);
            int fyBottom = clamp(main.height - (int) Math.round(Math.max(a.y, b.y) * sy), 0, main.height);
            int fyTop = clamp(main.height - (int) Math.round(Math.min(a.y, b.y) * sy), 0, main.height);
            if (fx1 - fx0 < 1 || fyTop - fyBottom < 1) {
                return;
            }

            int fbW = fx1 - fx0;
            int fbH = fyTop - fyBottom;
            int fbR = (int) Math.round(radius * (fbW / (double) w));
            fbR = Math.max(0, Math.min(fbR, Math.min(fbW, fbH) / 2));

            // Only redirect the READ framebuffer to the blurred copy; the draw target stays main.
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, blurred.frameBufferId);
            if (fbR <= 0) {
                blitRow(fx0, fyBottom, fx1, fyTop);
            } else {
                blitRow(fx0, fyBottom + fbR, fx1, fyTop - fbR);
                for (int j = 0; j < fbR; j++) {
                    double dy = fbR - (j + 0.5);
                    double half = Math.sqrt(Math.max(0.0, (double) fbR * fbR - dy * dy));
                    int inset = (int) Math.round(fbR - half) + 1;
                    int rx0 = fx0 + inset;
                    int rx1 = fx1 - inset;
                    if (rx1 > rx0) {
                        blitRow(rx0, fyTop - 1 - j, rx1, fyTop - j);       // top corner row
                        blitRow(rx0, fyBottom + j, rx1, fyBottom + j + 1); // bottom corner row
                    }
                }
            }
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId); // restore read
        } catch (Throwable t) {
            fail("compositing blurred panel backdrop", t);
        }
    }

    private static void capture() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        int w = main.width, h = main.height;
        if (w <= 0 || h <= 0) {
            return;
        }

        if (blurred == null) {
            blurred = new TextureTarget(w, h, false);
        } else if (blurred.width != w || blurred.height != h) {
            blurred.resize(w, h);
        }

        // Copy the sharp scene into our private target.
        try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("hud/blur/capture/blitScene")) {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, blurred.frameBufferId);
            GlStateManager._glBlitFrameBuffer(0, 0, w, h, 0, 0, w, h, GL30.GL_COLOR_BUFFER_BIT, GL30.GL_NEAREST);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, main.frameBufferId);
        }

        // Blur the copy with the engine's own post-effect.
        PostChain chain = mc.getShaderManager().getPostChain(BLUR_CHAIN, LevelTargetBundle.MAIN_TARGETS);
        if (chain == null) {
            fail("blur post-chain not loaded", null);
            return;
        }
        chain.setUniform("Radius", BLUR_RADIUS);
        if (pool == null) {
            pool = new CrossFrameResourcePool(3);
        }
        try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("hud/blur/capture/postChain")) {
            chain.process(blurred, pool);
            pool.endFrame(); // recycle the intermediate targets for the next capture (no fresh-black swap)
        }

        main.bindWrite(false); // process() rebinds targets; restore main for the HUD
        capturedOk = true;
        lastCaptureW = w;
        lastCaptureH = h;
    }

    private static void fail(String where, Throwable t) {
        if (!disabled) {
            disabled = true;
            FrogHelper.LOGGER.warn("[FrogHelper] HUD blur disabled ({}) — falling back to flat tint.", where, t);
        }
        capturedOk = false;
    }

    /** Blit one rectangle from the (bound) blurred read target to the (bound) main draw target. */
    private static void blitRow(int x0, int y0, int x1, int y1) {
        if (x1 > x0 && y1 > y0) {
            ModProfiler.getInstance().incrementCounter("hud/blur/blurBehind/blits");
            GlStateManager._glBlitFrameBuffer(x0, y0, x1, y1, x0, y0, x1, y1,
                    GL30.GL_COLOR_BUFFER_BIT, GL30.GL_NEAREST);
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
