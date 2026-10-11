package ru.wilyfox.client.hud.internal;

import ru.wilyfox.client.hud.widget.AbstractWidget;
import ru.wilyfox.client.hud.widget.WidgetCorner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class HudSnapGraphNormalizer {
    private HudSnapGraphNormalizer() {
    }

    public static void normalize(Map<String, AbstractWidget> widgetByKey) {
        Map<String, List<SnapEdge>> adjacency = new HashMap<>();

        for (AbstractWidget widget : widgetByKey.values()) {
            if (!widget.hasWidgetSnap()) {
                continue;
            }

            AbstractWidget target = widgetByKey.get(widget.getSnapTargetKey());
            if (target == null || target == widget) {
                widget.clearWidgetSnap();
                continue;
            }

            SnapEdge edge = new SnapEdge(widget, target, widget.getSnapOwnCorner(), widget.getSnapTargetCorner());
            adjacency.computeIfAbsent(widget.getConfigKey(), ignored -> new ArrayList<>()).add(edge);
            adjacency.computeIfAbsent(target.getConfigKey(), ignored -> new ArrayList<>()).add(edge);
        }

        Set<String> visited = new HashSet<>();
        for (AbstractWidget widget : widgetByKey.values()) {
            String widgetKey = widget.getConfigKey();
            if (widgetKey == null || visited.contains(widgetKey) || !adjacency.containsKey(widgetKey)) {
                continue;
            }

            List<AbstractWidget> component = new ArrayList<>();
            collectSnapComponent(widget, adjacency, visited, component);
            reorientSnapComponent(component, adjacency);
        }
    }

    private static void collectSnapComponent(
            AbstractWidget start,
            Map<String, List<SnapEdge>> adjacency,
            Set<String> visited,
            List<AbstractWidget> component
    ) {
        List<AbstractWidget> queue = new ArrayList<>();
        queue.add(start);
        visited.add(start.getConfigKey());

        for (int index = 0; index < queue.size(); index++) {
            AbstractWidget current = queue.get(index);
            component.add(current);

            for (SnapEdge edge : adjacency.getOrDefault(current.getConfigKey(), List.of())) {
                AbstractWidget neighbor = edge.other(current);
                if (neighbor == null || neighbor.getConfigKey() == null || !visited.add(neighbor.getConfigKey())) {
                    continue;
                }
                queue.add(neighbor);
            }
        }
    }

    private static void reorientSnapComponent(List<AbstractWidget> component, Map<String, List<SnapEdge>> adjacency) {
        if (component.isEmpty()) {
            return;
        }

        AbstractWidget root = chooseSnapRoot(component);
        Set<String> visited = new HashSet<>();
        List<AbstractWidget> queue = new ArrayList<>();

        root.clearWidgetSnap();
        visited.add(root.getConfigKey());
        queue.add(root);

        for (int index = 0; index < queue.size(); index++) {
            AbstractWidget current = queue.get(index);

            for (SnapEdge edge : adjacency.getOrDefault(current.getConfigKey(), List.of())) {
                AbstractWidget neighbor = edge.other(current);
                if (neighbor == null || neighbor.getConfigKey() == null || !visited.add(neighbor.getConfigKey())) {
                    continue;
                }

                WidgetCorner childCorner = edge.childCornerFor(neighbor);
                WidgetCorner parentCorner = edge.parentCornerFor(current);
                if (childCorner == null || parentCorner == null) {
                    continue;
                }

                neighbor.setScreenAnchor(null);
                neighbor.setWidgetSnap(current.getConfigKey(), childCorner, parentCorner);
                queue.add(neighbor);
            }
        }
    }

    private static AbstractWidget chooseSnapRoot(List<AbstractWidget> component) {
        AbstractWidget bestAnchored = null;
        AbstractWidget existingRoot = null;
        AbstractWidget bestFree = null;

        for (AbstractWidget widget : component) {
            if (!widget.hasWidgetSnap() && (existingRoot == null || isHigherPrioritySnapRoot(widget, existingRoot))) {
                existingRoot = widget;
            }
            if (widget.getScreenAnchor() != null) {
                if (bestAnchored == null || isHigherPrioritySnapRoot(widget, bestAnchored)) {
                    bestAnchored = widget;
                }
            } else if (bestFree == null || isHigherPrioritySnapRoot(widget, bestFree)) {
                bestFree = widget;
            }
        }

        // Keep a valid tree's original parent direction. Picking the top-left widget again after
        // auto-sizing or an editor transition makes the group's position depend on its last frame.
        return bestAnchored != null ? bestAnchored : existingRoot != null ? existingRoot : bestFree;
    }

    private static boolean isHigherPrioritySnapRoot(AbstractWidget candidate, AbstractWidget currentBest) {
        if (candidate.getStartY() != currentBest.getStartY()) {
            return candidate.getStartY() < currentBest.getStartY();
        }
        return candidate.getStartX() < currentBest.getStartX();
    }

    private record SnapEdge(
            AbstractWidget child,
            AbstractWidget parent,
            WidgetCorner childCorner,
            WidgetCorner parentCorner
    ) {
        private AbstractWidget other(AbstractWidget widget) {
            if (widget == child) {
                return parent;
            }
            if (widget == parent) {
                return child;
            }
            return null;
        }

        private WidgetCorner childCornerFor(AbstractWidget widget) {
            if (widget == child) {
                return childCorner;
            }
            if (widget == parent) {
                return parentCorner;
            }
            return null;
        }

        private WidgetCorner parentCornerFor(AbstractWidget widget) {
            if (widget == parent) {
                return parentCorner;
            }
            if (widget == child) {
                return childCorner;
            }
            return null;
        }
    }
}
