package ru.wilyfox.client.hud.internal;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.*;
import com.mojang.blaze3d.textures.GpuTextureView;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.WidgetCatalog;
import ru.wilyfox.client.hud.widget.*;
import java.util.*;
import ru.wilyfox.client.profiler.ModProfiler;

/** One bounded snapshot per widget, replayed only during a 500 ms departure. */
public final class WidgetLayoutAnimation {
    private final Map<Widget, Entry> entries = new LinkedHashMap<>();
    private long observedResourceEpoch = resourceEpoch();
    private static volatile long resourceEpoch;
    public static void invalidateResources() { resourceEpoch++; }
    private static long resourceEpoch() { return resourceEpoch; }

    public void clear() { entries.clear(); observedResourceEpoch = resourceEpoch(); }
    public void reset(List<Widget> all, List<Widget> active) {
        clear();
        for (Widget widget : all) entries.put(widget, new Entry(new WidgetTransition(inLayout(widget, active),
                widget.getStartX(), widget.getStartY(), scale(widget))));
    }
    public void change(List<Widget> all, List<Widget> active, long now) {
        for (Widget widget : all) {
            Entry entry = entries.computeIfAbsent(widget, ignored -> new Entry(new WidgetTransition(false,
                    widget.getStartX(), widget.getStartY(), scale(widget))));
            entry.transition.retarget(inLayout(widget, active), widget.getStartX(), widget.getStartY(), scale(widget), now);
        }
    }
    public void beforePlacement(List<Widget> active) {
        // Account for anchors/auto-sized widgets since the previous location change.
        for (Widget widget : active) {
            Entry entry = entries.get(widget);
            if (entry != null && entry.transition.visible())
                entry.transition.destination(widget.getStartX(), widget.getStartY(), scale(widget));
        }
    }
    public void checkResources(List<Widget> all, List<Widget> active) {
        if (observedResourceEpoch != resourceEpoch()) reset(all, active);
    }
    public void hiddenData(Widget widget) {
        Entry entry = entries.get(widget);
        // Do not resurrect old quests/popups after they have already disappeared normally.
        // Layout departures retain their previous frame until their fade has finished.
        if (entry != null && entry.transition.visible()) entry.snapshot.clear();
    }
    public void editorAdded(Widget widget, long now) {
        Entry entry = entries.computeIfAbsent(widget, ignored -> new Entry(new WidgetTransition(true,
                widget.getStartX(), widget.getStartY(), scale(widget))));
        if (entry.editorSize == null) entry.editorSize = new WidgetSizeTransition(false);
        entry.editorSize.target(true, now);
    }
    public void editorRemoved(Widget widget, long now) {
        Entry entry = entries.get(widget);
        if (entry == null) return; // Nothing was drawn yet.
        if (entry.editorSize == null) entry.editorSize = new WidgetSizeTransition(true);
        entry.editorSize.target(false, now);
    }
    public boolean hasDepartures(long now, boolean editor) {
        if (!editor) return hasDepartures(now);
        boolean found = false;
        for (Entry entry : entries.values()) {
            if (entry.editorSize == null || entry.editorSize.present()) continue;
            if (entry.editorSize.size(now) <= 0) entry.snapshot.clear();
            else if (!entry.snapshot.isEmpty() && entry.snapshotSize > 0) found = true;
        }
        return found;
    }
    public void renderEditor(Widget widget, GuiGraphicsExtractor context, long now, Runnable draw) {
        Entry entry = entries.get(widget);
        if (entry == null) { draw.run(); return; }
        float size = entry.editorSize == null ? 1 : entry.editorSize.size(now);
        if (size <= 0) return;
        float cx = widget.getStartX() + widget.getWidth() / 2f;
        float cy = widget.getStartY() + widget.getHeight() / 2f;
        entry.staging.clear();
        context.pose().pushMatrix();
        try (var scope = new WidgetRenderScope(entry.staging, 1)) {
            try (var profile = ModProfiler.getInstance().scope("hud/animation/editorTransform")) {
                context.pose().translate(cx, cy).scale(size, size).translate(-cx, -cy);
            }
            draw.run();
            try (var profile = ModProfiler.getInstance().scope("hud/animation/snapshot")) {
                var previous = entry.snapshot; entry.snapshot = entry.staging; entry.staging = previous;
                entry.staging.clear();
                entry.snapshotSize = size; entry.snapshotCenterX = cx; entry.snapshotCenterY = cy;
                ModProfiler.getInstance().incrementCounter("hud/animation/snapshotElements", entry.snapshot.size());
            }
        } finally { context.pose().popMatrix(); }
    }
    public boolean hasDepartures(long now) {
        boolean found = false;
        for (Entry entry : entries.values()) {
            if (entry.transition.visible()) continue;
            if (entry.transition.alpha(now) <= 0) entry.snapshot.clear();
            else if (!entry.snapshot.isEmpty()) found = true;
        }
        return found;
    }
    public void render(Widget widget, GuiGraphicsExtractor context, DeltaTracker delta, long now) {
        Entry entry = entries.get(widget);
        if (entry == null) { widget.render(context, delta); return; }
        if (!entry.transition.visible()) return;
        float scale = scale(widget);
        entry.transition.destination(widget.getStartX(), widget.getStartY(), scale);
        entry.staging.clear();
        context.pose().pushMatrix();
        try (var scope = new WidgetRenderScope(entry.staging, entry.transition.alpha(now))) {
            try (var profile = ModProfiler.getInstance().scope("hud/animation/locationTransform")) {
                context.pose().translate(entry.transition.x(now), entry.transition.y(now));
                float ratio = entry.transition.scale(now) / scale;
                context.pose().scale(ratio, ratio);
                context.pose().translate(-widget.getStartX(), -widget.getStartY());
            }
            widget.render(context, delta);
            try (var profile = ModProfiler.getInstance().scope("hud/animation/snapshot")) {
                var previous = entry.snapshot; entry.snapshot = entry.staging; entry.staging = previous;
                entry.staging.clear();
                ModProfiler.getInstance().incrementCounter("hud/animation/snapshotElements", entry.snapshot.size());
            }
        } finally { context.pose().popMatrix(); }
    }
    public void renderDepartures(GuiGraphicsExtractor context, long now, ru.wilyfox.client.hud.layer.HudLayer layer) {
        renderDepartures(context, now, layer, false);
    }
    public void renderDepartures(GuiGraphicsExtractor context, long now, ru.wilyfox.client.hud.layer.HudLayer layer, boolean editor) {
        try (var profile = ModProfiler.getInstance().scope("hud/animation/departures")) {
            var state = ((WidgetGuiState) context).froghelper$guiState();
            for (var pair : entries.entrySet()) {
                Entry entry = pair.getValue();
                if (layer != null && pair.getKey().getLayer() != layer) continue;
                org.joml.Matrix3x2fc transform = null;
                float alpha;
                if (editor) {
                    if (entry.editorSize == null || entry.editorSize.present() || entry.snapshotSize <= 0) continue;
                    float size = entry.editorSize.size(now);
                    if (size <= 0) { entry.snapshot.clear(); continue; }
                    float ratio = size / entry.snapshotSize;
                    transform = new org.joml.Matrix3x2f().translate(entry.snapshotCenterX, entry.snapshotCenterY)
                            .scale(ratio).translate(-entry.snapshotCenterX, -entry.snapshotCenterY);
                    alpha = 1;
                } else {
                    if (entry.transition.visible()) continue;
                    alpha = entry.transition.alpha(now);
                }
                if (alpha <= 0) { entry.snapshot.clear(); continue; }
                ModProfiler.getInstance().incrementCounter("hud/animation/departureWidgets");
                ModProfiler.getInstance().incrementCounter("hud/animation/replayedElements", entry.snapshot.size());
                try (var scope = new WidgetRenderScope(null, alpha, transform)) {
                    for (Object element : entry.snapshot) {
                        if (element instanceof GuiElementRenderState gui) {
                            var textures = gui.textureSetup();
                            if (live(textures.texure0()) && live(textures.texure1()) && live(textures.texure2())) state.addGuiElement(gui);
                        } else if (element instanceof GuiTextRenderState text) state.addText(text);
                        else if (element instanceof GuiItemRenderState item) state.addItem(item);
                    }
                }
            }
        }
    }
    private static boolean live(GpuTextureView view) { return view == null || !view.isClosed() && !view.texture().isClosed(); }
    private static float scale(Widget widget) { return widget instanceof AbstractWidget placed ? placed.getScale() : 1; }
    private static boolean inLayout(Widget widget, List<Widget> active) {
        String key = widget instanceof AbstractWidget placed ? placed.getConfigKey() : widget.getClass().getSimpleName();
        return active.contains(widget) && (WidgetCatalog.find(key) == null || ConfigManager.isWidgetInCurrentLayout(key));
    }
    private static final class Entry {
        final WidgetTransition transition;
        WidgetSizeTransition editorSize;
        float snapshotSize = 1, snapshotCenterX, snapshotCenterY;
        List<Object> snapshot = new ArrayList<>(), staging = new ArrayList<>();
        Entry(WidgetTransition transition) { this.transition = transition; }
    }
}
