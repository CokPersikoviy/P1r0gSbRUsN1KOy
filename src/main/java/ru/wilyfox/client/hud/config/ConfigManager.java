package ru.wilyfox.client.hud.config;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import ru.wilyfox.client.hud.widget.AbstractWidget;
import ru.wilyfox.client.hud.widget.Widget;
import ru.wilyfox.client.hud.widget.WidgetTheme;
import ru.wilyfox.utils.AtomicFileWriter;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;

import static ru.wilyfox.FrogHelper.LOGGER;
import static ru.wilyfox.client.debug.DebugLogger.error;

public class ConfigManager {
    private static final Gson GSON = HudConfigCodec.createGson();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("froghelper.json");
    private static HudConfig CONFIG = load();
    private static String editorLayout;
    private static long layoutRevision;
    private static long cachedRevision = -1;
    private static String cachedLocation;
    private static HudConfig cachedConfig;
    private static Set<String> cachedWidgets = Set.of();
    private static Set<String> cachedVisibleWidgets = Set.of();

    static {
        // Loading must finish before WidgetTheme reads CONFIG, including on a same-size restart.
        WidgetTheme.syncConfiguredTheme();
    }

    public static HudConfig get() {
        return CONFIG;
    }

    public static synchronized void save() {
        CONFIG = HudConfigSanitizer.sanitize(CONFIG);
        WidgetTheme.syncConfiguredTheme();

        try {
            AtomicFileWriter.write(CONFIG_PATH, writer -> GSON.toJson(CONFIG, writer));
        } catch (IOException | JsonIOException exception) {
            error(LOGGER, "Failed to save FrogHelper config to {}", CONFIG_PATH, exception);
        }
    }

    public static synchronized WidgetLayoutConfig getWidgetLayout(String key) {
        return HudLayoutResolver.placement(CONFIG, key, editorLayout, DiamondWorldProtocolClient.getCurrentGameLocation());
    }

    public static String getEditorLayout() { return editorLayout; }
    public static long getLayoutRevision() { return layoutRevision; }
    public static void layoutChanged() { layoutRevision++; }
    public static void setEditorLayout(String id) {
        editorLayout = id == null ? null : CONFIG.locationLayouts.containsKey(id) ? id : HudLayoutResolver.MAIN;
        layoutChanged();
    }
    public static Set<String> getActiveWidgetKeys() {
        String location = DiamondWorldProtocolClient.getCurrentGameLocation();
        if (editorLayout != null) return HudLayoutResolver.widgets(CONFIG, editorLayout, location);
        if (cachedConfig != CONFIG || cachedRevision != layoutRevision || !Objects.equals(cachedLocation, location)) {
            cachedWidgets = HudLayoutResolver.widgets(CONFIG, null, location);
            var visible = new LinkedHashSet<String>();
            for (String key : cachedWidgets) {
                var rule = CONFIG.widgetLocations.get(key);
                if (rule == null || rule.isVisible(location)) visible.add(key);
            }
            cachedVisibleWidgets = visible;
            cachedConfig = CONFIG; cachedRevision = layoutRevision; cachedLocation = location;
        }
        return cachedWidgets;
    }
    public static boolean isWidgetInCurrentLayout(String key) {
        if (!getActiveWidgetKeys().contains(key)) return false;
        if (editorLayout != null) return true; // Preview the selected layout even outside its locations.
        return cachedVisibleWidgets.contains(key);
    }
    public static WidgetLocationConfig getWidgetLocations(String key) {
        return CONFIG.widgetLocations.computeIfAbsent(key, ignored -> new WidgetLocationConfig());
    }

    public static synchronized Integer getLastWindowWidth() {
        return CONFIG.lastWindowWidth;
    }

    public static synchronized Integer getLastWindowHeight() {
        return CONFIG.lastWindowHeight;
    }

    public static synchronized void saveWindowSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }

        if (Integer.valueOf(width).equals(CONFIG.lastWindowWidth) && Integer.valueOf(height).equals(CONFIG.lastWindowHeight)) {
            return;
        }

        CONFIG.lastWindowWidth = width;
        CONFIG.lastWindowHeight = height;
        save();
    }

    public static synchronized void saveWidgetLayout(AbstractWidget widget) {
        if (widget == null || widget.getConfigKey() == null || widget.getConfigKey().isBlank()) return;
        captureWidgetLayout(widget);
        save();
    }

    /** Update the in-memory layout while dragging a setting, without a file write per pixel. */
    public static synchronized void captureWidgetLayout(AbstractWidget widget) {
        if (widget == null || widget.getConfigKey() == null || widget.getConfigKey().isBlank()) {
            return;
        }

        updateWidgetLayout(widget,
                Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                Minecraft.getInstance().getWindow().getGuiScaledHeight());
    }

    public static synchronized void saveWidgetLayouts(Iterable<? extends Widget> widgets) {
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        boolean updated = false;
        for (Widget widget : widgets) {
            if (widget instanceof AbstractWidget abstractWidget
                    && abstractWidget.getConfigKey() != null && !abstractWidget.getConfigKey().isBlank()) {
                updateWidgetLayout(abstractWidget, screenWidth, screenHeight);
                updated = true;
            }
        }
        if (updated) {
            // A drop updates the complete layout, but writes the config only once.
            save();
        }
    }

    private static void updateWidgetLayout(AbstractWidget widget, int screenW, int screenH) {
        WidgetLayoutConfig layout = HudLayoutResolver.placementStorage(CONFIG, widget.getConfigKey(), editorLayout,
                DiamondWorldProtocolClient.getCurrentGameLocation()).computeIfAbsent(widget.getConfigKey(), ignored -> new WidgetLayoutConfig());
        layout.x = widget.getStartX();
        layout.y = widget.getStartY();
        // Persist the resolution-independent fraction (source of truth for free widgets on resize).
        if (screenW > 0 && screenH > 0) {
            layout.xFraction = clampFraction(widget.getStartX() / (double) screenW);
            layout.yFraction = clampFraction(widget.getStartY() / (double) screenH);
        }
        layout.scale = widget.getScale();
        layout.anchor = widget.getScreenAnchor();
        layout.snapTarget = widget.getSnapTargetKey();
        layout.snapOwnCorner = widget.getSnapOwnCorner();
        layout.snapTargetCorner = widget.getSnapTargetCorner();
        // Legacy hiding is replaced by membership of mainLayout.
        layout.hiddenInGameplay = null;
    }

    private static double clampFraction(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static HudConfig load() {
        if (!Files.exists(CONFIG_PATH)) {
            return HudConfigSanitizer.sanitize(new HudConfig());
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            return HudConfigCodec.decode(GSON, JsonParser.parseReader(reader));
        } catch (Exception exception) {
            error(LOGGER, "Failed to load FrogHelper config from {}", CONFIG_PATH, exception);
            return HudConfigSanitizer.sanitize(new HudConfig());
        }
    }

}
