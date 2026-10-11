package ru.wilyfox.client.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.*;
import net.minecraft.resources.Identifier;
import ru.wilyfox.client.moduser.BackendSocialClient;
import java.util.concurrent.CompletableFuture;

public final class FhImageScreen extends Screen {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("froghelper", "fh_chat_image");
    private final Screen parent;
    private final java.util.function.Supplier<CompletableFuture<byte[]>> download;
    private boolean started, loaded, failed;
    private volatile boolean closed;
    private int imageWidth, imageHeight;
    public FhImageScreen(Screen parent, long message, String attachment) {
        this(parent, () -> BackendSocialClient.downloadChatImage(message, attachment));
    }
    FhImageScreen(Screen parent, java.util.function.Supplier<CompletableFuture<byte[]>> download) {
        super(Component.translatable("froghelper.chat.image_open")); this.parent = parent; this.download = download;
    }
    public static boolean openLink(Style style, Screen parent) {
        if (style == null || !(style.getClickEvent() instanceof ClickEvent.RunCommand link)
                || !link.command().startsWith("froghelper:image:")) return false;
        // This namespace is exclusively local; even a malformed link must never reach Minecraft's server.
        String[] parts = link.command().split(":");
        if (parts.length == 4 && parts[2].matches("[1-9][0-9]{0,18}") && parts[3].matches("0|[0-9]{17,20}")) {
            try { Minecraft.getInstance().gui.setScreen(new FhImageScreen(parent, Long.parseLong(parts[2]), parts[3])); }
            catch (NumberFormatException ignored) { }
        }
        return true;
    }
    @Override protected void init() {
        if (started) return; started = true;
        download.get().thenApplyAsync(bytes -> {
            if (closed) return null;
            try { return FhImages.decode(bytes); } catch (java.io.IOException failure) { throw new java.util.concurrent.CompletionException(failure); }
        }, FhImages.IO).whenComplete((image, error) -> minecraft.execute(() -> {
            if (closed || minecraft.gui.screen() != this) { if (image != null) image.close(); return; }
            if (error != null) { failed = true; return; }
            imageWidth = image.getWidth(); imageHeight = image.getHeight();
            try { minecraft.getTextureManager().register(TEXTURE, new DynamicTexture(() -> "FH chat image", image)); loaded = true; }
            catch (RuntimeException failure) { image.close(); failed = true; }
        }));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        graphics.fill(0, 0, width, height, 0xE8101010);
        if (loaded) {
            double scale = Math.min((width - 24d) / imageWidth, (height - 48d) / imageHeight);
            int w = Math.max(1, (int) (imageWidth * scale)), h = Math.max(1, (int) (imageHeight * scale));
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, (width - w) / 2, (height - h) / 2, 0, 0, w, h,
                    imageWidth, imageHeight, imageWidth, imageHeight);
        } else graphics.centeredText(font, Component.translatable(failed ? "froghelper.chat.image_failed" : "froghelper.chat.image_loading"), width / 2, height / 2, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("froghelper.chat.image_close"), width / 2, height - 18, 0xFFAAAAAA);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public void removed() { closed = true; if (loaded) minecraft.getTextureManager().release(TEXTURE); }
}
