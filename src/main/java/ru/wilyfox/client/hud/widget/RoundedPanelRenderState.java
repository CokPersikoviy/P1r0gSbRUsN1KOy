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
    private static final int CORNER_SS = 4;

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
    private final boolean textured;
    private final float guiWidth;
    private final float guiHeight;

    private RoundedPanelRenderState(ColoredRectangleRenderState template, TextureSetup textureSetup,
                                    int x, int y, int width, int height, int radius, int color,
                                    boolean textured, float guiWidth, float guiHeight) {
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
        this.textured = textured;
        this.guiWidth = guiWidth;
        this.guiHeight = guiHeight;
    }

    public static void fill(GuiGraphicsExtractor context, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0 || color >>> 24 == 0) {
            return;
        }
        ((GuiElementSubmitter) context).froghelper$submit(x, y, width, height,
                template -> new RoundedPanelRenderState(template, TextureSetup.noTexture(),
                        x, y, width, height, radius, color, false, 1, 1));
    }

    public static void blit(GuiGraphicsExtractor context, GpuTextureView texture, GpuSampler sampler,
                            int x, int y, int width, int height, int radius, float guiWidth, float guiHeight) {
        if (width <= 0 || height <= 0 || guiWidth <= 0 || guiHeight <= 0) {
            return;
        }
        ((GuiElementSubmitter) context).froghelper$submit(x, y, width, height,
                template -> new RoundedPanelRenderState(template, TextureSetup.singleTexture(texture, sampler),
                        x, y, width, height, radius, -1, true, guiWidth, guiHeight));
    }

    @Override
    public void buildVertices(VertexConsumer vertices) {
        if (radius == 0) {
            quad(vertices, x, y, x + width, y + height, color);
            return;
        }
        quad(vertices, x + radius, y, x + width - radius, y + radius, color);
        quad(vertices, x, y + radius, x + width, y + height - radius, color);
        quad(vertices, x + radius, y + height - radius, x + width - radius, y + height, color);
        corner(vertices, x, y, true, true);
        corner(vertices, x + width - radius, y, false, true);
        corner(vertices, x, y + height - radius, true, false);
        corner(vertices, x + width - radius, y + height - radius, false, false);
    }

    private void corner(VertexConsumer vertices, int boxX, int boxY, boolean centerRight, boolean centerBottom) {
        int baseAlpha = color >>> 24;
        int rgb = color & 0x00FFFFFF;
        int subRadius = radius * CORNER_SS;
        int cy = centerBottom ? subRadius : 0;
        float scale = 1.0f / CORNER_SS;
        for (int py = 0; py < subRadius; py++) {
            double dy = py + 0.5 - cy;
            double half = Math.sqrt(Math.max(0.0, (double) subRadius * subRadius - dy * dy));
            float top = boxY + py * scale;
            float bottom = top + scale;
            if (centerRight) {
                double edge = subRadius - half;
                int solid = (int) Math.ceil(edge);
                quad(vertices, boxX + solid * scale, top, boxX + radius, bottom, color);
                int alpha = (int) Math.round(baseAlpha * (solid - edge));
                if (solid - 1 >= 0 && alpha > 0) {
                    quad(vertices, boxX + (solid - 1) * scale, top, boxX + solid * scale, bottom, (alpha << 24) | rgb);
                }
            } else {
                int solid = (int) Math.floor(half);
                quad(vertices, boxX, top, boxX + solid * scale, bottom, color);
                int alpha = (int) Math.round(baseAlpha * (half - solid));
                if (solid < subRadius && alpha > 0) {
                    quad(vertices, boxX + solid * scale, top, boxX + (solid + 1) * scale, bottom, (alpha << 24) | rgb);
                }
            }
        }
    }

    private void quad(VertexConsumer vertices, float left, float top, float right, float bottom, int color) {
        if (left >= right || top >= bottom) {
            return;
        }
        vertex(vertices, left, top, color);
        vertex(vertices, left, bottom, color);
        vertex(vertices, right, bottom, color);
        vertex(vertices, right, top, color);
    }

    private void vertex(VertexConsumer vertices, float x, float y, int color) {
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
