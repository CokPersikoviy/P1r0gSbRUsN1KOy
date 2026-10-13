package ru.wilyfox.client.chat;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import net.fabricmc.loader.api.FabricLoader;

/** Explicit user saves only; immutable content IDs keep config undo/profile changes independent. */
public final class ImageWidgetStore {
    private ImageWidgetStore() {}
    private static Path directory() { return FabricLoader.getInstance().getConfigDir().resolve("froghelper-images"); }
    public static String save(byte[] bytes) throws IOException { return save(directory(), bytes); }
    public static byte[] read(String name) throws IOException { return read(directory(), name); }
    static String save(Path directory, byte[] bytes) throws IOException {
        try (var profile = ru.wilyfox.client.profiler.ModProfiler.getInstance().scope("chat/imageStore/save")) {
            if (FhImages.isGif(bytes)) { try (var gif = new GifAnimation(bytes)) { gif.nextFrame().pixels().flush(); } }
            else FhImages.dimensions(bytes);
            String name;
            try { name = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)) + ".img"; }
            catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
            Files.createDirectories(directory);
            Path destination = directory.resolve(name);
            {
                Path temporary = Files.createTempFile(directory, "saving-", ".tmp");
                try {
                    Files.write(temporary, bytes);
                    try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                    catch (AtomicMoveNotSupportedException noAtomic) { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING); }
                } finally { Files.deleteIfExists(temporary); }
            }
            return name;
        }
    }
    static byte[] read(Path directory, String name) throws IOException {
        try (var profile = ru.wilyfox.client.profiler.ModProfiler.getInstance().scope("chat/imageStore/read")) {
            if (name == null || !name.matches("[a-f0-9]{64}\\.img")) throw new IOException("Invalid image asset");
            try (var stream = Files.newInputStream(directory.resolve(name))) {
                byte[] bytes = stream.readNBytes(FhImages.MAX_DOWNLOAD_BYTES + 1);
                if (bytes.length > FhImages.MAX_DOWNLOAD_BYTES) throw new IOException("Image exceeds size limit");
                try {
                    String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)) + ".img";
                    if (!expected.equals(name)) throw new IOException("Image asset changed");
                } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
                return bytes;
            }
        }
    }
}
