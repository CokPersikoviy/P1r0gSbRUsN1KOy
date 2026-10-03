package ru.wilyfox.client.hud.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
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

import static ru.wilyfox.FrogHelper.LOGGER;
import static ru.wilyfox.client.debug.DebugLogger.error;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("froghelper.json");
    private static HudConfig CONFIG = load();

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
        return CONFIG.widgetLayouts.get(key);
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
        if (widget == null || widget.getConfigKey() == null || widget.getConfigKey().isBlank()) {
            return;
        }

        updateWidgetLayout(widget,
                Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                Minecraft.getInstance().getWindow().getGuiScaledHeight());
        save();
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
        WidgetLayoutConfig layout = CONFIG.widgetLayouts.computeIfAbsent(widget.getConfigKey(), ignored -> new WidgetLayoutConfig());
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
        layout.hiddenInGameplay = widget.isHiddenInGameplay();
    }

    private static double clampFraction(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static HudConfig load() {
        if (!Files.exists(CONFIG_PATH)) {
            return HudConfigSanitizer.sanitize(new HudConfig());
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            return HudConfigSanitizer.sanitize(GSON.fromJson(reader, HudConfig.class));
        } catch (Exception exception) {
            error(LOGGER, "Failed to load FrogHelper config from {}", CONFIG_PATH, exception);
            return HudConfigSanitizer.sanitize(new HudConfig());
        }
    }

}
