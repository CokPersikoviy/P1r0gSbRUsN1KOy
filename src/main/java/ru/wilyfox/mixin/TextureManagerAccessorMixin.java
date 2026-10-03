package ru.wilyfox.mixin;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TickableTexture;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.Set;

@Mixin(TextureManager.class)
public interface TextureManagerAccessorMixin {
    @Accessor("byPath")
    Map<Identifier, AbstractTexture> froghelper$getTextures();

    @Accessor("tickableTextures")
    Set<TickableTexture> froghelper$getTickableTextures();
}
