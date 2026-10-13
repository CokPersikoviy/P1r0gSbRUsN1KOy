package ru.wilyfox.client.boss;

import org.junit.jupiter.api.Test;
import ru.wilyfox.client.hud.config.*;
import com.google.gson.JsonParser;
import static org.junit.jupiter.api.Assertions.*;

class BossTimerRowFormatterTest {
    @Test void capturesMythicalEventAndCollectibleAreIndependentWithNameHidden() {
        var config = new BossWidgetConfig();
        var colors = BossTimerRowFormatter.appearance(config, 0xFF123456);
        assertEquals("✗ ✦ Цербер ◇", BossTimerRowFormatter.label(config, "Цербер", true, true, false, true, colors).getString());
        config.showCapture = false;
        assertEquals("✦ Цербер ◇", BossTimerRowFormatter.label(config, "Цербер", true, true, false, true, colors).getString());
        config.showMythical = false;
        assertEquals("Цербер ✔", BossTimerRowFormatter.label(config, "Цербер", true, true, true, true, colors).getString());
        assertEquals("✔", BossTimerRowFormatter.label(config, "Цербер", true, true, true, false, colors).getString());
        config.showCollectibles = false;
        assertEquals("", BossTimerRowFormatter.label(config, "Цербер", true, true, true, false, colors).getString());
        config.showCapture = true; config.showMythical = true; config.showCollectibles = true;
        assertEquals("✗ ✦", BossTimerRowFormatter.label(config, "Цербер", true, true, null, false, colors).getString());
    }
    @Test void customColorsDoNotLeakBetweenFragmentsAndInvalidCodesUseTheme() {
        var config = new BossWidgetConfig();
        config.colorCode(BossTimerElement.CAPTURE, "&c"); config.colorCode(BossTimerElement.MYTHICAL, "§b");
        config.colorCode(BossTimerElement.COLLECTIBLES, "&a"); config.colorCode(BossTimerElement.NAME, "&z");
        var colors = BossTimerRowFormatter.appearance(config, 0xFF123456);
        var text = BossTimerRowFormatter.label(config, "Цербер", true, true, true, true, colors);
        assertEquals(0xFF5555, text.getSiblings().get(0).getStyle().getColor().getValue());
        assertEquals(0x55FFFF, text.getSiblings().get(2).getStyle().getColor().getValue());
        assertEquals(0x123456, text.getSiblings().get(4).getStyle().getColor().getValue());
        assertEquals(0x55FF55, text.getSiblings().get(6).getStyle().getColor().getValue());
        config.colorCode(BossTimerElement.NAME, "");
        assertEquals(0xFF987654, BossTimerRowFormatter.color(config, BossTimerElement.NAME, 0xFF987654));
        assertEquals("&b", config.colorCode(BossTimerElement.MYTHICAL));
    }
    @Test void legacyVisibilityIsKeptAndNewSettingsPersistThroughRealCodec() {
        var gson = HudConfigCodec.createGson();
        var config = HudConfigCodec.decode(gson, JsonParser.parseString("{\"mainLayout\":{\"widgets\":[]},\"bossWidget\":{\"showName\":false,\"showIcons\":false,\"showCollectibles\":false}}"));
        assertFalse(config.bossWidget.showName); assertFalse(config.bossWidget.showIcons); assertFalse(config.bossWidget.showCollectibles);
        assertTrue(config.bossWidget.showCapture); assertTrue(config.bossWidget.showMythical); assertTrue(config.bossWidget.showTitle);
        config.bossWidget.showCapture = false; config.bossWidget.showTitle = false;
        config.bossWidget.colorCode(BossTimerElement.TIMER, "&d");
        var restored = HudConfigCodec.decode(gson, gson.toJsonTree(config));
        assertFalse(restored.bossWidget.showCapture); assertFalse(restored.bossWidget.showTitle);
        assertEquals("&d", restored.bossWidget.colorCode(BossTimerElement.TIMER));
        restored.bossWidget.colors = null;
        assertEquals("", restored.bossWidget.colorCode(BossTimerElement.NAME));
    }
}
