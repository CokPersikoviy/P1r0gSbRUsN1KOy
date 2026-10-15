package ru.wilyfox.client.visuals;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fc;
import ru.wilyfox.client.hud.internal.GuiElementSubmitter;

/** One GUI ordering-tree insertion per disjoint colour region, rather than one per scanline. */
final class CrosshairRenderState implements GuiElementRenderState {
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("froghelper", "textures/gui/visuals_white.png");
    private final Matrix3x2fc pose;
    private final ScreenRectangle scissor, bounds;
    private final List<CrosshairGeometry.Rect> rects;
    private final int x, y, color;
    private final boolean inverted;
    private final TextureSetup texture;

    private CrosshairRenderState(ColoredRectangleRenderState template, List<CrosshairGeometry.Rect> rects,
                                 int x, int y, int color, boolean inverted, TextureSetup texture) {
        pose = template.pose(); scissor = template.scissorArea(); bounds = template.bounds();
        // Shapes are immutable; the indicator's temporary lists are frozen before publishing.
        if (scissor == null) this.rects = List.copyOf(rects);
        else {
            var visible = new java.util.ArrayList<CrosshairGeometry.Rect>();
            for (var r : rects) {
                // Match vanilla's per-rectangle integer bounds at fractional scale/scissor edges.
                var area = new ScreenRectangle(x + r.x(), y + r.y(), r.width(), r.height()).transformMaxBounds(pose);
                if (area.intersection(scissor) != null) visible.add(r);
            }
            this.rects = List.copyOf(visible);
        }
        this.x = x; this.y = y; this.color = inverted ? -1 : color;
        this.inverted = inverted; this.texture = texture;
    }

    static void fill(GuiGraphicsExtractor graphics, int x, int y, List<CrosshairGeometry.Rect> rects,
                     int color, boolean inverted) {
        if (rects.isEmpty()) return;
        int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;
        for (var r : rects) {
            left = Math.min(left, r.x()); top = Math.min(top, r.y());
            right = Math.max(right, r.x() + r.width()); bottom = Math.max(bottom, r.y() + r.height());
        }
        TextureSetup texture = TextureSetup.noTexture();
        if (inverted) {
            var white = Minecraft.getInstance().getTextureManager().getTexture(WHITE);
            texture = TextureSetup.singleTexture(white.getTextureView(), white.getSampler());
        }
        var capturedTexture = texture;
        ((GuiElementSubmitter) graphics).froghelper$submit(x + left, y + top, right - left, bottom - top,
                template -> new CrosshairRenderState(template, rects, x, y, color, inverted, capturedTexture));
    }

    @Override public void buildVertices(VertexConsumer vertices) {
        for (var r : rects) {
            float l = x + r.x(), t = y + r.y(), right = l + r.width(), b = t + r.height();
            if (inverted) {
                vertex(vertices, l, t, 0, 0); vertex(vertices, l, b, 0, 1);
                vertex(vertices, right, b, 1, 1); vertex(vertices, right, t, 1, 0);
            } else {
                // GuiGraphicsExtractor.fill reverses both endpoints before emitting its quad.
                vertex(vertices, right, b, 0, 0); vertex(vertices, right, t, 0, 0);
                vertex(vertices, l, t, 0, 0); vertex(vertices, l, b, 0, 0);
            }
        }
    }
    private void vertex(VertexConsumer vertices, float x, float y, float u, float v) {
        var vertex = vertices.addVertexWith2DPose(pose, x, y);
        if (inverted) vertex.setUv(u, v);
        vertex.setColor(color);
    }
    @Override public RenderPipeline pipeline() { return inverted ? RenderPipelines.CROSSHAIR : RenderPipelines.GUI; }
    @Override public TextureSetup textureSetup() { return texture; }
    @Override public ScreenRectangle scissorArea() { return scissor; }
    @Override public ScreenRectangle bounds() { return bounds; }
}
