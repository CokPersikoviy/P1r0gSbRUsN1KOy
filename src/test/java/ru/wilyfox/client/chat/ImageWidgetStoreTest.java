package ru.wilyfox.client.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class ImageWidgetStoreTest {
    @TempDir Path directory;
    @Test void explicitSavePersistsIndependentOfChatAndDetectsCorruption() throws Exception {
        var output = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(32,16,BufferedImage.TYPE_INT_RGB),"png",output);
        byte[] bytes=output.toByteArray(); String asset=ImageWidgetStore.save(directory,bytes);
        assertArrayEquals(bytes,ImageWidgetStore.read(directory,asset));
        assertEquals(asset,ImageWidgetStore.save(directory,bytes));
        try(var files=Files.list(directory)){assertEquals(1,files.count());}
        Files.writeString(directory.resolve(asset),"corrupt");
        assertThrows(IOException.class,()->ImageWidgetStore.read(directory,asset));
        assertEquals(asset,ImageWidgetStore.save(directory,bytes));
        assertArrayEquals(bytes,ImageWidgetStore.read(directory,asset));
        assertThrows(IOException.class,()->ImageWidgetStore.read(directory,"../froghelper.json"));
        assertThrows(IOException.class,()->ImageWidgetStore.save(directory,new byte[]{1,2,3}));
    }
    @Test void gifCanBeSavedWithoutExpandingAllAnimationFrames() throws Exception {
        byte[] bytes=GifAnimationTest.animation(3);
        assertArrayEquals(bytes,ImageWidgetStore.read(directory,ImageWidgetStore.save(directory,bytes)));
    }
}
