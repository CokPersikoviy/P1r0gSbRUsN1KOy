package ru.wilyfox.client.hud.internal;

import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.WidgetLayoutConfig;
import ru.wilyfox.client.hud.indicators.CornerSnapIndicator;
import ru.wilyfox.client.hud.indicators.ScreenAnchor;
import ru.wilyfox.client.hud.snap.SnapCandidate;
import ru.wilyfox.client.hud.widget.AbstractWidget;
import ru.wilyfox.client.hud.widget.Widget;
import ru.wilyfox.client.hud.widget.WidgetCorner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ru.wilyfox.client.hud.snap.SnapFinder.overlapsHorizontally;
import static ru.wilyfox.client.hud.snap.SnapFinder.overlapsVertically;

public final class HudSnapLayoutEngine {
    private final List<Widget> widgets;
    private final int screenSnapMargin;
    private final int screenSnapDistance;
    private final int widgetSnapDistance;
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
        this.screenSnapDistance = screenSnapDistance;
        this.widgetSnapDistance = widgetSnapDistance;
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
            if (widget == host.getDraggedWidget()) {
                continue;
            }

            if (widget instanceof AbstractWidget abstractWidget && abstractWidget.getScreenAnchor() != null) {
                applyStoredScreenAnchor(abstractWidget, abstractWidget.getScreenAnchor(), screenWidth, screenHeight);
            }
        }
    }

    public void updateSnappedWidgets(HudSnapLayoutHost host) {
        Map<String, AbstractWidget> widgetByKey = getAbstractWidgetMap();
        Set<String> visiting = new HashSet<>();

        for (AbstractWidget widget : widgetByKey.values()) {
            applyStoredWidgetSnap(host, widget, widgetByKey, visiting);
        }
    }

    public boolean applyScreenAnchorSnapping(HudSnapLayoutHost host, Widget widget, int screenWidth, int screenHeight, boolean shiftHeld) {
        HudScreenAnchorHelper.AnchorPlacement best = null;

        for (ScreenAnchor anchor : ScreenAnchor.values()) {
            if (shiftHeld != host.isCenterSideAnchor(anchor)) {
                continue;
            }
            if (host.isScreenAnchorOccupied(anchor, widget)) {
                continue;
            }

            HudScreenAnchorHelper.AnchorPlacement placement = getAnchorPlacement(anchor, widget, screenWidth, screenHeight);
            int distance = HudScreenAnchorHelper.getAnchorSnapDistance(widget, placement, shiftHeld);
            if (distance > screenSnapDistance * 2) {
                continue;
            }

            if (best == null || distance < best.distance()) {
                best = placement.withDistance(distance);
            }
        }

        if (best == null) {
            return false;
        }

        applyStoredScreenAnchor(widget, best.anchor(), screenWidth, screenHeight);
        host.setActiveScreenAnchor(best.anchor());
        return true;
    }

    public void applyStoredScreenAnchor(Widget widget, ScreenAnchor anchor, int screenWidth, int screenHeight) {
        HudScreenAnchorHelper.AnchorPlacement placement = getAnchorPlacement(anchor, widget, screenWidth, screenHeight);
        widget.setStartX(placement.startX());
        widget.setStartY(placement.startY());

        if (widget instanceof AbstractWidget abstractWidget) {
            abstractWidget.setScreenAnchor(anchor);
        }
    }

    public void applyWidgetSnapping(HudSnapLayoutHost host, int screenWidth, int screenHeight) {
        Widget draggedWidget = host.getDraggedWidget();
        SnapCandidate horizontal = findBestHorizontalSnap(draggedWidget);
        if (horizontal != null) {
            draggedWidget.setStartX(horizontal.getSnappedStartX());
            draggedWidget.setStartY(horizontal.getSnappedStartY());

            if (draggedWidget instanceof AbstractWidget abstractWidget) {
                abstractWidget.setScreenAnchor(null);
            }

            persistWidgetSnap(host, horizontal);

            host.setActiveDraggedCornerIndicator(new CornerSnapIndicator(draggedWidget, horizontal.getDraggedCorner()));
            host.setActiveTargetCornerIndicator(new CornerSnapIndicator(horizontal.getTargetWidget(), horizontal.getTargetCorner()));
            return;
        }

        SnapCandidate vertical = findBestVerticalSnap(draggedWidget);
        if (vertical != null) {
            draggedWidget.setStartX(vertical.getSnappedStartX());
            draggedWidget.setStartY(vertical.getSnappedStartY());

            if (draggedWidget instanceof AbstractWidget abstractWidget) {
                abstractWidget.setScreenAnchor(null);
            }

            persistWidgetSnap(host, vertical);

            host.setActiveDraggedCornerIndicator(new CornerSnapIndicator(draggedWidget, vertical.getDraggedCorner()));
            host.setActiveTargetCornerIndicator(new CornerSnapIndicator(vertical.getTargetWidget(), vertical.getTargetCorner()));
            return;
        }

        if (draggedWidget instanceof AbstractWidget abstractWidget) {
            abstractWidget.clearWidgetSnap();
        }
    }

    public void resolveWidgetOverlap(HudSnapLayoutHost host, boolean draggingWidgetGroup, int screenWidth, int screenHeight) {
        Widget draggedWidget = host.getDraggedWidget();
        if (draggingWidgetGroup || draggedWidget == null) {
            return;
        }

        OverlapResolution best = null;
        for (Widget other : widgets) {
            if (other == draggedWidget || !other.isVisible()) {
                continue;
            }

            if (!widgetsOverlap(draggedWidget, other)) {
                continue;
            }

            OverlapResolution candidate = findBestOverlapResolution(draggedWidget, other, screenWidth, screenHeight);
            if (candidate != null && (best == null || candidate.distance() < best.distance())) {
                best = candidate;
            }
        }

        if (best == null) {
            return;
        }

        draggedWidget.setStartX(best.x());
        draggedWidget.setStartY(best.y());
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
            repositionForResize(host, widget, screenWidth, screenHeight);
        }

        updateSnappedWidgets(host);
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
        if (widget == null || widget == host.getDraggedWidget() || !widget.hasWidgetSnap()) {
            return;
        }

        String key = widget.getConfigKey();
        if (key == null || !visiting.add(key)) {
            return;
        }

        AbstractWidget target = widgetByKey.get(widget.getSnapTargetKey());
        if (target == null || target == widget) {
            widget.clearWidgetSnap();
            visiting.remove(key);
            return;
        }

        applyStoredWidgetSnap(host, target, widgetByKey, visiting);
        applyWidgetSnapPosition(widget, target, widget.getSnapOwnCorner(), widget.getSnapTargetCorner());
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

    private void persistWidgetSnap(HudSnapLayoutHost host, SnapCandidate candidate) {
        Widget draggedWidget = host.getDraggedWidget();
        if (!(draggedWidget instanceof AbstractWidget draggedAbstract) || !(candidate.getTargetWidget() instanceof AbstractWidget targetAbstract)) {
            return;
        }

        if (createsSnapCycle(draggedAbstract, targetAbstract)) {
            draggedAbstract.clearWidgetSnap();
            return;
        }

        draggedAbstract.setScreenAnchor(null);
        draggedAbstract.setWidgetSnap(targetAbstract.getConfigKey(), candidate.getDraggedCorner(), candidate.getTargetCorner());
        applyWidgetSnapPosition(draggedAbstract, targetAbstract, candidate.getDraggedCorner(), candidate.getTargetCorner());
    }

    private boolean createsSnapCycle(AbstractWidget child, AbstractWidget parent) {
        if (child == null || parent == null) {
            return true;
        }

        String childKey = child.getConfigKey();
        AbstractWidget current = parent;
        Set<String> visited = new HashSet<>();
        while (current != null && current.hasWidgetSnap() && visited.add(current.getConfigKey())) {
            if (current.getConfigKey().equals(childKey)) {
                return true;
            }
            current = findAbstractWidget(current.getSnapTargetKey());
        }
        return false;
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

        child.setStartX(startX);
        child.setStartY(startY);
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

    private OverlapResolution findBestOverlapResolution(Widget dragged, Widget other, int screenWidth, int screenHeight) {
        List<OverlapResolution> candidates = new ArrayList<>(4);
        addOverlapResolutionCandidate(candidates, other.getLeft() - groupGap - dragged.getWidth(), dragged.getStartY(), dragged, screenWidth, screenHeight);
        addOverlapResolutionCandidate(candidates, other.getRight() + groupGap, dragged.getStartY(), dragged, screenWidth, screenHeight);
        addOverlapResolutionCandidate(candidates, dragged.getStartX(), other.getTop() - groupGap - dragged.getHeight(), dragged, screenWidth, screenHeight);
        addOverlapResolutionCandidate(candidates, dragged.getStartX(), other.getBottom() + groupGap, dragged, screenWidth, screenHeight);

        OverlapResolution best = null;
        for (OverlapResolution candidate : candidates) {
            if (best == null || candidate.distance() < best.distance()) {
                best = candidate;
            }
        }
        return best;
    }

    private void addOverlapResolutionCandidate(
            List<OverlapResolution> candidates,
            int targetX,
            int targetY,
            Widget dragged,
            int screenWidth,
            int screenHeight
    ) {
        int maxX = Math.max(0, screenWidth - dragged.getWidth());
        int maxY = Math.max(0, screenHeight - dragged.getHeight());
        int clampedX = clamp(targetX, 0, maxX);
        int clampedY = clamp(targetY, 0, maxY);

        if (clampedX != targetX || clampedY != targetY) {
            return;
        }

        int distance = Math.abs(dragged.getStartX() - clampedX) + Math.abs(dragged.getStartY() - clampedY);
        candidates.add(new OverlapResolution(clampedX, clampedY, distance));
    }

    private boolean widgetsOverlap(Widget first, Widget second) {
        return first.getLeft() < second.getRight()
                && first.getRight() > second.getLeft()
                && first.getTop() < second.getBottom()
                && first.getBottom() > second.getTop();
    }

    private SnapCandidate findBestHorizontalSnap(Widget dragged) {
        SnapCandidate best = null;

        for (Widget other : widgets) {
            if (other == dragged || !other.isVisible()) {
                continue;
            }

            if (!overlapsVertically(dragged, other)) {
                continue;
            }

            int targetX1 = other.getLeft() - groupGap - dragged.getWidth();
            int distance1 = Math.abs(dragged.getStartX() - targetX1);

            if (distance1 <= widgetSnapDistance) {
                int topAlignmentDistance = Math.abs(dragged.getTop() - other.getTop());
                SnapCandidate candidate = new SnapCandidate(
                        distance1 + topAlignmentDistance,
                        targetX1,
                        other.getTop(),
                        other,
                        WidgetCorner.TOP_RIGHT,
                        WidgetCorner.TOP_LEFT
                );
                best = pickBetter(best, candidate);

                int bottomAlignmentDistance = Math.abs(dragged.getBottom() - other.getBottom());
                SnapCandidate bottomAlignedCandidate = new SnapCandidate(
                        distance1 + bottomAlignmentDistance,
                        targetX1,
                        other.getBottom() - dragged.getHeight(),
                        other,
                        WidgetCorner.BOTTOM_RIGHT,
                        WidgetCorner.BOTTOM_LEFT
                );
                best = pickBetter(best, bottomAlignedCandidate);
            }

            int targetX2 = other.getRight() + groupGap;
            int distance2 = Math.abs(dragged.getStartX() - targetX2);

            if (distance2 <= widgetSnapDistance) {
                int topAlignmentDistance = Math.abs(dragged.getTop() - other.getTop());
                SnapCandidate candidate = new SnapCandidate(
                        distance2 + topAlignmentDistance,
                        targetX2,
                        other.getTop(),
                        other,
                        WidgetCorner.TOP_LEFT,
                        WidgetCorner.TOP_RIGHT
                );
                best = pickBetter(best, candidate);

                int bottomAlignmentDistance = Math.abs(dragged.getBottom() - other.getBottom());
                SnapCandidate bottomAlignedCandidate = new SnapCandidate(
                        distance2 + bottomAlignmentDistance,
                        targetX2,
                        other.getBottom() - dragged.getHeight(),
                        other,
                        WidgetCorner.BOTTOM_LEFT,
                        WidgetCorner.BOTTOM_RIGHT
                );
                best = pickBetter(best, bottomAlignedCandidate);
            }
        }

        return best;
    }

    private SnapCandidate findBestVerticalSnap(Widget dragged) {
        SnapCandidate best = null;

        for (Widget other : widgets) {
            if (other == dragged || !other.isVisible()) {
                continue;
            }

            if (!overlapsHorizontally(dragged, other)) {
                continue;
            }

            int targetY1 = other.getTop() - groupGap - dragged.getHeight();
            int distance1 = Math.abs(dragged.getStartY() - targetY1);

            if (distance1 <= widgetSnapDistance) {
                int leftAlignmentDistance = Math.abs(dragged.getLeft() - other.getLeft());
                SnapCandidate candidate = new SnapCandidate(
                        distance1 + leftAlignmentDistance,
                        other.getLeft(),
                        targetY1,
                        other,
                        WidgetCorner.BOTTOM_LEFT,
                        WidgetCorner.TOP_LEFT
                );
                best = pickBetter(best, candidate);

                int rightAlignmentDistance = Math.abs(dragged.getRight() - other.getRight());
                SnapCandidate rightAlignedCandidate = new SnapCandidate(
                        distance1 + rightAlignmentDistance,
                        other.getRight() - dragged.getWidth(),
                        targetY1,
                        other,
                        WidgetCorner.BOTTOM_RIGHT,
                        WidgetCorner.TOP_RIGHT
                );
                best = pickBetter(best, rightAlignedCandidate);
            }

            int targetY2 = other.getBottom() + groupGap;
            int distance2 = Math.abs(dragged.getStartY() - targetY2);

            if (distance2 <= widgetSnapDistance) {
                int leftAlignmentDistance = Math.abs(dragged.getLeft() - other.getLeft());
                SnapCandidate candidate = new SnapCandidate(
                        distance2 + leftAlignmentDistance,
                        other.getLeft(),
                        targetY2,
                        other,
                        WidgetCorner.TOP_LEFT,
                        WidgetCorner.BOTTOM_LEFT
                );
                best = pickBetter(best, candidate);

                int rightAlignmentDistance = Math.abs(dragged.getRight() - other.getRight());
                SnapCandidate rightAlignedCandidate = new SnapCandidate(
                        distance2 + rightAlignmentDistance,
                        other.getRight() - dragged.getWidth(),
                        targetY2,
                        other,
                        WidgetCorner.TOP_RIGHT,
                        WidgetCorner.BOTTOM_RIGHT
                );
                best = pickBetter(best, rightAlignedCandidate);
            }
        }

        return best;
    }

    private SnapCandidate pickBetter(SnapCandidate current, SnapCandidate candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null || candidate.getDistance() < current.getDistance()) {
            return candidate;
        }
        return current;
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

    private record OverlapResolution(int x, int y, int distance) {
    }
}
