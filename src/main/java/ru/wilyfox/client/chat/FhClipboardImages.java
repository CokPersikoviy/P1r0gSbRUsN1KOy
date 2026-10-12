package ru.wilyfox.client.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Platform;
import org.lwjgl.system.windows.Kernel32;
import org.lwjgl.system.windows.User32;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Read only on an explicit paste; Minecraft intentionally runs AWT in headless mode. */
public final class FhClipboardImages {
    interface Source {
        boolean hasImage();
        byte[] read() throws IOException;
    }
    private static Source source = Platform.get() == Platform.WINDOWS ? new WindowsSource() : null;
    private static final AtomicBoolean reading = new AtomicBoolean();
    private FhClipboardImages() { }

    public static boolean tryPaste(Screen parent, Consumer<byte[]> accept) {
        var reader = source;
        if (reader == null) return false;
        try { if (!reader.hasImage()) return false; }
        catch (RuntimeException | LinkageError unavailable) { return false; }
        if (!reading.compareAndSet(false, true)) return true;
        var client = Minecraft.getInstance();
        var focused = parent.getFocused();
        FhImages.supply(() -> {
            try (var ignored = ru.wilyfox.client.profiler.ModProfiler.getInstance().scope("chat/clipboardImage")) {
                return reader.read();
            } catch (IOException failure) { throw new CompletionException(failure); }
        }).whenComplete((bytes, error) -> client.execute(() -> {
            reading.set(false);
            if (client.gui.screen() != parent || parent.getFocused() != focused
                    || parent instanceof net.minecraft.client.gui.screens.ChatScreen && ChatDock.outgoingChannel() != ChatTab.FH) return;
            if (error != null) client.gui.hud.setOverlayMessage(Component.translatable("froghelper.chat.clipboard_failed"), false);
            else accept.accept(bytes);
        }));
        return true;
    }

    static final class WindowsSource implements Source {
        private static final long OPEN = User32.getLibrary().getFunctionAddress("OpenClipboard");
        private static final long CLOSE = User32.getLibrary().getFunctionAddress("CloseClipboard");
        private static final long AVAILABLE = User32.getLibrary().getFunctionAddress("IsClipboardFormatAvailable");
        private static final long GET = User32.getLibrary().getFunctionAddress("GetClipboardData");
        private static final long REGISTER = User32.getLibrary().getFunctionAddress("RegisterClipboardFormatW");
        private static final long SIZE = Kernel32.getLibrary().getFunctionAddress("GlobalSize");
        private static final long LOCK = Kernel32.getLibrary().getFunctionAddress("GlobalLock");
        private static final long UNLOCK = Kernel32.getLibrary().getFunctionAddress("GlobalUnlock");
        private static final int PNG = pngFormat();
        private static int pngFormat() {
            try (var stack = MemoryStack.stackPush()) {
                return JNI.callPI(MemoryUtil.memAddress(stack.UTF16("PNG")), REGISTER);
            }
        }
        private static boolean available(int format) { return format != 0 && JNI.callI(format, AVAILABLE) != 0; }
        @Override public boolean hasImage() { return available(PNG) || available(17) || available(8) || available(15); }
        @Override public byte[] read() throws IOException {
            byte[] bytes; boolean png, file;
            // Never keep the system clipboard locked while decoding or compressing pixels.
            int attempts = 0;
            while (JNI.callPI(0L, OPEN) == 0) {
                if (++attempts >= 3) throw new IOException("Clipboard is busy");
                java.util.concurrent.locks.LockSupport.parkNanos(10_000_000);
            }
            try {
                int format = available(PNG) ? PNG : available(17) ? 17 : available(8) ? 8 : available(15) ? 15 : 0;
                if (format == 0) throw new IOException("Clipboard image changed");
                png = format == PNG;
                file = format == 15;
                long handle = JNI.callP(format, GET);
                if (handle == 0) throw new IOException("Clipboard image unavailable");
                long size = JNI.callPP(handle, SIZE);
                if (size <= 0 || size > ClipboardImageCodec.MAX_SOURCE_BYTES) throw new IOException("Clipboard image too large");
                long pointer = JNI.callPP(handle, LOCK);
                if (pointer == 0) throw new IOException("Cannot lock clipboard image");
                try {
                    ByteBuffer buffer = MemoryUtil.memByteBuffer(pointer, (int) size);
                    bytes = new byte[(int) size]; buffer.get(bytes);
                } finally { JNI.callPI(handle, UNLOCK); }
            } finally { JNI.callI(CLOSE); }
            return file ? ClipboardImageCodec.file(ClipboardImageCodec.fileDrop(bytes))
                    : png ? ClipboardImageCodec.png(bytes) : ClipboardImageCodec.dib(bytes);
        }
    }
}
