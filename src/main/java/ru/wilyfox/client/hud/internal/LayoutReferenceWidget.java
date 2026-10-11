package ru.wilyfox.client.hud.internal;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.hud.widget.AbstractWidget;

/** Geometry only: resolves Main's snap graph without moving or rendering shared widget instances. */
public final class LayoutReferenceWidget extends AbstractWidget {
    public LayoutReferenceWidget(String key) { super(0, 0, HudLayer.CONTENT); setConfigKey(key); }

    public void measure(AbstractWidget source, float mainScale) {
        float previous = source.getScale();
        try {
            source.setScale(mainScale);
            width = source.getWidth(); height = source.getHeight();
        } finally { source.setScale(previous); }
    }

    @Override public void render(GuiGraphicsExtractor graphics, DeltaTracker delta) { }
}
