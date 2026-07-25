package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.statistic.DailyBlocksStore;

public final class DailyBlocksWidget extends AbstractWidget {
    private final DailyBlocksStore store;

    public DailyBlocksWidget(int x, int y, HudLayer layer, DailyBlocksStore store) {
        super(x, y, layer);
        this.store = store;
    }

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        if (!isVisible()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        String text = getText();

        context.pose().pushPose();
        context.pose().translate(startX, startY, 0.0F);
        context.pose().scale(scale, scale, 1.0F);

        context.drawString(minecraft.font, text, 0, 0, WidgetTheme.TEXT_SOFT, true);

        context.pose().popPose();
    }

    @Override
    public int getWidth() {
        return Math.round(Minecraft.getInstance().font.width(getText()) * getScale());
    }

    @Override
    public int getHeight() {
        return Math.round(Minecraft.getInstance().font.lineHeight * getScale());
    }

    @Override
    public boolean isVisible() {
        return ConfigManager.get().dailyBlocks.active
                && (store.getSnapshot().available() || Minecraft.getInstance().screen instanceof HudEditingScreen);
    }

    @Override
    public String getDisplayName() {
        return "Daily Blocks";
    }

    private String getText() {
        return "Daily Blocks: " + store.getSnapshot().blocks();
    }
}
