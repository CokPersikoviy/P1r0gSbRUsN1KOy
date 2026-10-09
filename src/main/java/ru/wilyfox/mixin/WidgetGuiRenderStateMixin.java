package ru.wilyfox.mixin;

import net.minecraft.client.renderer.state.gui.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.hud.internal.*;
import java.util.IdentityHashMap;

@Mixin(GuiRenderState.class)
public class WidgetGuiRenderStateMixin implements WidgetGuiState.Opacity {
    @Unique private IdentityHashMap<Object, Float> froghelper$opacities;
    @Unique private IdentityHashMap<Object, org.joml.Matrix3x2fc> froghelper$transforms;
    @Unique private void froghelper$remember(Object state) {
        WidgetRenderScope.capture(state);
        float alpha = WidgetRenderScope.alpha();
        if (alpha < 1) {
            if (froghelper$opacities == null) froghelper$opacities = new IdentityHashMap<>();
            froghelper$opacities.put(state, alpha);
        } else if (froghelper$opacities != null) froghelper$opacities.remove(state);
        var transform = WidgetRenderScope.transform();
        if (transform != null) {
            if (froghelper$transforms == null) froghelper$transforms = new IdentityHashMap<>();
            froghelper$transforms.put(state, transform);
        } else if (froghelper$transforms != null) froghelper$transforms.remove(state);
    }
    @Override public float froghelper$opacity(Object state) {
        return froghelper$opacities == null ? 1 : froghelper$opacities.getOrDefault(state, 1f);
    }
    @Override public org.joml.Matrix3x2fc froghelper$transform(Object state) {
        return froghelper$transforms == null ? null : froghelper$transforms.get(state);
    }
    @Inject(method = "addText", at = @At("HEAD"))
    private void froghelper$text(GuiTextRenderState state, CallbackInfo ci) { froghelper$remember(state); }
    @Inject(method = "addItem", at = @At("HEAD"))
    private void froghelper$item(GuiItemRenderState state, CallbackInfo ci) { froghelper$remember(state); }
    @ModifyVariable(method = "addGuiElement", at = @At("HEAD"), argsOnly = true)
    private GuiElementRenderState froghelper$element(GuiElementRenderState state) {
        WidgetRenderScope.capture(state);
        return TransformedGuiElement.apply(FadedGuiElement.apply(state, WidgetRenderScope.alpha()), WidgetRenderScope.transform());
    }
    @ModifyVariable(method = "addGlyphToCurrentLayer", at = @At("HEAD"), argsOnly = true)
    private GuiElementRenderState froghelper$glyph(GuiElementRenderState state) {
        return TransformedGuiElement.apply(FadedGuiElement.apply(state, WidgetRenderScope.alpha()), WidgetRenderScope.transform());
    }
    @ModifyVariable(method = "addBlitToCurrentLayer", at = @At("HEAD"), argsOnly = true)
    private BlitRenderState froghelper$blit(BlitRenderState state) {
        return TransformedGuiElement.apply(FadedGuiElement.apply(state, WidgetRenderScope.alpha()), WidgetRenderScope.transform());
    }
    @Inject(method = "reset", at = @At("RETURN"))
    private void froghelper$clear(CallbackInfo ci) {
        if (froghelper$opacities != null) froghelper$opacities.clear();
        if (froghelper$transforms != null) froghelper$transforms.clear();
    }
}
