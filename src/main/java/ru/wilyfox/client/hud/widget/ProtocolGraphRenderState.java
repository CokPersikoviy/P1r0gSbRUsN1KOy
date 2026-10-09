package ru.wilyfox.client.hud.widget;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;
import ru.wilyfox.client.hud.internal.GuiElementSubmitter;

import java.util.ArrayList;
import java.util.List;

/** Batches the graph into one GUI element instead of inserting an element for every pixel. */
final class ProtocolGraphRenderState implements GuiElementRenderState {
    private final Matrix3x2fc pose;
    private final ScreenRectangle scissor;
    private final ScreenRectangle bounds;
    private final List<Primitive> primitives;

    private ProtocolGraphRenderState(ColoredRectangleRenderState template, List<Primitive> primitives) {
        pose = template.pose();
        scissor = template.scissorArea();
        bounds = template.bounds();
        this.primitives = primitives;
    }

    @Override public void buildVertices(VertexConsumer vertices) {
        for (Primitive primitive : primitives) primitive.emit(vertices, pose);
    }
    @Override public RenderPipeline pipeline() { return RenderPipelines.GUI; }
    @Override public TextureSetup textureSetup() { return TextureSetup.noTexture(); }
    @Override public ScreenRectangle scissorArea() { return scissor; }
    @Override public ScreenRectangle bounds() { return bounds; }

    static final class Builder {
        private final List<Primitive> primitives = new ArrayList<>();
        private int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE;
        private int right = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;

        void line(int x1, int y1, int x2, int y2, int color) {
            if (color >>> 24 == 0) return;
            if (x1 == x2 && y1 == y2) {
                circle(x1, y1, 1, color);
                return;
            }
            primitives.add(new Line(x1, y1, x2, y2, color));
            include(Math.min(x1, x2) - 2, Math.min(y1, y2) - 2,
                    Math.max(x1, x2) + 3, Math.max(y1, y2) + 3);
        }

        void circle(int x, int y, int radius, int color) {
            if (radius < 0 || color >>> 24 == 0) return;
            primitives.add(new Circle(x, y, radius, color));
            include(x - radius, y - radius, x + radius + 1, y + radius + 1);
        }

        private void include(int x1, int y1, int x2, int y2) {
            left = Math.min(left, x1); top = Math.min(top, y1);
            right = Math.max(right, x2); bottom = Math.max(bottom, y2);
        }

        void submit(GuiGraphicsExtractor context) {
            if (primitives.isEmpty()) return;
            List<Primitive> snapshot = List.copyOf(primitives);
            ((GuiElementSubmitter) context).froghelper$submit(left, top, right - left, bottom - top,
                    template -> new ProtocolGraphRenderState(template, snapshot));
        }
    }

    private interface Primitive {
        void emit(VertexConsumer vertices, Matrix3x2fc pose);
    }

    private record Line(int x1, int y1, int x2, int y2, int color) implements Primitive {
        @Override public void emit(VertexConsumer vertices, Matrix3x2fc pose) {
            double dx = (double) x2 - x1, dy = (double) y2 - y1;
            double length = Math.hypot(dx, dy);
            float nx = (float) (-dy / length * 1.5), ny = (float) (dx / length * 1.5);
            vertex(vertices, pose, x1 + .5f + nx, y1 + .5f + ny, color);
            vertex(vertices, pose, x2 + .5f + nx, y2 + .5f + ny, color);
            vertex(vertices, pose, x2 + .5f - nx, y2 + .5f - ny, color);
            vertex(vertices, pose, x1 + .5f - nx, y1 + .5f - ny, color);
        }
    }

    private record Circle(int x, int y, int radius, int color) implements Primitive {
        @Override public void emit(VertexConsumer vertices, Matrix3x2fc pose) {
            // Same pixel coverage as the old circle, with one quad per row rather than per pixel.
            for (int dy = -radius; dy <= radius; dy++) {
                int half = (int) Math.sqrt(radius * radius - dy * dy);
                vertex(vertices, pose, x - half, y + dy, color);
                vertex(vertices, pose, x - half, y + dy + 1, color);
                vertex(vertices, pose, x + half + 1, y + dy + 1, color);
                vertex(vertices, pose, x + half + 1, y + dy, color);
            }
        }
    }

    private static void vertex(VertexConsumer vertices, Matrix3x2fc pose, float x, float y, int color) {
        vertices.addVertexWith2DPose(pose, x, y).setColor(color);
    }
}
