package ru.wilyfox.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyMapping.class)
public interface KeyMappingAccessorMixin {
    @Accessor("key")
    InputConstants.Key froghelper$getKey();

    @Accessor("isDown")
    void froghelper$setDownDirect(boolean down);
}
