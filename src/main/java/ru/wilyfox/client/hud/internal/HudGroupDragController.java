package ru.wilyfox.client.hud.internal;

import ru.wilyfox.client.hud.widget.AbstractWidget;
import ru.wilyfox.client.hud.widget.WidgetCorner;

import java.util.List;
import java.util.Map;

public final class HudGroupDragController {
    private HudGroupDragController() {
    }

    public static boolean beginGroupDrag(
            AbstractWidget rootWidget,
            List<AbstractWidget> descendants,
            List<AbstractWidget> draggedGroupWidgets,
            Map<String, GroupDragState> draggedGroupStates
    ) {
        draggedGroupWidgets.clear();
        draggedGroupStates.clear();

        if (descendants.isEmpty()) {
            return false;
        }

        draggedGroupWidgets.add(rootWidget);
        draggedGroupStates.put(rootWidget.getConfigKey(), new GroupDragState(0, 0, null, null, null));

        for (AbstractWidget widget : descendants) {
            draggedGroupWidgets.add(widget);
            draggedGroupStates.put(widget.getConfigKey(), new GroupDragState(
                    widget.getStartX() - rootWidget.getStartX(),
                    widget.getStartY() - rootWidget.getStartY(),
                    widget.getSnapTargetKey(),
                    widget.getSnapOwnCorner(),
                    widget.getSnapTargetCorner(), widget.getSnapOffsetX(), widget.getSnapOffsetY()
            ));
            widget.clearWidgetSnap();
        }

        return true;
    }

    public static void finishGroupDrag(
            AbstractWidget draggedWidget,
            List<AbstractWidget> draggedGroupWidgets,
            Map<String, GroupDragState> draggedGroupStates
    ) {
        for (AbstractWidget widget : draggedGroupWidgets) {
            if (widget == draggedWidget) {
                continue;
            }

            GroupDragState state = draggedGroupStates.get(widget.getConfigKey());
            if (state == null || state.snapTargetKey() == null || state.snapOwnCorner() == null || state.snapTargetCorner() == null) {
                continue;
            }

            widget.setWidgetSnap(state.snapTargetKey(), state.snapOwnCorner(), state.snapTargetCorner(), state.offsetSnapX(), state.offsetSnapY());
        }

        draggedGroupWidgets.clear();
        draggedGroupStates.clear();
    }

    public static void updateDraggedGroupPositions(
            AbstractWidget rootWidget,
            List<AbstractWidget> draggedGroupWidgets,
            Map<String, GroupDragState> draggedGroupStates
    ) {
        for (AbstractWidget widget : draggedGroupWidgets) {
            if (widget == rootWidget) {
                continue;
            }

            GroupDragState state = draggedGroupStates.get(widget.getConfigKey());
            if (state == null) {
                continue;
            }

            widget.setStartX(rootWidget.getStartX() + state.offsetX());
            widget.setStartY(rootWidget.getStartY() + state.offsetY());
        }
    }

    public record GroupDragState(
            int offsetX,
            int offsetY,
            String snapTargetKey,
            WidgetCorner snapOwnCorner,
            WidgetCorner snapTargetCorner, int offsetSnapX, int offsetSnapY
    ) {
        public GroupDragState(int offsetX, int offsetY, String target, WidgetCorner own, WidgetCorner other) {
            this(offsetX, offsetY, target, own, other, 0, 0);
        }
    }
}
