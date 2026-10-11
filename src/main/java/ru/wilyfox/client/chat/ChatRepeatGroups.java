package ru.wilyfox.client.chat;

import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Style;
import ru.wilyfox.utils.Formatting;
import java.util.*;

/** Display-only runs over original messages; timestamps never participate in equality. */
final class ChatRepeatGroups {
    private final IdentityHashMap<GuiMessage, Integer> counts = new IdentityHashMap<>();
    private GuiMessage last;
    private Key lastKey;
    int count(GuiMessage message) { return counts.getOrDefault(message, 1); }
    void append(ChatMessageEntry entry) {
        var message = entry.message(); var key = key(entry);
        int count = 1;
        if (last != null && key.equals(lastKey)) {
            count = Math.min(999_999, count(last) + 1); counts.put(last, 0);
        }
        counts.put(message, count); last = message; lastKey = key;
    }
    void rebuild(Iterable<ChatMessageEntry> entries) {
        clear(); for (var entry : entries) append(entry);
    }
    void forget(GuiMessage message) {
        counts.remove(message);
        if (message == last) { last = null; lastKey = null; }
    }
    void clear() { counts.clear(); last = null; lastKey = null; }
    private static Key key(ChatMessageEntry entry) {
        String text = ChatTimestampFormatter.stripTimestampPrefix(Formatting.stripMinecraftFormatting(
                ChatMessageSanitizer.forLogic(entry.component().getString())));
        // Equal captions with different links/images/actions are different messages.
        var actions = new ArrayList<Object>();
        entry.component().visit((style, part) -> {
            if (style.getClickEvent() != null) { actions.add(part); actions.add(style.getClickEvent()); }
            if (style.getInsertion() != null) { actions.add(part); actions.add(style.getInsertion()); }
            return Optional.empty();
        }, Style.EMPTY);
        // FH keeps Discord and Minecraft origins distinct even for the same display name/text.
        boolean discord = entry.tab() == ChatTab.FH && entry.searchText().startsWith("discord ");
        return new Key(text, entry.tab(), entry.message().source(), discord, actions);
    }
    private record Key(String text, ChatTab tab, Object source, boolean discord, List<Object> actions) {}
}
