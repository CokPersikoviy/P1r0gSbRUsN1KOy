package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import ru.wilyfox.client.hud.layer.HudLayer;

public class PlainTextWidget extends AbstractWidget {
    private final String text;

    public PlainTextWidget(int x, int y, HudLayer layer, String text) {
        super(x, y, layer);
        this.text = text;
    }

    @Override
    public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        if (!isVisible()) {
            return;
        }

        context.pose().pushMatrix();
        context.pose().translate(startX, startY);
        context.pose().scale(scale, scale);

        context.text(Minecraft.getInstance().font, text, 0, 0, WidgetTheme.TEXT_SOFT);

        context.pose().popMatrix();
    }

    @Override
    public int getWidth() {
        return Math.round(Minecraft.getInstance().font.width(text) * getScale());
    }

    @Override
    public int getHeight() {
        return Math.round(Minecraft.getInstance().font.lineHeight * getScale());
    }

    @Override
    public String getDisplayName() {
        return "Text";
    }
}
