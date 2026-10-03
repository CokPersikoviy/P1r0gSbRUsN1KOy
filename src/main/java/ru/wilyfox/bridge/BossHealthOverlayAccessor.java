package ru.wilyfox.bridge;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LerpingBossEvent;

import java.util.List;

public interface BossHealthOverlayAccessor {
    void froghelper$renderAt(GuiGraphicsExtractor context, int x, int y);
    int froghelper$getRenderedHeight();
    int froghelper$getRenderedWidth();
    List<LerpingBossEvent> froghelper$getEvents();
}
