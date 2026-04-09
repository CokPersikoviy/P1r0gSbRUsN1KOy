package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.visibility.VisibilityStatusStore;

public class VisibilityStatusWidget extends AbstractWidget {
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 5;
    private static final int ROW_GAP = 2;
    private static final int SEGMENT_GAP = 6;
    private static final int EMPTY_WIDTH = 122;
    private static final int EMPTY_HEIGHT = 28;

    private final VisibilityStatusStore store;

    public VisibilityStatusWidget(int x, int y, HudLayer layer, VisibilityStatusStore store) {
        super(x, y, layer);
        this.store = store;
    }

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        if (!isVisible()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        int width = getUnscaledWidth(mc);
        int height = getUnscaledHeight(mc);

        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        context.fill(0, 0, width, height, WidgetTheme.WIDGET_PANEL_BG);
        context.fill(0, 0, width, 1, WidgetTheme.WIDGET_ACCENT_LINE);

        if (ConfigManager.get().visibilityStatus.compact) {
            renderCompact(context, mc);
        } else {
            renderDetailed(context, mc);
        }

        context.pose().popPose();
    }

    @Override
    public int getWidth() {
        return Math.round(getUnscaledWidth(Minecraft.getInstance()) * getScale());
    }

    @Override
    public int getHeight() {
        return Math.round(getUnscaledHeight(Minecraft.getInstance()) * getScale());
    }

    @Override
    public boolean isVisible() {
        return ConfigManager.get().visibilityStatus.active || isEditorPreview();
    }

    @Override
    public String getDisplayName() {
        return "Visibility Status";
    }

    private int getUnscaledWidth(Minecraft mc) {
        if (mc == null) {
            return EMPTY_WIDTH;
        }

        if (ConfigManager.get().visibilityStatus.compact) {
            return mc.font.width(compactText()) + PADDING_X * 2;
        }

        int max = mc.font.width("Clan [" + statusText(store.isClanVisible()) + "]");
        max = Math.max(max, mc.font.width("Players [" + statusText(store.isPlayersVisible()) + "]"));
        max = Math.max(max, mc.font.width("Pets [" + store.getPetsVisibility().displayName() + "]"));
        return max + PADDING_X * 2;
    }

    private int getUnscaledHeight(Minecraft mc) {
        if (mc == null) {
            return EMPTY_HEIGHT;
        }

        if (ConfigManager.get().visibilityStatus.compact) {
            return PADDING_Y * 2 + mc.font.lineHeight;
        }

        return PADDING_Y * 2 + mc.font.lineHeight * 3 + ROW_GAP * 2;
    }

    private boolean isEditorPreview() {
        return Minecraft.getInstance().screen instanceof HudEditingScreen;
    }

    private String statusText(boolean visible) {
        return visible ? "Visible" : "Hidden";
    }

    private String compactStatus(boolean visible) {
        return visible ? "On" : "Off";
    }

    private int statusColor(boolean visible) {
        return visible ? WidgetTheme.STATUS_SUCCESS : WidgetTheme.STATUS_WARNING;
    }

    private int petsStatusColor(VisibilityStatusStore.PetsVisibility visibility) {
        return switch (visibility) {
            case ENABLED -> WidgetTheme.STATUS_SUCCESS;
            case ONLY_OWN -> WidgetTheme.STATUS_INFO;
            case DISABLED -> WidgetTheme.STATUS_WARNING;
        };
    }

    private void renderDetailed(GuiGraphics context, Minecraft mc) {
        int y = PADDING_Y;
        context.drawString(mc.font, "Clan [" + statusText(store.isClanVisible()) + "]", PADDING_X, y, statusColor(store.isClanVisible()));
        y += mc.font.lineHeight + ROW_GAP;
        context.drawString(mc.font, "Players [" + statusText(store.isPlayersVisible()) + "]", PADDING_X, y, statusColor(store.isPlayersVisible()));
        y += mc.font.lineHeight + ROW_GAP;
        context.drawString(mc.font, "Pets [" + store.getPetsVisibility().displayName() + "]", PADDING_X, y, petsStatusColor(store.getPetsVisibility()));
    }

    private void renderCompact(GuiGraphics context, Minecraft mc) {
        int x = PADDING_X;
        x = drawCompactSegment(context, mc, x, "Cl [" + compactStatus(store.isClanVisible()) + "]", statusColor(store.isClanVisible()));
        x += SEGMENT_GAP;
        x = drawCompactSegment(context, mc, x, "Pl [" + compactStatus(store.isPlayersVisible()) + "]", statusColor(store.isPlayersVisible()));
        x += SEGMENT_GAP;
        drawCompactSegment(context, mc, x, "Pt [" + store.getPetsVisibility().compactName() + "]", petsStatusColor(store.getPetsVisibility()));
    }

    private int drawCompactSegment(GuiGraphics context, Minecraft mc, int x, String text, int color) {
        context.drawString(mc.font, text, x, PADDING_Y, color);
        return x + mc.font.width(text);
    }

    private String compactText() {
        return "Cl [" + compactStatus(store.isClanVisible()) + "]"
                + "  Pl [" + compactStatus(store.isPlayersVisible()) + "]"
                + "  Pt [" + store.getPetsVisibility().compactName() + "]";
    }
}

