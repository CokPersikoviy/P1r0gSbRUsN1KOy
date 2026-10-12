package ru.wilyfox.client.chat;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.*;
import ru.wilyfox.client.hud.config.ConfigManager;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;

public final class ChatDiscordImagesClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> ConfigManager.snapshot());
        var chat = ChatTabManager.getInstance();
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                chat.clearBackendMessages(); ConfigManager.get().render.chatTimestamps = false;
                ChatDock.select(ChatDock.key(ChatTab.FH)); client.gui.setScreen(new ChatScreen("caption", false));
                long now = System.currentTimeMillis();
                chat.acceptBackendMessages(List.of(new BackendChatMessage(1, "Лиса 🐸", "Картинка", now, "discord", List.of())), false);
                chat.acceptBackendMessages(List.of(new BackendChatMessage(1, "Лиса 🐸", "Картинка", now, "discord",
                        List.of(new BackendChatMessage.Attachment("1558934610765807800", "frog.png", 32, 16)))), false);
                check(chat.getMessages(ChatTab.FH).size() == 1, "Late attachment duplicated the FH message");
                var entry = chat.getMessages(ChatTab.FH).getLast();
                check(entry.message().content().getString().equals("\uF8FE Лиса 🐸: Картинка [frog.png]"), "Discord badge, identity or image link missing");
                var badge = entry.message().content().getSiblings().getFirst();
                check(badge.getStyle().getFont().equals(new FontDescription.Resource(
                        net.minecraft.resources.Identifier.fromNamespaceAndPath("froghelper", "discord_badge"))), "Discord badge lost its bitmap font");
                check(ChatMessageCopyExtractor.selectCopiedText(ChatMessageSanitizer.forLogic(
                        entry.message().content().getString()), true).equals("Лиса 🐸: Картинка [frog.png]"), "Copied Discord message contains a display glyph");
                var style = entry.message().content().getSiblings().getLast().getStyle();
                try {
                    var click = ChatScreen.class.getDeclaredMethod("handleComponentClicked", Style.class, boolean.class);
                    click.setAccessible(true);
                    check(Boolean.TRUE.equals(click.invoke(client.gui.screen(), style, false)), "Image link fell through to server chat");
                } catch (ReflectiveOperationException error) { throw new AssertionError("Actual chat click handler failed", error); }
                check(client.gui.screen() instanceof FhImageScreen, "Image viewer did not open");
                client.gui.screen().onClose();
                check(client.gui.screen() instanceof ChatScreen, "Viewer lost the original chat screen");
                var attach = new FhImageAttachScreen(client.gui.screen(), "caption"); client.gui.setScreen(attach);
                attach.onFilesDrop(List.of(Path.of("D:/test.png")));
                check(attach.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.EditBox)
                        .map(c -> ((net.minecraft.client.gui.components.EditBox)c).getValue()).findFirst().orElse("").endsWith("test.png"), "File drop lost path");
                attach.onClose();
            });
            context.waitTicks(2);
            context.takeScreenshot("fh-discord-badge");
            AtomicBoolean decoded = new AtomicBoolean();
            for (String format : List.of("png", "jpeg", "large-jpeg")) {
                boolean large = format.equals("large-jpeg");
                byte[] bytes;
                try {
                    var source = new BufferedImage(large ? 3304 : 64, large ? 4320 : 32,
                            large ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_INT_RGB);
                    for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++)
                        source.setRGB(x, y, x < source.getWidth() / 2 ? 0xFF3366 : 0x339966);
                    var stream = new ByteArrayOutputStream(); ImageIO.write(source, large ? "jpeg" : format, stream); bytes = stream.toByteArray();
                } catch (java.io.IOException failure) { throw new AssertionError(failure); }
                decoded.set(false);
                var future = CompletableFuture.supplyAsync(() -> {
                    try (var image = FhImages.decode(bytes)) {
                        check(image.getWidth() == (large ? 826 : 64) && image.getHeight() == (large ? 1080 : 32), "Decoded image dimensions changed");
                        check((image.getPixel(2, 2) & 0x00FF0000) != 0, "Decoded image lost red channel");
                        decoded.set(true); return bytes;
                    } catch (java.io.IOException error) { throw new java.util.concurrent.CompletionException(error); }
                }, FhImages.IO);
                context.runOnClient(client -> client.gui.setScreen(new FhImageScreen(new ChatScreen("caption", false), () -> future)));
                context.waitTicks(15);
                check(decoded.get(), "Image was not decoded on the worker");
                context.takeScreenshot("fh-image-" + format);
                context.runOnClient(client -> client.gui.screen().onClose());
            }
            context.runOnClient(client -> {
                Style invalid = Style.EMPTY.withClickEvent(new ClickEvent.RunCommand("froghelper:image:../../secret"));
                check(FhImageScreen.openLink(invalid, client.gui.screen()), "Malformed local link could reach server");
                check(client.gui.screen() instanceof ChatScreen, "Malformed local link opened viewer");
                System.out.println("Discord FH: unicode names, local attachment links, file drop, PNG/JPEG worker decoding and viewer lifecycle passed");
            });
        } finally { context.runOnClient(client -> { client.gui.setScreen(null); chat.clearBackendMessages(); ConfigManager.restoreSnapshot(original); }); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
