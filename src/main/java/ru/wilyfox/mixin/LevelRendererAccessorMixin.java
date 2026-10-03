package ru.wilyfox.mixin;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(LevelRenderer.class)
public interface LevelRendererAccessorMixin {
    @Accessor("levelRenderState") LevelRenderState froghelper$getLevelRenderState();
}
