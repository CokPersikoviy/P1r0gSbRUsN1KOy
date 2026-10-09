package ru.wilyfox.client.hud.internal;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;

/** Preserve vanilla batching/bounds while multiplying the opacity of every emitted vertex. */
public record FadedGuiElement(GuiElementRenderState original, float alpha) implements GuiElementRenderState {
    public static GuiElementRenderState apply(GuiElementRenderState state, float alpha) {
        return alpha >= 1 ? state : new FadedGuiElement(state, alpha);
    }
    public static BlitRenderState apply(BlitRenderState state, float alpha) {
        if (alpha >= 1) return state;
        return new BlitRenderState(state.pipeline(), state.textureSetup(), state.pose(),
                state.x0(), state.y0(), state.x1(), state.y1(), state.u0(), state.u1(), state.v0(), state.v1(),
                color(state.color(), alpha, state.pipeline() == RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA),
                state.scissorArea(), state.bounds());
    }
    public static int color(int argb, float alpha, boolean premultiplied) {
        int a = Math.round((argb >>> 24) * alpha);
        if (!premultiplied) return (a << 24) | (argb & 0xffffff);
        return (a << 24) | (Math.round(((argb >>> 16) & 255) * alpha) << 16)
                | (Math.round(((argb >>> 8) & 255) * alpha) << 8) | Math.round((argb & 255) * alpha);
    }
    @Override public void buildVertices(VertexConsumer vertices) {
        original.buildVertices(new FadingVertices(vertices, alpha, pipeline() == RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA));
    }
    @Override public RenderPipeline pipeline() { return original.pipeline(); }
    @Override public TextureSetup textureSetup() { return original.textureSetup(); }
    @Override public ScreenRectangle scissorArea() { return original.scissorArea(); }
    @Override public ScreenRectangle bounds() { return original.bounds(); }

    private record FadingVertices(VertexConsumer delegate, float alpha, boolean premultiplied) implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x, float y, float z) { delegate.addVertex(x, y, z); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) {
            if (premultiplied) { r = Math.round(r * alpha); g = Math.round(g * alpha); b = Math.round(b * alpha); }
            delegate.setColor(r, g, b, Math.round(a * alpha)); return this;
        }
        @Override public VertexConsumer setColor(int argb) { delegate.setColor(color(argb, alpha, premultiplied)); return this; }
        @Override public VertexConsumer setUv(float u, float v) { delegate.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { delegate.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { delegate.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { delegate.setNormal(x, y, z); return this; }
        @Override public VertexConsumer setLineWidth(float width) { delegate.setLineWidth(width); return this; }
    }
}
