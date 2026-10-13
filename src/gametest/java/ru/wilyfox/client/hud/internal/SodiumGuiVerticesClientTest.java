package ru.wilyfox.client.hud.internal;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Optional API is referenced only by the nested test loaded with -PwithSodium. */
public final class SodiumGuiVerticesClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")) context.runOnClient(client -> Checks.run());
        else System.out.println("Sodium GUI bulk writing: optional compatibility class remains unloaded without Sodium");
    }

    private static final class Checks {
        private static void run() {
            for (boolean premultiplied : new boolean[]{false, true}) for (float alpha : new float[]{.1f, .333f, .65f})
                for (boolean transform : new boolean[]{false, true}) for (var format : new com.mojang.blaze3d.vertex.VertexFormat[]{
                        com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR,
                        com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION}) {
                    test(format, alpha, premultiplied, transform);
                }
            System.out.println("Sodium GUI bulk writing: 24 cases match opacity/transform, preserve source bytes, positions and UVs");
        }
        private static void test(com.mojang.blaze3d.vertex.VertexFormat format, float alpha, boolean premultiplied, boolean transform) {
            var destination = new Sink();
            var matrix = new org.joml.Matrix3x2f().translate(3.25f, -8.5f).rotate(.13f).scale(.8f, 1.2f);
            byte[][] original = {null};
            var element = new net.minecraft.client.renderer.state.gui.GuiElementRenderState() {
                @Override public com.mojang.blaze3d.pipeline.RenderPipeline pipeline() { return premultiplied
                        ? net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA : net.minecraft.client.renderer.RenderPipelines.GUI; }
                @Override public net.minecraft.client.gui.render.TextureSetup textureSetup() { return net.minecraft.client.gui.render.TextureSetup.noTexture(); }
                @Override public net.minecraft.client.gui.navigation.ScreenRectangle scissorArea() { return null; }
                @Override public net.minecraft.client.gui.navigation.ScreenRectangle bounds() { return null; }
                @Override public void buildVertices(com.mojang.blaze3d.vertex.VertexConsumer consumer) {
                    var writer = net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter.tryOf(consumer);
                    if (writer == null) throw new AssertionError("GUI wrapper disabled Sodium bulk writing");
                    try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
                        int stride = format.getVertexSize(); long source = stack.nmalloc(4, stride * 4);
                        org.lwjgl.system.MemoryUtil.memSet(source, 0x5A, stride * 4L);
                        for (int i = 0; i < 4; i++) {
                            long vertex = source + (long) stride * i;
                            int position = format.getElement("Position").offset();
                            org.lwjgl.system.MemoryUtil.memPutFloat(vertex + position, 1.5f + i);
                            org.lwjgl.system.MemoryUtil.memPutFloat(vertex + position + 4, 6.75f - i);
                            org.lwjgl.system.MemoryUtil.memPutFloat(vertex + position + 8, .25f);
                            var color = format.getElement("Color");
                            if (color != null) org.lwjgl.system.MemoryUtil.memPutInt(vertex + color.offset(), 0xA17A4532);
                        }
                        original[0] = bytes(source, stride * 4);
                        writer.push(stack, source, 4, format);
                        if (!java.util.Arrays.equals(original[0], bytes(source, stride * 4))) throw new AssertionError("GUI wrapper modified Sodium's reusable source buffer");
                    }
                }
            };
            var state = FadedGuiElement.apply(element, alpha);
            if (transform) state = TransformedGuiElement.apply(state, matrix);
            state.buildVertices(destination);
            var expected = java.nio.ByteBuffer.wrap(original[0]).order(java.nio.ByteOrder.nativeOrder());
            for (int i = 0; i < 4; i++) {
                int position = i * format.getVertexSize() + format.getElement("Position").offset();
                if (transform) {
                    float x = expected.getFloat(position), y = expected.getFloat(position + 4);
                    expected.putFloat(position, matrix.m00() * x + matrix.m10() * y + matrix.m20());
                    expected.putFloat(position + 4, matrix.m01() * x + matrix.m11() * y + matrix.m21());
                }
                var color = format.getElement("Color");
                if (color != null) {
                    int offset = i * format.getVertexSize() + color.offset();
                    expected.putInt(offset, FadedGuiElement.color(expected.getInt(offset), alpha, premultiplied));
                }
            }
            if (!java.util.Arrays.equals(expected.array(), destination.received)) throw new AssertionError("Sodium bulk opacity/transform changed other vertex attributes");
        }
        private static byte[] bytes(long source, int count) {
            byte[] bytes = new byte[count]; org.lwjgl.system.MemoryUtil.memByteBuffer(source, count).get(bytes); return bytes;
        }
        private static final class Sink implements com.mojang.blaze3d.vertex.VertexConsumer,
                net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter {
            private byte[] received;
            @Override public void push(org.lwjgl.system.MemoryStack stack, long source, int count, com.mojang.blaze3d.vertex.VertexFormat format) { received = bytes(source, count * format.getVertexSize()); }
            @Override public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x, float y, float z) { throw new AssertionError("Slow vertex path used"); }
            @Override public com.mojang.blaze3d.vertex.VertexConsumer setColor(int r, int g, int b, int a) { return this; }
            @Override public com.mojang.blaze3d.vertex.VertexConsumer setColor(int color) { return this; }
            @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv(float u, float v) { return this; }
            @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv1(int u, int v) { return this; }
            @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv2(int u, int v) { return this; }
            @Override public com.mojang.blaze3d.vertex.VertexConsumer setNormal(float x, float y, float z) { return this; }
            @Override public com.mojang.blaze3d.vertex.VertexConsumer setLineWidth(float width) { return this; }
        }
    }
}
