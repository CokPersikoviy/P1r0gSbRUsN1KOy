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

/** One batched GUI element for an inherited widget's editor-only dotted outline. */
public final class DottedOutlineRenderState implements GuiElementRenderState {
    private final Matrix3x2fc pose;
    private final ScreenRectangle scissor, bounds;
    private final int x, y, width, height, color;

    private DottedOutlineRenderState(ColoredRectangleRenderState template, int x, int y, int width, int height, int color) {
        pose = template.pose(); scissor = template.scissorArea();
        var transformed = new ScreenRectangle(x, y, width, height).transformMaxBounds(pose);
        bounds = scissor == null ? transformed : transformed.intersection(scissor);
        this.x = x; this.y = y; this.width = width; this.height = height; this.color = color;
    }

    public static void draw(GuiGraphicsExtractor context, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0 || color >>> 24 == 0) return;
        ((GuiElementSubmitter) context).froghelper$submit(x, y, width, height,
                template -> new DottedOutlineRenderState(template, x, y, width, height, color));
    }

    @Override public void buildVertices(VertexConsumer vertices) {
        for (int offset = 0; offset < width; offset += 5) {
            int end = Math.min(offset + 2, width);
            quad(vertices, x + offset, y, x + end, y + 1);
            if (height > 1) quad(vertices, x + offset, y + height - 1, x + end, y + height);
        }
        for (int offset = 5; offset < height - 1; offset += 5) {
            int end = Math.min(offset + 2, height - 1);
            quad(vertices, x, y + offset, x + 1, y + end);
            if (width > 1) quad(vertices, x + width - 1, y + offset, x + width, y + end);
        }
    }

    private void quad(VertexConsumer vertices, int left, int top, int right, int bottom) {
        vertices.addVertexWith2DPose(pose, left, top).setColor(color);
        vertices.addVertexWith2DPose(pose, left, bottom).setColor(color);
        vertices.addVertexWith2DPose(pose, right, bottom).setColor(color);
        vertices.addVertexWith2DPose(pose, right, top).setColor(color);
    }

    @Override public RenderPipeline pipeline() { return RenderPipelines.GUI; }
    @Override public TextureSetup textureSetup() { return TextureSetup.noTexture(); }
    @Override public ScreenRectangle scissorArea() { return scissor; }
    @Override public ScreenRectangle bounds() { return bounds; }
}
