package ru.wilyfox.client.chat;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import ru.wilyfox.client.profiler.ModProfiler;

/** A HUD image owns one texture and at most one prefetched frame. All decoding is off the render thread. */
public final class FhImageTexture implements AutoCloseable {
    private static final AtomicLong IDS = new AtomicLong();
    private final Minecraft client = Minecraft.getInstance();
    private final Identifier id = Identifier.fromNamespaceAndPath("froghelper", "hud_image_" + IDS.incrementAndGet());
    private DynamicTexture texture;
    private GifAnimation gif;
    private Frame pending;
    private boolean decoding, failed;
    private volatile boolean closed;
    private long due;
    private int width, height;
    private final String decodeSection, uploadSection, gifDecodeSection, gifUploadSection;
    private record Frame(NativeImage image, GifAnimation gif, int delay) {}
    public FhImageTexture(Supplier<CompletableFuture<byte[]>> source, boolean animated) {
        this(source, animated, "hud/ImageWidget");
    }
    FhImageTexture(Supplier<CompletableFuture<byte[]>> source, boolean animated, String profilePrefix) {
        decodeSection = profilePrefix + "/decode"; uploadSection = profilePrefix + "/upload";
        gifDecodeSection = profilePrefix + "/gifFrameDecode"; gifUploadSection = profilePrefix + "/gifUpload";
        source.get().thenApplyAsync(bytes -> {
            if (closed) return null;
            GifAnimation decoder = null;
            try (var ignored = ModProfiler.getInstance().scope(decodeSection)) {
                if (!FhImages.isGif(bytes)) return new Frame(FhImages.decode(bytes), null, 0);
                decoder = new GifAnimation(bytes); var frame = decoder.nextFrame(); var image = FhImages.gifPixels(frame);
                if (!animated) { decoder.close(); decoder = null; }
                return new Frame(image, decoder, frame.delayMillis());
            } catch (java.io.IOException | RuntimeException error) {
                if (decoder != null) decoder.close(); throw new java.util.concurrent.CompletionException(error);
            }
        }, FhImages.IO).whenComplete((frame, error) -> client.execute(() -> {
            try (var profile = ru.wilyfox.client.profiler.ModProfiler.getInstance().scope(uploadSection)) {
                if (closed) { discard(frame); return; }
                if (error != null) { failed = true; return; }
                gif = frame.gif; width = frame.image.getWidth(); height = frame.image.getHeight();
                try {
                    texture = new DynamicTexture(() -> "FH image widget", frame.image);
                    client.getTextureManager().register(id, texture);
                    due = System.nanoTime() + frame.delay * 1_000_000L;
                } catch (RuntimeException bad) { if (texture != null) texture.close(); else frame.image.close(); if (gif != null) gif.close(); texture = null; failed = true; }
            }
        }));
    }
    public Identifier id() { return id; }
    public int width() { return width; }
    public int height() { return height; }
    public boolean loaded() { return texture != null && !closed; }
    public boolean failed() { return failed; }
    public void advance() {
        if (!loaded() || gif == null || !client.isWindowActive()) return;
        if (pending != null && System.nanoTime() >= due) {
            var next = pending; pending = null;
            try (var profile = ModProfiler.getInstance().scope(gifUploadSection)) {
                texture.setPixels(next.image); texture.upload();
            }
            due = System.nanoTime() + next.delay * 1_000_000L;
        }
        if (pending != null || decoding) return;
        decoding = true; var decoder = gif;
        FhImages.supply(() -> {
            try (var ignored = ModProfiler.getInstance().scope(gifDecodeSection)) {
                var frame = decoder.nextFrame(); return new Frame(FhImages.gifPixels(frame), null, frame.delayMillis());
            } catch (java.io.IOException error) { throw new java.util.concurrent.CompletionException(error); }
        }).whenComplete((frame, error) -> client.execute(() -> {
            decoding = false;
            if (closed) { discard(frame); return; }
            if (error != null) { gif.close(); gif = null; return; }
            pending = frame;
        }));
    }
    private static void discard(Frame frame) { if (frame != null) { frame.image.close(); if (frame.gif != null) frame.gif.close(); } }
    @Override public void close() {
        if (closed) return; closed = true;
        if (gif != null) gif.close(); discard(pending); pending = null;
        if (texture != null) { client.getTextureManager().release(id); texture = null; }
    }
}
