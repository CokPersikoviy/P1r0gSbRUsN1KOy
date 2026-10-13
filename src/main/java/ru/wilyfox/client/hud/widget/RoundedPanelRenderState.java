package ru.wilyfox.client.hud.widget;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;
import ru.wilyfox.client.hud.internal.GuiElementSubmitter;

/** A complete rounded panel, with one bounds check and one GUI render-state insertion. */
public final class RoundedPanelRenderState implements GuiElementRenderState {
    private static final java.util.concurrent.atomic.AtomicReferenceArray<Arc> ARCS =
            new java.util.concurrent.atomic.AtomicReferenceArray<>(65);
    private record Arc(float[] cos, float[] sin) { }
    private static Arc arc(int segments) {
        var cached = ARCS.get(segments); if (cached != null) return cached;
        float[] cos = new float[segments + 1], sin = new float[segments + 1];
        for (int i = 0; i <= segments; i++) {
            double angle = Math.PI * .5 * i / segments;
            cos[i] = (float) Math.cos(angle); sin[i] = (float) Math.sin(angle);
        }
        // Exact endpoints prevent microscopic gaps where the arc meets a straight edge.
        cos[0] = sin[segments] = 1; sin[0] = cos[segments] = 0;
        var result = new Arc(cos, sin);
        if (!ARCS.compareAndSet(segments, null, result)) result = ARCS.get(segments);
        return result;
    }

    private final Matrix3x2fc pose;
    private final ScreenRectangle scissor;
    private final ScreenRectangle bounds;
    private final TextureSetup textureSetup;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int radius;
    private final int color;
    private final Arc arc;
    private final float edgeWidth;
    private final boolean textured;
    private final float guiWidth;
    private final float guiHeight;
    private final boolean menuGradient;

    private RoundedPanelRenderState(ColoredRectangleRenderState template, TextureSetup textureSetup,
                                    int x, int y, int width, int height, int radius, int color,
                                    boolean textured, float guiWidth, float guiHeight, boolean menuGradient) {
        // The sentinel rectangle already owns a snapshot, rather than the mutable pose stack.
        this.pose = template.pose();
        this.scissor = template.scissorArea();
        ScreenRectangle transformed = new ScreenRectangle(x, y, width, height).transformMaxBounds(pose);
        this.bounds = scissor == null ? transformed : transformed.intersection(scissor);
        this.textureSetup = textureSetup;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.radius = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        this.color = color;
        float sx = (float) Math.hypot(pose.m00(), pose.m01());
        float sy = (float) Math.hypot(pose.m10(), pose.m11());
        float guiScale = (float) net.minecraft.client.Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState.guiScale;
        float pixelsPerUnit = Math.max(.01f, Math.min(sx, sy) * guiScale);
        edgeWidth = Math.min(this.radius, 1f / pixelsPerUnit);
        // Chord error stays subpixel; density follows the largest axis for stretched panels.
        int segments = Math.clamp(((int) Math.ceil(2 * Math.sqrt(this.radius * Math.max(sx, sy) * guiScale)) + 1) & ~1, 4, 64);
        arc = this.radius == 0 ? null : arc(segments);
        this.textured = textured;
        this.guiWidth = guiWidth;
        this.guiHeight = guiHeight;
        this.menuGradient = menuGradient;
    }

    public static void fill(GuiGraphicsExtractor context, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0 || color >>> 24 == 0) {
            return;
        }
        ((GuiElementSubmitter) context).froghelper$submit(x, y, width, height,
                template -> new RoundedPanelRenderState(template, TextureSetup.noTexture(),
                        x, y, width, height, radius, color, false, 1, 1, false));
    }

    public static void blit(GuiGraphicsExtractor context, GpuTextureView texture, GpuSampler sampler,
                            int x, int y, int width, int height, int radius, float guiWidth, float guiHeight) {
        if (width <= 0 || height <= 0 || guiWidth <= 0 || guiHeight <= 0) {
            return;
        }
        ((GuiElementSubmitter) context).froghelper$submit(x, y, width, height,
                template -> new RoundedPanelRenderState(template, TextureSetup.singleTexture(texture, sampler),
                        x, y, width, height, radius, -1, true, guiWidth, guiHeight, false));
    }

    /** Same C0101010→D0101010 gradient as Screen.extractTransparentBackground, clipped to the plate. */
    public static void menuShade(GuiGraphicsExtractor context, int x, int y, int width, int height, int radius, float guiHeight) {
        if (width <= 0 || height <= 0 || guiHeight <= 0) return;
        ((GuiElementSubmitter) context).froghelper$submit(x, y, width, height,
                template -> new RoundedPanelRenderState(template, TextureSetup.noTexture(),
                        x, y, width, height, radius, -1, false, 1, guiHeight, true));
    }

    @Override
    public void buildVertices(VertexConsumer vertices) { buildVertices(vertices, 1); }

    /** Applies opacity after edge/gradient rounding, exactly as the original vertex wrapper did. */
    public void buildVertices(VertexConsumer vertices, float opacity) {
        try (var ignored = ru.wilyfox.client.profiler.ModProfiler.getInstance().scope("hud/panel/geometry")) {
            buildPanelVertices(vertices, opacity);
        }
    }
    private void buildPanelVertices(VertexConsumer vertices, float opacity) {
        if (radius == 0) {
            quad(vertices, x, y, x + width, y + height, color, opacity);
            return;
        }
        float a = edgeWidth;
        quad(vertices, x + radius, y + a, x + width - radius, y + radius, color, opacity);
        quad(vertices, x + a, y + radius, x + width - a, y + height - radius, color, opacity);
        quad(vertices, x + radius, y + height - radius, x + width - radius, y + height - a, color, opacity);
        int clear = color & 0xffffff;
        edge(vertices, x + radius, y, x + width - radius, y + a, clear, color, color, clear, opacity);
        edge(vertices, x + radius, y + height - a, x + width - radius, y + height, color, clear, clear, color, opacity);
        edge(vertices, x, y + radius, x + a, y + height - radius, clear, clear, color, color, opacity);
        edge(vertices, x + width - a, y + radius, x + width, y + height - radius, color, color, clear, clear, opacity);
        corner(vertices, x + radius, y + radius, -1, -1, opacity);
        corner(vertices, x + width - radius, y + radius, 1, -1, opacity);
        corner(vertices, x + radius, y + height - radius, -1, 1, opacity);
        corner(vertices, x + width - radius, y + height - radius, 1, 1, opacity);
    }

    private void corner(VertexConsumer vertices, float cx, float cy, int dx, int dy, float opacity) {
        float inner = radius - edgeWidth;
        int n = arc.cos.length - 1;
        // Both triangles of each quad form a fan; the outer ring interpolates coverage continuously.
        for (int i = 0; i < n; i += 2) {
            vertex(vertices, cx, cy, color, opacity);
            for (int j = 0; j < 3; j++) {
                int k = dx * dy > 0 ? i + 2 - j : i + j;
                vertex(vertices, cx + dx * inner * arc.cos[k], cy + dy * inner * arc.sin[k], color, opacity);
            }
        }
        int clear = color & 0xffffff;
        for (int i = 0; i < n; i++) {
            int first = dx * dy > 0 ? i : i + 1, next = dx * dy > 0 ? i + 1 : i;
            vertex(vertices, cx + dx * inner * arc.cos[first], cy + dy * inner * arc.sin[first], color, opacity);
            vertex(vertices, cx + dx * inner * arc.cos[next], cy + dy * inner * arc.sin[next], color, opacity);
            vertex(vertices, cx + dx * radius * arc.cos[next], cy + dy * radius * arc.sin[next], clear, opacity);
            vertex(vertices, cx + dx * radius * arc.cos[first], cy + dy * radius * arc.sin[first], clear, opacity);
        }
    }

    private void edge(VertexConsumer vertices, float l, float t, float r, float b, int tl, int bl, int br, int tr, float opacity) {
        if (l >= r || t >= b) return;
        vertex(vertices, l, t, tl, opacity); vertex(vertices, l, b, bl, opacity);
        vertex(vertices, r, b, br, opacity); vertex(vertices, r, t, tr, opacity);
    }

    private void quad(VertexConsumer vertices, float left, float top, float right, float bottom, int color, float opacity) {
        if (left >= right || top >= bottom) {
            return;
        }
        vertex(vertices, left, top, color, opacity);
        vertex(vertices, left, bottom, color, opacity);
        vertex(vertices, right, bottom, color, opacity);
        vertex(vertices, right, top, color, opacity);
    }

    private void vertex(VertexConsumer vertices, float x, float y, int color, float opacity) {
        if (menuGradient) {
            float screenY = pose.m01() * x + pose.m11() * y + pose.m21();
            float gradientAlpha = 192 + 16 * Math.clamp(screenY / guiHeight, 0f, 1f);
            // Preserve antialias coverage at rounded edges while sampling in screen coordinates.
            int alpha = Math.round(gradientAlpha * (color >>> 24) / 255f);
            color = (alpha << 24) | 0x101010;
        }
        if (!(opacity >= 1)) color = ru.wilyfox.client.hud.internal.FadedGuiElement.color(color, opacity, false);
        vertices.addVertexWith2DPose(pose, x, y).setColor(color);
        if (textured) {
            float screenX = pose.m00() * x + pose.m10() * y + pose.m20();
            float screenY = pose.m01() * x + pose.m11() * y + pose.m21();
            vertices.setUv(screenX / guiWidth, 1.0f - screenY / guiHeight);
        }
    }

    @Override
    public RenderPipeline pipeline() {
        return textured ? RenderPipelines.GUI_TEXTURED : RenderPipelines.GUI;
    }

    @Override
    public TextureSetup textureSetup() {
        return textureSetup;
    }

    @Override
    public ScreenRectangle scissorArea() {
        return scissor;
    }

    @Override
    public ScreenRectangle bounds() {
        return bounds;
    }
}
