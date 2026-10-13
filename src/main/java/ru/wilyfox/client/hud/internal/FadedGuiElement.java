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
        if (original instanceof ru.wilyfox.client.hud.widget.RoundedPanelRenderState panel) {
            panel.buildVertices(vertices, alpha);
        } else {
            original.buildVertices(fadingVertices(vertices, alpha, pipeline() == RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA));
        }
    }
    @Override public RenderPipeline pipeline() { return original.pipeline(); }
    @Override public TextureSetup textureSetup() { return original.textureSetup(); }
    @Override public ScreenRectangle scissorArea() { return original.scissorArea(); }
    @Override public ScreenRectangle bounds() { return original.bounds(); }

    private static final boolean SODIUM = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium");
    static VertexConsumer fadingVertices(VertexConsumer delegate, float alpha, boolean premultiplied) {
        // The nested compatibility class is loaded only when Sodium supplies its optional API.
        return SODIUM ? SodiumCompatibility.create(delegate, alpha, premultiplied)
                : new FadingVertices(delegate, alpha, premultiplied);
    }

    private static class FadingVertices implements VertexConsumer {
        protected final VertexConsumer delegate;
        protected final float alpha;
        protected final boolean premultiplied;
        private FadingVertices(VertexConsumer delegate, float alpha, boolean premultiplied) {
            this.delegate = delegate; this.alpha = alpha; this.premultiplied = premultiplied;
        }
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

    private static final class SodiumCompatibility {
        private static VertexConsumer create(VertexConsumer delegate, float alpha, boolean premultiplied) {
            return new SodiumFadingVertices(delegate, alpha, premultiplied);
        }
    }

    private static final class SodiumFadingVertices extends FadingVertices
            implements net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter {
        private SodiumFadingVertices(VertexConsumer delegate, float alpha, boolean premultiplied) {
            super(delegate, alpha, premultiplied);
        }
        @Override public boolean canUseIntrinsics() {
            return delegate instanceof net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter writer
                    && writer.canUseIntrinsics();
        }
        @Override public void push(org.lwjgl.system.MemoryStack stack, long source, int count,
                                   com.mojang.blaze3d.vertex.VertexFormat format) {
            int stride = format.getVertexSize();
            var colorElement = format.getElement("Color");
            if (colorElement == null) {
                ((net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter) delegate).push(stack, source, count, format);
                return;
            }
            int offset = colorElement.offset();
            // Sodium may reuse its source vertices. Fade a temporary copy, never that source.
            try (var frame = stack.push()) {
                long copy = frame.nmalloc(4, Math.multiplyExact(stride, count));
                org.lwjgl.system.MemoryUtil.memCopy(source, copy, (long) stride * count);
                for (int i = 0; i < count; i++) {
                    long address = copy + (long) i * stride + offset;
                    int packed = org.lwjgl.system.MemoryUtil.memGetInt(address);
                    org.lwjgl.system.MemoryUtil.memPutInt(address, color(packed, alpha, premultiplied));
                }
                ((net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter) delegate).push(frame, copy, count, format);
            }
        }
    }
}
