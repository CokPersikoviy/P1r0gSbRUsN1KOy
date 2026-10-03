package ru.wilyfox;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.data.registries.VanillaRegistries;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.file.Path;
public final class MinecraftTestBootstrap {
    private static boolean initialized;
    private MinecraftTestBootstrap() {}
    public static synchronized void initialize() {
        if (initialized) return;
        // Plain JUnit does not launch Fabric's game provider. Give the loader an isolated
        // config directory before a store/handler initializes ConfigManager.
        try {
            var loader = FabricLoader.getInstance();
            var configDir = loader.getClass().getDeclaredField("configDir");
            configDir.setAccessible(true);
            if (configDir.get(loader) == null) {
                Path runtime = Files.createDirectories(Path.of("build", "test-runtime"));
                configDir.set(loader, Files.createTempDirectory(runtime, "config-").toAbsolutePath());
            }
        } catch (ReflectiveOperationException | java.io.IOException exception) {
            throw new IllegalStateException("Unable to initialize the isolated Fabric test config", exception);
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createLookup())
                .forEach(DataComponentInitializers.PendingComponents::apply);
        initialized = true;
    }
}
