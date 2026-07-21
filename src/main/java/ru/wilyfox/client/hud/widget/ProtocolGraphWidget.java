package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.protocol.ProtocolGraphTelemetry;
import ru.wilyfox.client.protocol.ProtocolGraphTelemetry.GraphEdgeSnapshot;
import ru.wilyfox.client.protocol.ProtocolGraphTelemetry.GraphNodeKind;
import ru.wilyfox.client.protocol.ProtocolGraphTelemetry.GraphNodeSnapshot;
import ru.wilyfox.client.protocol.ProtocolGraphTelemetry.GraphSnapshot;
import ru.wilyfox.utils.MouseUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ProtocolGraphWidget extends AbstractWidget {
    private static final int PADDING_X = 10;
    private static final int PADDING_Y = 8;
    private static final int HEADER_HEIGHT = 26;
    private static final int FOOTER_HEIGHT = 18;
    private static final int EMPTY_GRAPH_HEIGHT = 150;
    private static final int MIN_GRAPH_HEIGHT = 250;
    private static final int BASE_WIDTH = 760;
    private static final int NODE_RADIUS = 4;
    private static final int NODE_RING_RADIUS = 6;
    private static final int EDGE_THICKNESS = 1;
    private static final int NODE_MIN_VERTICAL_GAP = 22;

    public ProtocolGraphWidget(int x, int y, HudLayer layer) {
        super(x, y, layer);
    }

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        if (!isVisible()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        GraphSnapshot snapshot = ProtocolGraphTelemetry.getInstance().snapshot();
        Layout layout = buildLayout(snapshot);
        int panelWidth = getBaseWidth();
        int panelHeight = getBaseHeight(snapshot);
        double mouseGuiX = MouseUtils.getMouseX();
        double mouseGuiY = MouseUtils.getMouseY();
        double localMouseX = (mouseGuiX - startX) / scale;
        double localMouseY = (mouseGuiY - startY) / scale;

        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        HudSurface.drawPanel(context, panelWidth, panelHeight);

        renderHeader(context, mc, snapshot, panelWidth);
        renderEdges(context, snapshot, layout);
        renderNodes(context, mc, snapshot, layout);
        renderFooter(context, mc, snapshot, panelHeight);

        context.pose().popPose();

        PositionedNode hoveredNode = layout.findHoveredNode(localMouseX, localMouseY);
        if (hoveredNode != null) {
            renderNodeTooltip(context, mc, hoveredNode.node(), (int) mouseGuiX, (int) mouseGuiY, snapshot.capturedAt());
        }
    }

    @Override
    public int getWidth() {
        return Math.round(getBaseWidth() * getScale());
    }

    @Override
    public int getHeight() {
        return Math.round(getBaseHeight(ProtocolGraphTelemetry.getInstance().snapshot()) * getScale());
    }

    @Override
    public boolean isVisible() {
        return ConfigManager.get().protocolGraphWidget.active;
    }

    @Override
    public String getDisplayName() {
        return "Protocol Graph";
    }

    private void renderHeader(GuiGraphics context, Minecraft mc, GraphSnapshot snapshot, int panelWidth) {
        context.drawString(mc.font, "Protocol Graph", PADDING_X, PADDING_Y, WidgetTheme.TITLE);

        long activeNodes = snapshot.nodes().stream()
                .filter(node -> isRecentlyActive(node.lastActiveAt(), snapshot.capturedAt()))
                .count();
        long activeEdges = snapshot.edges().stream()
                .filter(edge -> isRecentlyActive(edge.lastActiveAt(), snapshot.capturedAt()))
                .count();
        String meta = "nodes " + activeNodes + "  edges " + activeEdges;
        context.drawString(
                mc.font,
                meta,
                Math.max(PADDING_X, panelWidth - PADDING_X - mc.font.width(meta)),
                PADDING_Y,
                WidgetTheme.TEXT_SECONDARY
        );

        context.drawString(mc.font, "Input, decode, handle, store, state, widget", PADDING_X, PADDING_Y + 12, WidgetTheme.TEXT_MUTED);
    }

    private void renderFooter(GuiGraphics context, Minecraft mc, GraphSnapshot snapshot, int panelHeight) {
        GraphNodeSnapshot hottestInput = snapshot.nodes().stream()
                .filter(node -> node.kind() == GraphNodeKind.INPUT)
                .max(Comparator.comparingLong(GraphNodeSnapshot::lastActiveAt).thenComparingInt(GraphNodeSnapshot::hits))
                .orElse(null);

        String text = hottestInput == null || hottestInput.lastActiveAt() <= 0L
                ? "No runtime activity yet"
                : "Latest input: " + hottestInput.label() + "  hits " + hottestInput.hits() + "  errors " + hottestInput.errors();
        context.drawString(mc.font, text, PADDING_X, panelHeight - FOOTER_HEIGHT + 4, WidgetTheme.TEXT_SECONDARY);
    }

    private void renderEdges(GuiGraphics context, GraphSnapshot snapshot, Layout layout) {
        Map<String, PositionedNode> visibleNodes = layout.nodesById();

        for (GraphEdgeSnapshot edge : snapshot.edges()) {
            PositionedNode from = visibleNodes.get(edge.fromNodeId());
            PositionedNode to = visibleNodes.get(edge.toNodeId());
            if (from == null || to == null) {
                continue;
            }

            int color = edgeColor(edge, snapshot.capturedAt());
            drawEdge(context, from.dotX(), from.dotY(), to.dotX(), to.dotY(), color, edge, snapshot.capturedAt());
        }
    }

    private void renderNodes(GuiGraphics context, Minecraft mc, GraphSnapshot snapshot, Layout layout) {
        for (PositionedNode node : layout.nodes()) {
            GraphNodeSnapshot snapshotNode = node.node();
            int dotColor = nodeColor(snapshotNode, snapshot.capturedAt());
            int ringColor = ringColor(snapshotNode, snapshot.capturedAt());

            fillCircle(context, node.dotX(), node.dotY(), NODE_RING_RADIUS, ringColor);
            fillCircle(context, node.dotX(), node.dotY(), NODE_RADIUS, dotColor);
        }
    }

    private Layout buildLayout(GraphSnapshot snapshot) {
        List<GraphNodeSnapshot> inputs = selectVisibleNodes(snapshot.nodesByKind(GraphNodeKind.INPUT));
        List<GraphNodeSnapshot> decoders = selectVisibleNodes(follow(snapshot, inputs, GraphNodeKind.DECODER));
        List<GraphNodeSnapshot> handlers = selectVisibleNodes(follow(snapshot, decoders, GraphNodeKind.HANDLER));
        List<GraphNodeSnapshot> stores = selectVisibleNodes(follow(snapshot, handlers, GraphNodeKind.STORE));
        List<GraphNodeSnapshot> states = selectVisibleNodes(follow(snapshot, handlers, GraphNodeKind.STATE));
        List<GraphNodeSnapshot> widgets = selectVisibleNodes(follow(snapshot, handlers, GraphNodeKind.WIDGET));
        List<GraphNodeSnapshot> roots = new ArrayList<>(snapshot.nodesByKind(GraphNodeKind.ROOT));

        int graphTop = HEADER_HEIGHT + PADDING_Y + 8;
        int graphBottom = getGraphAreaHeight(snapshot) + HEADER_HEIGHT - 4;

        List<PositionedNode> positioned = new ArrayList<>();
        positioned.addAll(positionCloud(roots, 36, graphTop, graphBottom, 0.50, 0, 0));
        positioned.addAll(positionCloud(inputs, 130, graphTop, graphBottom, 0.50, 10, 8));
        positioned.addAll(positionCloud(decoders, 286, graphTop, graphBottom, 0.44, 16, 10));
        positioned.addAll(positionCloud(handlers, 452, graphTop, graphBottom, 0.56, 16, 10));
        positioned.addAll(positionCloud(stores, 596, graphTop, graphBottom, 0.32, 10, 8));
        positioned.addAll(positionCloud(states, 652, graphTop, graphBottom, 0.55, 10, 8));
        positioned.addAll(positionCloud(widgets, 708, graphTop, graphBottom, 0.76, 10, 8));
        return new Layout(positioned);
    }

    private List<GraphNodeSnapshot> follow(GraphSnapshot snapshot, Collection<GraphNodeSnapshot> sources, GraphNodeKind targetKind) {
        Map<String, GraphNodeSnapshot> byId = snapshot.nodes().stream()
                .collect(LinkedHashMap::new, (map, node) -> map.put(node.id(), node), Map::putAll);
        List<GraphNodeSnapshot> result = new ArrayList<>();

        for (GraphEdgeSnapshot edge : snapshot.edges()) {
            boolean linked = sources.stream().anyMatch(source -> source.id().equals(edge.fromNodeId()));
            if (!linked) {
                continue;
            }

            GraphNodeSnapshot target = byId.get(edge.toNodeId());
            if (target == null || target.kind() != targetKind || result.stream().anyMatch(existing -> existing.id().equals(target.id()))) {
                continue;
            }
            result.add(target);
        }

        result.sort(Comparator.comparingLong(GraphNodeSnapshot::lastActiveAt).reversed().thenComparing(GraphNodeSnapshot::label));
        return result;
    }

    private List<GraphNodeSnapshot> selectVisibleNodes(Collection<GraphNodeSnapshot> nodes) {
        return nodes.stream()
                .sorted(Comparator.comparingLong(GraphNodeSnapshot::lastActiveAt).reversed().thenComparingInt(GraphNodeSnapshot::hits).reversed().thenComparing(GraphNodeSnapshot::label))
                .toList();
    }

    private List<PositionedNode> positionCloud(List<GraphNodeSnapshot> nodes, int anchorX, int top, int bottom, double phase, int spreadX, int spreadY) {
        List<PositionedNode> positioned = new ArrayList<>();
        if (nodes.isEmpty()) {
            return positioned;
        }

        double gap = nodes.size() == 1 ? 0.0 : (double) (bottom - top) / (nodes.size() - 1);
        int midY = (top + bottom) / 2;
        for (int index = 0; index < nodes.size(); index++) {
            double wave = Math.sin((index + 1) * 0.9 + phase) * spreadX;
            double bend = Math.cos((index + 1) * 0.7 + phase) * spreadY;
            int baseY = nodes.size() == 1 ? midY : top + (int) Math.round(index * gap);
            int dotX = anchorX + (int) Math.round(wave);
            int dotY = baseY + (int) Math.round(bend);
            positioned.add(new PositionedNode(nodes.get(index), dotX, dotY));
        }
        return resolveVerticalOverlaps(positioned, top, bottom);
    }

    private List<PositionedNode> resolveVerticalOverlaps(List<PositionedNode> nodes, int top, int bottom) {
        List<PositionedNode> sorted = new ArrayList<>(nodes);
        sorted.sort(Comparator.comparingInt(PositionedNode::dotY));

        List<PositionedNode> adjusted = new ArrayList<>();
        int previousY = Integer.MIN_VALUE / 4;
        for (PositionedNode node : sorted) {
            int dotY = Math.max(node.dotY(), top);
            if (dotY - previousY < NODE_MIN_VERTICAL_GAP) {
                dotY = previousY + NODE_MIN_VERTICAL_GAP;
            }
            int clampedY = Math.min(dotY, bottom);
            adjusted.add(new PositionedNode(node.node(), node.dotX(), clampedY));
            previousY = Math.min(dotY, bottom);
        }

        for (int index = adjusted.size() - 2; index >= 0; index--) {
            PositionedNode current = adjusted.get(index);
            PositionedNode next = adjusted.get(index + 1);
            int allowedY = next.dotY() - NODE_MIN_VERTICAL_GAP;
            if (current.dotY() > allowedY) {
                int newY = Math.max(top, allowedY);
                adjusted.set(index, new PositionedNode(current.node(), current.dotX(), newY));
            }
        }

        adjusted.sort(Comparator.comparing(node -> node.node().label()));
        List<PositionedNode> restored = new ArrayList<>();
        for (PositionedNode original : nodes) {
            PositionedNode match = adjusted.stream()
                    .filter(candidate -> candidate.node().id().equals(original.node().id()))
                    .findFirst()
                    .orElse(original);
            restored.add(match);
        }
        return restored;
    }

    private void drawEdge(GuiGraphics context, int x1, int y1, int x2, int y2, int color, GraphEdgeSnapshot edge, long now) {
        drawSegment(context, x1, y1, x2, y2, color);

        for (long pulseStartedAt : edge.pulseStartedAt()) {
            if (!shouldRenderPulse(pulseStartedAt, now)) {
                continue;
            }

            double progress = pulseProgress(pulseStartedAt, now);
            int pulseX = lerp(x1, x2, progress);
            int pulseY = lerp(y1, y2, progress);
            int pulseColor = WidgetTheme.withAlpha(WidgetTheme.TITLE, pulseAlpha(pulseStartedAt, now));
            fillCircle(context, pulseX, pulseY, 2, pulseColor);
        }
    }

    private void drawSegment(GuiGraphics context, int x1, int y1, int x2, int y2, int color) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps <= 0) {
            fillCircle(context, x1, y1, EDGE_THICKNESS, color);
            return;
        }

        for (int step = 0; step <= steps; step++) {
            double t = step / (double) steps;
            int x = x1 + (int) Math.round(dx * t);
            int y = y1 + (int) Math.round(dy * t);
            fillCircle(context, x, y, EDGE_THICKNESS, color);
        }
    }

    private void fillCircle(GuiGraphics context, int centerX, int centerY, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dy * dy <= radius * radius) {
                    context.fill(centerX + dx, centerY + dy, centerX + dx + 1, centerY + dy + 1, color);
                }
            }
        }
    }

    private int nodeColor(GraphNodeSnapshot node, long now) {
        if (node.errors() > 0 && isRecentlyActive(node.lastActiveAt(), now)) {
            return WidgetTheme.STATUS_WARNING;
        }

        return switch (node.kind()) {
            case ROOT -> isRecentlyActive(node.lastActiveAt(), now) ? WidgetTheme.TITLE : WidgetTheme.TEXT_SECONDARY;
            case INPUT -> isRecentlyActive(node.lastActiveAt(), now) ? WidgetTheme.TEXT_SOFT : WidgetTheme.TEXT_SECONDARY;
            case DECODER -> isRecentlyActive(node.lastActiveAt(), now) ? WidgetTheme.TEXT_ACCENT : WidgetTheme.TEXT_MUTED;
            case HANDLER -> isRecentlyActive(node.lastActiveAt(), now) ? WidgetTheme.STATUS_SUCCESS : WidgetTheme.TEXT_MUTED;
            case STORE -> isRecentlyActive(node.lastActiveAt(), now) ? WidgetTheme.STATUS_INFO : WidgetTheme.TEXT_MUTED;
            case STATE -> isRecentlyActive(node.lastActiveAt(), now) ? WidgetTheme.TEXT_PRIMARY : WidgetTheme.TEXT_MUTED;
            case WIDGET -> isRecentlyActive(node.lastActiveAt(), now) ? WidgetTheme.TITLE : WidgetTheme.TEXT_MUTED;
        };
    }

    private int ringColor(GraphNodeSnapshot node, long now) {
        if (node.errors() > 0 && isRecentlyActive(node.lastActiveAt(), now)) {
            return WidgetTheme.withAlpha(WidgetTheme.STATUS_WARNING, 0x3C);
        }
        if (isRecentlyActive(node.lastActiveAt(), now)) {
            return WidgetTheme.withAlpha(WidgetTheme.WIDGET_ACCENT_LINE, 0x34);
        }
        return WidgetTheme.withAlpha(WidgetTheme.TEXT_MUTED, 0x18);
    }

    private int edgeColor(GraphEdgeSnapshot edge, long now) {
        if (edge.errors() > 0 && isRecentlyActive(edge.lastActiveAt(), now)) {
            return WidgetTheme.withAlpha(WidgetTheme.STATUS_WARNING, 0xB0);
        }
        if (isRecentlyActive(edge.lastActiveAt(), now)) {
            return WidgetTheme.withAlpha(WidgetTheme.WIDGET_ACCENT_LINE, 0x98);
        }
        return WidgetTheme.withAlpha(WidgetTheme.TEXT_MUTED, 0x34);
    }

    private boolean isRecentlyActive(long lastActiveAt, long now) {
        return lastActiveAt > 0L && now - lastActiveAt <= ProtocolGraphTelemetry.ACTIVE_WINDOW_MS;
    }

    private int getBaseWidth() {
        return BASE_WIDTH;
    }

    private int getBaseHeight(GraphSnapshot snapshot) {
        return HEADER_HEIGHT + getGraphAreaHeight(snapshot) + FOOTER_HEIGHT + PADDING_Y;
    }

    private int getGraphAreaHeight(GraphSnapshot snapshot) {
        boolean hasActivity = snapshot.nodes().stream().anyMatch(node -> node.hits() > 0);
        if (!hasActivity) {
            return EMPTY_GRAPH_HEIGHT;
        }

        int maxColumnNodes = Math.max(
                Math.max(snapshot.nodesByKind(GraphNodeKind.INPUT).size(), snapshot.nodesByKind(GraphNodeKind.DECODER).size()),
                Math.max(
                        Math.max(snapshot.nodesByKind(GraphNodeKind.HANDLER).size(), snapshot.nodesByKind(GraphNodeKind.STORE).size()),
                        Math.max(snapshot.nodesByKind(GraphNodeKind.STATE).size(), snapshot.nodesByKind(GraphNodeKind.WIDGET).size())
                )
        );
        int requiredHeight = 60 + Math.max(0, maxColumnNodes - 1) * NODE_MIN_VERTICAL_GAP;
        return Math.max(MIN_GRAPH_HEIGHT, requiredHeight);
    }

    private void renderNodeTooltip(GuiGraphics context, Minecraft mc, GraphNodeSnapshot node, int mouseX, int mouseY, long now) {
        String age = node.lastActiveAt() <= 0L ? "never" : formatAge(now - node.lastActiveAt());
        List<Component> lines = List.of(
                Component.literal(node.label()),
                Component.literal("kind: " + node.kind().name().toLowerCase()),
                Component.literal("hits: " + node.hits() + "  errors: " + node.errors()),
                Component.literal("bytes: " + node.bytes()),
                Component.literal("last: " + age)
        );
        context.renderTooltip(mc.font, lines, Optional.empty(), mouseX, mouseY);
    }

    private String formatAge(long ageMs) {
        if (ageMs < 1_000L) {
            return ageMs + " ms ago";
        }
        if (ageMs < 60_000L) {
            return (ageMs / 1_000L) + " s ago";
        }
        return (ageMs / 60_000L) + " min ago";
    }

    private boolean shouldRenderPulse(long pulseStartedAt, long now) {
        return pulseStartedAt > 0L && now - pulseStartedAt <= ProtocolGraphTelemetry.EDGE_PULSE_WINDOW_MS;
    }

    private double pulseProgress(long pulseStartedAt, long now) {
        long elapsed = Math.max(0L, now - pulseStartedAt);
        return Math.max(0.0, Math.min(1.0, elapsed / (double) ProtocolGraphTelemetry.EDGE_PULSE_WINDOW_MS));
    }

    private int pulseAlpha(long pulseStartedAt, long now) {
        long elapsed = Math.max(0L, now - pulseStartedAt);
        double life = Math.max(0.0, 1.0 - elapsed / (double) ProtocolGraphTelemetry.EDGE_PULSE_WINDOW_MS);
        return Math.max(72, (int) Math.round(255 * life));
    }

    private int lerp(int start, int end, double t) {
        return (int) Math.round(start + (end - start) * t);
    }

    private record Layout(List<PositionedNode> nodes) {
        private Map<String, PositionedNode> nodesById() {
            Map<String, PositionedNode> result = new LinkedHashMap<>();
            for (PositionedNode node : nodes) {
                result.put(node.node().id(), node);
            }
            return result;
        }

        private PositionedNode findHoveredNode(double localMouseX, double localMouseY) {
            for (PositionedNode node : nodes) {
                double dx = localMouseX - node.dotX();
                double dy = localMouseY - node.dotY();
                if (dx * dx + dy * dy <= (NODE_RING_RADIUS + 2) * (NODE_RING_RADIUS + 2)) {
                    return node;
                }
            }
            return null;
        }
    }

    private record PositionedNode(GraphNodeSnapshot node, int dotX, int dotY) {
    }
}
