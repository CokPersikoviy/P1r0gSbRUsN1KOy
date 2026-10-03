package ru.wilyfox.client.protocol;

import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class DwHandshakeFingerprint {
    private static final HexFormat HEX = HexFormat.of();

    private DwHandshakeFingerprint() {
    }

    private static final class Cached {
        private static final String VALUE = calculate();
    }

    static String generate() {
        return Cached.VALUE;
    }

    private static String calculate() {
        List<String> parts = new ArrayList<>();
        addSystemProperties(parts);
        addMemoryInfo(parts);
        addFileStores(parts);
        addEnvironment(parts);
        addPlatformFiles(parts);

        parts.removeIf(part -> part == null || part.isBlank());
        parts.sort(String::compareTo);

        return sha256Hex(String.join("|", parts));
    }

    private static void addSystemProperties(List<String> parts) {
        addProperty(parts, "os.arch");
        addProperty(parts, "os.name");
        addProperty(parts, "os.version");
        addProperty(parts, "user.home");
        addProperty(parts, "user.name");

        int processors = Runtime.getRuntime().availableProcessors();
        parts.add("cpu.cores=" + processors);
    }

    private static void addMemoryInfo(List<String> parts) {
        try {
            if (ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean bean) {
                parts.add("mem.total=" + bean.getTotalMemorySize());
            }
        } catch (Exception ignored) {
        }
    }

    private static void addFileStores(List<String> parts) {
        try {
            List<FileStore> stores = new ArrayList<>();
            FileSystems.getDefault().getFileStores().forEach(stores::add);
            stores.sort(Comparator.comparing(FileStore::name));
            for (FileStore store : stores) {
                try {
                    parts.add("fs:" + store.name() + "=" + store.getTotalSpace());
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
            // Inaccessible storage is omitted, as in EvoPlus.
        }
    }

    private static void addEnvironment(List<String> parts) {
        Map<String, String> environment = System.getenv();
        addEnvironment(parts, "computername", environment.get("COMPUTERNAME"));
        addEnvironment(parts, "hostname", environment.get("HOSTNAME"));
        addEnvironment(parts, "cpu.id", environment.get("PROCESSOR_IDENTIFIER"));
        addEnvironment(parts, "cpu.arch", environment.get("PROCESSOR_ARCHITECTURE"));
        addEnvironment(parts, "cpu.count", environment.get("NUMBER_OF_PROCESSORS"));
    }

    private static void addPlatformFiles(List<String> parts) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("nux") && !os.contains("nix")) {
            return;
        }
        addPlatformFile(parts, "machine.uuid", "/sys/class/dmi/id/product_uuid");
        addPlatformFile(parts, "mb.serial", "/sys/class/dmi/id/board_serial");
        addPlatformFile(parts, "product.serial", "/sys/class/dmi/id/product_serial");
        addPlatformFile(parts, "chassis.serial", "/sys/class/dmi/id/chassis_serial");
        addPlatformFile(parts, "machine.id", "/etc/machine-id");
    }

    private static void addPlatformFile(List<String> parts, String label, String path) {
        try {
            String value = Files.readString(Path.of(path)).trim();
            if (!value.isBlank()) {
                parts.add(label + "=" + value);
            }
        } catch (Exception ignored) {
        }
    }

    private static void addProperty(List<String> parts, String key) {
        parts.add(key + "=" + System.getProperty(key, ""));
    }

    private static void addEnvironment(List<String> parts, String label, String value) {
        if (value == null) {
            return;
        }

        parts.add(label + "=" + value);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
