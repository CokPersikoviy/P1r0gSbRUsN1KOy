package ru.wilyfox.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.resources.MapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MapTextureManager.class)
public interface MapTextureManagerAccessorMixin {
    @Accessor("maps")
    Int2ObjectMap<?> froghelper$getMaps();
}
