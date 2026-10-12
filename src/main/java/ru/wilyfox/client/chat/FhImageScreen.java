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
    private volatile byte[] sourceBytes;
    private boolean pinning;
    private net.minecraft.client.gui.components.Button pinButton;
    private GifAnimation animation;
    private DynamicTexture texture;
    private Frame pending;
    private boolean decoding;
    private long frameDue;
    private record Frame(com.mojang.blaze3d.platform.NativeImage image, GifAnimation animation, int delayMillis) {}
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
        pinButton = addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                Component.translatable("froghelper.image_widget.pin"), button -> pinImage())
                .bounds(Math.max(12, width / 2 - 100), height - 44, Math.min(200, width - 24), 20).build());
        pinButton.active = loaded && !pinning;
        if (started) return; started = true;
        download.get().thenApplyAsync(bytes -> {
            if (closed) return null;
            sourceBytes = bytes;
            GifAnimation gif = null;
            try (var ignored = ru.wilyfox.client.profiler.ModProfiler.getInstance().scope("chat/imageDecode")) {
                if (!FhImages.isGif(bytes)) return new Frame(FhImages.decode(bytes), null, 0);
                gif = new GifAnimation(bytes); var frame = gif.nextFrame();
                return new Frame(FhImages.gifPixels(frame), gif, frame.delayMillis());
            } catch (java.io.IOException | RuntimeException failure) {
                if (gif != null) gif.close(); throw new java.util.concurrent.CompletionException(failure);
            }
        }, FhImages.IO).whenComplete((frame, error) -> minecraft.execute(() -> {
            if (closed || minecraft.gui.screen() != this) { discard(frame); return; }
            if (error != null) { failed = true; return; }
            imageWidth = frame.image.getWidth(); imageHeight = frame.image.getHeight();
            animation = frame.animation;
            try {
                texture = new DynamicTexture(() -> "FH chat image", frame.image);
                minecraft.getTextureManager().register(TEXTURE, texture); loaded = true;
                frameDue = System.nanoTime() + frame.delayMillis * 1_000_000L;
                preloadFrame();
            } catch (RuntimeException failure) { discard(frame); failed = true; }
        }));
    }
    private void pinImage() {
        if (!loaded || sourceBytes == null || pinning) return;
        pinning = true; pinButton.active = false; byte[] bytes = sourceBytes;
        var config = ru.wilyfox.client.hud.config.ConfigManager.get();
        FhImages.supply(() -> {
            try { return ImageWidgetStore.save(bytes); }
            catch (java.io.IOException error) { throw new java.util.concurrent.CompletionException(error); }
        }).whenComplete((name, error) -> minecraft.execute(() -> {
            pinning = false;
            if (error != null) { minecraft.gui.hud.setOverlayMessage(Component.translatable("froghelper.image_widget.save_failed"), false); return; }
            if (config != ru.wilyfox.client.hud.config.ConfigManager.get()) return;
            config.imageWidget.asset = name;
            for (var widget : ru.wilyfox.client.Client.getInstance().getHudRenderer().getWidgets())
                if (widget instanceof ru.wilyfox.client.hud.widget.ImageWidget imageWidget) imageWidget.release();
            config.mainLayout.widgets.add(ru.wilyfox.client.hud.config.WidgetCatalog.IMAGE.key());
            ru.wilyfox.client.hud.config.ConfigManager.layoutChanged();
            ru.wilyfox.client.hud.config.ConfigManager.save();
            minecraft.gui.hud.setOverlayMessage(Component.translatable("froghelper.image_widget.saved"), false);
        }));
    }
    private static void discard(Frame frame) {
        if (frame != null) { frame.image.close(); if (frame.animation != null) frame.animation.close(); }
    }
    private void preloadFrame() {
        if (animation == null || closed || decoding || pending != null) return;
        decoding = true;
        FhImages.supply(() -> {
            try (var ignored = ru.wilyfox.client.profiler.ModProfiler.getInstance().scope("chat/gifFrameDecode")) {
                var frame = animation.nextFrame(); return new Frame(FhImages.gifPixels(frame), null, frame.delayMillis());
            }
            catch (java.io.IOException failure) { throw new java.util.concurrent.CompletionException(failure); }
        }).whenComplete((frame, error) -> minecraft.execute(() -> {
            decoding = false;
            if (closed || minecraft.gui.screen() != this) { discard(frame); return; }
            if (error != null) { animation.close(); animation = null; return; }
            pending = frame;
        }));
    }
    private void advanceAnimation() {
        if (!loaded || animation == null || !minecraft.isWindowActive()) return;
        if (pending != null && System.nanoTime() >= frameDue) {
            var frame = pending; pending = null;
            texture.setPixels(frame.image); texture.upload();
            frameDue = System.nanoTime() + frame.delayMillis * 1_000_000L;
        }
        preloadFrame();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        advanceAnimation();
        pinButton.active = loaded && sourceBytes != null && !pinning;
        graphics.fill(0, 0, width, height, 0xE8101010);
        if (loaded) {
            double scale = Math.min((width - 24d) / imageWidth, (height - 80d) / imageHeight);
            int w = Math.max(1, (int) (imageWidth * scale)), h = Math.max(1, (int) (imageHeight * scale));
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, (width - w) / 2, (height - h) / 2, 0, 0, w, h,
                    imageWidth, imageHeight, imageWidth, imageHeight);
        } else graphics.centeredText(font, Component.translatable(failed ? "froghelper.chat.image_failed" : "froghelper.chat.image_loading"), width / 2, height / 2, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("froghelper.chat.image_close"), width / 2, height - 18, 0xFFAAAAAA);
        super.extractRenderState(graphics, x, y, delta);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public void removed() {
        closed = true;
        sourceBytes = null;
        if (animation != null) animation.close();
        discard(pending); pending = null;
        if (loaded) minecraft.getTextureManager().release(TEXTURE);
    }
}
