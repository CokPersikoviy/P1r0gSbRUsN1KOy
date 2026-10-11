package ru.wilyfox.client.moduser;

import org.junit.jupiter.api.Test;
import ru.wilyfox.client.chat.BackendChatMessage;
import ru.wilyfox.client.chat.FhImages;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class BackendChatImagesTest {
    private static final long NOW = 1_700_000_000_000L;
    @Test void discordIdentityIsSeparateFromGameIdentityAndImagesNeedNoCaption() {
        var image = new BackendChatMessage.Attachment("1558934610765807800", "Жабка.png", 1280, 720);
        var message = new BackendChatMessage(1, "Лиса 🐸", "", NOW, "discord", List.of(image));
        assertDoesNotThrow(message::validate);
        assertTrue(message.fromDiscord());
        var decoded = SocialWire.JSON.fromJson(SocialWire.JSON.toJson(message), BackendChatMessage.class);
        assertEquals(message, decoded);
        assertDoesNotThrow(() -> new BackendChatClient.Page(1, List.of(decoded), false).validate(NOW));
        assertThrows(IllegalArgumentException.class, () -> new BackendChatMessage(2, "Лиса 🐸", "Hi", NOW).validate());
        assertThrows(IllegalArgumentException.class, () -> new BackendChatMessage(2, "Fox", "Hi", NOW, "admin", List.of()).validate());
        assertThrows(IllegalArgumentException.class, () -> new BackendChatMessage(2, "Fox", "", NOW).validate());
        assertDoesNotThrow(() -> new BackendChatMessage(2, "Fox", "", NOW, "minecraft", List.of(image)).validate());
    }
    @Test void attachmentsAndTextCannotInjectStylesPathsOrUnboundedTextures() {
        assertThrows(IllegalArgumentException.class, () -> new BackendChatMessage.Attachment("../../secret", "image.png", 16, 16).validate());
        assertThrows(IllegalArgumentException.class, () -> new BackendChatMessage.Attachment("0", "§cimage", 16, 16).validate());
        assertThrows(IllegalArgumentException.class, () -> new BackendChatMessage.Attachment("0", "image.jpg", 8192, 8192).validate());
        assertDoesNotThrow(() -> new BackendChatMessage.Attachment("1558947400167723088", "StarWatcher.jpg", 3304, 4320).validate());
        var image = new BackendChatMessage.Attachment("0", "image.png", 16, 16);
        assertThrows(IllegalArgumentException.class, () -> new BackendChatMessage(1, "Fox", "text", NOW, "minecraft", List.of(image, image)).validate());
        assertDoesNotThrow(() -> new BackendChatMessage(1, "Discord user", "🐸".repeat(2000), NOW, "discord", List.of()).validate());
        assertThrows(IllegalArgumentException.class, () -> new BackendChatMessage(1, "Discord user", "🐸".repeat(2001), NOW, "discord", List.of()).validate());
    }
    @Test void compressedImageHeadersAreCheckedBeforePixelAllocation() throws Exception {
        var source = new BufferedImage(32, 16, BufferedImage.TYPE_INT_RGB);
        for (String format : List.of("png", "jpeg")) {
            var stream = new ByteArrayOutputStream(); ImageIO.write(source, format, stream);
            assertArrayEquals(new int[]{32, 16}, FhImages.dimensions(stream.toByteArray()));
        }
        assertThrows(java.io.IOException.class, () -> FhImages.dimensions(new byte[FhImages.MAX_BYTES + 1]));
        assertThrows(java.io.IOException.class, () -> FhImages.dimensions("not an image".getBytes()));
        var stream = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(4096, 4096, BufferedImage.TYPE_BYTE_GRAY), "png", stream);
        assertTrue(stream.size() < FhImages.MAX_BYTES);
        assertThrows(java.io.IOException.class, () -> FhImages.dimensions(stream.toByteArray()));
    }
}
