package ru.wilyfox.client.chat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.IIOImage;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GifAnimationTest {
    @Test void transparentPixelsPreservePreviousCanvasAndBackgroundDisposalClearsItsRectangle() throws Exception {
        var palette = new java.awt.image.IndexColorModel(8, 4, new byte[]{0, (byte)255, 0, 0},
                new byte[]{0, 0, 0, (byte)255}, new byte[]{0, 0, (byte)255, 0}, 0);
        var output = new ByteArrayOutputStream(); var writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (var stream = new MemoryCacheImageOutputStream(output)) {
            writer.setOutput(stream); writer.prepareWriteSequence(null);
            for (int i = 0; i < 3; i++) {
                var image = new BufferedImage(i == 0 ? 6 : 2, i == 0 ? 6 : 2, BufferedImage.TYPE_BYTE_INDEXED, palette);
                if (i == 0) for (int y=0;y<6;y++) for(int x=0;x<6;x++) image.getRaster().setSample(x,y,0,1);
                else image.getRaster().setSample(0,0,0,i==1?2:3);
                var metadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(image), null);
                var tree = (IIOMetadataNode) metadata.getAsTree("javax_imageio_gif_image_1.0");
                var descriptor = (IIOMetadataNode) tree.getElementsByTagName("ImageDescriptor").item(0);
                descriptor.setAttribute("imageLeftPosition",i==1?"2":"0"); descriptor.setAttribute("imageTopPosition",i==1?"2":"0");
                var control = (IIOMetadataNode) tree.getElementsByTagName("GraphicControlExtension").item(0);
                control.setAttribute("disposalMethod",i==1?"restoreToBackgroundColor":"none");
                control.setAttribute("transparentColorFlag","TRUE");control.setAttribute("transparentColorIndex","0");
                metadata.setFromTree("javax_imageio_gif_image_1.0",tree);
                writer.writeToSequence(new IIOImage(image,null,metadata),null);
            }
            writer.endWriteSequence();stream.flush();
        } finally { writer.dispose(); }
        try(var gif=new GifAnimation(output.toByteArray())) {
            gif.nextFrame();var overlaid=gif.nextFrame();
            assertEquals(0xFF0000FF,overlaid.pixels().getRGB(2,2));
            assertEquals(0xFFFF0000,overlaid.pixels().getRGB(3,2));
            var cleared=gif.nextFrame();assertEquals(0,cleared.pixels().getRGB(2,2));
            assertEquals(0xFFFF0000,cleared.pixels().getRGB(5,5));
        }
    }
    @Test void partialFramesRestorePreviousAndLoopWithoutKeepingOldFramePixels() throws Exception {
        try (var gif = new GifAnimation(animation(3))) {
            var first = gif.nextFrame();
            assertEquals(40, first.delayMillis());
            assertEquals(0xFFFF0000, first.pixels().getRGB(3, 4));
            var second = gif.nextFrame();
            assertEquals(0xFF0000FF, second.pixels().getRGB(3, 4));
            assertEquals(0xFFFF0000, second.pixels().getRGB(0, 0));
            assertEquals(0xFFFF0000, first.pixels().getRGB(3, 4));
            var third = gif.nextFrame();
            assertEquals(0xFFFF0000, third.pixels().getRGB(3, 4));
            assertEquals(0xFF00FF00, third.pixels().getRGB(6, 7));
            var loop = gif.nextFrame();
            assertEquals(0xFFFF0000, loop.pixels().getRGB(6, 7));
        }
    }
    @Test void limitsAreCheckedBeforeCanvasAllocationAndCloseIsIdempotent() throws Exception {
        byte[] valid = animation(3);
        byte[] wide = valid.clone(); wide[6] = (byte) 0x81; wide[7] = 7; // 1921 pixels.
        assertThrows(IOException.class, () -> new GifAnimation(wide));
        assertThrows(IOException.class, () -> new GifAnimation(new byte[GifAnimation.MAX_BYTES + 1]));
        assertThrows(IOException.class, () -> new GifAnimation(animation(601)));
        var gif = new GifAnimation(valid); gif.nextFrame(); gif.close(); gif.close();
        assertThrows(IOException.class, gif::nextFrame);
    }
    static byte[] animation(int count) throws IOException {
        var output = new ByteArrayOutputStream();
        var writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (var stream = new MemoryCacheImageOutputStream(output)) {
            writer.setOutput(stream); writer.prepareWriteSequence(null);
            for (int i = 0; i < count; i++) {
                var image = new BufferedImage(i == 0 ? 16 : 2, i == 0 ? 16 : 2, BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics();
                try { graphics.setColor(i == 0 ? Color.RED : i == 1 ? Color.BLUE : Color.GREEN); graphics.fillRect(0, 0, image.getWidth(), image.getHeight()); }
                finally { graphics.dispose(); }
                var metadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(image), null);
                var tree = (IIOMetadataNode) metadata.getAsTree("javax_imageio_gif_image_1.0");
                var descriptor = (IIOMetadataNode) tree.getElementsByTagName("ImageDescriptor").item(0);
                descriptor.setAttribute("imageLeftPosition", i == 1 ? "2" : i > 1 ? "5" : "0");
                descriptor.setAttribute("imageTopPosition", i == 1 ? "3" : i > 1 ? "6" : "0");
                var control = (IIOMetadataNode) tree.getElementsByTagName("GraphicControlExtension").item(0);
                control.setAttribute("disposalMethod", i == 1 ? "restoreToPrevious" : "none");
                control.setAttribute("delayTime", i == 0 ? "2" : "5");
                metadata.setFromTree("javax_imageio_gif_image_1.0", tree);
                writer.writeToSequence(new IIOImage(image, null, metadata), null);
            }
            writer.endWriteSequence(); stream.flush();
        } finally { writer.dispose(); }
        return output.toByteArray();
    }
}
