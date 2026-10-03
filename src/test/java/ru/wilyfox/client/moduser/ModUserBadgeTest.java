package ru.wilyfox.client.moduser;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModUserBadgeTest {
    @Test
    void insertingBadgePreservesStyleInheritedByNestedName() {
        Component name = Component.literal("Rank ").withStyle(ChatFormatting.RED)
                .append(Component.literal("Player").withStyle(ChatFormatting.BOLD));
        Component decorated = ModUserBadge.insert(name, 5);
        List<Style> nameStyles = new ArrayList<>();
        decorated.visit((style, text) -> {
            if (text.equals("Player")) {
                nameStyles.add(style);
            }
            return Optional.empty();
        }, Style.EMPTY);

        assertEquals(1, nameStyles.size());
        assertEquals(0xFF5555, nameStyles.getFirst().getColor().getValue());
        assertTrue(nameStyles.getFirst().isBold());
        assertEquals(name.getString(), ModUserBadge.strip(decorated).getString());
    }

    @Test
    void stripsBadgeAndSeparatorInTheSameLiteral() {
        assertEquals("Player", ModUserBadge.strip(Component.literal("\uF8FF Player")).getString());
    }

    @Test
    void doesNotRemoveALaterSpaceWhenBadgeHasNoSeparator() {
        Component message = Component.literal("\uF8FF")
                .append(Component.empty())
                .append(Component.literal("Player"))
                .append(Component.literal(" tail"));
        assertEquals("Player tail", ModUserBadge.strip(message).getString());
    }
}
