package ru.wilyfox.client.chat;

import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChatRepeatGroupsTest {
    private static ChatMessageEntry entry(String text) {
        var message = new GuiMessage(0, Component.literal(text), null, GuiMessageSource.SYSTEM_CLIENT, null);
        return new ChatMessageEntry(message, Instant.EPOCH, ChatTab.ALL, text, 1);
    }
    @Test void timestampsDoNotBreakConsecutiveRunsButOtherMessagesDo() {
        var groups = new ChatRepeatGroups();
        var first = entry("[12:00:01] Способность активировалась!");
        var second = entry("[12:00:02] Способность активировалась!");
        var third = entry("(12:00:03) Способность активировалась!");
        groups.append(first); groups.append(second); groups.append(third);
        assertEquals(0, groups.count(first.message())); assertEquals(0, groups.count(second.message()));
        assertEquals(3, groups.count(third.message()));
        assertFalse(third.component().getString().contains("[x3]"));
        groups.append(entry("Other message"));
        var next = entry("Способность активировалась!"); groups.append(next);
        assertEquals(1, groups.count(next.message()));
        groups.rebuild(List.of(first, third)); assertEquals(2, groups.count(third.message()));
    }
    @Test void EqualCaptionsWithDifferentClickActionsAreSeparateMessages() {
        var first = entry("Image [photo.jpg]"); var second = entry("Image [photo.jpg]");
        var a = new ChatMessageEntry(new GuiMessage(0, first.component().copy().withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("image:1"))),
                null, GuiMessageSource.SYSTEM_CLIENT, null), Instant.EPOCH, ChatTab.ALL, "image", 1);
        var b = new ChatMessageEntry(new GuiMessage(0, second.component().copy().withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("image:2"))),
                null, GuiMessageSource.SYSTEM_CLIENT, null), Instant.EPOCH, ChatTab.ALL, "image", 1);
        var groups = new ChatRepeatGroups(); groups.append(a); groups.append(b);
        assertEquals(1, groups.count(a.message())); assertEquals(1, groups.count(b.message()));
        groups.clear(); assertEquals(1, groups.count(b.message()));
    }
}
