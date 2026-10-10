package ru.wilyfox.client.chat;

import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.Test;
import ru.wilyfox.client.hud.config.LowHpMessageElement;
import ru.wilyfox.client.hud.config.LowHpMessageFormatConfig;

import static org.junit.jupiter.api.Assertions.*;
import static ru.wilyfox.client.hud.config.LowHpMessageElement.*;

class LowHpMessageFormatterTest {
    @Test
    void respawnUsesIdentityColorsInBothChatsEvenWhenLowHpFieldsAreHidden() {
        var config = new LowHpMessageFormatConfig();
        config.element(NAME).colorCode = "§A";
        config.element(LEVEL).colorCode = "&d";
        config.element(NAME).visible = false;
        config.element(LEVEL).visible = false;
        for (String announcement : new String[]{"возродился", "возродится через 30с"}) {
            var message = LowHpMessageFormatter.formatRespawn(config, "§cКригер", 15, announcement);
            assertEquals("Кригер [15] " + announcement, message.component().getString());
            assertEquals("&aКригер&f &d[15]&7 " + announcement + "&r", message.clanText());
            var parts = message.component().getSiblings();
            assertEquals(0x55FF55, parts.getFirst().getStyle().getColor().getValue());
            assertEquals(0xFF55FF, parts.get(2).getStyle().getColor().getValue());
            assertEquals(0xAAAAAA, parts.getLast().getStyle().getColor().getValue());
        }
    }

    @Test
    void formatsReadableMessageAndKeepsColorCodesOutOfLocalText() {
        var message = LowHpMessageFormatter.preview(new LowHpMessageFormatConfig());
        assertEquals("[PE1.2] Бессмертный Легион [130] [Прок] — 125❤ (20%) [Стадия 2/4]", message.plainText());
        assertEquals(message.plainText(), message.component().getString());
        assertTrue(message.clanText().contains("&e[PE1.2]"));
        assertTrue(message.clanText().contains("&c125❤"));
        assertTrue(message.clanText().endsWith("&7[Стадия 2/4]&r"));
        assertFalse(message.clanText().contains("§"));
    }

    @Test
    void hidesIdentityAndStageIndependentlyFromHealth() {
        var config = new LowHpMessageFormatConfig();
        for (var part : new LowHpMessageElement[]{SERVER, NAME, LEVEL, CURSE, STAGE}) config.element(part).visible = false;
        assertEquals("125❤ (20%)", LowHpMessageFormatter.preview(config).plainText());
        config.element(HEALTH).visible = false;
        assertEquals("20%", LowHpMessageFormatter.preview(config).plainText());
        config.element(PERCENT).visible = false;
        config.element(STAGE).visible = true;
        assertEquals("[Стадия 2/4]", LowHpMessageFormatter.preview(config).plainText());
    }

    @Test
    void emptyConfigurationProducesNoLocalOrClanMessage() {
        var config = new LowHpMessageFormatConfig();
        for (var part : LowHpMessageElement.values()) config.element(part).visible = false;
        var message = LowHpMessageFormatter.preview(config);
        assertTrue(message.isEmpty());
        assertEquals("", message.clanText());
        assertEquals("", message.component().getString());
    }

    @Test
    void everyVisibilityCombinationHasNoDanglingSpacesOrSeparators() {
        var parts = LowHpMessageElement.values();
        for (int mask = 0; mask < 1 << parts.length; mask++) {
            var config = new LowHpMessageFormatConfig();
            for (int i = 0; i < parts.length; i++) config.element(parts[i]).visible = (mask & 1 << i) != 0;
            String text = LowHpMessageFormatter.preview(config).plainText();
            assertEquals(text.trim(), text, "Visibility mask " + mask);
            assertFalse(text.startsWith("—") || text.endsWith("—") || text.contains("  "), text);
        }
    }

    @Test
    void appliesEveryConfiguredColorWithoutLeakingItIntoTheNextElement() {
        var config = new LowHpMessageFormatConfig();
        for (var part : LowHpMessageElement.values()) config.element(part).colorCode = "&" + part.ordinal();
        config.element(NAME).colorCode = "§A";
        var message = LowHpMessageFormatter.preview(config);
        for (var fragment : message.fragments()) {
            assertNotNull(ChatFormatting.getByCode(fragment.color()));
        }
        var local = message.component().getSiblings();
        assertEquals(0x55FF55, local.stream().filter(part -> part.getString().equals("Бессмертный Легион"))
                .findFirst().orElseThrow().getStyle().getColor().getValue());
        assertEquals(0x00AA00, local.stream().filter(part -> part.getString().equals("[130]"))
                .findFirst().orElseThrow().getStyle().getColor().getValue());
        assertEquals(0xAA0000, local.stream().filter(part -> part.getString().equals("125❤"))
                .findFirst().orElseThrow().getStyle().getColor().getValue());
        assertTrue(message.clanText().contains("&aБессмертный Легион"));
        assertTrue(message.clanText().contains("&2[130]"));
    }

    @Test
    void invalidColorUsesDefaultAndCannotInsertExtraMessageText() {
        var config = new LowHpMessageFormatConfig();
        config.element(NAME).colorCode = "&c injected text";
        config.element(HEALTH).colorCode = null;
        assertFalse(LowHpMessageFormatter.isValidColor("&g"));
        assertFalse(LowHpMessageFormatter.isValidColor("&"));
        assertFalse(LowHpMessageFormatter.isValidColor("&r"));
        assertTrue(LowHpMessageFormatter.isValidColor("§C"));
        var message = LowHpMessageFormatter.preview(config);
        assertTrue(message.clanText().contains("&6Бессмертный Легион"));
        assertTrue(message.clanText().contains("&c125❤"));
        assertFalse(message.clanText().contains("injected"));
    }

    @Test
    void omitsUnavailableFieldsAndKeepsDecimalHealthAndPercentFallback() {
        var config = new LowHpMessageFormatConfig();
        var message = LowHpMessageFormatter.format(config, "", "Кригер", 15, 125.5, 20, false, "");
        assertEquals("Кригер [15] — 125.5❤ (20%)", message.plainText());
        message = LowHpMessageFormatter.format(config, null, "Кригер", 15, -1, 20, false, null);
        assertEquals("Кригер [15] — 20%", message.plainText());
    }
}
