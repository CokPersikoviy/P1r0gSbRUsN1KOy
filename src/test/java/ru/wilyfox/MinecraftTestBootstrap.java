package ru.wilyfox;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.data.registries.VanillaRegistries;
public final class MinecraftTestBootstrap {
    private static boolean initialized;
    private MinecraftTestBootstrap() {}
    public static synchronized void initialize() {
        if (initialized) return;
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createLookup())
                .forEach(DataComponentInitializers.PendingComponents::apply);
        initialized = true;
    }
}
