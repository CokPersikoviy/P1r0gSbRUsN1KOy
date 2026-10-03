package ru.wilyfox.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AtomicFileWriterTest {
    @TempDir Path directory;

    @Test
    void failedSerializationKeepsExistingSaveAndRemovesTemporaryFile() throws IOException {
        Path target = directory.resolve("config.json");
        Files.writeString(target, "{\"previous\":true}");

        assertThrows(IOException.class, () -> AtomicFileWriter.write(target, writer -> {
            writer.write("{\"unfinished\":");
            throw new IOException("Interrupted save");
        }));

        assertEquals("{\"previous\":true}", Files.readString(target));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void createsParentsAndReplacesCompleteUtf8Save() throws IOException {
        Path target = directory.resolve("nested/config.json");
        AtomicFileWriter.write(target, writer -> writer.write("старый"));
        AtomicFileWriter.write(target, writer -> writer.write("{\"новый\":\"🐸\"}"));

        assertEquals("{\"новый\":\"🐸\"}", Files.readString(target));
        try (var files = Files.list(target.getParent())) {
            assertEquals(1, files.count());
        }
    }
}
