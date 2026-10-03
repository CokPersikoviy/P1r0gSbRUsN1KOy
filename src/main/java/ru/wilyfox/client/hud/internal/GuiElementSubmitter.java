package ru.wilyfox.client.hud.internal;

import net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;

import java.util.function.Function;

/** Creates a GUI element with the extractor's current pose and scissor. */
public interface GuiElementSubmitter {
    void froghelper$submit(int x, int y, int width, int height,
                          Function<ColoredRectangleRenderState, GuiElementRenderState> factory);
}
