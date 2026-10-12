package ru.wilyfox.client.hud.internal;

import ru.wilyfox.client.hud.indicators.CornerSnapIndicator;
import ru.wilyfox.client.hud.indicators.ScreenAnchor;
import ru.wilyfox.client.hud.widget.Widget;
import ru.wilyfox.client.hud.widget.AbstractWidget;
import ru.wilyfox.client.hud.config.WidgetLayoutConfig;

public interface HudSnapLayoutHost {
    default WidgetLayoutConfig getStoredPlacement(AbstractWidget widget) { return null; }
    default boolean isSnapTargetVisible(Widget widget) { return widget.isVisible(); }
    default boolean isPlacementReadOnly(Widget widget) { return false; }
    default boolean isMoving(Widget widget) { return widget == getDraggedWidget(); }

    Widget getDraggedWidget();

    void setActiveScreenAnchor(ScreenAnchor anchor);

    void setActiveDraggedCornerIndicator(CornerSnapIndicator indicator);

    void setActiveTargetCornerIndicator(CornerSnapIndicator indicator);

    int getLastScreenWidth();

    int getLastScreenHeight();

    void setLastScreenWidth(int width);

    void setLastScreenHeight(int height);

    boolean isScreenAnchorOccupied(ScreenAnchor anchor, Widget ignoredWidget);

    boolean isCenterSideAnchor(ScreenAnchor anchor);
}
