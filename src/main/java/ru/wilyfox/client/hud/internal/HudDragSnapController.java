package ru.wilyfox.client.hud.internal;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import ru.wilyfox.client.hud.config.HudSnapConfig;
import ru.wilyfox.client.hud.indicators.ScreenAnchor;
import ru.wilyfox.client.hud.snap.SnapSession;
import ru.wilyfox.client.hud.widget.*;
import ru.wilyfox.client.profiler.ModProfiler;
import java.util.*;

/** Bridges a pure SnapSession to widget positions; relations are committed only on release. */
public final class HudDragSnapController {
    private SnapSession session;
    private AbstractWidget root;
    private final Map<String, AbstractWidget> targets = new HashMap<>();
    private final Set<String> movingIds = new HashSet<>();
    private List<? extends Widget> moving = List.of();
    private final Map<Widget, Long> movingSizes = new IdentityHashMap<>();
    private int rawX, rawY;
    private double lastScale;
    private boolean hasMoved, lastBypass;
    private int screenWidth, screenHeight;
    public void begin(AbstractWidget root, List<? extends Widget> moving, List<Widget> widgets, int width, int height) {
        cancel(); this.root = root; this.moving = List.copyOf(moving);
        rawX = root.getStartX(); rawY = root.getStartY();
        rebuild(widgets, width, height);
    }
    private void rebuild(List<Widget> widgets, int width, int height) {
        screenWidth = width; screenHeight = height;
        targets.clear(); movingIds.clear(); movingSizes.clear();
        double left = Double.POSITIVE_INFINITY, top = Double.POSITIVE_INFINITY, right = Double.NEGATIVE_INFINITY, bottom = Double.NEGATIVE_INFINITY;
        for (Widget widget : moving) {
            if (widget instanceof AbstractWidget placed) movingIds.add(placed.getConfigKey());
            movingSizes.put(widget, size(widget));
            left = Math.min(left, widget.getLeft()); top = Math.min(top, widget.getTop());
            right = Math.max(right, widget.getRight()); bottom = Math.max(bottom, widget.getBottom());
        }
        var geometry = new ArrayList<SnapSession.Target>();
        for (Widget widget : widgets) if (widget instanceof AbstractWidget placed && placed.getConfigKey() != null && !movingIds.contains(placed.getConfigKey())) {
            targets.put(placed.getConfigKey(), placed);
            geometry.add(new SnapSession.Target(placed.getConfigKey(), rect(placed)));
        }
        int hotbarLeft = HudScreenAnchorHelper.getHotbarLeftAnchorX(width, 182, 0, 29);
        int hotbarRight = HudScreenAnchorHelper.getHotbarRightAnchorX(width, 182, 0, 29);
        geometry.add(new SnapSession.Target("@hotbar", new SnapSession.Rect(hotbarLeft, height - 22, hotbarRight - hotbarLeft, 22)));
        var previous = session;
        session = new SnapSession(rect(root), new SnapSession.Rect(left, top, right - left, bottom - top), geometry, movingIds,
                moving.stream().map(HudDragSnapController::rect).toList());
        if (previous != null) session.inheritGuides(previous);
    }
    public void move(int rawX, int rawY, double scale, HudSnapConfig config, boolean bypass) {
        if (session == null || root == null) return;
        this.rawX = rawX; this.rawY = rawY; lastScale = scale; lastBypass = bypass; hasMoved = true;
        try (var profile = ModProfiler.getInstance().scope("hud/snap/solve")) {
            var result = session.solve(rawX, rawY, screenWidth, screenHeight, scale, config, bypass);
            root.setStartX((int) Math.round(result.x())); root.setStartY((int) Math.round(result.y()));
        }
    }
    /** No geometry rebuild on ordinary frames; pointer correction is refreshed only when needed. */
    public void refresh(List<Widget> widgets, int width, int height, double scale, HudSnapConfig config, boolean bypass) {
        if (session == null || root == null || !hasMoved) return;
        boolean changed = width != screenWidth || height != screenHeight
                || moving.stream().anyMatch(widget -> !Objects.equals(movingSizes.get(widget), size(widget)));
        int targetCount = 0;
        for (Widget widget : widgets) if (widget instanceof AbstractWidget placed && placed.getConfigKey() != null && !movingIds.contains(placed.getConfigKey())) {
            targetCount++;
            if (targets.get(placed.getConfigKey()) != placed) changed = true;
        }
        if (targetCount != targets.size()) changed = true;
        for (var target : session.targets()) {
            if (target.id().equals("@hotbar")) {
                int left = HudScreenAnchorHelper.getHotbarLeftAnchorX(width, 182, 0, 29);
                int right = HudScreenAnchorHelper.getHotbarRightAnchorX(width, 182, 0, 29);
                changed |= !target.bounds().equals(new SnapSession.Rect(left, height - 22, right - left, 22));
            } else {
                var widget = targets.get(target.id());
                changed |= widget == null || !target.bounds().equals(rect(widget));
            }
        }
        if (changed) rebuild(widgets, width, height);
        if (changed || lastBypass != bypass || lastScale != scale) move(rawX, rawY, scale, config, bypass);
    }
    public void commit(HudSnapConfig config) {
        if (session == null || root == null || session.result() == null || !config.enabled || config.behavior == HudSnapConfig.Behavior.ALIGN_ONLY) return;
        var result = session.result();
        SnapSession.Candidate x = result.horizontal(), y = result.vertical();
        var dock = docking(x) ? x : docking(y) ? y : null;
        if (dock != null) {
            var target = targets.get(dock.targetId());
            if (target == null || movingIds.contains(target.getConfigKey()) || createsCycle(root, target)) return;
            WidgetCorner own, other;
            if (dock.axis() == SnapSession.Axis.X) {
                boolean bottom = y != null && y.targetId().equals(dock.targetId()) && y.own() == SnapSession.Edge.END && y.targetEdge() == SnapSession.Edge.END;
                boolean onRight = dock.own() == SnapSession.Edge.START;
                own = bottom ? onRight ? WidgetCorner.BOTTOM_LEFT : WidgetCorner.BOTTOM_RIGHT
                        : onRight ? WidgetCorner.TOP_LEFT : WidgetCorner.TOP_RIGHT;
                other = bottom ? onRight ? WidgetCorner.BOTTOM_RIGHT : WidgetCorner.BOTTOM_LEFT
                        : onRight ? WidgetCorner.TOP_RIGHT : WidgetCorner.TOP_LEFT;
            } else {
                boolean right = x != null && x.targetId().equals(dock.targetId()) && x.own() == SnapSession.Edge.END && x.targetEdge() == SnapSession.Edge.END;
                boolean below = dock.own() == SnapSession.Edge.START;
                own = right ? below ? WidgetCorner.TOP_RIGHT : WidgetCorner.BOTTOM_RIGHT
                        : below ? WidgetCorner.TOP_LEFT : WidgetCorner.BOTTOM_LEFT;
                other = right ? below ? WidgetCorner.BOTTOM_RIGHT : WidgetCorner.TOP_RIGHT
                        : below ? WidgetCorner.BOTTOM_LEFT : WidgetCorner.TOP_LEFT;
            }
            int baseX = target.getCornerX(other) - (own == WidgetCorner.TOP_RIGHT || own == WidgetCorner.BOTTOM_RIGHT ? root.getWidth() : 0);
            int baseY = target.getCornerY(other) - (own == WidgetCorner.BOTTOM_LEFT || own == WidgetCorner.BOTTOM_RIGHT ? root.getHeight() : 0);
            baseX += (own == WidgetCorner.TOP_LEFT || own == WidgetCorner.BOTTOM_LEFT ? 1 : -1)
                    * (dock.axis() == SnapSession.Axis.X ? 5 : 0);
            baseY += (own == WidgetCorner.TOP_LEFT || own == WidgetCorner.TOP_RIGHT ? 1 : -1)
                    * (dock.axis() == SnapSession.Axis.Y ? 5 : 0);
            root.setWidgetSnap(target.getConfigKey(), own, other, root.getStartX() - baseX, root.getStartY() - baseY);
            root.setScreenAnchor(null);
            return;
        }
        ScreenAnchor anchor = anchor(x, y);
        if (anchor != null) {
            var placement = HudScreenAnchorHelper.getAnchorPlacement(anchor, root, screenWidth, screenHeight, 8, 182, 22, 6, 29);
            root.clearWidgetSnap(); root.setScreenAnchor(anchor);
            root.setAnchorOffsets(root.getStartX() - placement.startX(), root.getStartY() - placement.startY());
        }
    }
    private static boolean docking(SnapSession.Candidate c) { return c != null && c.kind() == SnapSession.Kind.DOCK && !c.targetId().startsWith("@"); }
    private boolean createsCycle(AbstractWidget child, AbstractWidget parent) {
        var seen = new HashSet<String>();
        for (AbstractWidget current = parent; current != null; current = targets.get(current.getSnapTargetKey())) {
            if (Objects.equals(current.getConfigKey(), child.getConfigKey()) || !seen.add(current.getConfigKey())) return true;
            if (!current.hasWidgetSnap()) return false;
            if (Objects.equals(current.getSnapTargetKey(), child.getConfigKey())) return true;
        }
        return false;
    }
    private static ScreenAnchor anchor(SnapSession.Candidate x, SnapSession.Candidate y) {
        if (x != null && x.kind() == SnapSession.Kind.DOCK && x.targetId().equals("@hotbar"))
            return x.own() == SnapSession.Edge.START ? ScreenAnchor.HOTBAR_RIGHT : ScreenAnchor.HOTBAR_LEFT;
        boolean sx = x != null && x.kind() == SnapSession.Kind.SCREEN, sy = y != null && y.kind() == SnapSession.Kind.SCREEN;
        if (!sx && !sy) return null;
        boolean cx = sx && x.own() == SnapSession.Edge.CENTER, cy = sy && y.own() == SnapSession.Edge.CENTER;
        boolean right = sx && x.own() == SnapSession.Edge.END, bottom = sy && y.own() == SnapSession.Edge.END;
        if (cx && cy) return ScreenAnchor.CENTER;
        if (cx) return bottom ? ScreenAnchor.BOTTOM_CENTER : ScreenAnchor.TOP_CENTER;
        if (cy) return right ? ScreenAnchor.RIGHT_CENTER : ScreenAnchor.LEFT_CENTER;
        return bottom ? right ? ScreenAnchor.BOTTOM_RIGHT : ScreenAnchor.BOTTOM_LEFT : right ? ScreenAnchor.TOP_RIGHT : ScreenAnchor.TOP_LEFT;
    }
    public void drawGuides(GuiGraphicsExtractor graphics, HudSnapConfig config) {
        if (!config.showGuides || session == null || session.result() == null) return;
        var result = session.result();
        guide(graphics, result.horizontal()); guide(graphics, result.vertical());
    }
    private static void guide(GuiGraphicsExtractor graphics, SnapSession.Candidate c) {
        if (c == null) return;
        int color = WidgetTheme.ACCENT_LINE | 0xC0000000;
        int line = (int) Math.round(c.line()), from = (int) Math.floor(c.from()) - 4, to = (int) Math.ceil(c.to()) + 4;
        if (c.axis() == SnapSession.Axis.X) {
            graphics.fill(line, from, line + 1, to, color);
            graphics.fill(line - 3, from, line + 4, from + 1, color); graphics.fill(line - 3, to - 1, line + 4, to, color);
        } else {
            graphics.fill(from, line, to, line + 1, color);
            graphics.fill(from, line - 3, from + 1, line + 4, color); graphics.fill(to - 1, line - 3, to, line + 4, color);
        }
        if (c.kind() == SnapSession.Kind.GAP) graphics.text(Minecraft.getInstance().font,
                Integer.toString((int) Math.round(c.gap())), c.axis() == SnapSession.Axis.X ? line + 3 : to + 3,
                c.axis() == SnapSession.Axis.X ? from : line + 3, WidgetTheme.TEXT_ACCENT);
    }
    public void cancel() {
        session = null; root = null; moving = List.of(); targets.clear(); movingIds.clear(); movingSizes.clear(); hasMoved = false;
    }
    public SnapSession.Result result() { return session == null ? null : session.result(); }
    private static SnapSession.Rect rect(Widget w) { return new SnapSession.Rect(w.getStartX(), w.getStartY(), w.getWidth(), w.getHeight()); }
    private static long size(Widget w) { return (long) w.getWidth() << 32 | Integer.toUnsignedLong(w.getHeight()); }
}
