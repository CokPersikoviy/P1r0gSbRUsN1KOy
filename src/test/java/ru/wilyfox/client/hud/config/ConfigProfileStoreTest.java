package ru.wilyfox.client.hud.config;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class ConfigProfileStoreTest {
    @TempDir Path directory;
    private static JsonObject config(int offset) {
        var settings = new HudConfig(); settings.playerHealthBars.verticalOffset = offset;
        var keys = new JsonObject(); keys.addProperty("Social", "key.keyboard.c");
        return SharedConfigCodec.pack(HudConfigCodec.createGson().toJsonTree(settings), keys);
    }
    @Test void downloadedEditsAndRestartNeverReplaceMain() throws Exception {
        var main = config(4); var store = new ConfigProfileStore(directory, main);
        String downloaded = store.add("Fox", config(20));
        assertEquals("main", store.activeId());
        store.select(downloaded); store.saveActive(config(30));
        var restarted = new ConfigProfileStore(directory, config(-10));
        assertEquals(downloaded, restarted.activeId());
        assertEquals(main, restarted.read("main"));
        assertEquals(config(30), restarted.read(downloaded));
        assertThrows(IOException.class, () -> restarted.delete("main"));
        assertThrows(IOException.class, () -> restarted.delete(downloaded));
        restarted.select("main"); restarted.delete(downloaded);
        assertEquals(1, restarted.entries().size()); assertEquals(main, restarted.read("main"));
    }
    @Test void exchangeDropsHistoryAndKeepsChatTabsAndBinds() {
        var source = config(4);
        source.addProperty("gameToken", "never-share");
        source.add("chatHistory", new JsonArray());
        var settings = source.getAsJsonObject("settings");
        settings.addProperty("clans", "never-share");
        settings.getAsJsonObject("render").addProperty("chatHistory", "never-share");
        settings.getAsJsonObject("chatWidgets").getAsJsonObject("ChatWidgetGlobal").addProperty("title", "Public");
        source.getAsJsonObject("keybinds").addProperty("key.forward", "key.keyboard.w");
        var clean = SharedConfigCodec.normalize(source);
        assertFalse(clean.toString().contains("never-share"));
        assertEquals(source.get("settings").getAsJsonObject().get("chatTabOrder"), clean.getAsJsonObject("settings").get("chatTabOrder"));
        assertTrue(clean.getAsJsonObject("settings").has("chatWidgets"));
        assertEquals("Public", clean.getAsJsonObject("settings").getAsJsonObject("chatWidgets")
                .getAsJsonObject("ChatWidgetGlobal").get("title").getAsString());
        assertEquals(1, clean.getAsJsonObject("keybinds").size());
        assertEquals("key.keyboard.c", clean.getAsJsonObject("keybinds").get("Social").getAsString());
    }
    @Test void corruptDownloadFallsBackToMainAndLibraryIsBounded() throws Exception {
        var store = new ConfigProfileStore(directory, config(4));
        String id = store.add("Fox", config(2)); store.select(id);
        java.nio.file.Files.writeString(directory.resolve(id + ".json"), "{broken");
        var restarted = new ConfigProfileStore(directory, config(10));
        assertEquals("main", restarted.activeId());
        for (int i = 1; i < ConfigProfileStore.MAX_DOWNLOADS; i++) restarted.add("Fox", config(i));
        assertThrows(IOException.class, () -> restarted.add("Fox", config(5)));
    }
    @Test void sharedConfigKeepsClickerModeAndItsNativeBinding() {
        var source = config(4);
        source.getAsJsonObject("settings").getAsJsonObject("clicker").addProperty("actionMode", "HOLD");
        source.getAsJsonObject("keybinds").addProperty("Clicker mode", "key.mouse.4");
        var clean = SharedConfigCodec.parse(SharedConfigCodec.encode(source));
        assertEquals("HOLD", clean.getAsJsonObject("settings").getAsJsonObject("clicker").get("actionMode").getAsString());
        assertEquals("key.mouse.4", clean.getAsJsonObject("keybinds").get("Clicker mode").getAsString());
        source.getAsJsonObject("keybinds").remove("Clicker mode");
        assertDoesNotThrow(() -> SharedConfigCodec.normalize(source));
    }
    @Test void sharedPayloadStaysCompactAndRetainsNewWidgetSettings() {
        var value = config(4);
        var settings = value.getAsJsonObject("settings");
        for (String field : java.util.List.of("levelGoal", "nearbyWorms", "nearbyBarrels", "nearbyCrystals"))
            assertTrue(settings.has(field), "Missing shared settings: " + field);
        String encoded = SharedConfigCodec.encode(value);
        assertFalse(encoded.contains("\n"), "Wire encoder must not use the pretty-printing settings Gson");
        assertEquals(value, SharedConfigCodec.parse(encoded));
        assertTrue(encoded.length() < HudConfigCodec.createGson().toJson(value).length());
    }
}
