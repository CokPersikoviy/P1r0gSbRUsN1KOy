package ru.wilyfox.client.hud.config;

import java.util.EnumMap;
import java.util.Map;

public final class LowHpMessageFormatConfig {
    public Map<LowHpMessageElement, ElementConfig> elements = new EnumMap<>(LowHpMessageElement.class);

    public LowHpMessageFormatConfig() { sanitize(); }

    public ElementConfig element(LowHpMessageElement element) {
        if (elements == null) elements = new EnumMap<>(LowHpMessageElement.class);
        return elements.computeIfAbsent(element, key -> new ElementConfig(key.defaultColor()));
    }

    public void sanitize() {
        for (LowHpMessageElement part : LowHpMessageElement.values()) {
            ElementConfig config = element(part);
            // Keep incomplete input while editing; the formatter safely falls back to the default.
            if (config.colorCode == null) config.colorCode = part.defaultColor();
        }
    }

    public static final class ElementConfig {
        public boolean visible = true;
        public String colorCode;

        public ElementConfig() {}
        public ElementConfig(String colorCode) { this.colorCode = colorCode; }
    }
}
