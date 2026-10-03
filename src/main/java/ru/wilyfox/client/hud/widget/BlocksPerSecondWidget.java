package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.utility.BlockBreakCounter;

public class BlocksPerSecondWidget extends AbstractWidget {
    public BlocksPerSecondWidget(int x, int y, HudLayer layer) {
        super(x, y, layer);
    }

    @Override
    public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        if (!ConfigManager.get().blocksPerSecondWidget.active) {
            return;
        }

        String text = "B/s: " + BlockBreakCounter.getBreakPerSecond();

        context.pose().pushMatrix();
        context.pose().translate(startX, startY);
        context.pose().scale(scale, scale);

        context.text(Minecraft.getInstance().font, text, 0, 0, WidgetTheme.TEXT_SOFT);

        context.pose().popMatrix();
    }

    @Override
    public boolean isHovered(double mouseX, double mouseY) {
        return super.isHovered(mouseX, mouseY);
    }

    @Override
    public int getLeft() {
        return super.getLeft();
    }

    @Override
    public int getRight() {
        return super.getRight();
    }

    @Override
    public int getTop() {
        return super.getTop();
    }

    @Override
    public int getBottom() {
        return super.getBottom();
    }

    @Override
    public int getCenterX() {
        return super.getCenterX();
    }

    @Override
    public int getCenterY() {
        return super.getCenterY();
    }

    @Override
    public int getCornerX(WidgetCorner corner) {
        return super.getCornerX(corner);
    }

    @Override
    public int getCornerY(WidgetCorner corner) {
        return super.getCornerY(corner);
    }

    @Override
    public int getWidth() {
        return Math.round(Minecraft.getInstance().font.width("B/s: " + BlockBreakCounter.getBreakPerSecond()) * getScale());
    }

    @Override
    public int getHeight() {
        return Math.round(Minecraft.getInstance().font.lineHeight * getScale());
    }

    @Override
    public boolean isVisible() {
        return ConfigManager.get().blocksPerSecondWidget.active;
    }

    @Override
    public String getDisplayName() {
        return "Blocks/s";
    }
}
