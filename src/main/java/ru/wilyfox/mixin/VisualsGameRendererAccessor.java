package ru.wilyfox.mixin;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(GameRenderer.class)
public interface VisualsGameRendererAccessor {
    @Invoker("shouldRenderBlockOutline") boolean froghelper$shouldRenderBlockOutline();
}
