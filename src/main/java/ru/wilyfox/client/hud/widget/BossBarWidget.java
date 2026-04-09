package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import ru.wilyfox.bridge.BossHealthOverlayAccessor;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;

public class BossBarWidget extends AbstractWidget {
    private static final int EMPTY_WIDTH = 182;
    private static final int EMPTY_HEIGHT = 12;
    private boolean inited = false;

    public BossBarWidget(int x, int y, HudLayer layer) {
        super(x, y, layer);
    }

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        if (!isVisible()) {
            return;
        }

        BossHealthOverlay overlay = Minecraft.getInstance().gui.getBossOverlay();
        if (overlay instanceof BossHealthOverlayAccessor accessor) {
            if (accessor.froghelper$getRenderedWidth() <= 0 || accessor.froghelper$getRenderedHeight() <= 0) {
                if (isEditorPreview()) {
                    renderPlaceholder(context);
                }
            } else {
                context.pose().pushPose();
                context.pose().translate(startX, startY, 0);
                context.pose().scale(getScale(), getScale(), 1.0f);

                accessor.froghelper$renderAt(context, 0, 0);

                context.pose().popPose();
            }
        }

        if (!inited) {
            initialize();
        }
    }

    public void initialize() {
        if (getConfigKey() != null && ConfigManager.getWidgetLayout(getConfigKey()) != null) {
            inited = true;
            return;
        }

        setStartX(Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2 - 91);
        setStartY(3);

        inited = true;
    }

    @Override
    public String getDisplayName() {
        return "Boss Bar";
    }

    @Override
    public boolean isVisible() {
        return ConfigManager.get().bossBar.active && (hasRenderedBossBar() || isEditorPreview());
    }

    @Override
    public int getWidth() {
        BossHealthOverlay overlay = Minecraft.getInstance().gui.getBossOverlay();
        if (overlay instanceof BossHealthOverlayAccessor accessor) {
            int width = accessor.froghelper$getRenderedWidth();
            if (width > 0) {
                return Math.round(width * getScale());
            }
        }

        return Math.round(EMPTY_WIDTH * getScale());
    }

    @Override
    public int getHeight() {
        BossHealthOverlay overlay = Minecraft.getInstance().gui.getBossOverlay();
        if (overlay instanceof BossHealthOverlayAccessor accessor) {
            int height = accessor.froghelper$getRenderedHeight();
            if (height > 0) {
                return Math.round(height * getScale());
            }
        }

        return Math.round(EMPTY_HEIGHT * getScale());
    }

    private boolean isEditorPreview() {
        return Minecraft.getInstance().screen instanceof HudEditingScreen;
    }

    private boolean hasRenderedBossBar() {
        BossHealthOverlay overlay = Minecraft.getInstance().gui.getBossOverlay();
        if (overlay instanceof BossHealthOverlayAccessor accessor) {
            return accessor.froghelper$getRenderedWidth() > 0 && accessor.froghelper$getRenderedHeight() > 0;
        }

        return false;
    }

    private void renderPlaceholder(GuiGraphics context) {
        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(getScale(), getScale(), 1.0f);

        context.fill(0, 0, EMPTY_WIDTH, EMPTY_HEIGHT, WidgetTheme.WIDGET_PANEL_BG_SOFT);
        context.fill(0, 0, EMPTY_WIDTH, 1, WidgetTheme.WIDGET_ACCENT_LINE);
        context.drawString(Minecraft.getInstance().font, "Boss Bar", 6, 2, WidgetTheme.TITLE);

        context.pose().popPose();
    }
}

