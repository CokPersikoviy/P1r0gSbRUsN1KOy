package ru.wilyfox.client.chat;

import com.sun.net.httpserver.HttpServer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.client.Client;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.moduser.BackendSocialClient;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;

/** Inject clipboard snapshots without reading or overwriting the user's real system clipboard. */
public final class ClipboardImageClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> ConfigManager.snapshot());
        var originalSource = context.computeOnClient(client -> field(FhClipboardImages.class, null, "source"));
        var originalBackend = context.computeOnClient(client -> field(BackendSocialClient.class, null, "DEFAULT"));
        int guiScale = context.computeOnClient(client -> client.options.guiScale().get());
        int windowWidth = context.computeOnClient(client -> client.getWindow().getScreenWidth());
        int windowHeight = context.computeOnClient(client -> client.getWindow().getScreenHeight());
        var submitted = new AtomicReference<byte[]>(); var caption = new AtomicReference<String>();
        var reads = new AtomicInteger(); var cancelled = new CountDownLatch(1);
        byte[] png;
        HttpServer server;
        try {
            var image = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 32; y++) for (int x = 0; x < 64; x++) image.setRGB(x, y, x < 32 ? 0xFFFF0000 : 0xFF0000FF);
            var bytes = new ByteArrayOutputStream(); ImageIO.write(image, "png", bytes); png = bytes.toByteArray();
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (java.io.IOException failure) { throw new AssertionError(failure); }
        server.createContext("/v1/chat/image", exchange -> {
            try {
                check(exchange.getRequestMethod().equals("POST"), "Image did not use POST");
                check("Bearer unit-test-session".equals(exchange.getRequestHeaders().getFirst("Authorization")), "Image upload lost session");
                submitted.set(exchange.getRequestBody().readAllBytes());
                caption.set(java.net.URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8));
                var body = new com.google.gson.Gson().toJson(new BackendChatMessage(991, "Player0", "Clipboard caption",
                        System.currentTimeMillis(), "minecraft", java.util.List.of(new BackendChatMessage.Attachment("0", "clipboard.png", 64, 32))))
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body);
            } finally { exchange.close(); }
        });
        server.start();
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                Client.getInstance().getHudRenderer().setSettings(false); Client.getInstance().getHudRenderer().setEditing(false);
                ChatDock.dock(ChatDock.key(ChatTab.FH)); ChatDock.select(ChatDock.key(ChatTab.FH));
                client.gui.setScreen(new ChatScreen("Clipboard caption", false));
                set(FhClipboardImages.class, null, "source", new FhClipboardImages.Source() {
                    public boolean hasImage() { return false; }
                    public byte[] read() { throw new AssertionError("Text-only paste read image contents"); }
                });
                check(!FhClipboardImages.tryPaste(client.gui.screen(), ignored -> { }), "Text-only clipboard did not fall through");
                set(FhClipboardImages.class, null, "source", new FhClipboardImages.Source() {
                    public boolean hasImage() { return true; }
                    public byte[] read() { reads.incrementAndGet(); return png; }
                });
                client.gui.screen().keyPressed(paste());
            });
            for (int i = 0; i < 100 && !context.computeOnClient(client -> client.gui.screen() instanceof FhImageAttachScreen); i++) context.waitTick();
            var attach = context.computeOnClient(client -> (FhImageAttachScreen) client.gui.screen());
            for (int i = 0; i < 100 && !context.computeOnClient(client -> ((Button) field(FhImageAttachScreen.class, attach, "send")).active); i++) context.waitTick();
            context.runOnClient(client -> {
                check(reads.get() == 1 && submitted.get() == null, "Paste read repeatedly or sent automatically");
                check(((EditBox) field(FhImageAttachScreen.class, attach, "captionInput")).getValue().equals("Clipboard caption"), "Chat draft did not become caption");
                var preview = (FhImageTexture) field(FhImageAttachScreen.class, attach, "preview");
                check(preview.loaded(), "Clipboard preview did not decode");
                check(((DynamicTexture) client.getTextureManager().getTexture(preview.id())).getPixels().getPixel(0, 0) == 0xFFFF0000,
                        "Preview pixels have wrong channel order");
            });
            context.waitTicks(2); context.takeScreenshot("fh-clipboard-image");
            context.runOnClient(client -> {
                client.getWindow().setWindowed(1000, 760);
            });
            context.waitTicks(2);
            context.runOnClient(client -> {
                client.options.guiScale().set(3); client.resizeGui();
                check(attach.width <= 360, "Compact attach fixture did not reach the intended GUI scale");
                var input = (EditBox) field(FhImageAttachScreen.class, attach, "captionInput");
                attach.mouseClicked(new MouseButtonEvent(input.getX() + 6, input.getY() + 8, new MouseButtonInfo(0, 0)), false);
                check(input.isFocused() && input.getCursorPosition() == 0, "Themed input lost its native caret hit position");
                var sendButton = (Button) field(FhImageAttachScreen.class, attach, "send");
                check(sendButton.getRight() <= attach.width && sendButton.getBottom() <= attach.height, "Attach actions leave a compact GUI");
                check(input.getValue().equals("Clipboard caption"), "Resize discarded image caption");
            });
            context.waitTicks(2); context.takeScreenshot("fh-clipboard-image-small");
            context.runOnClient(client -> { client.options.guiScale().set(guiScale); client.getWindow().setWindowed(windowWidth, windowHeight); client.resizeGui(); });
            var pixels = context.computeOnClient(client -> {
                var preview = (FhImageTexture) field(FhImageAttachScreen.class, attach, "preview");
                return ((DynamicTexture) client.getTextureManager().getTexture(preview.id())).getPixels();
            });
            context.runOnClient(client -> {
                attach.onClose(); check(pixels.isClosed(), "Cancel retained preview texture");
                check(submitted.get() == null && client.gui.screen() instanceof ChatScreen, "Cancel sent an image or lost chat");
                client.gui.screen().keyPressed(paste());
            });
            for (int i = 0; i < 100 && !context.computeOnClient(client -> client.gui.screen() instanceof FhImageAttachScreen); i++) context.waitTick();
            var second = context.computeOnClient(client -> (FhImageAttachScreen) client.gui.screen());
            for (int i = 0; i < 100 && !context.computeOnClient(client -> ((Button) field(FhImageAttachScreen.class, second, "send")).active); i++) context.waitTick();
            context.runOnClient(client -> {
                try {
                    var constructor = BackendSocialClient.class.getDeclaredConstructor(URI.class); constructor.setAccessible(true);
                    var service = constructor.newInstance(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
                    set(BackendSocialClient.class, service, "accessToken", "unit-test-session");
                    var chat = field(BackendSocialClient.class, service, "chat"); set(chat.getClass(), chat, "enabled", true);
                    set(BackendSocialClient.class, null, "DEFAULT", service);
                    try { ((Button) field(FhImageAttachScreen.class, second, "send")).onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)); }
                    finally { set(BackendSocialClient.class, null, "DEFAULT", originalBackend); }
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            });
            for (int i = 0; i < 200 && submitted.get() == null; i++) context.waitTick();
            check(java.util.Arrays.equals(png, submitted.get()) && caption.get().contains("text=Clipboard caption"), "Image bytes or caption did not reach backend");
            for (int i = 0; i < 100 && !context.computeOnClient(client -> ChatTabManager.getInstance().getMessages(ChatTab.FH).stream()
                    .anyMatch(entry -> entry.component().getString().contains("[clipboard.png]"))); i++) context.waitTick();
            context.runOnClient(client -> check(ChatTabManager.getInstance().getMessages(ChatTab.FH).stream()
                    .anyMatch(entry -> entry.component().getString().contains("[clipboard.png]")), "Backend confirmation did not add the image to FH history"));
            context.runOnClient(client -> {
                ConfigManager.get().mainLayout.widgets.clear(); ConfigManager.layoutChanged();
                ChatDock.detach(ChatDock.key(ChatTab.FH), 20, 20);
                var widget = ChatDock.widget(ChatDock.key(ChatTab.FH)); widget.settings().rows = 4;
                var screen = (ChatScreen) client.gui.screen();
                screen.mouseClicked(new MouseButtonEvent(widget.inputX() + 4, widget.inputY() + 3, new MouseButtonInfo(0, 0)), false);
                check(ChatDock.outgoingChannel() == ChatTab.FH, "FH widget did not acquire paste focus");
                screen.keyPressed(paste());
            });
            for (int i = 0; i < 100 && !context.computeOnClient(client -> client.gui.screen() instanceof FhImageAttachScreen); i++) context.waitTick();
            context.runOnClient(client -> {
                ((FhImageAttachScreen) client.gui.screen()).onClose();
                check(ChatDock.outgoingChannel() == ChatTab.FH
                        && ((ru.wilyfox.bridge.ChatInputHost) client.gui.screen()).froghelper$inlineInputKey() != null,
                        "Closing preview lost the original FH widget input focus");
            });
            context.runOnClient(client -> {
                set(FhClipboardImages.class, null, "source", new FhClipboardImages.Source() {
                    public boolean hasImage() { return true; }
                    public byte[] read() throws java.io.IOException {
                        try { if (!cancelled.await(5, TimeUnit.SECONDS)) throw new java.io.IOException("Test timed out"); }
                        catch (InterruptedException failure) { throw new java.io.IOException(failure); }
                        return png;
                    }
                });
                client.gui.screen().keyPressed(paste()); client.gui.setScreen(null);
            });
            cancelled.countDown(); context.waitTicks(5);
            context.runOnClient(client -> check(client.gui.screen() == null, "Late clipboard completion reopened a closed chat"));
            System.out.println("Clipboard images: native Ctrl+V, text fallback, preview pixels, cancel cleanup, explicit HTTP upload, captions and late completion passed");
        } finally {
            cancelled.countDown(); server.stop(0);
            context.runOnClient(client -> {
                client.gui.setScreen(null); set(FhClipboardImages.class, null, "source", originalSource);
                set(BackendSocialClient.class, null, "DEFAULT", originalBackend);
                ChatTabManager.getInstance().clearBackendMessages(); ConfigManager.restoreSnapshot(original);
                client.options.guiScale().set(guiScale); client.getWindow().setWindowed(windowWidth, windowHeight); client.resizeGui();
            });
        }
    }
    private static KeyEvent paste() { return new KeyEvent(GLFW.GLFW_KEY_V, 0, GLFW.GLFW_MOD_CONTROL); }
    private static Object field(Class<?> type, Object object, String name) {
        try { var f = type.getDeclaredField(name); f.setAccessible(true); return f.get(object); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void set(Class<?> type, Object object, String name, Object value) {
        try { var f = type.getDeclaredField(name); f.setAccessible(true); f.set(object, value); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
