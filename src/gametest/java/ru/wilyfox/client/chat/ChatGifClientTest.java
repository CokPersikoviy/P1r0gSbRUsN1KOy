package ru.wilyfox.client.chat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import javax.imageio.IIOImage;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class ChatGifClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> { verifyPixelConversion(); benchmarkConversion(); });
            byte[] bytes = gif();
            var parent = context.computeOnClient(client -> new ChatScreen("caption", false));
            var viewer = context.computeOnClient(client -> new FhImageScreen(parent, () -> CompletableFuture.completedFuture(bytes)));
            context.runOnClient(client -> client.gui.setScreen(viewer));
            var id = Identifier.fromNamespaceAndPath("froghelper", "fh_chat_image");
            boolean sawRed = false, sawBlue = false;
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (System.nanoTime() < deadline && !(sawRed && sawBlue)) {
                int color = context.computeOnClient(client -> {
                    var texture = client.getTextureManager().getTexture(id);
                    if (!(texture instanceof DynamicTexture dynamic) || dynamic.getPixels().isClosed()) return 0;
                    return dynamic.getPixels().getPixel(2, 2);
                });
                sawRed |= (color & 0x00FFFFFF) == 0xFF0000;
                sawBlue |= (color & 0x00FFFFFF) == 0x0000FF;
                context.waitTicks(1);
            }
            check(sawRed && sawBlue, "GIF did not animate through both actual texture frames");
            var pixels = context.computeOnClient(client -> ((DynamicTexture) client.getTextureManager().getTexture(id)).getPixels());
            context.runOnClient(client -> viewer.onClose());
            check(pixels.isClosed(), "Closing GIF retained native texture pixels");
            check(context.computeOnClient(client -> client.gui.screen() == parent), "Closing GIF lost chat screen");
            // Also close while its payload is still downloading; late completion must not reopen or allocate a texture.
            var pending = new CompletableFuture<byte[]>();
            var late = context.computeOnClient(client -> new FhImageScreen(parent, () -> pending));
            context.runOnClient(client -> { client.gui.setScreen(late); late.onClose(); });
            pending.complete(bytes); context.waitTicks(3);
            check(context.computeOnClient(client -> client.gui.screen() == parent), "Late GIF download reopened viewer");
            System.out.println("FH GIF: actual animated texture, close cleanup and late completion passed");
        } finally { context.runOnClient(client -> client.gui.setScreen(null)); }
    }
    private static void verifyPixelConversion() {
        int tested = 0;
        for (int type : new int[]{BufferedImage.TYPE_INT_ARGB, BufferedImage.TYPE_INT_ARGB_PRE,
                BufferedImage.TYPE_INT_RGB, BufferedImage.TYPE_4BYTE_ABGR, BufferedImage.TYPE_BYTE_INDEXED}) {
            var source = new BufferedImage(17, 11, type);
            for (int y = 0; y < 11; y++) for (int x = 0; x < 17; x++) source.setRGB(x, y, (x * 15) << 24 | (y * 20) << 16 | x * 10 << 8 | y * 12);
            for (var image : new BufferedImage[]{source, source.getSubimage(3, 2, 9, 6)}) {
                try (var converted = FhImages.gifPixels(new GifAnimation.Frame(image, 100))) {
                    for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
                        check(converted.getPixel(x, y) == image.getRGB(x, y), "Optimized GIF conversion changed a pixel/alpha");
                }
                tested++;
            }
        }
        System.out.println("GIF conversion: " + tested + " image/subimage cases preserve every original ARGB pixel");
    }
    private static void benchmarkConversion() {
        var image = new BufferedImage(512, 256, BufferedImage.TYPE_INT_ARGB);
        var bean = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported()) return;
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        for (int i = 0; i < 4; i++) { try (var old = legacyPixels(image); var fresh = FhImages.gifPixels(new GifAnimation.Frame(image, 100))) { } }
        long thread = Thread.currentThread().threadId(), before = bean.getThreadAllocatedBytes(thread), started = System.nanoTime();
        for (int i = 0; i < 20; i++) try (var converted = legacyPixels(image)) { converted.getPixel(0, 0); }
        long oldTime = System.nanoTime() - started, oldBytes = bean.getThreadAllocatedBytes(thread) - before;
        before = bean.getThreadAllocatedBytes(thread); started = System.nanoTime();
        for (int i = 0; i < 20; i++) try (var converted = FhImages.gifPixels(new GifAnimation.Frame(image, 100))) { converted.getPixel(0, 0); }
        long newTime = System.nanoTime() - started, newBytes = bean.getThreadAllocatedBytes(thread) - before;
        System.out.println("GIF benchmark 20 x 512x256: previous=" + oldTime / 1e6 + "ms, " + oldBytes
                + " allocated bytes; optimized=" + newTime / 1e6 + "ms, " + newBytes + " allocated bytes");
    }
    private static com.mojang.blaze3d.platform.NativeImage legacyPixels(BufferedImage image) {
        var result = new com.mojang.blaze3d.platform.NativeImage(image.getWidth(), image.getHeight(), false);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) result.setPixel(x, y, image.getRGB(x, y));
        image.flush(); return result;
    }
    static byte[] gif() {
        var bytes = new ByteArrayOutputStream();
        var writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (var output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output); writer.prepareWriteSequence(null);
            for (Color color : new Color[]{Color.RED, Color.BLUE}) {
                var image = new BufferedImage(32, 16, BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics(); graphics.setColor(color); graphics.fillRect(0, 0, 32, 16); graphics.dispose();
                var metadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(image), null);
                var tree = (IIOMetadataNode) metadata.getAsTree("javax_imageio_gif_image_1.0");
                var control = (IIOMetadataNode) tree.getElementsByTagName("GraphicControlExtension").item(0);
                control.setAttribute("delayTime", "10"); metadata.setFromTree("javax_imageio_gif_image_1.0", tree);
                writer.writeToSequence(new IIOImage(image, null, metadata), null);
            }
            writer.endWriteSequence(); output.flush(); return bytes.toByteArray();
        } catch (java.io.IOException error) { throw new AssertionError(error); }
        finally { writer.dispose(); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
