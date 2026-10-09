package ru.wilyfox.client.hud.internal;

import net.minecraft.client.renderer.state.gui.GuiRenderState;

/** Access to the extractor's existing deferred state; no extra framebuffer or texture copies. */
public interface WidgetGuiState {
    GuiRenderState froghelper$guiState();
    interface Opacity {
        float froghelper$opacity(Object state);
        org.joml.Matrix3x2fc froghelper$transform(Object state);
    }
}
