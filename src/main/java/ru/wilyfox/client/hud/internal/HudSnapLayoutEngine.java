package ru.wilyfox.client.hud.internal;

import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.WidgetLayoutConfig;
import ru.wilyfox.client.hud.indicators.ScreenAnchor;
import ru.wilyfox.client.hud.widget.AbstractWidget;
import ru.wilyfox.client.hud.widget.Widget;
import ru.wilyfox.client.hud.widget.WidgetCorner;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public final class HudSnapLayoutEngine {
    private final List<Widget> widgets;
    private final int screenSnapMargin;
    private final int groupGap;
    private final int hotbarWidth;
    private final int hotbarHeight;
    private final int hotbarAnchorGap;
    private final int offhandSlotWidth;

    public HudSnapLayoutEngine(
            List<Widget> widgets,
            int screenSnapMargin,
            int screenSnapDistance,
            int widgetSnapDistance,
            int groupGap,
            int hotbarWidth,
            int hotbarHeight,
            int hotbarAnchorGap,
            int offhandSlotWidth
    ) {
        this.widgets = widgets;
        this.screenSnapMargin = screenSnapMargin;
        this.groupGap = groupGap;
        this.hotbarWidth = hotbarWidth;
        this.hotbarHeight = hotbarHeight;
        this.hotbarAnchorGap = hotbarAnchorGap;
        this.offhandSlotWidth = offhandSlotWidth;
    }

    public void handleScreenResize(HudSnapLayoutHost host, int screenWidth, int screenHeight) {
        if (host.getLastScreenWidth() <= 0 || host.getLastScreenHeight() <= 0) {
            Integer storedWidth = ConfigManager.getLastWindowWidth();
            Integer storedHeight = ConfigManager.getLastWindowHeight();

            if (storedWidth != null && storedHeight != null && storedWidth > 0 && storedHeight > 0) {
                host.setLastScreenWidth(storedWidth);
                host.setLastScreenHeight(storedHeight);
                repositionAllWidgets(host, screenWidth, screenHeight);
            } else {
                clampWidgetsToScreen(screenWidth, screenHeight);
            }

            ConfigManager.saveWindowSize(screenWidth, screenHeight);
            host.setLastScreenWidth(screenWidth);
            host.setLastScreenHeight(screenHeight);
            return;
        }

        if (host.getLastScreenWidth() == screenWidth && host.getLastScreenHeight() == screenHeight) {
            return;
        }

        repositionAllWidgets(host, screenWidth, screenHeight);

        ConfigManager.saveWindowSize(screenWidth, screenHeight);
        host.setLastScreenWidth(screenWidth);
        host.setLastScreenHeight(screenHeight);
    }

    public void updateAnchoredWidgets(HudSnapLayoutHost host, int screenWidth, int screenHeight) {
        for (Widget widget : widgets) {
            if (host.isMoving(widget)) {
                continue;
            }

            if (widget instanceof AbstractWidget abstractWidget && abstractWidget.getScreenAnchor() != null) {
                applyStoredScreenAnchor(abstractWidget, abstractWidget.getScreenAnchor(), screenWidth, screenHeight);
            }
        }
    }

    public void updateSnappedWidgets(HudSnapLayoutHost host) {
        updateSnappedWidgets(host, host.getLastScreenWidth(), host.getLastScreenHeight());
    }

    public void updateSnappedWidgets(HudSnapLayoutHost host, int screenWidth, int screenHeight) {
        Map<String, AbstractWidget> widgetByKey = getAbstractWidgetMap();
        Set<String> visiting = new HashSet<>();

        // Boundary correction is display-only. Rebuild free roots from their saved placement so
        // a temporary wider child cannot permanently push its entire group across the screen.
        for (AbstractWidget widget : widgetByKey.values()) {
            AbstractWidget parent = widgetByKey.get(widget.getSnapTargetKey());
            if (parent == null || parent.hasWidgetSnap() || parent.getScreenAnchor() != null
                    || parent == host.getDraggedWidget() || host.isPlacementReadOnly(parent)) continue;
            WidgetLayoutConfig stored = host.getStoredPlacement(parent);
            if (stored == null || screenWidth <= 0 || screenHeight <= 0) continue;
            int x = stored.xFraction != null ? (int) Math.round(stored.xFraction * screenWidth)
                    : stored.x != null ? stored.x : parent.getStartX();
            int y = stored.yFraction != null ? (int) Math.round(stored.yFraction * screenHeight)
                    : stored.y != null ? stored.y : parent.getStartY();
            parent.setStartX(clamp(x, 0, Math.max(0, screenWidth - parent.getWidth())));
            parent.setStartY(clamp(y, 0, Math.max(0, screenHeight - parent.getHeight())));
        }

        for (AbstractWidget widget : widgetByKey.values()) {
            applyStoredWidgetSnap(host, widget, widgetByKey, visiting);
        }
        keepSnapGroupsOnScreen(host, widgetByKey, screenWidth, screenHeight);
    }

    private void keepSnapGroupsOnScreen(HudSnapLayoutHost host, Map<String, AbstractWidget> widgetByKey,
                                         int screenWidth, int screenHeight) {
        if (screenWidth <= 0 || screenHeight <= 0) return;
        Set<AbstractWidget> handled = new HashSet<>();
        for (AbstractWidget widget : widgetByKey.values()) {
            if (!widget.hasWidgetSnap() || handled.contains(widget)) continue;
            var group = HudSnapGroupResolver.getConnectedSnapGroup(widget, widgets);
            handled.addAll(group);
            // Do not fight the pointer while the user is dragging this group.
            if (group.contains(host.getDraggedWidget())) continue;
            if (group.stream().anyMatch(host::isPlacementReadOnly)) {
                // A local widget can snap to Main, but reaching a screen edge must not move Main.
                for (AbstractWidget member : group) if (!host.isPlacementReadOnly(member)) {
                    member.setStartX(clamp(member.getStartX(), 0, Math.max(0, screenWidth - member.getWidth())));
                    member.setStartY(clamp(member.getStartY(), 0, Math.max(0, screenHeight - member.getHeight())));
                }
                continue;
            }
            int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE;
            int right = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;
            for (AbstractWidget member : group) {
                left = Math.min(left, member.getLeft()); top = Math.min(top, member.getTop());
                right = Math.max(right, member.getRight()); bottom = Math.max(bottom, member.getBottom());
            }
            boolean fitsX = right - left <= screenWidth, fitsY = bottom - top <= screenHeight;
            int dx = fitsX ? clamp(0, -left, screenWidth - right) : 0;
            int dy = fitsY ? clamp(0, -top, screenHeight - bottom) : 0;
            for (AbstractWidget member : group) {
                // Translate a fitting group as a whole, preserving corners and gaps. If one axis
                // cannot fit, keep each member reachable without deleting its saved snap relation.
                member.setStartX(fitsX ? member.getStartX() + dx
                        : clamp(member.getStartX(), 0, Math.max(0, screenWidth - member.getWidth())));
                member.setStartY(fitsY ? member.getStartY() + dy
                        : clamp(member.getStartY(), 0, Math.max(0, screenHeight - member.getHeight())));
            }
        }
    }

    public void applyStoredScreenAnchor(Widget widget, ScreenAnchor anchor, int screenWidth, int screenHeight) {
        HudScreenAnchorHelper.AnchorPlacement placement = getAnchorPlacement(anchor, widget, screenWidth, screenHeight);
        int offsetX = widget instanceof AbstractWidget placed ? placed.getAnchorOffsetX() : 0;
        int offsetY = widget instanceof AbstractWidget placed ? placed.getAnchorOffsetY() : 0;
        widget.setStartX(clamp(placement.startX() + offsetX, 0, Math.max(0, screenWidth - widget.getWidth())));
        widget.setStartY(clamp(placement.startY() + offsetY, 0, Math.max(0, screenHeight - widget.getHeight())));

        if (widget instanceof AbstractWidget abstractWidget) {
            abstractWidget.setScreenAnchor(anchor);
        }
    }

    public Map<String, AbstractWidget> getAbstractWidgetMap() {
        Map<String, AbstractWidget> widgetByKey = new HashMap<>();
        for (Widget widget : widgets) {
            if (widget instanceof AbstractWidget abstractWidget && abstractWidget.getConfigKey() != null) {
                widgetByKey.put(abstractWidget.getConfigKey(), abstractWidget);
            }
        }
        return widgetByKey;
    }

    public AbstractWidget findAbstractWidget(String configKey) {
        if (configKey == null || configKey.isBlank()) {
            return null;
        }

        for (Widget widget : widgets) {
            if (widget instanceof AbstractWidget abstractWidget && configKey.equals(abstractWidget.getConfigKey())) {
                return abstractWidget;
            }
        }
        return null;
    }

    private void repositionAllWidgets(HudSnapLayoutHost host, int screenWidth, int screenHeight) {
        for (Widget widget : widgets) {
            if (host.isMoving(widget)) continue;
            repositionForResize(host, widget, screenWidth, screenHeight);
        }

        updateSnappedWidgets(host, screenWidth, screenHeight);
    }

    private void clampWidgetsToScreen(int screenWidth, int screenHeight) {
        for (Widget widget : widgets) {
            if (widget instanceof AbstractWidget abstractWidget && abstractWidget.getScreenAnchor() != null) {
                applyStoredScreenAnchor(abstractWidget, abstractWidget.getScreenAnchor(), screenWidth, screenHeight);
                continue;
            }

            int maxX = Math.max(0, screenWidth - widget.getWidth());
            int maxY = Math.max(0, screenHeight - widget.getHeight());
            widget.setStartX(clamp(widget.getStartX(), 0, maxX));
            widget.setStartY(clamp(widget.getStartY(), 0, maxY));
        }
    }

    private void applyStoredWidgetSnap(
            HudSnapLayoutHost host,
            AbstractWidget widget,
            Map<String, AbstractWidget> widgetByKey,
            Set<String> visiting
    ) {
        if (widget == null || widget == host.getDraggedWidget() || host.isPlacementReadOnly(widget) || !widget.hasWidgetSnap()) {
            return;
        }

        String key = widget.getConfigKey();
        if (key == null) {
            return;
        }
        if (!visiting.add(key)) {
            widget.clearWidgetSnap();
            return;
        }

        AbstractWidget target = widgetByKey.get(widget.getSnapTargetKey());
        if (target == null || target == widget) {
            widget.clearWidgetSnap();
            visiting.remove(key);
            return;
        }

        applyStoredWidgetSnap(host, target, widgetByKey, visiting);
        if (widget.hasWidgetSnap()) {
            applyWidgetSnapPosition(widget, target, widget.getSnapOwnCorner(), widget.getSnapTargetCorner());
        }
        visiting.remove(key);
    }

    private void repositionForResize(HudSnapLayoutHost host, Widget widget, int newScreenWidth, int newScreenHeight) {
        if (widget instanceof AbstractWidget abstractWidget && abstractWidget.getScreenAnchor() != null) {
            applyStoredScreenAnchor(abstractWidget, abstractWidget.getScreenAnchor(), newScreenWidth, newScreenHeight);
            return;
        }

        // Resolution-independent: place the widget at its stored screen fraction, computed straight
        // from the CURRENT size — no chaining through intermediate sizes, so no round-trip drift. The
        // clamp is display-only; the stored fraction is the source of truth and is never touched here.
        WidgetLayoutConfig layout = widget instanceof AbstractWidget aw ? ConfigManager.getWidgetLayout(aw.getConfigKey()) : null;
        if (layout != null && layout.xFraction != null && layout.yFraction != null) {
            int maxX = Math.max(0, newScreenWidth - widget.getWidth());
            int maxY = Math.max(0, newScreenHeight - widget.getHeight());
            widget.setStartX(clamp((int) Math.round(layout.xFraction * newScreenWidth), 0, maxX));
            widget.setStartY(clamp((int) Math.round(layout.yFraction * newScreenHeight), 0, maxY));
            return;
        }

        // Fallback for widgets without a stored fraction (never moved): the legacy ratio scaling.
        int oldAvailableWidth = Math.max(0, host.getLastScreenWidth() - widget.getWidth());
        int oldAvailableHeight = Math.max(0, host.getLastScreenHeight() - widget.getHeight());

        int newAvailableWidth = Math.max(0, newScreenWidth - widget.getWidth());
        int newAvailableHeight = Math.max(0, newScreenHeight - widget.getHeight());

        float horizontalRatio = oldAvailableWidth <= 0
                ? 0.0f
                : clamp(widget.getStartX(), 0, oldAvailableWidth) / (float) oldAvailableWidth;
        float verticalRatio = oldAvailableHeight <= 0
                ? 0.0f
                : clamp(widget.getStartY(), 0, oldAvailableHeight) / (float) oldAvailableHeight;

        int newX = Math.round(horizontalRatio * newAvailableWidth);
        int newY = Math.round(verticalRatio * newAvailableHeight);

        widget.setStartX(clamp(newX, 0, newAvailableWidth));
        widget.setStartY(clamp(newY, 0, newAvailableHeight));
    }

    private void applyWidgetSnapPosition(AbstractWidget child, Widget parent, WidgetCorner childCorner, WidgetCorner parentCorner) {
        int startX = switch (childCorner) {
            case TOP_LEFT, BOTTOM_LEFT -> parent.getCornerX(parentCorner);
            case TOP_RIGHT, BOTTOM_RIGHT -> parent.getCornerX(parentCorner) - child.getWidth();
        };

        int startY = switch (childCorner) {
            case TOP_LEFT, TOP_RIGHT -> parent.getCornerY(parentCorner);
            case BOTTOM_LEFT, BOTTOM_RIGHT -> parent.getCornerY(parentCorner) - child.getHeight();
        };

        if (isRightCorner(childCorner) && isLeftCorner(parentCorner)) {
            startX -= groupGap;
        } else if (isLeftCorner(childCorner) && isRightCorner(parentCorner)) {
            startX += groupGap;
        }

        if (isBottomCorner(childCorner) && isTopCorner(parentCorner)) {
            startY -= groupGap;
        } else if (isTopCorner(childCorner) && isBottomCorner(parentCorner)) {
            startY += groupGap;
        }

        child.setStartX(startX + child.getSnapOffsetX());
        child.setStartY(startY + child.getSnapOffsetY());
    }

    private HudScreenAnchorHelper.AnchorPlacement getAnchorPlacement(ScreenAnchor anchor, Widget widget, int screenWidth, int screenHeight) {
        return HudScreenAnchorHelper.getAnchorPlacement(
                anchor,
                widget,
                screenWidth,
                screenHeight,
                screenSnapMargin,
                hotbarWidth,
                hotbarHeight,
                hotbarAnchorGap,
                offhandSlotWidth
        );
    }

    private boolean isLeftCorner(WidgetCorner corner) {
        return corner == WidgetCorner.TOP_LEFT || corner == WidgetCorner.BOTTOM_LEFT;
    }

    private boolean isRightCorner(WidgetCorner corner) {
        return corner == WidgetCorner.TOP_RIGHT || corner == WidgetCorner.BOTTOM_RIGHT;
    }

    private boolean isTopCorner(WidgetCorner corner) {
        return corner == WidgetCorner.TOP_LEFT || corner == WidgetCorner.TOP_RIGHT;
    }

    private boolean isBottomCorner(WidgetCorner corner) {
        return corner == WidgetCorner.BOTTOM_LEFT || corner == WidgetCorner.BOTTOM_RIGHT;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

}
