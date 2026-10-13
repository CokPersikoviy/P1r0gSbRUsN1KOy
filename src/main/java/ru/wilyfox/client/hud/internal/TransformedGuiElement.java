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
    private static final boolean SODIUM = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium");
    @Override public void buildVertices(VertexConsumer vertices) {
        original.buildVertices(SODIUM ? SodiumCompatibility.create(vertices, transform) : new TransformedVertices(vertices, transform));
    }
    @Override public RenderPipeline pipeline() { return original.pipeline(); }
    @Override public TextureSetup textureSetup() { return original.textureSetup(); }
    @Override public ScreenRectangle scissorArea() { return original.scissorArea(); }
    @Override public ScreenRectangle bounds() { return original.bounds() == null ? null : original.bounds().transformMaxBounds(transform); }
    private static class TransformedVertices implements VertexConsumer {
        protected final VertexConsumer delegate;
        protected final Matrix3x2fc transform;
        private TransformedVertices(VertexConsumer delegate, Matrix3x2fc transform) {
            this.delegate = delegate; this.transform = transform;
        }
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

    private static final class SodiumCompatibility {
        private static VertexConsumer create(VertexConsumer delegate, Matrix3x2fc transform) {
            return new SodiumTransformedVertices(delegate, transform);
        }
    }

    private static final class SodiumTransformedVertices extends TransformedVertices
            implements net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter {
        private SodiumTransformedVertices(VertexConsumer delegate, Matrix3x2fc transform) { super(delegate, transform); }
        @Override public boolean canUseIntrinsics() {
            return delegate instanceof net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter writer
                    && writer.canUseIntrinsics();
        }
        @Override public void push(org.lwjgl.system.MemoryStack stack, long source, int count,
                                   com.mojang.blaze3d.vertex.VertexFormat format) {
            var position = format.getElement(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_SEMANTIC_NAME);
            var writer = (net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter) delegate;
            if (position == null) { writer.push(stack, source, count, format); return; }
            int stride = format.getVertexSize();
            try (var frame = stack.push()) {
                long copy = frame.nmalloc(4, Math.multiplyExact(stride, count));
                org.lwjgl.system.MemoryUtil.memCopy(source, copy, (long) stride * count);
                for (int i = 0; i < count; i++) {
                    long address = copy + (long) i * stride + position.offset();
                    float x = org.lwjgl.system.MemoryUtil.memGetFloat(address), y = org.lwjgl.system.MemoryUtil.memGetFloat(address + 4);
                    org.lwjgl.system.MemoryUtil.memPutFloat(address, transform.m00() * x + transform.m10() * y + transform.m20());
                    org.lwjgl.system.MemoryUtil.memPutFloat(address + 4, transform.m01() * x + transform.m11() * y + transform.m21());
                }
                writer.push(frame, copy, count, format);
            }
        }
    }
}
