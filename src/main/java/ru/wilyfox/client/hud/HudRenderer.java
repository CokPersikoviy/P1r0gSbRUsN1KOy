package ru.wilyfox.client.hud;

import ru.wilyfox.client.audio.UiSounds;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.WidgetLayoutConfig;
import ru.wilyfox.client.hud.config.WidgetCatalog;
import ru.wilyfox.client.hud.config.HudLayoutResolver;
import ru.wilyfox.client.hud.config.LocationWidgetLayoutConfig;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;
import ru.wilyfox.client.hud.internal.HudWidgetSidebar;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.client.hud.fishing.FishingSpotOverlayRenderer;
import ru.wilyfox.client.alchemy.AlchemyIngredientOverlayRenderer;
import ru.wilyfox.client.hud.internal.HudFrameClock;
import ru.wilyfox.client.hud.internal.WidgetPlacement;
import ru.wilyfox.client.hud.internal.HudEditorOverlayHost;
import ru.wilyfox.client.hud.internal.HudEditorOverlayRenderer;
import ru.wilyfox.client.hud.internal.HudGroupDragController;
import ru.wilyfox.client.hud.internal.HudSnapGraphNormalizer;
import ru.wilyfox.client.hud.internal.HudSnapLayoutEngine;
import ru.wilyfox.client.hud.internal.HudSnapLayoutHost;
import ru.wilyfox.client.hud.internal.HudScreenAnchorHelper;
import ru.wilyfox.client.hud.internal.HudSnapGroupResolver;
import ru.wilyfox.client.hud.indicators.ScreenAnchor;
import ru.wilyfox.client.hud.indicators.CornerSnapIndicator;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.hud.menu.HudSettingsPanel;
import ru.wilyfox.client.hud.widget.AbstractWidget;
import ru.wilyfox.client.hud.widget.BossHudWidget;
import ru.wilyfox.client.hud.widget.HudBlur;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.Widget;
import ru.wilyfox.client.hud.widget.WidgetTheme;
import ru.wilyfox.client.profiler.ModProfiler;
import ru.wilyfox.utils.MouseUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class HudRenderer {
    private final ru.wilyfox.client.hud.internal.WidgetLayoutAnimation layoutAnimation = new ru.wilyfox.client.hud.internal.WidgetLayoutAnimation();
    private final List<Widget> registeredWidgets = new ArrayList<>();
    private final List<Widget> widgets = new ArrayList<>();
    private final HudWidgetSidebar widgetSidebar = new HudWidgetSidebar(this);
    private Widget selectedWidget;
    private long widgetRegistryRevision;
    private String selectedLayout = HudLayoutResolver.MAIN;
    private long appliedLayoutRevision = -1;
    private Object appliedConfig;
    private String appliedLocation;
    private final Map<Widget, WidgetLayoutConfig> defaultPlacements = new IdentityHashMap<>();
    private HudSettingsPanel settingsPanel;

    private ScreenAnchor activeScreenAnchor = null;
    private CornerSnapIndicator activeDraggedCornerIndicator = null;
    private CornerSnapIndicator activeTargetCornerIndicator = null;

    private final int SCREEN_SNAP_MARGIN = 8;
    private final int SCREEN_SNAP_DISTANCE = 12;
    private final int WIDGET_SNAP_DISTANCE = 6;
    private final int GROUP_GAP = 5;
    private final int WIDGET_FRAME_PADDING = 4;
    private final int EDITOR_CONTROL_SIZE = 10;
    private final int EDITOR_CONTROL_MARGIN = 3;
    private final int HOTBAR_WIDTH = 182;
    private final int HOTBAR_HEIGHT = 22;
    private final int HOTBAR_ANCHOR_GAP = 6;
    private final int OFFHAND_SLOT_WIDTH = 29;

    private boolean editing = false;
    private boolean settingsOpen = false;
    private int lastScreenWidth = -1;
    private int lastScreenHeight = -1;
    private final Map<Widget, Integer> passiveLayoutWidthCache = new IdentityHashMap<>();
    private final Map<Widget, Integer> passiveLayoutHeightCache = new IdentityHashMap<>();
    private long nextPassiveLayoutCheck;
    private static final long PASSIVE_LAYOUT_INTERVAL_NANOS = 100_000_000L;

    private Widget draggedWidget = null;
    private Widget pendingLibraryDrag;
    private double libraryPressX, libraryPressY;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private boolean draggingWidgetGroup = false;
    private final List<AbstractWidget> draggedGroupWidgets = new ArrayList<>();
    private final Map<String, HudGroupDragController.GroupDragState> draggedGroupStates = new HashMap<>();
    private final HudSnapLayoutEngine snapLayoutEngine = new HudSnapLayoutEngine(
            widgets,
            SCREEN_SNAP_MARGIN,
            SCREEN_SNAP_DISTANCE,
            WIDGET_SNAP_DISTANCE,
            GROUP_GAP,
            HOTBAR_WIDTH,
            HOTBAR_HEIGHT,
            HOTBAR_ANCHOR_GAP,
            OFFHAND_SLOT_WIDTH
    );
    private final HudEditorOverlayHost overlayHost = new HudEditorOverlayHost() {
        @Override
        public ScreenAnchor getActiveScreenAnchor() {
            return activeScreenAnchor;
        }

        @Override
        public CornerSnapIndicator getActiveDraggedCornerIndicator() {
            return activeDraggedCornerIndicator;
        }

        @Override
        public CornerSnapIndicator getActiveTargetCornerIndicator() {
            return activeTargetCornerIndicator;
        }

        @Override
        public boolean isDraggingWidgetGroup() {
            return draggingWidgetGroup;
        }

        @Override
        public Widget getDraggedWidget() {
            return draggedWidget;
        }

        @Override
        public List<AbstractWidget> getDraggedGroupWidgets() {
            return draggedGroupWidgets;
        }

        @Override
        public List<AbstractWidget> getSnappedDescendants(AbstractWidget rootWidget) {
            return HudRenderer.this.getSnappedDescendants(rootWidget);
        }

        @Override
        public boolean isScreenAnchorOccupied(ScreenAnchor anchor, Widget ignoredWidget) {
            return HudRenderer.this.isScreenAnchorOccupied(anchor, ignoredWidget);
        }

        @Override
        public boolean isScreenAnchorCovered(int anchorX, int anchorY) {
            return HudRenderer.this.isScreenAnchorCovered(anchorX, anchorY);
        }

        @Override
        public int getHotbarLeftAnchorX(int screenWidth) {
            return HudRenderer.this.getHotbarLeftAnchorX(screenWidth);
        }

        @Override
        public int getHotbarRightAnchorX(int screenWidth) {
            return HudRenderer.this.getHotbarRightAnchorX(screenWidth);
        }

        @Override
        public int getHotbarAnchorY(int screenHeight) {
            return HudRenderer.this.getHotbarAnchorY(screenHeight);
        }

        @Override
        public boolean isCenterSideAnchor(ScreenAnchor anchor) {
            return HudRenderer.this.isCenterSideAnchor(anchor);
        }
    };
    private final HudSnapLayoutHost snapLayoutHost = new HudSnapLayoutHost() {
        @Override
        public Widget getDraggedWidget() {
            return draggedWidget;
        }

        @Override
        public void setActiveScreenAnchor(ScreenAnchor anchor) {
            activeScreenAnchor = anchor;
        }

        @Override
        public void setActiveDraggedCornerIndicator(CornerSnapIndicator indicator) {
            activeDraggedCornerIndicator = indicator;
        }

        @Override
        public void setActiveTargetCornerIndicator(CornerSnapIndicator indicator) {
            activeTargetCornerIndicator = indicator;
        }

        @Override
        public int getLastScreenWidth() {
            return lastScreenWidth;
        }

        @Override
        public int getLastScreenHeight() {
            return lastScreenHeight;
        }

        @Override
        public void setLastScreenWidth(int width) {
            lastScreenWidth = width;
        }

        @Override
        public void setLastScreenHeight(int height) {
            lastScreenHeight = height;
        }

        @Override
        public boolean isScreenAnchorOccupied(ScreenAnchor anchor, Widget ignoredWidget) {
            return HudRenderer.this.isScreenAnchorOccupied(anchor, ignoredWidget);
        }

        @Override
        public boolean isCenterSideAnchor(ScreenAnchor anchor) {
            return HudRenderer.this.isCenterSideAnchor(anchor);
        }
    };

    public HudRenderer(HudSettingsPanel s) {
        this.settingsPanel = s;
    }

    public boolean isEditing() {
        return editing;
    }

    public void setEditing(boolean editing) {
        if (this.editing == editing) return;
        layoutAnimation.clear();
        appliedConfig = null; // Editor transitions use exact destination positions, without animation.
        if (settingsPanel != null) settingsPanel.finishInteraction();
        this.editing = editing;
        if (editing && !ConfigManager.get().locationLayouts.containsKey(selectedLayout)) selectedLayout = HudLayoutResolver.MAIN;
        ConfigManager.setEditorLayout(editing ? selectedLayout : null);
        refreshLayout();
        nextPassiveLayoutCheck = 0;
        if (editing) widgetSidebar.open();

        if (!editing) {
            if (settingsPanel != null) settingsPanel.finishInteraction();
            selectedWidget = null;
            pendingLibraryDrag = null;
            draggedWidget = null;
            draggingWidgetGroup = false;
            draggedGroupWidgets.clear();
            draggedGroupStates.clear();
            activeScreenAnchor = null;
            activeDraggedCornerIndicator = null;
            activeTargetCornerIndicator = null;
        }
    }

    public void toggleSettings() {
        setSettings(!this.settingsOpen);
    }

    public void setSettings(boolean settings) {
        if (!settings && settingsPanel != null) settingsPanel.finishInteraction();
        if (!settings && settingsPanel != null && settingsPanel.isContextSettings()) settingsPanel.clearWidget();
        this.settingsOpen = settings;
        nextPassiveLayoutCheck = 0;
    }

    public boolean openWidgetSettings(Widget widget) {
        if (!editing || !widgets.contains(widget) || !settingsPanel.openWidget(widget, () -> setSettings(false))) return false;
        pendingLibraryDrag = null;
        draggedWidget = null;
        draggingWidgetGroup = false;
        draggedGroupWidgets.clear();
        draggedGroupStates.clear();
        selectedWidget = widget;
        setSettings(true);
        UiSounds.openClose();
        return true;
    }

    public String getSelectedLayout() { return selectedLayout; }
    public String getSelectedLayoutName() {
        var layout = ConfigManager.get().locationLayouts.get(selectedLayout);
        return layout == null ? "Main Layout" : layout.name;
    }
    public void selectLayout(String id) {
        if (!editing) return;
        if (settingsOpen) setSettings(false);
        saveAllWidgetLayouts();
        selectedLayout = ConfigManager.get().locationLayouts.containsKey(id) ? id : HudLayoutResolver.MAIN;
        ConfigManager.setEditorLayout(selectedLayout);
        selectedWidget = pendingLibraryDrag = draggedWidget = null;
        draggingWidgetGroup = false;
        draggedGroupWidgets.clear(); draggedGroupStates.clear();
        refreshLayout();
    }
    public String createLayout() {
        if (!editing) return null;
        String id = java.util.UUID.randomUUID().toString();
        var layout = new LocationWidgetLayoutConfig();
        layout.name = "Layout " + (ConfigManager.get().locationLayouts.size() + 1);
        ConfigManager.get().locationLayouts.put(id, layout);
        selectLayout(id);
        ConfigManager.save();
        openLayoutSettings();
        return id;
    }
    public boolean deleteLayout(String id) {
        if (id == null || id.isEmpty() || !ConfigManager.get().locationLayouts.containsKey(id)) return false;
        if (id.equals(selectedLayout)) selectLayout(HudLayoutResolver.MAIN);
        ConfigManager.get().locationLayouts.remove(id);
        ConfigManager.layoutChanged();
        ConfigManager.save();
        refreshLayout();
        return true;
    }
    public boolean openLayoutSettings() {
        if (!editing || selectedLayout.isEmpty()) return false;
        String id = selectedLayout;
        if (!settingsPanel.openLayout(id, () -> setSettings(false), () -> deleteLayout(id))) return false;
        setSettings(true); UiSounds.openClose();
        return true;
    }
    private java.util.Set<String> editedWidgetKeys() {
        return HudLayoutResolver.widgets(ConfigManager.get(), selectedLayout, null);
    }
    private void markLayoutApplied() {
        appliedLayoutRevision = ConfigManager.getLayoutRevision();
        appliedConfig = ConfigManager.get();
        appliedLocation = editing ? null : DiamondWorldProtocolClient.getCurrentGameLocation();
    }
    /** Switch existing instances without saving runtime overrides into the main layout. */
    public void refreshLayout() {
        if (Minecraft.getInstance().getWindow() == null) return; // Fabric entrypoints run before window creation.
        String location = editing ? null : DiamondWorldProtocolClient.getCurrentGameLocation();
        if (appliedConfig == ConfigManager.get() && appliedLayoutRevision == ConfigManager.getLayoutRevision()
                && java.util.Objects.equals(appliedLocation, location)) return;
        long animationTime = System.nanoTime();
        boolean animate = !editing && appliedConfig == ConfigManager.get()
                && appliedLayoutRevision == ConfigManager.getLayoutRevision()
                && !java.util.Objects.equals(appliedLocation, location)
                && Minecraft.getInstance().level != null;
        try (var profile = ModProfiler.getInstance().scope("hud/layout/apply")) {
            if (animate) layoutAnimation.beforePlacement(widgets);
            if (!editing && !java.util.Objects.equals(appliedLocation, location))
                ModProfiler.getInstance().recordClientEvent("hud-layout-location", location);
            widgets.clear();
            var keys = ConfigManager.getActiveWidgetKeys();
            for (String key : keys) for (Widget widget : registeredWidgets) {
                if (widget instanceof AbstractWidget placed && key.equals(placed.getConfigKey())) {
                    applyPlacement(placed, ConfigManager.getWidgetLayout(key));
                    widgets.add(widget);
                    break;
                }
            }
            for (Widget widget : registeredWidgets) if (WidgetCatalog.find(widget.getClass().getSimpleName()) == null) widgets.add(widget);
            normalizeWidgetSnapParents();
            var window = Minecraft.getInstance().getWindow();
            updateAnchoredWidgets(window.getGuiScaledWidth(), window.getGuiScaledHeight());
            updateSnappedWidgets();
            if (animate) layoutAnimation.change(registeredWidgets, widgets, animationTime);
            else layoutAnimation.reset(registeredWidgets, widgets);
            passiveLayoutWidthCache.clear(); passiveLayoutHeightCache.clear(); nextPassiveLayoutCheck = 0;
            if (!widgets.contains(selectedWidget)) selectedWidget = null;
            widgetRegistryRevision++;
            markLayoutApplied();
            ModProfiler.getInstance().incrementCounter("hud/layout/applied");
            ModProfiler.getInstance().incrementCounter("hud/layout/widgets", widgets.size());
        }
    }
    private void applyPlacement(AbstractWidget widget, WidgetLayoutConfig stored) {
        WidgetLayoutConfig defaults = defaultPlacements.get(widget);
        if (defaults == null) return;
        widget.setScale(stored != null && stored.scale != null ? stored.scale : defaults.scale);
        widget.setScreenAnchor(stored != null ? stored.anchor : defaults.anchor);
        widget.clearWidgetSnap();
        var window = Minecraft.getInstance().getWindow();
        int w = window.getGuiScaledWidth(), h = window.getGuiScaledHeight();
        int x = stored != null && stored.x != null ? stored.x : defaults.x;
        int y = stored != null && stored.y != null ? stored.y : defaults.y;
        if (stored != null && stored.xFraction != null) x = (int) Math.round(stored.xFraction * w);
        if (stored != null && stored.yFraction != null) y = (int) Math.round(stored.yFraction * h);
        widget.setStartX(clamp(x, 0, Math.max(0, w - widget.getWidth())));
        widget.setStartY(clamp(y, 0, Math.max(0, h - widget.getHeight())));
        if (stored != null && stored.snapTarget != null && stored.snapOwnCorner != null && stored.snapTargetCorner != null)
            widget.setWidgetSnap(stored.snapTarget, stored.snapOwnCorner, stored.snapTargetCorner);
    }

    public boolean isSettingsOpen() {
        return this.settingsOpen;
    }

    public void registerWidget(Widget widget) {
        registerWidget(widget, null);
    }

    public void registerWidget(Widget widget, ScreenAnchor defaultAnchor) {
        registeredWidgets.add(widget);
        widgetRegistryRevision++;
        if (WidgetCatalog.find(widget.getClass().getSimpleName()) == null
                || WidgetCatalog.isAdded(ConfigManager.get(), widget.getClass().getSimpleName())) {
            widgets.add(widget);
        }
        nextPassiveLayoutCheck = 0;

        if (widget instanceof AbstractWidget abstractWidget) {
            abstractWidget.setConfigKey(widget.getClass().getSimpleName());
            var defaults = new WidgetLayoutConfig();
            defaults.x = abstractWidget.getStartX(); defaults.y = abstractWidget.getStartY();
            defaults.scale = abstractWidget.getScale(); defaults.anchor = defaultAnchor;
            defaultPlacements.put(widget, defaults);

            WidgetLayoutConfig storedLayout = ConfigManager.getWidgetLayout(abstractWidget.getConfigKey());
            if (storedLayout != null) {
                if (storedLayout.scale != null) {
                    abstractWidget.setScale(storedLayout.scale);
                }
                if (storedLayout.anchor != null) {
                    abstractWidget.setScreenAnchor(storedLayout.anchor);
                }
                if (storedLayout.x != null) {
                    abstractWidget.setStartX(storedLayout.x);
                }
                if (storedLayout.y != null) {
                    abstractWidget.setStartY(storedLayout.y);
                }
                if (storedLayout.snapTarget != null && storedLayout.snapOwnCorner != null && storedLayout.snapTargetCorner != null) {
                    abstractWidget.setWidgetSnap(storedLayout.snapTarget, storedLayout.snapOwnCorner, storedLayout.snapTargetCorner);
                } else {
                    abstractWidget.clearWidgetSnap();
                }
            } else if (defaultAnchor != null) {
                abstractWidget.setScreenAnchor(defaultAnchor);
            }
        }
    }

    public List<Widget> getWidgets() {
        return List.copyOf(registeredWidgets);
    }

    public List<Widget> getLayoutWidgets() {
        return List.copyOf(widgets);
    }

    public int getLayoutWidgetCount() { return widgets.size(); }
    public int getRegisteredWidgetCount() { return registeredWidgets.size(); }
    public long getWidgetRegistryRevision() { return widgetRegistryRevision; }
    public Widget getSelectedWidget() { return selectedWidget; }
    public boolean isWidgetDragging() { return draggedWidget != null; }
    public boolean isWidgetAdded(Widget widget) { return widgets.contains(widget); }

    public void selectWidget(Widget widget) {
        if (widgets.contains(widget)) selectedWidget = widget;
    }

    public void queueWidgetDragFromLibrary(Widget widget, double mouseX, double mouseY) {
        if (!editing || !widgets.contains(widget)) return;
        selectedWidget = widget;
        pendingLibraryDrag = widget;
        libraryPressX = mouseX;
        libraryPressY = mouseY;
    }

    public boolean addWidget(Widget widget) {
        if (!registeredWidgets.contains(widget) || widgets.contains(widget)) return false;
        if (!(widget instanceof AbstractWidget placed)) return false;
        String key = placed.getConfigKey();
        if (WidgetCatalog.find(key) == null) return false;
        editedWidgetKeys().add(key);
        ConfigManager.layoutChanged();
        widgets.add(widget);
        var window = Minecraft.getInstance().getWindow();
        int screenWidth = window.getGuiScaledWidth(), screenHeight = window.getGuiScaledHeight();
        WidgetLayoutConfig stored = ConfigManager.getWidgetLayout(key);
        applyPlacement(placed, stored);
        if (stored == null) {
            placed.setScreenAnchor(null);
            placed.clearWidgetSnap();
            int width = placed.getWidth(), height = placed.getHeight();
            var occupied = new ArrayList<WidgetPlacement.Bounds>();
            for (Widget existing : widgets) {
                if (existing != placed) occupied.add(new WidgetPlacement.Bounds(
                        existing.getStartX(), existing.getStartY(), existing.getWidth(), existing.getHeight()));
            }
            var position = WidgetPlacement.find(
                    screenWidth, screenHeight, widgetSidebar.canvasLeft(), width, height, occupied);
            placed.setStartX(position.x());
            placed.setStartY(position.y());
        } else {
            if (stored.xFraction != null) placed.setStartX(clamp((int) Math.round(stored.xFraction * screenWidth), 0, screenWidth - placed.getWidth()));
            if (stored.yFraction != null) placed.setStartY(clamp((int) Math.round(stored.yFraction * screenHeight), 0, screenHeight - placed.getHeight()));
        }
        normalizeWidgetSnapParents();
        updateAnchoredWidgets(screenWidth, screenHeight);
        updateSnappedWidgets();
        selectedWidget = widget;
        nextPassiveLayoutCheck = 0;
        saveAllWidgetLayouts();
        markLayoutApplied();
        if (editing) {
            layoutAnimation.editorAdded(widget, System.nanoTime());
            ModProfiler.getInstance().recordClientEvent("widget-add", key + " layout=" + selectedLayout);
            ModProfiler.getInstance().incrementCounter("hud/animation/editor/add");
        }
        return true;
    }

    public boolean removeWidget(Widget widget) {
        if (!(widget instanceof AbstractWidget placed) || !widgets.contains(widget)) return false;
        if (editing) {
            layoutAnimation.editorRemoved(widget, System.nanoTime());
            ModProfiler.getInstance().recordClientEvent("widget-remove", placed.getConfigKey() + " layout=" + selectedLayout);
            ModProfiler.getInstance().incrementCounter("hud/animation/editor/remove");
        }
        // Keep the removed widget's last placement, and detach only children that depend on it.
        List<Widget> changedLayouts = new ArrayList<>(widgets);
        for (Widget candidate : widgets) {
            if (candidate instanceof AbstractWidget child && placed.getConfigKey().equals(child.getSnapTargetKey())) {
                child.clearWidgetSnap();
            }
        }
        editedWidgetKeys().remove(placed.getConfigKey());
        ConfigManager.layoutChanged();
        widgets.remove(widget);
        if (selectedWidget == widget) selectedWidget = null;
        pendingLibraryDrag = null;
        draggedWidget = null;
        draggingWidgetGroup = false;
        draggedGroupWidgets.clear();
        draggedGroupStates.clear();
        activeScreenAnchor = null;
        activeDraggedCornerIndicator = null;
        activeTargetCornerIndicator = null;
        passiveLayoutWidthCache.remove(widget);
        passiveLayoutHeightCache.remove(widget);
        nextPassiveLayoutCheck = 0;
        ConfigManager.saveWidgetLayouts(changedLayouts);
        markLayoutApplied();
        return true;
    }

    public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        try (ModProfiler.Scope renderScope = ModProfiler.getInstance().scope("hud/render")) {
            refreshLayout();
            HudFrameClock.advance(); // one tick per HUD frame; widgets key per-frame caches off this
            long animationTime = System.nanoTime();
            int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
            double mouseX = MouseUtils.getMouseX();
            double mouseY = MouseUtils.getMouseY();
            Widget hoveredWidget = null;
            if (editing) {
                try (ModProfiler.Scope hoveredScope = ModProfiler.getInstance().scope("hud/findTopHoveredWidget")) {
                    hoveredWidget = findTopHoveredWidget(mouseX, mouseY);
                    if (hoveredWidget == null && widgets.contains(selectedWidget)) hoveredWidget = selectedWidget;
                }
            }

            try (ModProfiler.Scope layoutScope = ModProfiler.getInstance().scope("hud/layout")) {
                boolean resized = handleScreenResize(screenWidth, screenHeight);
                boolean needsPassiveLayoutRefresh = editing || resized || hasPassiveLayoutChanges();
                ModProfiler.getInstance().incrementCounter("hud/layout/resized", resized ? 1 : 0);
                ModProfiler.getInstance().incrementCounter("hud/layout/passiveRefresh", needsPassiveLayoutRefresh ? 1 : 0);
                if (needsPassiveLayoutRefresh) {
                    updateAnchoredWidgets(screenWidth, screenHeight);
                    updateSnappedWidgets();
                    refreshPassiveLayoutCache();
                    if (resized) layoutAnimation.reset(registeredWidgets, widgets);
                } else {
                    ModProfiler.getInstance().incrementCounter("hud/layout/skippedPassiveRefresh");
                }
            }

            // While the mod's settings menu is open, hide all widgets — only the panel shows.
            layoutAnimation.checkResources(registeredWidgets, widgets);
            boolean departures = layoutAnimation.hasDepartures(animationTime, editing);
            if ((!settingsOpen || settingsPanel.isContextSettings()) && (!widgets.isEmpty() || departures)) {
            // Capture + blur the screen once, before any widget composites its frosted backdrop.
            try (ModProfiler.Scope blurScope = ModProfiler.getInstance().scope("hud/blurCapture")) {
                HudBlur.beginFrame(context);
                if (departures) HudBlur.requestSnapshotCapture();
            }

            int renderedWidgets = 0;
            int skippedWidgets = 0;
            try (ModProfiler.Scope widgetLoopScope = ModProfiler.getInstance().scope("hud/widgetLoop")) {
                if (departures) layoutAnimation.renderDepartures(context, animationTime, null, editing);
                for (Widget widget : widgets) {
                    if (!shouldRenderWidget(widget, editing)) {
                        if (!editing) layoutAnimation.hiddenData(widget);
                        skippedWidgets++;
                        continue;
                    }

                    renderedWidgets++;
                    try (ModProfiler.Scope widgetScope = ModProfiler.getInstance().typedScope("widget", widget.getClass().getSimpleName())) {
                        if (editing) layoutAnimation.renderEditor(widget, context, animationTime, () -> {
                            if (!widget.isVisible()) renderEmptyWidgetPreview(context, widget);
                            else widget.render(context, tickCounter);
                        });
                        else layoutAnimation.render(widget, context, tickCounter, animationTime);
                    }
                }
            }
            ModProfiler.getInstance().incrementCounter("hud/widgetLoop/renderedWidgets", renderedWidgets);
            ModProfiler.getInstance().incrementCounter("hud/widgetLoop/skippedWidgets", skippedWidgets);
            }

            if (editing) {
                try (ModProfiler.Scope editorScope = ModProfiler.getInstance().scope("hud/editorOverlay")) {
                    if (hoveredWidget != null) {
                        try (ModProfiler.Scope hoveredOverlayScope = ModProfiler.getInstance().scope("hud/editorOverlay/hoveredWidget")) {
                            renderHoveredWidgetOutline(context, hoveredWidget);
                            renderHoveredWidgetRemoveControl(context, hoveredWidget, mouseX, mouseY);

                            if (ru.wilyfox.utils.InputModifiers.hasAltDown()) {
                                renderGroupTooltip(context, hoveredWidget);
                            } else if (ru.wilyfox.utils.InputModifiers.hasControlDown()) {
                                renderScaleTooltip(context, hoveredWidget);
                            }
                        }
                    }

                    try (ModProfiler.Scope screenAnchorsScope = ModProfiler.getInstance().scope("hud/editorOverlay/screenAnchors")) {
                        renderScreenAnchors(context, screenWidth, screenHeight);
                    }
                    try (ModProfiler.Scope snapIndicatorsScope = ModProfiler.getInstance().scope("hud/editorOverlay/snapIndicators")) {
                        renderWidgetSnapIndicators(context);
                    }
                }
            }

            // The settings panel renders in a separate layer above vanilla chat (renderSettingsOverlay).

            if (ConfigManager.get().fishing.showFishingMarkers) {
                try (ModProfiler.Scope fishingOverlayScope = ModProfiler.getInstance().scope("hud/FishingSpotOverlayRenderer")) {
                    FishingSpotOverlayRenderer.render(context);
                }
            }

            if (ConfigManager.get().render.showAlchemyIngredientMarkers) {
                try (ModProfiler.Scope alchemyOverlayScope = ModProfiler.getInstance().scope("hud/AlchemyIngredientOverlayRenderer")) {
                    AlchemyIngredientOverlayRenderer.render(context);
                }
            }

        }
    }

    /**
     * The settings panel, drawn in its own HUD layer AFTER vanilla chat so it sits above it. Captures
     * the screen here (this layer runs after chat) so the frosted panel blurs the game + chat behind it.
     */
    public void renderSettingsOverlay(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        if (!settingsOpen) {
            if (editing) {
                try (var profile = ModProfiler.getInstance().scope("ui/widgetSidebar/render")) {
                    widgetSidebar.render(context, MouseUtils.getMouseX(), MouseUtils.getMouseY());
                }
            }
            return;
        }
        try (ModProfiler.Scope settingsScope = ModProfiler.getInstance().scope("hud/settingsPanel")) {
            HudBlur.beginFrame(context);
            settingsPanel.render(context, MouseUtils.getMouseX(), MouseUtils.getMouseY());
        }
    }

    public void renderLayer(HudLayer layer, GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        refreshLayout();
        if (settingsOpen && !settingsPanel.isContextSettings()) return;
        long animationTime = System.nanoTime();
        layoutAnimation.checkResources(registeredWidgets, widgets);
        if (layoutAnimation.hasDepartures(animationTime, editing)) {
            HudBlur.beginFrame(context);
            HudBlur.requestSnapshotCapture();
            layoutAnimation.renderDepartures(context, animationTime, layer, editing);
        }
        for (Widget widget : widgets) {
            if (!shouldRenderWidget(widget, editing)) {
                if (!editing) layoutAnimation.hiddenData(widget);
                continue;
            }

            if (widget.getLayer() == layer) {
                if (editing) layoutAnimation.renderEditor(widget, context, animationTime, () -> {
                    if (!widget.isVisible()) renderEmptyWidgetPreview(context, widget);
                    else widget.render(context, tickCounter);
                });
                else layoutAnimation.render(widget, context, tickCounter, animationTime);
            }
        }
    }

    public void onMousePressed(double mouseX, double mouseY, int button) {
        if (settingsOpen) {
            settingsPanel.mousePressed(mouseX, mouseY, button);
            return;
        }

        if (!editing || (button != 0 && button != 1)) {
            return;
        }

        pendingLibraryDrag = null;
        if (widgetSidebar.mousePressed(mouseX, mouseY, button)) return;


        Widget hovered = findTopHoveredWidget(mouseX, mouseY);
        if (hovered == null) {
            selectedWidget = null;
            return;
        }

        selectedWidget = hovered;
        if (button == 1) {
            openWidgetSettings(hovered);
            return;
        }
        if (isOverRemoveControl(hovered, mouseX, mouseY)) {
            removeWidget(hovered);
            UiSounds.click();
            return;
        }

        beginWidgetDrag(hovered, mouseX, mouseY);
    }

    private void beginWidgetDrag(Widget widget, double mouseX, double mouseY) {
        draggedWidget = widget;
        dragOffsetX = (int) (mouseX - widget.getStartX());
        dragOffsetY = (int) (mouseY - widget.getStartY());

        if (widget instanceof AbstractWidget abstractWidget) {
            abstractWidget.setScreenAnchor(null);
            abstractWidget.clearWidgetSnap();

            if (ru.wilyfox.utils.InputModifiers.hasAltDown()) {
                beginGroupDrag(abstractWidget);
            } else {
                // beginGroupDrag() always resets this state itself; a plain single-widget
                // drag must do it too, otherwise a group-drag left dangling by a missed
                // mouseReleased (e.g. consumed by the settings panel) leaks into this drag
                // and drags unrelated leftover group members along with it.
                draggingWidgetGroup = false;
                draggedGroupWidgets.clear();
                draggedGroupStates.clear();
                detachSnappedDescendants(abstractWidget);
            }
        }
    }

    public void onMouseReleased(int button, int screenWidth, int screenHeight, double mouseX, double mouseY) {
        if (button != 0) {
            return;
        }

        if (settingsOpen && settingsPanel.mouseReleased(mouseX, mouseY, button)) {
            return;
        }

        pendingLibraryDrag = null;
        if (editing && draggedWidget != null) {
            onMouseDragged(mouseX, mouseY, screenWidth, screenHeight, button);
        }
        Widget releasedWidget = draggedWidget;
        if (draggingWidgetGroup) {
            finishGroupDrag();
        }
        draggedWidget = null;
        activeScreenAnchor = null;
        activeDraggedCornerIndicator = null;
        activeTargetCornerIndicator = null;

        if (editing && releasedWidget instanceof AbstractWidget) {
            normalizeWidgetSnapParents();
            saveAllWidgetLayouts();
            UiSounds.click();
        }
    }

    public void onMouseDragged(double mouseX, double mouseY, int screenWidth, int screenHeight, int button) {
        if (settingsOpen && settingsPanel.mouseDragged(mouseX, mouseY, button)) {
            return;
        }

        if (!editing || button != 0) return;
        if (pendingLibraryDrag != null) {
            double dx = mouseX - libraryPressX, dy = mouseY - libraryPressY;
            if (dx * dx + dy * dy < 9) return;
            Widget widget = pendingLibraryDrag;
            pendingLibraryDrag = null;
            beginWidgetDrag(widget, widget.getStartX() + widget.getWidth() / 2.0,
                    widget.getStartY() + widget.getHeight() / 2.0);
        }
        if (draggedWidget == null) return;

        activeScreenAnchor = null;
        activeDraggedCornerIndicator = null;
        activeTargetCornerIndicator = null;

        int newX = (int) mouseX - dragOffsetX;
        int newY = (int) mouseY - dragOffsetY;

        newX = clamp(newX, 0, screenWidth - draggedWidget.getWidth());
        newY = clamp(newY, 0, screenHeight - draggedWidget.getHeight());

        if (draggedWidget instanceof AbstractWidget abstractWidget) {
            abstractWidget.setScreenAnchor(null);
        }

        draggedWidget.setStartX(newX);
        draggedWidget.setStartY(newY);
        if (draggingWidgetGroup && draggedWidget instanceof AbstractWidget abstractWidget) {
            updateDraggedGroupPositions(abstractWidget);
        }

        boolean snappedToAnchor = applyScreenAnchorSnapping(draggedWidget, screenWidth, screenHeight, ru.wilyfox.utils.InputModifiers.hasShiftDown());
        if (!snappedToAnchor) {
            applyWidgetSnapping(screenWidth, screenHeight);
        } else if (draggedWidget instanceof AbstractWidget abstractWidget) {
            abstractWidget.clearWidgetSnap();
        }

        resolveWidgetOverlap(screenWidth, screenHeight);

        if (draggingWidgetGroup && draggedWidget instanceof AbstractWidget abstractWidget) {
            updateDraggedGroupPositions(abstractWidget);
        }
    }

    public boolean onMouseScrolled(double mouseX, double mouseY, double scrollY, boolean ctrlHeld, boolean shiftHeld) {
        if (settingsOpen) {
            settingsPanel.mouseScrolled(mouseX, mouseY, scrollY);
            return true;
        }
        if (editing && widgetSidebar.mouseScrolled(mouseX, mouseY, scrollY)) return true;

        if (!editing || !ctrlHeld) {
            return false;
        }

        Widget hovered = findTopHoveredWidget(mouseX, mouseY);
        if (hovered == null) {
            return false;
        }

        if (hovered instanceof AbstractWidget scalableWidget) {
            float step = shiftHeld ? 0.02f : 0.10f;
            float previousScale = scalableWidget.getScale();
            scalableWidget.adjustScale(scrollY > 0 ? step : -step);
            if (previousScale != scalableWidget.getScale()) UiSounds.scroll();
            if (scalableWidget.getScreenAnchor() != null) {
                int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
                int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
                applyStoredScreenAnchor(scalableWidget, scalableWidget.getScreenAnchor(), screenWidth, screenHeight);
            }
            ConfigManager.saveWidgetLayout(scalableWidget);
            return true;
        }

        return false;
    }

    public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (settingsOpen) return settingsPanel.keyPressed(keyCode, scanCode, modifiers);
        if (editing && !settingsOpen && keyCode == GLFW.GLFW_KEY_TAB) {
            widgetSidebar.toggleVisibility();
            return true;
        }
        if (editing && widgetSidebar.keyPressed(keyCode)) return true;
        if (editing && keyCode == GLFW.GLFW_KEY_DELETE && selectedWidget != null) {
            boolean removed = removeWidget(selectedWidget);
            if (removed) UiSounds.click();
            return removed;
        }
        return settingsOpen && settingsPanel.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean onCharTyped(int codePoint, int modifiers) {
        if (settingsOpen) return settingsPanel.charTyped(codePoint, modifiers);
        if (editing && widgetSidebar.charTyped(codePoint)) return true;
        return settingsOpen && settingsPanel.charTyped(codePoint, modifiers);
    }

    public boolean handleChatClick(double mouseX, double mouseY, int button) {
        if (button != 0 || editing || settingsOpen) {
            return false;
        }

        Widget hovered = findTopHoveredWidget(mouseX, mouseY);
        if (hovered instanceof BossHudWidget bossHudWidget) {
            return bossHudWidget.handleChatClick(mouseX, mouseY);
        }

        return false;
    }

    private Widget findTopHoveredWidget(double mouseX, double mouseY) {
        if (editing && widgetSidebar.contains(mouseX, mouseY)) return null;
        for (int i = widgets.size() - 1; i >= 0; i--) {
            Widget widget = widgets.get(i);

            if (!shouldRenderWidget(widget, editing)) {
                continue;
            }

            if (widget.isHovered(mouseX, mouseY)) {
                return widget;
            }
        }

        return null;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean handleScreenResize(int screenWidth, int screenHeight) {
        int beforeWidth = lastScreenWidth;
        int beforeHeight = lastScreenHeight;
        snapLayoutEngine.handleScreenResize(snapLayoutHost, screenWidth, screenHeight);
        return beforeWidth != lastScreenWidth || beforeHeight != lastScreenHeight;
    }

    private void updateAnchoredWidgets(int screenWidth, int screenHeight) {
        snapLayoutEngine.updateAnchoredWidgets(snapLayoutHost, screenWidth, screenHeight);
    }

    private void updateSnappedWidgets() {
        snapLayoutEngine.updateSnappedWidgets(snapLayoutHost);
    }

    private boolean applyScreenAnchorSnapping(Widget widget, int screenWidth, int screenHeight, boolean shiftHeld) {
        return snapLayoutEngine.applyScreenAnchorSnapping(snapLayoutHost, widget, screenWidth, screenHeight, shiftHeld);
    }

    private void applyStoredScreenAnchor(Widget widget, ScreenAnchor anchor, int screenWidth, int screenHeight) {
        snapLayoutEngine.applyStoredScreenAnchor(widget, anchor, screenWidth, screenHeight);
    }

    private void applyWidgetSnapping(int screenWidth, int screenHeight) {
        snapLayoutEngine.applyWidgetSnapping(snapLayoutHost, screenWidth, screenHeight);
    }

    private Map<String, AbstractWidget> getAbstractWidgetMap() {
        return snapLayoutEngine.getAbstractWidgetMap();
    }

    private AbstractWidget findAbstractWidget(String configKey) {
        return snapLayoutEngine.findAbstractWidget(configKey);
    }

    private void renderHoveredWidgetOutline(GuiGraphicsExtractor context, Widget hoveredWidget) {
        HudEditorOverlayRenderer.renderHoveredWidgetOutline(overlayHost, context, hoveredWidget);
    }

    private void renderHoveredWidgetRemoveControl(GuiGraphicsExtractor context, Widget hoveredWidget, double mouseX, double mouseY) {
        if (!(hoveredWidget instanceof AbstractWidget abstractWidget)) {
            return;
        }

        int x = getRemoveControlX(hoveredWidget);
        int y = getRemoveControlY(hoveredWidget);
        boolean hovered = mouseX >= x && mouseX <= x + EDITOR_CONTROL_SIZE
                && mouseY >= y && mouseY <= y + EDITOR_CONTROL_SIZE;

        int background = WidgetTheme.withAlpha(WidgetTheme.PANEL_BG, hovered ? 0xEC : 0xC0);
        int lineColor = hovered ? WidgetTheme.STATUS_WARNING : WidgetTheme.TEXT_SOFT;

        context.fill(x, y, x + EDITOR_CONTROL_SIZE, y + EDITOR_CONTROL_SIZE, background);
        context.fill(x, y, x + EDITOR_CONTROL_SIZE, y + 1, WidgetTheme.withAlpha(lineColor, 0xC8));

        for (int i = 2; i < EDITOR_CONTROL_SIZE - 2; i++) {
            context.fill(x + i, y + i, x + i + 1, y + i + 1, lineColor);
            context.fill(x + EDITOR_CONTROL_SIZE - i - 1, y + i, x + EDITOR_CONTROL_SIZE - i, y + i + 1, lineColor);
        }
    }

    private void renderEmptyWidgetPreview(GuiGraphicsExtractor context, Widget widget) {
        context.pose().pushMatrix();
        context.pose().translate(widget.getStartX(), widget.getStartY());
        float scale = widget instanceof AbstractWidget placed ? placed.getScale() : 1;
        context.pose().scale(scale, scale);
        HudSurface.drawPanel(context, Math.round(widget.getWidth() / scale), Math.round(widget.getHeight() / scale));
        context.text(Minecraft.getInstance().font, widget.getDisplayName(), 7, 6, WidgetTheme.TITLE);
        context.text(Minecraft.getInstance().font, "No data here", 7, 20, WidgetTheme.TEXT_MUTED);
        context.pose().popMatrix();
    }

    private void resolveWidgetOverlap(int screenWidth, int screenHeight) {
        snapLayoutEngine.resolveWidgetOverlap(snapLayoutHost, draggingWidgetGroup, screenWidth, screenHeight);
    }

    private void saveAllWidgetLayouts() {
        ConfigManager.saveWidgetLayouts(widgets);
    }

    private void normalizeWidgetSnapParents() {
        HudSnapGraphNormalizer.normalize(getAbstractWidgetMap());
    }

    public void finalizeWidgetRegistration() {
        appliedLayoutRevision = -1;
        List<String> order = new ArrayList<>(ConfigManager.get().mainLayout.widgets);
        widgets.sort(java.util.Comparator.comparingInt(widget -> {
            int index = order.indexOf(widget.getClass().getSimpleName());
            return index < 0 ? Integer.MAX_VALUE : index;
        }));
        // Config-loaded snap data skips the cycle checks that live edits go through
        // (see persistWidgetSnap/HudSnapGraphNormalizer); a stale or hand-edited config
        // could otherwise leave a cyclic snap graph in place until the first manual drag.
        normalizeWidgetSnapParents();
    }

    private void beginGroupDrag(AbstractWidget rootWidget) {
        List<AbstractWidget> descendants = getSnappedDescendants(rootWidget);
        draggingWidgetGroup = HudGroupDragController.beginGroupDrag(
                rootWidget,
                descendants,
                draggedGroupWidgets,
                draggedGroupStates
        );
    }

    private void finishGroupDrag() {
        draggingWidgetGroup = false;
        HudGroupDragController.finishGroupDrag(
                draggedWidget instanceof AbstractWidget abstractWidget ? abstractWidget : null,
                draggedGroupWidgets,
                draggedGroupStates
        );
    }

    private void updateDraggedGroupPositions(AbstractWidget rootWidget) {
        HudGroupDragController.updateDraggedGroupPositions(rootWidget, draggedGroupWidgets, draggedGroupStates);
    }

    private List<AbstractWidget> getSnappedDescendants(AbstractWidget rootWidget) {
        return HudSnapGroupResolver.getSnappedDescendants(rootWidget, widgets);
    }

    private List<AbstractWidget> getConnectedSnapGroup(AbstractWidget startWidget) {
        return HudSnapGroupResolver.getConnectedSnapGroup(startWidget, widgets);
    }

    private void detachSnappedDescendants(AbstractWidget rootWidget) {
        for (AbstractWidget widget : getSnappedDescendants(rootWidget)) {
            widget.clearWidgetSnap();
        }
    }

    private void renderScreenAnchors(GuiGraphicsExtractor context, int screenWidth, int screenHeight) {
        HudEditorOverlayRenderer.renderScreenAnchors(
                overlayHost,
                context,
                screenWidth,
                screenHeight,
                SCREEN_SNAP_MARGIN
        );
    }

    private boolean isScreenAnchorOccupied(ScreenAnchor anchor, Widget ignoredWidget) {
        for (Widget widget : widgets) {
            if (widget == ignoredWidget || !widget.isVisible() || !(widget instanceof AbstractWidget abstractWidget)) {
                continue;
            }

            if (anchor == abstractWidget.getScreenAnchor()) {
                return true;
            }
        }

        return false;
    }

    private boolean isScreenAnchorCovered(int anchorX, int anchorY) {
        for (Widget widget : widgets) {
            if (!widget.isVisible()) {
                continue;
            }

            if (anchorX >= widget.getStartX()
                    && anchorX <= widget.getStartX() + widget.getWidth()
                    && anchorY >= widget.getStartY()
                    && anchorY <= widget.getStartY() + widget.getHeight()) {
                return true;
            }
        }

        return false;
    }

    private int getHotbarLeftAnchorX(int screenWidth) {
        return HudScreenAnchorHelper.getHotbarLeftAnchorX(screenWidth, HOTBAR_WIDTH, HOTBAR_ANCHOR_GAP, OFFHAND_SLOT_WIDTH);
    }

    private int getHotbarRightAnchorX(int screenWidth) {
        return HudScreenAnchorHelper.getHotbarRightAnchorX(screenWidth, HOTBAR_WIDTH, HOTBAR_ANCHOR_GAP, OFFHAND_SLOT_WIDTH);
    }

    private int getHotbarAnchorY(int screenHeight) {
        return HudScreenAnchorHelper.getHotbarAnchorY(screenHeight, HOTBAR_HEIGHT);
    }

    private boolean isCenterSideAnchor(ScreenAnchor anchor) {
        return anchor == ScreenAnchor.TOP_CENTER
                || anchor == ScreenAnchor.LEFT_CENTER
                || anchor == ScreenAnchor.RIGHT_CENTER;
    }

    private void renderWidgetSnapIndicators(GuiGraphicsExtractor context) {
        HudEditorOverlayRenderer.renderWidgetSnapIndicators(overlayHost, context);
    }

    private boolean hasPassiveLayoutChanges() {
        long now = System.nanoTime();
        if (now < nextPassiveLayoutCheck) return false;
        nextPassiveLayoutCheck = now + PASSIVE_LAYOUT_INTERVAL_NANOS;
        boolean trackedAny = false;
        for (Widget widget : widgets) {
            if (!requiresPassiveLayoutTracking(widget)) {
                continue;
            }

            trackedAny = true;
            Integer cachedWidth = passiveLayoutWidthCache.get(widget);
            Integer cachedHeight = passiveLayoutHeightCache.get(widget);
            if (cachedWidth == null || cachedHeight == null || cachedWidth != widget.getWidth() || cachedHeight != widget.getHeight()) {
                return true;
            }
        }

        return !trackedAny && (passiveLayoutWidthCache.isEmpty() && passiveLayoutHeightCache.isEmpty()) ? false : prunePassiveLayoutCache();
    }

    private void refreshPassiveLayoutCache() {
        passiveLayoutWidthCache.clear();
        passiveLayoutHeightCache.clear();
        for (Widget widget : widgets) {
            if (!requiresPassiveLayoutTracking(widget)) {
                continue;
            }

            passiveLayoutWidthCache.put(widget, widget.getWidth());
            passiveLayoutHeightCache.put(widget, widget.getHeight());
        }
    }

    private boolean prunePassiveLayoutCache() {
        boolean removed = passiveLayoutWidthCache.keySet().removeIf(widget -> !requiresPassiveLayoutTracking(widget));
        passiveLayoutHeightCache.keySet().removeIf(widget -> !requiresPassiveLayoutTracking(widget));
        return removed;
    }

    private boolean requiresPassiveLayoutTracking(Widget widget) {
        if (!(widget instanceof AbstractWidget abstractWidget)) {
            return false;
        }

        return abstractWidget.getScreenAnchor() != null || abstractWidget.hasWidgetSnap();
    }

    private boolean shouldRenderWidget(Widget widget, boolean editingMode) {
        return editingMode || widget.isVisible();
    }

    private boolean isOverRemoveControl(Widget widget, double mouseX, double mouseY) {
        int x = getRemoveControlX(widget);
        int y = getRemoveControlY(widget);
        return mouseX >= x
                && mouseX <= x + EDITOR_CONTROL_SIZE
                && mouseY >= y
                && mouseY <= y + EDITOR_CONTROL_SIZE;
    }

    private int getRemoveControlX(Widget widget) {
        return widget.getStartX() + Math.max(0, widget.getWidth() - EDITOR_CONTROL_SIZE - EDITOR_CONTROL_MARGIN);
    }

    private int getRemoveControlY(Widget widget) {
        return widget.getStartY() + EDITOR_CONTROL_MARGIN;
    }

    private void renderGroupTooltip(GuiGraphicsExtractor context, Widget hovered) {
        if (!(hovered instanceof AbstractWidget abstractWidget)) {
            return;
        }

        int groupSize = draggingWidgetGroup && draggedGroupWidgets.contains(abstractWidget)
                ? draggedGroupWidgets.size()
                : getSnappedDescendants(abstractWidget).size() + 1;
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

        context.pose().pushMatrix();
        context.pose().translate(x + paddingX, y + (height - Minecraft.getInstance().font.lineHeight * labelScale) / 2.0f);
        context.pose().scale(labelScale, labelScale);

        context.text(
                Minecraft.getInstance().font,
                text,
                0,
                0,
                WidgetTheme.TOOLTIP_TEXT
        );

        context.pose().popMatrix();
    }

    private void renderScaleTooltip(GuiGraphicsExtractor context, Widget hovered) {
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

        context.pose().pushMatrix();
        context.pose().translate(x + paddingX, y + (height - Minecraft.getInstance().font.lineHeight * labelScale) / 2.0f);
        context.pose().scale(labelScale, labelScale);

        context.text(
                Minecraft.getInstance().font,
                text,
                0,
                0,
                WidgetTheme.TOOLTIP_TEXT
        );

        context.pose().popMatrix();
    }
}
