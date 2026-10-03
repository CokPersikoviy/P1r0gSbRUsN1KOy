package ru.wilyfox.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import ru.wilyfox.client.hud.internal.GuiElementSubmitter;

import java.util.function.Function;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin implements GuiElementSubmitter {
    @Unique
    private Function<ColoredRectangleRenderState, GuiElementRenderState> froghelper$pendingElementFactory;

    @Shadow
    public abstract void fill(int x0, int y0, int x1, int y1, int color);

    @Override
    public void froghelper$submit(int x, int y, int width, int height,
                                 Function<ColoredRectangleRenderState, GuiElementRenderState> factory) {
        froghelper$pendingElementFactory = factory;
        try {
            // Vanilla captures its private scissor and a copy of the pose in this rectangle.
            // Replace it before submission; the sentinel itself never reaches the render state.
            fill(x, y, x + width, y + height, 0);
        } finally {
            froghelper$pendingElementFactory = null;
        }
    }

    @ModifyArg(method = "innerFill", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;addGuiElement(Lnet/minecraft/client/renderer/state/gui/GuiElementRenderState;)V"), index = 0)
    private GuiElementRenderState froghelper$submitCustomElement(GuiElementRenderState vanilla) {
        var factory = froghelper$pendingElementFactory;
        if (factory == null) {
            return vanilla;
        }
        froghelper$pendingElementFactory = null;
        return factory.apply((ColoredRectangleRenderState) vanilla);
    }
}
