package ru.wilyfox.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Set;

@Mixin(LevelRenderer.class)
public interface LevelRendererAccessorMixin {
    @Accessor("globalBlockEntities")
    Set<BlockEntity> froghelper$getGlobalBlockEntities();

    @Accessor("visibleEntities")
    List<Entity> froghelper$getVisibleEntities();

    @Accessor("visibleEntityCount")
    int froghelper$getVisibleEntityCount();
}
