package ru.wilyfox.client.hud.config;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import ru.wilyfox.client.chat.ChatTab;
import static org.junit.jupiter.api.Assertions.*;

class ChatWindowsConfigTest {
    @Test void defaultTabsAreDockedAndNeverAddedByLegacyMigration() {
        var config = HudConfigCodec.decode(HudConfigCodec.createGson(), JsonParser.parseString("{}"));
        assertEquals(7, config.chatWidgets.size());
        for (var catalog : WidgetCatalog.values()) if (catalog.chatChannel() != null) {
            var tab = config.chatWidgets.get(catalog.key());
            assertEquals(catalog.chatChannel(), tab.channel);
            assertFalse(tab.detached);
            assertFalse(config.mainLayout.widgets.contains(catalog.key()));
        }
    }
    @Test void detachedCustomTabsSurviveRoundTripWithTheirLayoutsAndFilters() {
        String id = "ChatWidgetCustom_aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        var config = HudConfigSanitizer.sanitize(new HudConfig());
        config.render.fixedChatWidgetFont = true;
        var tab = new ChatWidgetConfig(ChatTab.CLAN);
        tab.detached = true; tab.title = "Bosses"; tab.textFilter = "Legion"; tab.rows = 7; tab.width = 170;
        config.chatWidgets.put(id, tab); config.mainLayout.widgets.add(id);
        var location = new LocationWidgetLayoutConfig(); location.widgets.add(id); location.locationVisibility.add("shaft_1");
        var position = new WidgetLayoutConfig(); position.x = 18; position.scale = 1.2f;
        location.placements.put(id, position); config.locationLayouts.put("mine", location);
        var gson = HudConfigCodec.createGson();
        var restored = HudConfigCodec.decode(gson, JsonParser.parseString(gson.toJson(config)));
        assertTrue(restored.chatWidgets.get(id).detached);
        assertTrue(restored.render.fixedChatWidgetFont);
        assertEquals("Legion", restored.chatWidgets.get(id).textFilter);
        assertEquals(ChatTab.CLAN, restored.chatWidgets.get(id).channel);
        assertTrue(HudLayoutResolver.widgets(restored, null, "shaft_1").contains(id));
        assertEquals(1.2f, HudLayoutResolver.placement(restored, id, null, "shaft_1").scale);
    }
    @Test void corruptSettingsAreRepairedAndCustomWindowsAreBounded() {
        var config = new HudConfig();
        config.chatWidgets.put("ChatWidgetClan", null);
        for (int i = 0; i < 30; i++) {
            var tab = new ChatWidgetConfig(); tab.rows = Integer.MAX_VALUE; tab.width = -1; tab.title = null; tab.textFilter = null;
            config.chatWidgets.put("ChatWidgetCustom_" + String.format("%08x", i) + "-bbbb-cccc-dddd-eeeeeeeeeeee", tab);
        }
        var repaired = HudConfigSanitizer.sanitize(config);
        assertEquals(19, repaired.chatWidgets.size());
        assertEquals(ChatTab.CLAN, repaired.chatWidgets.get("ChatWidgetClan").channel);
        var custom = repaired.chatWidgets.values().stream().filter(tab -> tab.width == 100).findFirst().orElseThrow();
        assertEquals(30, custom.rows); assertEquals(ChatTab.ALL, custom.channel); assertEquals("", custom.textFilter);
        assertNull(WidgetCatalog.find("ChatWidgetCustom_bad"));
    }
    @Test void deletingBuiltInTabsSurvivesReloadAndClearsTheirLayouts() {
        var config = HudConfigSanitizer.sanitize(new HudConfig());
        String key = WidgetCatalog.CHAT_TRADE.key();
        config.chatWidgets.get(key).deleted = true;
        config.chatWidgets.get(key).detached = true;
        config.mainLayout.widgets.add(key); config.widgetLayouts.put(key, new WidgetLayoutConfig());
        var location = new LocationWidgetLayoutConfig(); location.widgets.add(key); location.placements.put(key, new WidgetLayoutConfig());
        config.locationLayouts.put("mine", location);
        var gson = HudConfigCodec.createGson();
        var restored = HudConfigCodec.decode(gson, JsonParser.parseString(gson.toJson(config)));
        assertTrue(restored.chatWidgets.get(key).deleted);
        assertFalse(restored.chatWidgets.get(key).detached);
        assertFalse(restored.mainLayout.widgets.contains(key));
        assertFalse(restored.widgetLayouts.containsKey(key));
        assertFalse(restored.locationLayouts.get("mine").widgets.contains(key));
        assertFalse(restored.locationLayouts.get("mine").placements.containsKey(key));
    }
    @Test void allRemainsCanonicalWhileOtherBuiltInTabsKeepTheirEdits() {
        var config = HudConfigSanitizer.sanitize(new HudConfig());
        var all = config.chatWidgets.get(WidgetCatalog.CHAT_ALL.key());
        all.deleted = true; all.channel = ChatTab.TRADE; all.title = "Changed"; all.textFilter = "fish";
        all.width = 200; all.rows = 2; all.showTitle = false; all.detached = true;
        var global = config.chatWidgets.get(WidgetCatalog.CHAT_GLOBAL.key());
        global.title = "Bosses"; global.channel = ChatTab.CLAN; global.textFilter = "Legion";
        var gson = HudConfigCodec.createGson();
        var restored = HudConfigCodec.decode(gson, JsonParser.parseString(gson.toJson(config)));
        all = restored.chatWidgets.get(WidgetCatalog.CHAT_ALL.key());
        assertFalse(all.deleted); assertEquals(ChatTab.ALL, all.channel); assertEquals("ALL", all.title);
        assertEquals("", all.textFilter); assertEquals(280, all.width); assertEquals(10, all.rows); assertTrue(all.showTitle);
        assertFalse(all.detached, "ALL stays pinned in the dock");
        global = restored.chatWidgets.get(WidgetCatalog.CHAT_GLOBAL.key());
        assertEquals("Bosses", global.title); assertEquals(ChatTab.CLAN, global.channel); assertEquals("Legion", global.textFilter);
    }

    @Test void savedTabOrderRepairsDuplicatesButRetainsDetachedWindowSlots() {
        var config = HudConfigSanitizer.sanitize(new HudConfig());
        String custom = "ChatWidgetCustom_aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        config.chatWidgets.put(custom, new ChatWidgetConfig(ChatTab.CLAN));
        config.chatWidgets.get("ChatWidgetTrade").deleted = true;
        config.chatWidgets.get("ChatWidgetClan").detached = true;
        config.chatTabOrder = new java.util.ArrayList<>(java.util.Arrays.asList(custom, "ChatWidgetClan", "ChatWidgetAll",
                "ChatWidgetClan", "unknown", null, "ChatWidgetTrade"));
        var gson = HudConfigCodec.createGson();
        var restored = HudConfigCodec.decode(gson, JsonParser.parseString(gson.toJson(config)));
        assertEquals(java.util.List.of("ChatWidgetAll", "ChatWidgetFH", custom, "ChatWidgetClan", "ChatWidgetGlobal", "ChatWidgetLocal", "ChatWidgetPrivate"), restored.chatTabOrder);
        assertTrue(restored.chatWidgets.get("ChatWidgetClan").detached);
        assertEquals(restored.chatTabOrder, HudConfigCodec.decode(gson, JsonParser.parseString(gson.toJson(restored))).chatTabOrder);
    }
    @Test void oldConfigsGetStandardOrder() {
        var restored = HudConfigCodec.decode(HudConfigCodec.createGson(), JsonParser.parseString("{\"chatTabOrder\":null}"));
        assertEquals(java.util.List.of("ChatWidgetAll", "ChatWidgetFH", "ChatWidgetGlobal", "ChatWidgetTrade", "ChatWidgetLocal", "ChatWidgetClan", "ChatWidgetPrivate"), restored.chatTabOrder);
    }
    @Test void pinnedTabsCannotBeDetachedDeletedOrPlacedByAnOldConfig() {
        var config = HudConfigSanitizer.sanitize(new HudConfig());
        for (var key : java.util.List.of("ChatWidgetAll", "ChatWidgetFH")) {
            var tab = config.chatWidgets.get(key); tab.deleted = true; tab.detached = true; tab.title = "Changed"; tab.channel = ChatTab.CLAN;
            config.mainLayout.widgets.add(key); config.widgetLayouts.put(key, new WidgetLayoutConfig());
            var location = new LocationWidgetLayoutConfig(); location.widgets.add(key); location.placements.put(key, new WidgetLayoutConfig());
            config.locationLayouts.put(key, location);
        }
        config.chatTabOrder = new java.util.ArrayList<>(java.util.List.of("ChatWidgetTrade", "ChatWidgetFH", "ChatWidgetAll"));
        var repaired = HudConfigSanitizer.sanitize(config);
        assertEquals(java.util.List.of("ChatWidgetAll", "ChatWidgetFH"), repaired.chatTabOrder.subList(0, 2));
        for (var key : java.util.List.of("ChatWidgetAll", "ChatWidgetFH")) {
            assertFalse(repaired.chatWidgets.get(key).detached); assertFalse(repaired.chatWidgets.get(key).deleted);
            assertFalse(repaired.mainLayout.widgets.contains(key)); assertFalse(repaired.locationLayouts.get(key).widgets.contains(key));
            assertFalse(repaired.widgetLayouts.containsKey(key)); assertFalse(repaired.locationLayouts.get(key).placements.containsKey(key));
        }
        assertEquals(ChatTab.ALL, repaired.chatWidgets.get("ChatWidgetAll").channel);
        assertEquals(ChatTab.FH, repaired.chatWidgets.get("ChatWidgetFH").channel);
    }

}
