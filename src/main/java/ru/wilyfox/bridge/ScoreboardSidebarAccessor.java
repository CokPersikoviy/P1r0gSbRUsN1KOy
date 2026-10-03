package ru.wilyfox.bridge;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public interface ScoreboardSidebarAccessor {
    void froghelper$renderAt(GuiGraphicsExtractor context, int x, int y);
    int froghelper$getRenderedWidth();
    int froghelper$getRenderedHeight();
    int froghelper$getDefaultX();
    int froghelper$getDefaultY();
}
