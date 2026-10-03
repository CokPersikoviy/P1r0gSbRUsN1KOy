package ru.wilyfox.client.utility;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import ru.wilyfox.client.hud.config.RenderConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerNameFormatterTest {
    @Test
    void cleanNamesUseRealProfileNameAndDropServerDecoration() {
        Component serverName = Component.literal("[Clan] Nickname").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Component.literal(" rank"));

        Component result = PlayerNameFormatter.baseName(serverName, "RealPlayer", true);

        assertEquals("RealPlayer", result.getString());
        assertEquals(0xFFFFFF, result.getStyle().getColor().getValue());
        assertFalse(result.getStyle().isBold());
        assertTrue(result.getSiblings().isEmpty());
        assertEquals("[Clan] Nickname rank", serverName.getString());
    }

    @Test
    void disabledByDefaultAndPreservesOriginalComponent() {
        Component serverName = Component.literal("Nickname").withStyle(ChatFormatting.GREEN);

        assertFalse(new RenderConfig().cleanPlayerNames);
        assertSame(serverName, PlayerNameFormatter.baseName(serverName, "RealPlayer", false));
    }

    @Test
    void missingServerNameFallsBackToRealProfileName() {
        assertEquals("RealPlayer", PlayerNameFormatter.baseName(null, "RealPlayer", false).getString());
    }
}
