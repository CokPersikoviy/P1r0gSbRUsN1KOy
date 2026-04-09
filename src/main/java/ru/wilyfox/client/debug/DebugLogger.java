package ru.wilyfox.client.debug;

import org.slf4j.Logger;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.HudConfig;

public final class DebugLogger {
    private DebugLogger() {
    }

    public static boolean isEnabled() {
        HudConfig config = ConfigManager.get();
        return config != null && config.render != null && config.render.debug;
    }

    public static void debug(Logger logger, String message, Object... args) {
        if (isEnabled()) {
            logger.debug(message, args);
        }
    }

    public static void info(Logger logger, String message, Object... args) {
        if (isEnabled()) {
            logger.info(message, args);
        }
    }

    public static void warn(Logger logger, String message, Object... args) {
        logger.warn(message, args);
    }

    public static void error(Logger logger, String message, Object... args) {
        logger.error(message, args);
    }
}
