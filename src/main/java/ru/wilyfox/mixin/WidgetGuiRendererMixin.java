package ru.wilyfox.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.*;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import ru.wilyfox.client.hud.internal.*;

/** Text and item vertices are generated later, outside the widget's extraction call. */
@Mixin(GuiRenderer.class)
public class WidgetGuiRendererMixin {
    @Shadow @Final private GuiRenderState renderState;
    @WrapMethod(method = "lambda$prepareText$0")
    private void froghelper$textOpacity(GuiTextRenderState state, Operation<Void> original) {
        float alpha = ((WidgetGuiState.Opacity) renderState).froghelper$opacity(state);
        var transform = ((WidgetGuiState.Opacity) renderState).froghelper$transform(state);
        if (alpha >= 1 && transform == null) { original.call(state); return; }
        try (var profile = ru.wilyfox.client.profiler.ModProfiler.getInstance().scope("hud/animation/deferredText");
             var scope = new WidgetRenderScope(null, alpha, transform)) { original.call(state); }
    }
    @WrapMethod(method = "submitBlitFromItemAtlas")
    private void froghelper$itemOpacity(GuiItemRenderState state, GuiItemAtlas.SlotView slot, Operation<Void> original) {
        float alpha = ((WidgetGuiState.Opacity) renderState).froghelper$opacity(state);
        var transform = ((WidgetGuiState.Opacity) renderState).froghelper$transform(state);
        if (alpha >= 1 && transform == null) { original.call(state, slot); return; }
        try (var profile = ru.wilyfox.client.profiler.ModProfiler.getInstance().scope("hud/animation/deferredItem");
             var scope = new WidgetRenderScope(null, alpha, transform)) { original.call(state, slot); }
    }
    @WrapMethod(method = "lambda$prepareItemElements$1")
    private void froghelper$oversizedOpacity(int scale, GuiItemRenderState state, Operation<Void> original) {
        float alpha = ((WidgetGuiState.Opacity) renderState).froghelper$opacity(state);
        var transform = ((WidgetGuiState.Opacity) renderState).froghelper$transform(state);
        if (alpha >= 1 && transform == null) { original.call(scale, state); return; }
        try (var profile = ru.wilyfox.client.profiler.ModProfiler.getInstance().scope("hud/animation/deferredOversizedItem");
             var scope = new WidgetRenderScope(null, alpha, transform)) { original.call(scale, state); }
    }
}
