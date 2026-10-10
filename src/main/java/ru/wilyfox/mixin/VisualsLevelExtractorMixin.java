package ru.wilyfox.mixin;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.visuals.VisualBlockOutline;
@Mixin(LevelExtractor.class)
public abstract class VisualsLevelExtractorMixin {
    @Inject(method = "extractBlockOutline", at = @At("TAIL"))
    private void froghelper$outline(Camera camera, LevelRenderState state, CallbackInfo ci) {
        if (!ConfigManager.get().visuals.advancedOutline()) { VisualBlockOutline.reset(); return; }
        var outline = state.blockOutlineRenderState; state.blockOutlineRenderState = null;
        VisualBlockOutline.extract(outline, ((VisualsGameRendererAccessor) Minecraft.getInstance().gameRenderer).froghelper$shouldRenderBlockOutline(), camera);
    }
}
