package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.bridge.ScoreboardSidebarAccessor;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;

public class ScoreboardWidget extends AbstractWidget {
    private static final int EMPTY_WIDTH = 120;
    private static final int EMPTY_HEIGHT = 80;
    private boolean inited = false;

    public ScoreboardWidget(int x, int y, HudLayer layer) {
        super(x, y, layer);
    }

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        if (!isVisible()) {
            return;
        }

        Gui gui = Minecraft.getInstance().gui;
        if (gui instanceof ScoreboardSidebarAccessor accessor) {
            if (!inited) {
                initialize(accessor);
            }

            if (accessor.froghelper$getRenderedWidth() <= 0 || accessor.froghelper$getRenderedHeight() <= 0) {
                if (!isEditorPreview()) {
                    return;
                }

                renderPlaceholder(context);
                return;
            }

            context.pose().pushPose();
            context.pose().translate(startX, startY, 0);
            context.pose().scale(getScale(), getScale(), 1.0f);

            accessor.froghelper$renderAt(context, 0, 0);

            context.pose().popPose();
        }
    }

    private void initialize(ScoreboardSidebarAccessor accessor) {
        if (getConfigKey() != null && ConfigManager.getWidgetLayout(getConfigKey()) != null) {
            inited = true;
            return;
        }

        setStartX(accessor.froghelper$getDefaultX());
        setStartY(accessor.froghelper$getDefaultY());
        inited = true;
    }

    @Override
    public boolean isVisible() {
        return ConfigManager.get().scoreboard.active && (hasRenderedScoreboard() || isEditorPreview());
    }

    @Override
    public int getWidth() {
        Gui gui = Minecraft.getInstance().gui;
        if (gui instanceof ScoreboardSidebarAccessor accessor) {
            int width = accessor.froghelper$getRenderedWidth();
            if (width > 0) {
                return Math.round(width * getScale());
            }
        }

        return Math.round(EMPTY_WIDTH * getScale());
    }

    @Override
    public int getHeight() {
        Gui gui = Minecraft.getInstance().gui;
        if (gui instanceof ScoreboardSidebarAccessor accessor) {
            int height = accessor.froghelper$getRenderedHeight();
            if (height > 0) {
                return Math.round(height * getScale());
            }
        }

        return Math.round(EMPTY_HEIGHT * getScale());
    }

    @Override
    public String getDisplayName() {
        return "Scoreboard";
    }

    private boolean isEditorPreview() {
        return Minecraft.getInstance().screen instanceof HudEditingScreen;
    }

    private boolean hasRenderedScoreboard() {
        Gui gui = Minecraft.getInstance().gui;
        if (gui instanceof ScoreboardSidebarAccessor accessor) {
            return accessor.froghelper$getRenderedWidth() > 0 && accessor.froghelper$getRenderedHeight() > 0;
        }

        return false;
    }

    private void renderPlaceholder(GuiGraphics context) {
        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(getScale(), getScale(), 1.0f);

        HudSurface.drawPlaceholderPanel(context, EMPTY_WIDTH, EMPTY_HEIGHT);
        context.drawString(Minecraft.getInstance().font, "Scoreboard", 6, 6, WidgetTheme.TITLE);
        context.drawString(Minecraft.getInstance().font, "Sidebar hidden", 6, 16, WidgetTheme.TEXT_MUTED);

        context.pose().popPose();
    }
}

