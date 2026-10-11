package ru.wilyfox.client.hud.config;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import ru.wilyfox.client.chat.ChatTab;
import static org.junit.jupiter.api.Assertions.*;

class WidgetAppearanceConfigTest {
    @Test void legacyConfigsKeepTheirAppearanceAndValuesAreRepaired() {
        var gson = HudConfigCodec.createGson();
        var legacy = HudConfigCodec.decode(gson, JsonParser.parseString("{}"));
        assertTrue(legacy.widgetAppearance.isEmpty());
        var defaults = new WidgetAppearanceConfig();
        assertEquals(1f, defaults.opacity()); assertEquals(1f, defaults.backgroundOpacity());
        var repaired = HudConfigCodec.decode(gson, JsonParser.parseString("""
                {"widgetAppearance": {
                  "BossHudWidget": {"opacityPercent": -10, "backgroundOpacityPercent": 150},
                  "FpsWidget": {}, "unknown": {}, "ActiveRunesWidget": null
                }}
                """));
        assertEquals(2, repaired.widgetAppearance.size());
        assertEquals(0f, repaired.widgetAppearance.get("BossHudWidget").opacity());
        assertEquals(1f, repaired.widgetAppearance.get("BossHudWidget").backgroundOpacity());
        assertEquals(1f, repaired.widgetAppearance.get("FpsWidget").opacity());
    }

    @Test void independentSettingsAndCustomChatSurviveReload() {
        String custom = "ChatWidgetCustom_aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        var config = new HudConfig();
        config.chatWidgets.put(custom, new ChatWidgetConfig(ChatTab.CLAN));
        var boss = new WidgetAppearanceConfig(); boss.opacityPercent = 65; boss.backgroundOpacityPercent = 0;
        var chat = new WidgetAppearanceConfig(); chat.opacityPercent = 90; chat.backgroundOpacityPercent = 25;
        config.widgetAppearance.put("BossHudWidget", boss); config.widgetAppearance.put(custom, chat);
        var gson = HudConfigCodec.createGson();
        var restored = HudConfigCodec.decode(gson, JsonParser.parseString(gson.toJson(config)));
        assertEquals(65, restored.widgetAppearance.get("BossHudWidget").opacityPercent);
        assertEquals(0, restored.widgetAppearance.get("BossHudWidget").backgroundOpacityPercent);
        assertEquals(90, restored.widgetAppearance.get(custom).opacityPercent);
        assertEquals(25, restored.widgetAppearance.get(custom).backgroundOpacityPercent);
    }
}
