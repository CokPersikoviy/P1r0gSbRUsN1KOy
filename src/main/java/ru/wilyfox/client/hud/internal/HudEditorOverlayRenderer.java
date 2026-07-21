package ru.wilyfox.client.hud.internal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import ru.wilyfox.client.hud.indicators.CornerSnapIndicator;
import ru.wilyfox.client.hud.indicators.ScreenAnchor;
import ru.wilyfox.client.hud.widget.AbstractWidget;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.Widget;
import ru.wilyfox.client.hud.widget.WidgetCorner;
import ru.wilyfox.client.hud.widget.WidgetTheme;
import ru.wilyfox.client.hud.widget.WidgetUtils;

import java.util.ArrayList;
import java.util.List;

public final class HudEditorOverlayRenderer {
    private HudEditorOverlayRenderer() {
    }

    public static void renderHoveredWidgetOutline(HudEditorOverlayHost host, GuiGraphics context, Widget hoveredWidget) {
        int padding = 3;
        HudBounds bounds = Screen.hasAltDown() && hoveredWidget instanceof AbstractWidget abstractWidget
                ? getAltOutlineBounds(host, abstractWidget)
                : new HudBounds(hoveredWidget.getStartX(), hoveredWidget.getStartY(), hoveredWidget.getWidth(), hoveredWidget.getHeight());

        WidgetUtils.drawCorners(
                context,
                bounds.x() - padding,
                bounds.y() - padding,
                bounds.width() + padding * 2,
                bounds.height() + padding * 2,
                WidgetTheme.OUTLINE_ACTIVE
        );
    }

    public static void renderScreenAnchors(HudEditorOverlayHost host, GuiGraphics context, int screenWidth, int screenHeight, int screenSnapMargin) {
        boolean showCenterAnchors = Screen.hasShiftDown();

        renderSingleScreenAnchor(host, context, ScreenAnchor.TOP_LEFT, screenSnapMargin, screenSnapMargin);
        renderSingleScreenAnchor(host, context, ScreenAnchor.TOP_RIGHT, screenWidth - screenSnapMargin, screenSnapMargin);
        renderSingleScreenAnchor(host, context, ScreenAnchor.BOTTOM_LEFT, screenSnapMargin, screenHeight - screenSnapMargin);
        renderSingleScreenAnchor(host, context, ScreenAnchor.BOTTOM_RIGHT, screenWidth - screenSnapMargin, screenHeight - screenSnapMargin);

        if (showCenterAnchors) {
            renderSingleScreenAnchor(host, context, ScreenAnchor.TOP_CENTER, screenWidth / 2, screenSnapMargin);
            renderSingleScreenAnchor(host, context, ScreenAnchor.LEFT_CENTER, screenSnapMargin, screenHeight / 2);
            renderSingleScreenAnchor(host, context, ScreenAnchor.RIGHT_CENTER, screenWidth - screenSnapMargin, screenHeight / 2);
        }

        renderSingleScreenAnchor(host, context, ScreenAnchor.HOTBAR_LEFT, host.getHotbarLeftAnchorX(screenWidth), host.getHotbarAnchorY(screenHeight));
        renderSingleScreenAnchor(host, context, ScreenAnchor.HOTBAR_RIGHT, host.getHotbarRightAnchorX(screenWidth), host.getHotbarAnchorY(screenHeight));
    }

    public static void renderWidgetSnapIndicators(HudEditorOverlayHost host, GuiGraphics context) {
        CornerSnapIndicator activeDraggedCornerIndicator = host.getActiveDraggedCornerIndicator();
        CornerSnapIndicator activeTargetCornerIndicator = host.getActiveTargetCornerIndicator();
        if (activeDraggedCornerIndicator == null || activeTargetCornerIndicator == null) {
            return;
        }

        int padding = 3;

        if (activeDraggedCornerIndicator.getCorner() == WidgetCorner.TOP_RIGHT) {
            WidgetUtils.drawBottomLeftCorner(
                    context,
                    activeTargetCornerIndicator.getWidget().getStartX() - padding,
                    activeTargetCornerIndicator.getWidget().getStartY() - padding,
                    activeTargetCornerIndicator.getWidget().getWidth() + padding * 2,
                    activeTargetCornerIndicator.getWidget().getHeight() + padding * 2,
                    WidgetTheme.OUTLINE_ACTIVE
            );
        }

        if (activeDraggedCornerIndicator.getCorner() == WidgetCorner.TOP_LEFT) {
            WidgetUtils.drawBottomRightCorner(
                    context,
                    activeTargetCornerIndicator.getWidget().getStartX() - padding,
                    activeTargetCornerIndicator.getWidget().getStartY() - padding,
                    activeTargetCornerIndicator.getWidget().getWidth() + padding * 2,
                    activeTargetCornerIndicator.getWidget().getHeight() + padding * 2,
                    WidgetTheme.OUTLINE_ACTIVE
            );
        }

        if (activeDraggedCornerIndicator.getCorner() == WidgetCorner.BOTTOM_RIGHT) {
            WidgetUtils.drawTopLeftCorner(
                    context,
                    activeTargetCornerIndicator.getWidget().getStartX() - padding,
                    activeTargetCornerIndicator.getWidget().getStartY() - padding,
                    activeTargetCornerIndicator.getWidget().getWidth() + padding * 2,
                    activeTargetCornerIndicator.getWidget().getHeight() + padding * 2,
                    WidgetTheme.OUTLINE_ACTIVE
            );
        }

        if (activeDraggedCornerIndicator.getCorner() == WidgetCorner.BOTTOM_LEFT) {
            WidgetUtils.drawTopRightCorner(
                    context,
                    activeTargetCornerIndicator.getWidget().getStartX() - padding,
                    activeTargetCornerIndicator.getWidget().getStartY() - padding,
                    activeTargetCornerIndicator.getWidget().getWidth() + padding * 2,
                    activeTargetCornerIndicator.getWidget().getHeight() + padding * 2,
                    WidgetTheme.OUTLINE_ACTIVE
            );
        }
    }

    public static void renderGroupTooltip(HudEditorOverlayHost host, GuiGraphics context, Widget hovered) {
        if (!(hovered instanceof AbstractWidget abstractWidget)) {
            return;
        }

        int groupSize = host.isDraggingWidgetGroup() && host.getDraggedGroupWidgets().contains(abstractWidget)
                ? host.getDraggedGroupWidgets().size()
                : host.getSnappedDescendants(abstractWidget).size() + 1;
        if (groupSize <= 1) {
            return;
        }

        float labelScale = 0.95f;
        String text = "Group: " + groupSize;

        int paddingX = Math.round(6 * labelScale);
        int baseHeight = Minecraft.getInstance().font.lineHeight + 4;
        int height = Math.round(baseHeight * labelScale);
        int textWidth = Math.round(Minecraft.getInstance().font.width(text) * labelScale);
        int width = textWidth + paddingX * 2;

        int x = hovered.getStartX() + (hovered.getWidth() - width) / 2;
        int y = hovered.getStartY() - height - 4;

        if (y < 2) {
            y = hovered.getStartY() + hovered.getHeight() + 4;
        }

        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        if (x + width > screenWidth - 2) {
            x = screenWidth - width - 2;
        }
        if (x < 2) {
            x = 2;
        }

        HudSurface.fillRounded(context, x, y, width, height, 3, WidgetTheme.TOOLTIP_BG);
        context.fill(x + 3, y, x + width - 3, y + 1, WidgetTheme.ACCENT_LINE);

        context.pose().pushPose();
        context.pose().translate(x + paddingX, y + (height - Minecraft.getInstance().font.lineHeight * labelScale) / 2.0f, 0);
        context.pose().scale(labelScale, labelScale, 1.0f);
        context.drawString(Minecraft.getInstance().font, text, 0, 0, WidgetTheme.TOOLTIP_TEXT);
        context.pose().popPose();
    }

    public static void renderScaleTooltip(GuiGraphics context, Widget hovered) {
        if (!(hovered instanceof AbstractWidget scalableWidget)) {
            return;
        }

        float labelScale = 0.85f + (scalableWidget.getScale() - 1.0f) * 0.35f;
        labelScale = Math.max(0.7f, Math.min(1.1f, labelScale));
        String text = hovered.getDisplayName() + " x" + String.format("%.2f", scalableWidget.getScale());

        int paddingX = Math.round(6 * labelScale);
        int baseHeight = Minecraft.getInstance().font.lineHeight + 4;
        int height = Math.round(baseHeight * labelScale);
        int textWidth = Math.round(Minecraft.getInstance().font.width(text) * labelScale);
        int width = textWidth + paddingX * 2;

        int x = hovered.getStartX() + (hovered.getWidth() - width) / 2;
        int y = hovered.getStartY() - height - 4;

        if (y < 2) {
            y = hovered.getStartY() + hovered.getHeight() + 4;
        }

        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        if (x + width > screenWidth - 2) {
            x = screenWidth - width - 2;
        }
        if (x < 2) {
            x = 2;
        }

        HudSurface.fillRounded(context, x, y, width, height, 3, WidgetTheme.TOOLTIP_BG);
        context.fill(x + 3, y, x + width - 3, y + 1, WidgetTheme.ACCENT_LINE);

        context.pose().pushPose();
        context.pose().translate(x + paddingX, y + (height - Minecraft.getInstance().font.lineHeight * labelScale) / 2.0f, 0);
        context.pose().scale(labelScale, labelScale, 1.0f);
        context.drawString(Minecraft.getInstance().font, text, 0, 0, WidgetTheme.TOOLTIP_TEXT);
        context.pose().popPose();
    }

    private static HudBounds getAltOutlineBounds(HudEditorOverlayHost host, AbstractWidget hoveredWidget) {
        List<AbstractWidget> groupWidgets;
        if (host.isDraggingWidgetGroup() && host.getDraggedWidget() instanceof AbstractWidget) {
            groupWidgets = new ArrayList<>(host.getDraggedGroupWidgets());
        } else {
            groupWidgets = new ArrayList<>();
            groupWidgets.add(hoveredWidget);
            groupWidgets.addAll(host.getSnappedDescendants(hoveredWidget));
        }

        return getBoundsForWidgets(groupWidgets);
    }

    private static HudBounds getBoundsForWidgets(List<AbstractWidget> groupWidgets) {
        if (groupWidgets.isEmpty()) {
            return new HudBounds(0, 0, 0, 0);
        }

        AbstractWidget first = groupWidgets.get(0);
        int minX = first.getStartX();
        int minY = first.getStartY();
        int maxX = first.getRight();
        int maxY = first.getBottom();

        for (AbstractWidget widget : groupWidgets) {
            minX = Math.min(minX, widget.getStartX());
            minY = Math.min(minY, widget.getStartY());
            maxX = Math.max(maxX, widget.getRight());
            maxY = Math.max(maxY, widget.getBottom());
        }

        return new HudBounds(minX, minY, maxX - minX, maxY - minY);
    }

    private static void renderSingleScreenAnchor(HudEditorOverlayHost host, GuiGraphics context, ScreenAnchor anchor, int x, int y) {
        if (host.isScreenAnchorOccupied(anchor, null) || host.isScreenAnchorCovered(x, y)) {
            return;
        }

        int color = host.getActiveScreenAnchor() == anchor ? WidgetTheme.OUTLINE_ACTIVE : WidgetTheme.OUTLINE_SOFT;
        int size = host.isCenterSideAnchor(anchor) ? getPulsingAnchorSize() : 10;
        WidgetUtils.drawAnchorPoint(context, x, y, color, size);
    }

    private static int getPulsingAnchorSize() {
        double time = System.currentTimeMillis() / 180.0d;
        return 10 + (int) Math.round((Math.sin(time) + 1.0d) * 1.5d);
    }
}
