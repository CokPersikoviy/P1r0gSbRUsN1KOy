package ru.wilyfox.client.hud.internal;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

/** Transform the frozen frame's screen-space geometry without copying its textures. */
public record TransformedGuiElement(GuiElementRenderState original, Matrix3x2fc transform) implements GuiElementRenderState {
    public static GuiElementRenderState apply(GuiElementRenderState state, Matrix3x2fc transform) {
        return transform == null ? state : new TransformedGuiElement(state, transform);
    }
    public static BlitRenderState apply(BlitRenderState state, Matrix3x2fc transform) {
        if (transform == null) return state;
        return new BlitRenderState(state.pipeline(), state.textureSetup(), new Matrix3x2f(transform).mul(state.pose()),
                state.x0(), state.y0(), state.x1(), state.y1(), state.u0(), state.u1(), state.v0(), state.v1(),
                state.color(), state.scissorArea());
    }
    @Override public void buildVertices(VertexConsumer vertices) { original.buildVertices(new TransformedVertices(vertices, transform)); }
    @Override public RenderPipeline pipeline() { return original.pipeline(); }
    @Override public TextureSetup textureSetup() { return original.textureSetup(); }
    @Override public ScreenRectangle scissorArea() { return original.scissorArea(); }
    @Override public ScreenRectangle bounds() { return original.bounds() == null ? null : original.bounds().transformMaxBounds(transform); }
    private record TransformedVertices(VertexConsumer delegate, Matrix3x2fc transform) implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(transform.m00() * x + transform.m10() * y + transform.m20(),
                    transform.m01() * x + transform.m11() * y + transform.m21(), z); return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { delegate.setColor(r, g, b, a); return this; }
        @Override public VertexConsumer setColor(int color) { delegate.setColor(color); return this; }
        @Override public VertexConsumer setUv(float u, float v) { delegate.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { delegate.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { delegate.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { delegate.setNormal(x, y, z); return this; }
        @Override public VertexConsumer setLineWidth(float width) { delegate.setLineWidth(width); return this; }
    }
}
