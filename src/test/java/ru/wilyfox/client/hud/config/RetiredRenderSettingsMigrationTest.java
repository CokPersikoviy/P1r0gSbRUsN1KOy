package ru.wilyfox.client.hud.config;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RetiredRenderSettingsMigrationTest {
    @Test
    void oldLocalAndSharedConfigsKeepGameplaySettingsAndDropRetiredRendererOptions() {
        var gson = HudConfigCodec.createGson();
        var settings = JsonParser.parseString("""
                {"render":{"extremeDisplayMode":"LIVE_EXTRACT","extremeAllItemDisplays":true,
                  "extremePauseDisplayTicks":true,"displayModelBudget":true,"displayModelBudgetMicros":650,
                  "adaptiveDisplayModelBudget":false,"modelEngineNpcBudget":true,"npcGroupsPerFrame":3,
                  "adaptiveNpcBudget":false,"particleLightCache":true,"particleEmptyCollision":true,
                  "clearWater":true,"fixedChatWidgetFont":true,"widgetChrome":"BARE"},
                 "clicker":{"cps":11}}
                """).getAsJsonObject();
        var decoded = HudConfigCodec.decode(gson, settings);
        assertTrue(decoded.render.clearWater);
        assertTrue(decoded.render.fixedChatWidgetFont);
        assertEquals(WidgetChrome.BARE, decoded.render.widgetChrome);
        assertEquals(11, decoded.clicker.cps);

        var saved = gson.toJsonTree(decoded).getAsJsonObject();
        var shared = SharedConfigCodec.pack(settings, new com.google.gson.JsonObject());
        assertEquals(saved, shared.getAsJsonObject("settings"));
        assertEquals(11, shared.getAsJsonObject("settings").getAsJsonObject("clicker").get("cps").getAsInt());
        for (var field : settings.getAsJsonObject("render").keySet()) {
            if (field.equals("clearWater") || field.equals("fixedChatWidgetFont") || field.equals("widgetChrome")) continue;
            assertFalse(saved.getAsJsonObject("render").has(field), field);
        }
    }
}
